package xyz.thekgg.simple_controller;

import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class Control implements ActionListener {
	private static final String sharedSecret = "password";
	private static final String server = "http://localhost:9992";
	private static final String prefix = "/simple_server/";

	private static JLabel statusLabel = new JLabel("Connecting...");

	public static void main(String[] args) {
		new Control();
	}

	private Control() {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
			e.printStackTrace();
		}

		JFrame frame = new JFrame();
		frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		frame.setSize(400, 100);
		JPanel panel = new JPanel();
		frame.add(panel);

		SpringLayout layout = new SpringLayout();
		panel.setLayout(layout);

		JPanel controls = new JPanel();
		controls.add(new JLabel("Message:"));
		JTextField field = new JTextField();
		field.addActionListener(this);
		field.setColumns(30);
		controls.add(field);

		layout.putConstraint(SpringLayout.NORTH, controls, 0, SpringLayout.NORTH, panel);
		layout.putConstraint(SpringLayout.WEST, controls, 0, SpringLayout.WEST, panel);
		layout.putConstraint(SpringLayout.EAST, controls, 0, SpringLayout.EAST, panel);
		layout.putConstraint(SpringLayout.SOUTH, controls, -20, SpringLayout.SOUTH, panel);
		panel.add(controls);

		statusLabel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.black));
		layout.putConstraint(SpringLayout.NORTH, statusLabel, 0, SpringLayout.SOUTH, controls);
		layout.putConstraint(SpringLayout.WEST, statusLabel, 0, SpringLayout.WEST, panel);
		layout.putConstraint(SpringLayout.EAST, statusLabel, 0, SpringLayout.EAST, panel);
		layout.putConstraint(SpringLayout.SOUTH, statusLabel, 0, SpringLayout.SOUTH, panel);
		panel.add(statusLabel);

		frame.setVisible(true);

		startLoop();
	}

	private static void startLoop() {
		new Thread(() -> {
			while (true) {
				try {
					HttpURLConnection connection = (HttpURLConnection)new URL(server + prefix + "status").openConnection();
					connection.setRequestMethod("PUT");
					connection.setDoOutput(true);

					JsonObject object = new JsonObject();
					object.addProperty("secret", sharedSecret);

					try (OutputStream out = connection.getOutputStream()) {
						out.write(object.toString().getBytes(StandardCharsets.UTF_8));
					}

					JsonObject response = Util.readStreamToJson(connection.getInputStream());
					if(response == null)
						throw new Exception("response was null!");
					if(!response.has("count"))
						throw new Exception("response doesn't have count!!!");
					int count = response.get("count").getAsInt();
					statusLabel.setText("Connected! " + (count==1 ? "There is 1 client." : "There are " + count + " clients."));
					statusLabel.setForeground(Color.black);

				} catch (Exception e) {
					statusLabel.setForeground(Color.red);
					statusLabel.setText("Error! " + e.getMessage());
					e.printStackTrace();
				}

				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	private static void log(String s) {
		System.out.println(s);
	}

	@Override
	public void actionPerformed(ActionEvent event) {
		JsonObject object = new JsonObject();
		JTextField field = (JTextField)event.getSource();
		object.addProperty("secret", sharedSecret);
		object.addProperty("message", field.getText());
		log("Messaging \""+field.getText()+"\"");

		try {
			HttpURLConnection connection = (HttpURLConnection)new URL(server + prefix + "send").openConnection();
			connection.setRequestMethod("POST");
			connection.setDoOutput(true);

			try (OutputStream out = connection.getOutputStream()) {
				out.write(object.toString().getBytes(StandardCharsets.UTF_8));
			}
			connection.getResponseCode();
			field.setText("");
		} catch(Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(null, "uh oh " + e.getMessage());
		}
	}
}
