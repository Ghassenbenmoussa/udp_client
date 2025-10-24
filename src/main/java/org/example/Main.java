package org.example;
import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.util.Base64;

public class Main extends JFrame {
    private JTextPane chatPane;
    private JTextField messageField;
    private JButton sendButton, sendImageButton, sendFileButton;
    private JPanel clientsPanel;
    private Map<String, JCheckBox> clientCheckBoxes = new HashMap<>();
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private static final int UDP_PORT = 9876;
    private String username;

    private Map<String, Map<Integer, byte[]>> fileChunks = new HashMap<>();
    private Map<String, Integer> fileTotalChunks = new HashMap<>();

    public Main(String username) {
        super("UDP Chat Client - " + username);
        this.username = username;

        setSize(900, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        chatPane = new JTextPane();
        chatPane.setEditable(false);
        JScrollPane chatScroll = new JScrollPane(chatPane);

        // clients panel with checkboxes
        clientsPanel = new JPanel();
        clientsPanel.setLayout(new BoxLayout(clientsPanel, BoxLayout.Y_AXIS));
        JScrollPane listScroll = new JScrollPane(clientsPanel);
        listScroll.setPreferredSize(new Dimension(200,0));

        // Bottom panel with buttons above input field
        JPanel bottomPanel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        bottomPanel.add(messageField, BorderLayout.CENTER);

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        sendButton = new JButton("Send");
        sendImageButton = new JButton("Send Image");
        sendFileButton = new JButton("Send File");
        buttonsPanel.add(sendButton);
        buttonsPanel.add(sendImageButton);
        buttonsPanel.add(sendFileButton);

        bottomPanel.add(buttonsPanel, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chatScroll, listScroll);
        splitPane.setDividerLocation(700);
        add(splitPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        sendButton.addActionListener(this::sendMessage);
        messageField.addActionListener(this::sendMessage);
        sendImageButton.addActionListener(e -> sendImage());
        sendFileButton.addActionListener(e -> sendFileUDP());

        setVisible(true);
        startClient();
    }

    private void startClient() {
        try {
            socket = new DatagramSocket();
            serverAddress = InetAddress.getByName("localhost");
            sendPacket(username + ":JOIN");

            Executors.newSingleThreadExecutor().execute(() -> {
                byte[] buffer = new byte[65507];
                while (true) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);
                        String received = new String(packet.getData(), 0, packet.getLength());

                        if (received.startsWith("CLIENT_LIST:")) updateClientList(received.substring(12));
                        else if(received.startsWith("IMAGE:")) displayReceivedImage(received);
                        else if(received.startsWith("FILECHUNK:")) handleFileChunk(received);
                        else handleReceivedMessage(received);
                    } catch (Exception e) { e.printStackTrace(); }
                }
            });
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ========================= Update Client List =========================
    private void updateClientList(String listStr){
        String[] users = listStr.split(",");
        clientsPanel.removeAll();
        clientCheckBoxes.clear();

        JCheckBox allBox = new JCheckBox("All");
        allBox.addActionListener(e -> {
            boolean selected = allBox.isSelected();
            for(JCheckBox cb : clientCheckBoxes.values()){
                cb.setSelected(selected);
            }
        });
        clientsPanel.add(allBox);

        for(String u: users){
            if(!u.equals(username)){
                JCheckBox cb = new JCheckBox(u);
                clientCheckBoxes.put(u, cb);
                clientsPanel.add(cb);
            }
        }
        clientsPanel.revalidate();
        clientsPanel.repaint();
    }

    // ========================= Send Message =========================
    private void sendMessage(ActionEvent e){
        try {
            String message = messageField.getText().trim();
            if(message.isEmpty()) return;

            List<String> targets = new ArrayList<>();
            boolean allSelected = false;

            for(Map.Entry<String, JCheckBox> entry : clientCheckBoxes.entrySet()){
                if(entry.getValue().isSelected()){
                    targets.add(entry.getKey());
                    if(entry.getKey().equals("All")) allSelected = true;
                }
            }

            if(targets.isEmpty()){
                JOptionPane.showMessageDialog(this,"Select at least one recipient!");
                return;
            }

            String displayText;
            if(allSelected){
                displayText = "[Broadcast to All] " + message;
                targets.clear();
                targets.add("All");
            } else if(targets.size() == 1){
                displayText = "[Private] to " + targets.get(0) + ": " + message;
            } else {
                displayText = "[Private to multiple] to " + String.join(",", targets) + ": " + message;
            }

            for(String target : targets){
                sendPacket("PRIVATE:" + target + ":" + username + ":" + message);
            }

            appendMessage(displayText, Color.RED);
            messageField.setText("");
        } catch(Exception ex){ ex.printStackTrace(); }
    }

    // ========================= Send Image =========================
    private void sendImage(){
        try{
            JFileChooser chooser = new JFileChooser();
            if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION){
                File file = chooser.getSelectedFile();
                BufferedImage img = ImageIO.read(file);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img,"png",baos);
                String encoded = Base64.getEncoder().encodeToString(baos.toByteArray());

                List<String> targets = new ArrayList<>();
                boolean allSelected = false;
                for(Map.Entry<String,JCheckBox> entry : clientCheckBoxes.entrySet()){
                    if(entry.getValue().isSelected()){
                        targets.add(entry.getKey());
                        if(entry.getKey().equals("All")) allSelected = true;
                    }
                }
                if(targets.isEmpty()){ JOptionPane.showMessageDialog(this,"Select recipients!"); return; }

                String displayText;
                if(allSelected){
                    displayText = "[Broadcast to All] Image sent";
                    targets.clear();
                    targets.add("All");
                } else if(targets.size() == 1){
                    displayText = "[Private] to " + targets.get(0);
                } else {
                    displayText = "[Private to multiple] to " + String.join(",", targets);
                }

                for(String target : targets)
                    sendPacket("IMAGE:" + target + ":" + username + ":" + encoded);

                appendImageMessage(displayText, new ImageIcon(img));
            }
        } catch(Exception e){ e.printStackTrace(); }
    }

    // ========================= Send File =========================
    private void sendFileUDP(){
        try {
            JFileChooser chooser = new JFileChooser();
            if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION){
                File file = chooser.getSelectedFile();
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                int chunkSize = 30000;
                int totalChunks = (int)Math.ceil((double)fileBytes.length/chunkSize);

                List<String> targets = new ArrayList<>();
                boolean allSelected = false;
                for(Map.Entry<String,JCheckBox> entry : clientCheckBoxes.entrySet()){
                    if(entry.getValue().isSelected()){
                        targets.add(entry.getKey());
                        if(entry.getKey().equals("All")) allSelected = true;
                    }
                }
                if(targets.isEmpty()){ JOptionPane.showMessageDialog(this,"Select recipients!"); return; }

                String displayText;
                if(allSelected){
                    displayText = "[Broadcast to All] File sent: "+file.getName();
                    targets.clear();
                    targets.add("All");
                } else if(targets.size() == 1){
                    displayText = "[Private] to " + targets.get(0) + ": File sent: "+file.getName();
                } else {
                    displayText = "[Private to multiple] to " + String.join(",", targets) + ": File sent: "+file.getName();
                }

                for(String target : targets){
                    for(int i=0;i<totalChunks;i++){
                        int start = i*chunkSize;
                        int end = Math.min(start+chunkSize,fileBytes.length);
                        byte[] chunk = Arrays.copyOfRange(fileBytes,start,end);
                        String encoded = Base64.getEncoder().encodeToString(chunk);
                        String message = String.format("FILECHUNK:%s:%s:%s:%d:%d:%s",
                                target, username, file.getName(), i, totalChunks, encoded);
                        sendPacket(message);
                    }
                }

                appendMessage(displayText, Color.BLUE);
            }
        } catch(Exception e){ e.printStackTrace(); }
    }

    // ========================= Handle Received File Chunks =========================
    private void handleFileChunk(String received){
        try {
            String[] parts = received.split(":",7);
            String sender = parts[2];
            String filename = parts[3];
            int index = Integer.parseInt(parts[4]);
            int total = Integer.parseInt(parts[5]);
            byte[] chunkData = Base64.getDecoder().decode(parts[6]);

            String key = sender + "_" + filename;
            fileChunks.putIfAbsent(key, new HashMap<>());
            fileChunks.get(key).put(index, chunkData);
            fileTotalChunks.put(key, total);

            if(fileChunks.get(key).size() == total){
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                for(int i=0;i<total;i++) baos.write(fileChunks.get(key).get(i));
                byte[] fullFile = baos.toByteArray();
                appendFileMessage(sender + " sent a file", filename, fullFile);

                fileChunks.remove(key);
                fileTotalChunks.remove(key);
            }
        } catch(Exception e){ e.printStackTrace(); }
    }

    // ========================= Display File Inline =========================
    private void appendFileMessage(String title, String filename, byte[] fileBytes){
        try{
            StyledDocument doc = chatPane.getStyledDocument();
            Style style = chatPane.addStyle("Style", null);
            StyleConstants.setBold(style,true);
            doc.insertString(doc.getLength(), title + ": ", style);

            JButton openButton = new JButton(filename);
            openButton.setMargin(new Insets(0,0,0,0));
            openButton.setBorderPainted(false);
            openButton.setForeground(Color.BLUE);
            openButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
            openButton.addActionListener(e -> {
                try{
                    File tempFile = File.createTempFile("chatfile_","_"+filename);
                    tempFile.deleteOnExit();
                    Files.write(tempFile.toPath(), fileBytes);
                    Desktop.getDesktop().open(tempFile);
                } catch(Exception ex){ ex.printStackTrace(); }
            });

            chatPane.setCaretPosition(doc.getLength());
            chatPane.insertComponent(openButton);
            doc.insertString(doc.getLength(), "\n", null);
            chatPane.setCaretPosition(doc.getLength());
        } catch(Exception e){ e.printStackTrace(); }
    }

    // ========================= Handle Received Messages =========================
    private void handleReceivedMessage(String msg){
        try {
            if(msg.startsWith("PRIVATE:")){
                String[] parts = msg.split(":",4);
                String senderName = parts[2];
                String messageText = parts[3];
                if(!senderName.equals(username)){ // only display for others
                    appendMessage("[Private] " + senderName + ": " + messageText, Color.RED);
                }
            } else {
                appendMessage(msg, Color.BLACK);
            }
        } catch(Exception e){ e.printStackTrace(); }
    }

    private void appendMessage(String msg, Color color){
        try{
            StyledDocument doc = chatPane.getStyledDocument();
            Style style = chatPane.addStyle("Style", null);
            StyleConstants.setForeground(style, color);
            doc.insertString(doc.getLength(), msg + "\n", style);
            chatPane.setCaretPosition(doc.getLength());
        } catch(Exception e){ e.printStackTrace(); }
    }

    // ========================= Display Image =========================
    private void displayReceivedImage(String received){
        try{
            String[] parts = received.split(":",4);
            if(parts.length==4){
                String sender = parts[2];
                byte[] bytes = Base64.getDecoder().decode(parts[3]);
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                appendImageMessage(sender, new ImageIcon(img));
            }
        } catch(Exception e){ e.printStackTrace(); }
    }

    private void appendImageMessage(String title, ImageIcon icon){
        try{
            StyledDocument doc = chatPane.getStyledDocument();
            chatPane.setCaretPosition(doc.getLength());
            Style style = chatPane.addStyle("Style", null);
            StyleConstants.setBold(style,true);
            doc.insertString(doc.getLength(), title + " sent an image:\n", style);
            chatPane.setCaretPosition(doc.getLength());
            chatPane.insertIcon(icon);
            doc.insertString(doc.getLength(),"\n",null);
            chatPane.setCaretPosition(doc.getLength());
        } catch(Exception e){ e.printStackTrace(); }
    }

    private void sendPacket(String msg) throws Exception{
        DatagramPacket packet = new DatagramPacket(msg.getBytes(), msg.length(), serverAddress, UDP_PORT);
        socket.send(packet);
    }

    public static void main(String[] args){
        String username = JOptionPane.showInputDialog("Enter your username:");
        if(username!=null && !username.trim().isEmpty())
            new Main(username.trim());
        else System.exit(0);
    }
}
