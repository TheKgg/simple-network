package xyz.thekgg.simple_client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Util {
	private static final Gson gson = new Gson();

	@Nullable
	public static JsonObject readStreamToJson(InputStream stream) {
		StringBuilder response = new StringBuilder();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(stream))) {
			String line;
			while ((line = br.readLine()) != null) {
				response.append(line);
				response.append('\r');
			}
		} catch (Exception e) {
			return null;
		}
		return gson.fromJson(response.toString(), JsonElement.class).getAsJsonObject();
	}
}
