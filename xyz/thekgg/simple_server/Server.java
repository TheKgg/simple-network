package xyz.thekgg.simple_server;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class Server {
	//I have several things running on this server.
	private static final String PREFIX = "/simple_server/";

	//this is just so random people can't access the server
	private static final String SHARED_SECRET = "password";

	//duh
	private static final int MAXIMUM_CLIENTS_AT_ONCE = 32;

	//duh
	private static final int PORT = 9992;

	//The only value here will be "amount". I don't know any better way to synchronize just an integer between threads.
	private static final Map<String, Integer> CLIENTS_CONNECTED = Collections.synchronizedMap(new HashMap<>());

	//The only value here will be "new". Again, I don't know a better way to synchronize stuff like this.
	private static final Map<String, JsonObject> RETURN_VALUE = Collections.synchronizedMap(new HashMap<>());

	//Which clients have been sent the new value already
	private static final List<Integer> SENT_ALREADY = Collections.synchronizedList(new ArrayList<>());

	//there's probably a better locking method than recreating this over and over but idk what it is so oh well
	private static CountDownLatch latch = new CountDownLatch(1);

	public static void main(String[] args) throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
		server.setExecutor(new ThreadPoolExecutor(2, MAXIMUM_CLIENTS_AT_ONCE, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>()));

		server.createContext(PREFIX + "join", exchange -> {
			JsonObject json = Util.readStreamToJson(exchange.getRequestBody());
			if(!secretIsValid(exchange, json))
				return;

			int computers;
			synchronized (CLIENTS_CONNECTED) {
				computers = CLIENTS_CONNECTED.getOrDefault("amount", 0) + 1;
				CLIENTS_CONNECTED.put("amount", computers);
			}

			JsonObject object = new JsonObject();
			object.addProperty("id", computers);

			byte[] bytes = object.toString().getBytes(StandardCharsets.UTF_8);

			exchange.sendResponseHeaders(200, bytes.length);

			try (OutputStream out = exchange.getResponseBody()) {
				out.write(bytes);
			}

			log("Client #" + computers + " successfully joined");
		});

		server.createContext(PREFIX + "check", exchange -> {
			JsonObject json = Util.readStreamToJson(exchange.getRequestBody());
			if(!secretIsValid(exchange, json))
				return;
			int id;
			//noinspection ConstantConditions | cannot be null because checkForSecret already checks for that
			if(json.has("id")) {
				id = json.get("id").getAsInt();
			} else {
				log("No id on check!!");
				exchange.sendResponseHeaders(400, -1);
				return;
			}

			boolean wait = true;
			synchronized (RETURN_VALUE) {
				synchronized (SENT_ALREADY) {
					if(!SENT_ALREADY.contains(id) && RETURN_VALUE.containsKey("new")) {
						wait = false;
					}
				}
			}

			if(wait) {
				try {
					//waits until there's new data to send
					latch.await(20, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			JsonObject toSend = new JsonObject();
			synchronized (RETURN_VALUE) {
				synchronized (SENT_ALREADY) {
					if(!SENT_ALREADY.contains(id) && RETURN_VALUE.containsKey("new")) {
						toSend = RETURN_VALUE.get("new");
						SENT_ALREADY.add(id);
						synchronized (CLIENTS_CONNECTED) {
							if(SENT_ALREADY.size() >= CLIENTS_CONNECTED.getOrDefault("amount", 0)) {
								RETURN_VALUE.clear();
								SENT_ALREADY.clear();
							}
						}
					}
				}
			}

			if(toSend.toString().length() > 5) {
				log("Sending data to client " + id);
			}

			byte[] bytes = toSend.toString().getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, bytes.length);

			try (OutputStream out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		});

		server.createContext(PREFIX + "send", exchange -> {
			JsonObject json = Util.readStreamToJson(exchange.getRequestBody());
			if(!secretIsValid(exchange, json))
				return;
			//noinspection ConstantConditions | cannot be null because checkForSecret already checks for that
			json.remove("secret");
			log("Dispersing " + json.toString() + " to all clients");
			exchange.sendResponseHeaders(200, -1);

			synchronized (SENT_ALREADY) {
				synchronized (RETURN_VALUE) {
					RETURN_VALUE.put("new", json);
					SENT_ALREADY.clear();
				}
			}

			latch.countDown();
			latch = new CountDownLatch(1);
		});

		server.createContext(PREFIX + "status", exchange -> {
			JsonObject json = Util.readStreamToJson(exchange.getRequestBody());
			if(!secretIsValid(exchange, json))
				return;
			JsonObject toSend = new JsonObject();
			synchronized (CLIENTS_CONNECTED) {
				toSend.addProperty("count", CLIENTS_CONNECTED.getOrDefault("amount", 0));
			}

			byte[] bytes = toSend.toString().getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, bytes.length);

			try (OutputStream out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		});

		server.start();

		log("Server is ready to go on port "+PORT+"!");
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted") //generally method names without inverses are easier to read. "!valid" is better than "invalid"
	private static boolean secretIsValid(HttpExchange exchange, JsonObject json) {
		try {
			if(json == null) {
				exchange.sendResponseHeaders(400, -1);
				log("No request body");
				return false;
			}

			if(!json.has("secret")) {
				exchange.sendResponseHeaders(401, -1);
				log("No secret");
				return false;
			}

			String secret = json.get("secret").getAsString();
			if(!secret.equals(SHARED_SECRET)) {
				exchange.sendResponseHeaders(403, -1);
				log("Incorrect secret");
				return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
			try {
				exchange.sendResponseHeaders(500, -1);
			} catch (Exception ignored) {
			}
			return false;
		}

		return true;
	}

	private static void log(String s) {
		System.out.println(s);
	}
}
