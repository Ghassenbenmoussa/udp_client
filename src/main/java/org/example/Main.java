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
    private JList<String> clientList;
    private DefaultListModel<String> listModel;

    private DatagramSocket socket;
    private InetAddress serverAddress;
    private static final int SERVER_PORT = 9876;
    private String username;

    public Main(String username) {
        super("UDP Chat Client - " + username);
        this.username = username;

        setSize(700, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane chatScroll = new JScrollPane(chatArea);

        listModel = new DefaultListModel<>();
        clientList = new JList<>(listModel);
        clientList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane listScroll = new JScrollPane(clientList);
        listScroll.setPreferredSize(new Dimension(150, 0));

        JPanel bottomPanel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        sendButton = new JButton("Send");
        bottomPanel.add(messageField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chatScroll, listScroll);
        splitPane.setDividerLocation(500);
        add(splitPane, BorderLayout.CENTER);
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
            sendPacket(username + ":JOIN");

            Executors.newSingleThreadExecutor().execute(() -> {
                byte[] buffer = new byte[1024];
                while (true) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);
                        String received = new String(packet.getData(), 0, packet.getLength());

                        if (received.startsWith("CLIENT_LIST:")) {
                            SwingUtilities.invokeLater(() -> updateClientList(received.substring(12)));
                        } else {
                            chatArea.append(received + "\n");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateClientList(String listStr) {
        String[] users = listStr.split(",");
        listModel.clear();
        listModel.addElement("All"); // broadcast option
        for (String u : users) {
            if (!u.equals(username)) listModel.addElement(u);
        }
    }

    private void sendMessage(ActionEvent e) {
        try {
            String message = messageField.getText().trim();
            if (!message.isEmpty()) {
                String target = clientList.getSelectedValue();
                if (target == null || target.equals("All")) {
                    sendPacket(username + ": " + message); // broadcast
                } else {
                    sendPacket("PRIVATE:" + target + ":" + username + ": " + message); // private
                }
                messageField.setText("");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void sendPacket(String message) throws Exception {
        DatagramPacket packet = new DatagramPacket(message.getBytes(), message.length(), serverAddress, SERVER_PORT);
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
