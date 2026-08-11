package opendota;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class Main {

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(Integer.valueOf("5600")), 0);
        server.createContext("/", new MyHandler());
        server.createContext("/healthz", new HealthHandler());
        server.createContext("/blob", new BlobHandler());
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
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
            Path downloadFile = null;
            Path decompressedFile = null;
            try {
                Map<String, String> query = splitQuery(t.getRequestURI());
                URI replayUrl = URI.create(query.get("replay_url"));

                // Download the replay directly to a temp file on disk
                downloadFile = Files.createTempFile("replay-download-", ".bin");
                long tStart = System.currentTimeMillis();
                ExecutorService executor = Executors.newSingleThreadExecutor();
                final Path downloadTarget = downloadFile;
                try {
                    Future<Path> future = executor.submit(() -> {
                        HttpClient client = HttpClient.newHttpClient();
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(replayUrl)
                                .build();
                        HttpResponse<Path> response = client.send(request,
                                HttpResponse.BodyHandlers.ofFile(downloadTarget,
                                        java.nio.file.StandardOpenOption.CREATE,
                                        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                                        java.nio.file.StandardOpenOption.WRITE));
                        return response.body();
                    });
                    future.get(600, TimeUnit.SECONDS);
                } finally {
                    executor.shutdownNow();
                }
                long tEnd = System.currentTimeMillis();
                System.err.format("download: %dms\n", tEnd - tStart);

                // Peek at the first few bytes on disk to determine compression type
                byte[] header;
                try (InputStream headerIn = Files.newInputStream(downloadFile)) {
                    header = headerIn.readNBytes(4);
                }

                tStart = System.currentTimeMillis();
                try {
                    if (isZstd(header)) {
                        decompressedFile = Files.createTempFile("replay-decompressed-", ".bin");
                        try (InputStream fis = Files.newInputStream(downloadFile);
                             ZstdCompressorInputStream zis = new ZstdCompressorInputStream(fis);
                             OutputStream fos = Files.newOutputStream(decompressedFile,
                                     java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                            zis.transferTo(fos);
                        }
                    } else if (isBzip2(header)) {
                        decompressedFile = Files.createTempFile("replay-decompressed-", ".bin");
                        try (InputStream fis = Files.newInputStream(downloadFile);
                             BZip2CompressorInputStream bis = new BZip2CompressorInputStream(fis);
                             OutputStream fos = Files.newOutputStream(decompressedFile,
                                     java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                            bis.transferTo(fos);
                        }
                    } else {
                        // Not compressed, parse directly from the downloaded file
                        decompressedFile = downloadFile;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    // Corrupted replay, don't retry
                    t.sendResponseHeaders(204, 0);
                    t.getResponseBody().close();
                    return;
                }
                tEnd = System.currentTimeMillis();
                System.err.format("%s: %dms\n", "decompress", tEnd - tStart);

                // Start parser reading straight off disk
                tStart = System.currentTimeMillis();
                ByteArrayOutputStream parseOutStream = new ByteArrayOutputStream();
                try (InputStream parseIn = Files.newInputStream(decompressedFile)) {
                    new Parse(parseIn, parseOutStream, true);
                }
                byte[] parseOut = parseOutStream.toByteArray();
                tEnd = System.currentTimeMillis();
                System.err.format("parse: %dms\n", tEnd - tStart);

                t.sendResponseHeaders(200, parseOut.length);
                t.getResponseBody().write(parseOut);
                t.getResponseBody().close();
            } catch (Exception ex) {
                ex.printStackTrace();
                t.sendResponseHeaders(500, 0);
                t.getResponseBody().close();
            } finally {
                // Clean up temp files from disk
                try {
                    if (downloadFile != null) {
                        Files.deleteIfExists(downloadFile);
                    }
                    if (decompressedFile != null && !decompressedFile.equals(downloadFile)) {
                        Files.deleteIfExists(decompressedFile);
                    }
                } catch (IOException cleanupEx) {
                    cleanupEx.printStackTrace();
                }
            }
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
    public void run() {
        if (System.getenv().containsKey("SERVICE_REGISTRY_HOST")) {
            try {
                String ip = "";
                if (System.getenv().containsKey("EXTERNAL")) {
                    // If configured as external, request external IP and report it
                    ip = RegisterTask.shellExec("curl " + System.getenv().get("SERVICE_REGISTRY_HOST") + "/ip");
                } else {
                    // Otherwise, use hostname -i to get internal IP
                    ip = RegisterTask.shellExec("hostname -i");
                }
                long nproc = Math.round(Math.min(Runtime.getRuntime().availableProcessors() * 3, 48));
                String postCmd = "curl -X POST --max-time 60 -L " + System.getenv().get("SERVICE_REGISTRY_HOST")
                        + "/register/parser/" + ip + ":5600" + "?size=" + nproc + "&key="
                        + System.getenv().get("RETRIEVER_SECRET");
                System.err.println(postCmd);
                RegisterTask.shellExec(postCmd);
            } catch (Exception e) {
                System.err.println(e);
            }
        }
    }

    public static String shellExec(String cmdCommand) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder();
        String[] cmdArr = cmdCommand.split(" ");
        final Process process = Runtime.getRuntime().exec(cmdArr, null, null);
        final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            stringBuilder.append(line);
        }
        return stringBuilder.toString();
    }
}