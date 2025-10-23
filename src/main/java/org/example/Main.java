package org.example;
import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.net.*;
import java.util.concurrent.Executors;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.util.Base64;

public class Main extends JFrame {
    private JTextPane chatPane;
    private JTextField messageField;
    private JButton sendButton, sendImageButton;
    private JList<String> clientList;
    private DefaultListModel<String> listModel;

    private DatagramSocket socket;
    private InetAddress serverAddress;
    private static final int SERVER_PORT = 9876;
    private String username;

    public Main(String username) {
        super("UDP Chat Client - " + username);
        this.username = username;

        setSize(750, 450);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        chatPane = new JTextPane();
        chatPane.setEditable(false);
        JScrollPane chatScroll = new JScrollPane(chatPane);

        listModel = new DefaultListModel<>();
        clientList = new JList<>(listModel);
        clientList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane listScroll = new JScrollPane(clientList);
        listScroll.setPreferredSize(new Dimension(150, 0));

        JPanel bottomPanel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        sendButton = new JButton("Send");
        sendImageButton = new JButton("Send Image");
        bottomPanel.add(sendImageButton, BorderLayout.WEST);
        bottomPanel.add(messageField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chatScroll, listScroll);
        splitPane.setDividerLocation(580);
        add(splitPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        sendButton.addActionListener(this::sendMessage);
        messageField.addActionListener(this::sendMessage);
        sendImageButton.addActionListener(e -> sendImage());

        setVisible(true);
        startClient();
    }

    private void startClient() {
        try {
            socket = new DatagramSocket();
            serverAddress = InetAddress.getByName("localhost");

            // Announce presence
            sendPacket(username + ":JOIN");

            Executors.newSingleThreadExecutor().execute(() -> {
                byte[] buffer = new byte[65507]; // Max UDP packet size
                while (true) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);
                        String received = new String(packet.getData(), 0, packet.getLength());

                        if (received.startsWith("CLIENT_LIST:")) {
                            SwingUtilities.invokeLater(() -> updateClientList(received.substring(12)));
                        }
                        else if (received.startsWith("IMAGE:")) {
                            String[] parts = received.split(":", 4);
                            if (parts.length == 4) {
                                String sender = parts[2];
                                String base64Data = parts[3];

                                byte[] imgBytes = Base64.getDecoder().decode(base64Data);
                                ByteArrayInputStream bais = new ByteArrayInputStream(imgBytes);
                                BufferedImage img = ImageIO.read(bais);
                                ImageIcon icon = new ImageIcon(img);

                                appendImageMessage(sender, icon);
                            }
                        } else {
                            appendMessage(received);
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
        listModel.addElement("All"); // broadcast
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
                    sendPacket(username + ": " + message);
                } else {
                    sendPacket("PRIVATE:" + target + ":" + username + ": " + message);
                }
                messageField.setText("");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void sendImage() {
        try {
            JFileChooser chooser = new JFileChooser();
            int result = chooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                java.io.File file = chooser.getSelectedFile();
                BufferedImage img = ImageIO.read(file);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, "png", baos);
                String encodedImage = Base64.getEncoder().encodeToString(baos.toByteArray());

                String target = clientList.getSelectedValue();
                String message;
                if (target == null || target.equals("All")) {
                    message = "IMAGE:ALL:" + username + ":" + encodedImage;
                } else {
                    message = "IMAGE:" + target + ":" + username + ":" + encodedImage;
                }

                sendPacket(message);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void sendPacket(String message) throws Exception {
        DatagramPacket packet = new DatagramPacket(message.getBytes(), message.length(), serverAddress, SERVER_PORT);
        socket.send(packet);
    }

    // Append text message with color for private
    private void appendMessage(String message) {
        try {
            StyledDocument doc = chatPane.getStyledDocument();
            Style style = chatPane.addStyle("Style", null);
            if (message.startsWith("[Private]")) {
                StyleConstants.setForeground(style, Color.RED);
            } else {
                StyleConstants.setForeground(style, Color.BLACK);
            }
            doc.insertString(doc.getLength(), message + "\n", style);
            chatPane.setCaretPosition(doc.getLength());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void appendImageMessage(String sender, ImageIcon icon) {
        try {
            StyledDocument doc = chatPane.getStyledDocument();
            chatPane.setCaretPosition(doc.getLength());

            Style style = chatPane.addStyle("Style", null);
            StyleConstants.setBold(style, true);
            doc.insertString(doc.getLength(), sender + " sent an image:\n", style);

            chatPane.setCaretPosition(doc.getLength());
            chatPane.insertIcon(icon);

            doc.insertString(doc.getLength(), "\n", null);
            chatPane.setCaretPosition(doc.getLength());
        } catch (Exception e) {
            e.printStackTrace();
        }
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
