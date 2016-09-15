package yasp;

import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
    
public class Main {
    
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--file")) { System.exit(parseFile(args[1])); }
        else if (args.length > 0 && args[0].equals("--")) { System.exit(parseStream(System.in, System.out)); }
        else { startServer(args); }
    }
    
    public static void startServer(String[] args) throws Exception {
	HttpServer server = HttpServer.create(new InetSocketAddress(Integer.valueOf(args.length > 0 ? args[0] : "5600")), 0);
	server.createContext("/", new MyHandler());
	server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
	server.start();
    }

    private static int parseStream(InputStream is, OutputStream os) throws IOException {
	try {
	    new Parse(is, os);
	}
	catch (Exception e)
	{
	    e.printStackTrace();
            return -1;
	}
	finally {
	    is.close();
	    os.close();
	}
        
        return 0;
    }
    
    private static int parseFile(String replayFile) throws Exception {
	System.out.print(String.format("Parsing file %s", replayFile)); 
	return parseStream(new FileInputStream(replayFile), System.out);
    }

    static class MyHandler implements HttpHandler {
	@Override
	public void handle(HttpExchange t) throws IOException {
	    t.sendResponseHeaders(200, 0);
	    parseStream(t.getRequestBody(), t.getResponseBody());
	}
    }
}
