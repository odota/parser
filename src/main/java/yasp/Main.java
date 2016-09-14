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
    
    public static void main(String[] args) {
	try { 
                       
	    if (args.length > 0 && args[0].equals("--file")) { System.exit(parse(args[1])); }
            else if (args.length >0 && args[0].equals("--")) { System.exit(parseStream(System.in, System.out)); }
            else { startServer(args); }
	}
	catch (Exception e) {
	    e.printStackTrace();
	}
    }
    
    public static void startServer(String[] args) throws Exception {
	HttpServer server = HttpServer.create(new InetSocketAddress(Integer.valueOf(args.length > 0 ? args[0] : "5600")), 0);
	server.createContext("/", new MyHandler());
	server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
	server.start();
    }

    static int parseStream(InputStream is, OutputStream os) throws IOException {
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
    
    static int parse(String replay_file) throws Exception {
	System.out.print(String.format("Parsing file %s", replay_file)); 
	return parseStream(new FileInputStream(replay_file), System.out);
    }

    static class MyHandler implements HttpHandler {
	@Override
	public void handle(HttpExchange t) throws IOException {
	    t.sendResponseHeaders(200, 0);
	    parseStream(t.getRequestBody(), t.getResponseBody());
	}
    }
}
