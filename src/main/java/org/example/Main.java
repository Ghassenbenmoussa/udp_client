package org.example;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.net.*;
import java.util.concurrent.Executors;

public class Main extends JFrame {
    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private static final int SERVER_PORT = 9876;
    private String username;

    public Main(String username) {
        super("UDP Chat Client - " + username);
        this.username = username;

        setSize(500, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        add(new JScrollPane(chatArea), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        sendButton = new JButton("Send");
        bottomPanel.add(messageField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        sendButton.addActionListener(this::sendMessage);
        messageField.addActionListener(this::sendMessage);

        setVisible(true);
        startClient();
    }

    private void startClient() {
        try {
            socket = new DatagramSocket();
            serverAddress = InetAddress.getByName("localhost");

            // Announce presence to server
            sendPacket(username + " has joined the chat!");

            Executors.newSingleThreadExecutor().execute(() -> {
                byte[] buffer = new byte[1024];
                while (true) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);
                        String received = new String(packet.getData(), 0, packet.getLength());
                        chatArea.append(received + "\n");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(ActionEvent event) {
        try {
            String message = messageField.getText().trim();
            if (!message.isEmpty()) {
                sendPacket(username + ": " + message);
                messageField.setText("");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendPacket(String message) throws Exception {
        DatagramPacket packet = new DatagramPacket(
                message.getBytes(), message.length(), serverAddress, SERVER_PORT
        );
        socket.send(packet);
    }

    public static void main(String[] args) {
        String username = JOptionPane.showInputDialog("Enter your username:");
        if (username != null && !username.trim().isEmpty()) {
            new Main(username.trim());
        } else {
            System.exit(0);
        }
    }
}
