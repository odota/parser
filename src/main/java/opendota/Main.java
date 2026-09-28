package opendota;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.time.Duration;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class Main {
    /**
     * Marks an IOException as having originated while decompressing the
     * downloaded bytes (corrupted/truncated compressed data), as opposed
     * to an error thrown by Parse's own logic.
     */
    static class DecompressionException extends IOException {
        DecompressionException(Throwable cause) {
            super(cause);
        }
    }

    // Caps concurrent /blob requests (and threads) since each one holds full
    // in-memory copies of the replay (raw, decompressed, and parsed output)
    // at once; an unbounded thread pool could spawn enough concurrent
    // requests to exhaust available memory under load.
    static final int MAX_THREADS = Math.max(1, (int) Math.min(Runtime.getRuntime().availableProcessors() * 5, 40));

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(Integer.valueOf("5600")), 0);
        server.createContext("/", new MyHandler());
        server.createContext("/healthz", new HealthHandler());
        server.createContext("/blob", new BlobHandler());
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(MAX_THREADS));
        server.start();

        // Re-register ourselves
        Timer timer = new Timer();
        TimerTask task = new RegisterTask();
        timer.schedule(task, 0, 5000);
    }

    static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            t.sendResponseHeaders(200, 0);
            InputStream is = t.getRequestBody();
            OutputStream os = t.getResponseBody();
            boolean blob = false;
            if (t.getRequestURI().getRawQuery() != null && t.getRequestURI().getRawQuery().contains("blob")) {
                blob = true;
            }
            try {
                new Parse(is, os, blob);
            } catch (Exception e) {
                e.printStackTrace();
            }
            os.close();
        }
    }

    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            t.sendResponseHeaders(200, 2);
            OutputStream os = t.getResponseBody();
            os.write("ok".getBytes());
            os.close();
        }
    }

    static class BlobHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            URI replayUrl;
            try {
                Map<String, String> query = splitQuery(t.getRequestURI());
                replayUrl = URI.create(query.get("replay_url"));
            } catch (Exception e) {
                e.printStackTrace();
                // Missing or malformed replay_url query parameter
                t.sendResponseHeaders(500, 0);
                t.getResponseBody().close();
                return;
            }
            // Stage 1: download the full replay into memory
            long tStart = System.currentTimeMillis();
            byte[] compressIn;
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(replayUrl)
                        .timeout(Duration.ofSeconds(180))
                        .build();
                HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                compressIn = response.body();
            } catch (Exception e) {
                e.printStackTrace();
                // Network/connection failure while downloading, may be worth retrying
                t.sendResponseHeaders(500, 0);
                t.getResponseBody().close();
                return;
            }
            long tDownloaded = System.currentTimeMillis();
            System.err.format("download: %dms\n", tDownloaded - tStart);

            // Determine compression type from the first few bytes
            byte[] header = compressIn.length >= 4 ? Arrays.copyOf(compressIn, 4) : compressIn;

            // Wrap the downloaded bytes in the appropriate decompressing stream.
            // Compressed streams are guarded so a failure while decompressing
            // is distinguishable from a failure in Parse's own logic. Decompression
            // happens lazily as Parse reads, so there's no second full in-memory
            // copy of the decompressed replay.
            InputStream parseInput;
            if (isZstd(header)) {
                parseInput = guardDecompression(
                        new ZstdCompressorInputStream(new ByteArrayInputStream(compressIn)));
            } else if (isBzip2(header)) {
                parseInput = guardDecompression(
                        new BZip2CompressorInputStream(new ByteArrayInputStream(compressIn)));
            } else {
                parseInput = new ByteArrayInputStream(compressIn);
            }

            // Stage 2: decompress (on the fly) and parse
            byte[] parseOut;
            try (InputStream pi = parseInput) {
                ByteArrayOutputStream parseOutStream = new ByteArrayOutputStream();
                new Parse(pi, parseOutStream, true);
                parseOut = parseOutStream.toByteArray();
            } catch (DecompressionException e) {
                e.printStackTrace();
                // Corrupted/truncated replay, don't retry
                t.sendResponseHeaders(204, 0);
                t.getResponseBody().close();
                return;
            } catch (Exception ex) {
                if ("given stream does not seem to contain a valid replay".equals(ex.getMessage())) {
                    ex.printStackTrace();
                    // Corrupted/truncated replay, don't retry
                    t.sendResponseHeaders(204, 0);
                    t.getResponseBody().close();
                    return;
                }
                ex.printStackTrace();
                t.sendResponseHeaders(500, 0);
                t.getResponseBody().close();
                return;
            }
            long tParsed = System.currentTimeMillis();
            System.err.format("decompress+parse: %dms\n", tParsed - tDownloaded);

            t.sendResponseHeaders(200, parseOut.length);
            t.getResponseBody().write(parseOut);
            t.getResponseBody().close();
        }

        // Zstd magic number bytes, in file order (little-endian representation of 0xFD2FB528)
        private static final byte[] ZSTD_MAGIC = {
            (byte) 0x28, (byte) 0xB5, (byte) 0x2F, (byte) 0xFD
        };

        public static boolean isZstd(byte[] data) {
            if (data == null || data.length < ZSTD_MAGIC.length) {
                return false;
            }
            for (int i = 0; i < ZSTD_MAGIC.length; i++) {
                if (data[i] != ZSTD_MAGIC[i]) {
                    return false;
                }
            }
            return true;
        }

        public static boolean isBzip2(byte[] data) {
            if (data == null || data.length < 4) {
                return false;
            }
            // bzip2 files start with "BZh" followed by a digit '1'-'9'
            // indicating the block size (100k-900k)
            return data[0] == 'B'
                && data[1] == 'Z'
                && data[2] == 'h'
                && data[3] >= '1' && data[3] <= '9';
        }

        /**
         * Wraps a decompressing InputStream so that any IOException thrown
         * while reading from it (e.g. corrupted or truncated compressed data)
         * is rethrown as a DecompressionException, distinguishing it from
         * errors thrown by Parse's own logic once decompression succeeds.
         */
        private static InputStream guardDecompression(InputStream compressorStream) {
            return new FilterInputStream(compressorStream) {
                @Override
                public int read() throws IOException {
                    try {
                        return super.read();
                    } catch (IOException e) {
                        throw new DecompressionException(e);
                    }
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    try {
                        return super.read(b, off, len);
                    } catch (IOException e) {
                        throw new DecompressionException(e);
                    }
                }
            };
        }
    }

    public static Map<String, String> splitQuery(URI uri) throws UnsupportedEncodingException {
        Map<String, String> query_pairs = new LinkedHashMap<String, String>();
        String query = uri.getQuery();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            query_pairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
                    URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
        }
        return query_pairs;
    }
}

class RegisterTask extends TimerTask {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public void run() {
        if (System.getenv().containsKey("SERVICE_REGISTRY_HOST")) {
            try {
                String registryHost = System.getenv().get("SERVICE_REGISTRY_HOST");
                String ip;
                if (System.getenv().containsKey("EXTERNAL")) {
                    // If configured as external, request external IP and report it
                    ip = httpGet(registryHost + "/ip").trim();
                } else {
                    // Otherwise, use the local host's address as the internal IP
                    ip = java.net.InetAddress.getLocalHost().getHostAddress();
                }
                long nproc = Main.MAX_THREADS;
                String registerUrl = "http://" + registryHost + "/register/parser/" + ip + ":5600"
                        + "?size=" + nproc + "&key=" + System.getenv().get("RETRIEVER_SECRET");
                System.err.println("POST " + registerUrl);
                httpPost(registerUrl);
            } catch (Exception e) {
                System.err.println(e);
            }
        }
    }

    private static String httpGet(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(java.time.Duration.ofSeconds(60))
                .GET()
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private static void httpPost(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(java.time.Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
    }
}