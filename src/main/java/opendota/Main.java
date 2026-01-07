package opendota;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
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
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

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
            try {
                Map<String, String> query = splitQuery(t.getRequestURI());
                URI replayUrl = URI.create(query.get("replay_url"));
                // Get the replay as a byte[]
                long tStart = System.currentTimeMillis();
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Future<byte[]> future = executor.submit(() -> {
                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(replayUrl)
                            .build();
                    HttpResponse<byte[]> response = client.send(request,
                            HttpResponse.BodyHandlers.ofByteArray());
                    return response.body();
                });
                byte[] bzIn = future.get(600, TimeUnit.SECONDS);
                long tEnd = System.currentTimeMillis();
                System.err.format("download: %dms\n", tEnd - tStart);

                byte[] bzOut = bzIn;
                if (replayUrl.toString().endsWith(".bz2")) {
                    tStart = System.currentTimeMillis();
                    // Write byte[] to bunzip, get back decompressed byte[]
                    // The C decompressor is a bit faster than Java, 4.3 vs 4.8s
                    // BZip2CompressorInputStream bz = new BZip2CompressorInputStream(new ByteArrayInputStream(bzIn));
                    // bzOut = bz.readAllBytes();
                    // bz.close();

                    Process bz = new ProcessBuilder(new String[] { "bunzip2" }).start();
                    // Start separate thread so we can consume output while sending input
                    new Thread(() -> {
                        try {
                            bz.getOutputStream().write(bzIn);
                            bz.getOutputStream().close();
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }).start();

                    bzOut = bz.getInputStream().readAllBytes();
                    bz.getInputStream().close();
                    String bzError = new String(bz.getErrorStream().readAllBytes());
                    bz.getErrorStream().close();
                    System.err.println(bzError);
                    if (bzError.toString().contains("bunzip2: Data integrity error when decompressing") || bzError.contains("bunzip2: Compressed file ends unexpectedly")) {
                        // Corrupted replay, don't retry
                        t.sendResponseHeaders(204, 0);
                        t.getResponseBody().close();
                        return;
                    }
                    tEnd = System.currentTimeMillis();
                    System.err.format("bunzip2: %dms\n", tEnd - tStart);
                }

                // Start parser with input stream created from byte[]
                tStart = System.currentTimeMillis();
                ByteArrayOutputStream parseOutStream = new ByteArrayOutputStream();
                new Parse(new ByteArrayInputStream(bzOut), parseOutStream, true);
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
            }
            // long tStart = System.currentTimeMillis();
            // String cmd = String.format("""
            //         curl --max-time 145 --fail -L %s | %s | curl -X POST -T - "localhost:5600?blob"
            //         """,
            //         replayUrl.toString(),
            //         replayUrl.toString().endsWith(".bz2") ? "bunzip2" : "cat");
            // System.err.println(cmd);
            // Process proc = new ProcessBuilder(new String[] { "bash", "-c", cmd })
            //         .start();
            // byte[] parseOut = proc.getInputStream().readAllBytes();
            // String error = new String(proc.getErrorStream().readAllBytes());
            // System.err.println(error);
            // int exitCode = proc.waitFor();
            // long tEnd = System.currentTimeMillis();
            // System.err.format("download/bunzip2/parse: %sms\n", tEnd - tStart);
            // if (exitCode == 0) {
            //     t.sendResponseHeaders(200, parseOut.length);
            //     t.getResponseBody().write(parseOut);
            //     t.getResponseBody().close();
            // } else {
            //     // We can send 204 status here and no response if expected error
            //     // Maybe we can pass the specific error info in the response headers
            //     int status = 500;
            //     if (error.toString().contains("curl: (28) Operation timed out")) {
            //         // Parse took too long, maybe China replay?
            //         status = 204;
            //     }
            //     if (error.toString().contains("curl: (22) The requested URL returned error: 502")) {
            //         // Google-Edge-Cache: origin retries exhausted Error: 2010
            //         // Server error, don't retry
            //         status = 204;
            //     }
            //     if (error.toString().contains("bunzip2: Data integrity error when decompressing")) {
            //         // Corrupted replay, don't retry
            //         status = 204;
            //     }
            //     if (error.toString().contains("bunzip2: Compressed file ends unexpectedly")) {
            //         // Corrupted replay, don't retry
            //         status = 204;
            //     }
            //     if (error.toString().contains("bunzip2: (stdin) is not a bzip2 file.")) {
            //         // Tried to unzip a non-bz2 file
            //         status = 204;
            //     }
            //     if (status == 204) {
            //         t.sendResponseHeaders(status, 0);
            //         t.getResponseBody().close();
            //     } else {
            //         throw new Exception("Unexpected error in parse pipeline");
            //     }
            // }
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
                long nproc = Runtime.getRuntime().availableProcessors();
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
