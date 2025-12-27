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
            try {
                new Parse(is, os, false);
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
                // boolean v2 = t.getRequestURI().getRawQuery() != null ?
                // t.getRequestURI().getRawQuery().contains("v2") : false;
                // boolean v2 = true;

                // Get the replay as a byte[]
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .timeout(Duration.ofSeconds(145))
                        .uri(replayUrl)
                        .build();
                HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

                byte[] bzIn = response.body();
                byte[] bzOut = bzIn;

                if (replayUrl.toString().endsWith(".bz2")) {
                    // Write byte[] to bunzip, get back decompressed byte[]
                    Process bz = new ProcessBuilder(new String[] { "bunzip2" }).start();
                    
                    // Start separate thread so we can consume output while sending input
                    Thread thread = new Thread(() -> {
                        try {
                            bz.getOutputStream().write(bzIn);
                            bz.getOutputStream().close();
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    });
                    thread.start();

                    bzOut = bz.getInputStream().readAllBytes();
                    System.err.println(new String(bz.getErrorStream().readAllBytes()));
                }

                // Start parser with input stream created from byte[]
                ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
                new Parse(new ByteArrayInputStream(bzOut), baos2, true);
                byte[] parseOut = baos2.toByteArray();

                t.sendResponseHeaders(200, parseOut.length);
                t.getResponseBody().write(parseOut);
                t.getResponseBody().close();
            } catch (Exception ex) {
                // TODO handle timeouts and corrupted replays (don't retry in those cases)
                ex.printStackTrace();
                t.sendResponseHeaders(500, 0);
                t.getResponseBody().close();
            }

            // String cmd = String.format("""
            // curl --max-time 145 --fail -L %s | %s | curl -X POST -T - "localhost:5600%s"
            // %s
            // """,
            // replayUrl,
            // replayUrl.toString().endsWith(".bz2") ? "bunzip2" : "cat",
            // v2 ? "?blob" : "",
            // v2 ? "" : " | node processors/createParsedDataBlob.mjs"
            // );
            // System.err.println(cmd);
            // // Download, unzip, parse, aggregate
            // Process proc = new ProcessBuilder(new String[] {"bash", "-c", cmd})
            // .start();
            // ByteArrayOutputStream output = new ByteArrayOutputStream();
            // ByteArrayOutputStream error = new ByteArrayOutputStream();
            // copy(proc.getInputStream(), output);
            // // Write error to console
            // copy(proc.getErrorStream(), error);
            // System.err.println(error.toString());
            // int exitCode = proc.waitFor();
            // if (exitCode != 0) {
            // // We can send 200 status here and no response if expected error (read the
            // error string)
            // // Maybe we can pass the specific error info in the response headers
            // int status = 500;
            // if (error.toString().contains("curl: (28) Operation timed out")) {
            // // Parse took too long, maybe China replay?
            // status = 200;
            // }
            // if (error.toString().contains("curl: (22) The requested URL returned error:
            // 502")) {
            // // Google-Edge-Cache: origin retries exhausted Error: 2010
            // // Server error, don't retry
            // status = 200;
            // }
            // if (error.toString().contains("bunzip2: Data integrity error when
            // decompressing")) {
            // // Corrupted replay, don't retry
            // status = 200;
            // }
            // if (error.toString().contains("bunzip2: Compressed file ends unexpectedly"))
            // {
            // // Corrupted replay, don't retry
            // status = 200;
            // }
            // if (error.toString().contains("bunzip2: (stdin) is not a bzip2 file.")) {
            // // Tried to unzip a non-bz2 file
            // status = 200;
            // }
            // t.sendResponseHeaders(status, 0);
            // t.getResponseBody().close();
            // } else {
            // t.sendResponseHeaders(200, output.size());
            // output.writeTo(t.getResponseBody());
            // t.getResponseBody().close();
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
