package xyz.thekgg.simple_client;

import com.google.gson.JsonObject;

import javax.swing.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class Client {
	private static final String sharedSecret = "password";
	private static final String server = "http://localhost:9992";
	private static final String prefix = "/simple_server/";

	private static int id;

	public static void main(String[] args) {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

			HttpURLConnection connection = (HttpURLConnection)new URL(server + prefix + "join").openConnection();
			connection.setRequestMethod("PUT");
			connection.setDoOutput(true);
			connection.setConnectTimeout(2000);

			JsonObject object = new JsonObject();
			object.addProperty("secret", sharedSecret);
			try (OutputStream out = connection.getOutputStream()) {
				out.write(object.toString().getBytes(StandardCharsets.UTF_8));
			}

			JsonObject response = Util.readStreamToJson(connection.getInputStream());
			if(response == null)
				throw new Exception("response was null!");
			if(!response.has("id"))
				throw new Exception("response doesn't have ID!!!");

			id = response.get("id").getAsInt();

			startThatCheckLoop();
		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(null, e.getMessage() + "!!", "errror!!11", JOptionPane.ERROR_MESSAGE);
		}
	}

	private static void log(String s) {
		System.out.println(s);
	}

	private static void startThatCheckLoop() {
		int errorCount = 0;
		while (true) {
			HttpURLConnection connection = null;
			try {
				connection = (HttpURLConnection)new URL(server + prefix + "check").openConnection();
				connection.setRequestMethod("PUT");
				connection.setReadTimeout(25000);
				JsonObject object = new JsonObject();
				object.addProperty("secret", sharedSecret);
				object.addProperty("id", id);

				connection.setDoOutput(true);
				try (OutputStream out = connection.getOutputStream()) {
					out.write(object.toString().getBytes(StandardCharsets.UTF_8));
				}

				JsonObject response = Util.readStreamToJson(connection.getInputStream());
				if(response == null)
					throw new Exception("no response");
				log("Received: \n" + response.toString() +"\n\n");

			} catch (Exception e) {
				e.printStackTrace();
				errorCount++;
				if(errorCount > 5) {
					if(connection != null) {
						try {
							log("error response code " + connection.getResponseCode());
						} catch (IOException ex) {
							ex.printStackTrace();
						}
					}
					JOptionPane.showMessageDialog(null, e.getMessage() + "!!", "error!!!!111", JOptionPane.ERROR_MESSAGE);
					System.exit(1);
				}
			}
		}
	}
}
