package com.example;

import javax.swing.*;
import com.example.FirebaseInitializer;
import com.impinj.octane.*;
import com.google.cloud.firestore.Firestore;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import com.google.cloud.firestore.WriteResult;
import com.google.cloud.firestore.SetOptions;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class RFIDReaderGUI {
    private ImpinjReader reader1;
    private ImpinjReader reader2;
    private final JTextArea outputArea1;
    private final JTextArea outputArea2;
    private final JSlider txPowerSlider;
    private final JSlider rxSensitivitySlider;
    private final JCheckBox filterCheckbox;
    private final JLabel connectionIndicator1;
    private final JLabel connectionIndicator2;
    private final JTextField raceNameField; // Field for specifying race name

    private final Map<String, Map<String, Map<String, String>>> tagDataBuffer = new HashMap<>();
    private final Timer updateTimer = new Timer(true); // Daemon timer

    public RFIDReaderGUI() {
        FirebaseInitializer.initializeFirebase(); // Initialize Firebase

        JFrame frame = new JFrame("RFID Reader");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 500);

        outputArea1 = new JTextArea();
        outputArea1.setEditable(false);
        JScrollPane scrollPane1 = new JScrollPane(outputArea1);

        outputArea2 = new JTextArea();
        outputArea2.setEditable(false);
        JScrollPane scrollPane2 = new JScrollPane(outputArea2);

        JButton startButton = new JButton("Start Reading");
        JButton stopButton = new JButton("Stop Reading");
        JButton clearButton = new JButton("Clear Output");

        startButton.addActionListener(e -> startReading());
        stopButton.addActionListener(e -> stopReading());
        clearButton.addActionListener(e -> clearOutput());

        txPowerSlider = new JSlider(-30, 0, -5);
        txPowerSlider.setMajorTickSpacing(5);
        txPowerSlider.setPaintTicks(true);
        txPowerSlider.setPaintLabels(true);

        rxSensitivitySlider = new JSlider(-120, -60, -85);
        rxSensitivitySlider.setMajorTickSpacing(10);
        rxSensitivitySlider.setPaintTicks(true);
        rxSensitivitySlider.setPaintLabels(true);

        filterCheckbox = new JCheckBox("Filter EPCs with more than 9 characters");

        raceNameField = new JTextField(20); // Input for race name
        JPanel racePanel = new JPanel();
        racePanel.add(new JLabel("Race Name:"));
        racePanel.add(raceNameField);

        JPanel panel = new JPanel();
        panel.add(startButton);
        panel.add(stopButton);
        panel.add(clearButton);

        panel.add(new JLabel("Transmit Power (dBm):"));
        panel.add(txPowerSlider);
        panel.add(new JLabel("Receive Sensitivity (dBm):"));
        panel.add(rxSensitivitySlider);
        panel.add(filterCheckbox);

        JPanel outputPanel = new JPanel(new GridLayout(1, 2));
        outputPanel.add(scrollPane1);
        outputPanel.add(scrollPane2);

        connectionIndicator1 = createConnectionIndicator();
        connectionIndicator2 = createConnectionIndicator();

        JPanel indicatorPanel = new JPanel();
        indicatorPanel.add(new JLabel("Reader 1: "));
        indicatorPanel.add(connectionIndicator1);
        indicatorPanel.add(new JLabel("Reader 2: "));
        indicatorPanel.add(connectionIndicator2);

        frame.getContentPane().setLayout(new BoxLayout(frame.getContentPane(), BoxLayout.Y_AXIS));
        frame.getContentPane().add(racePanel);
        frame.getContentPane().add(indicatorPanel);
        frame.getContentPane().add(outputPanel);
        frame.getContentPane().add(panel);

        frame.setVisible(true);

        appendOutput(outputArea1, String.format("%-15s %-10s %s", "Tag #", "Date", "Time"));
        appendOutput(outputArea2, String.format("%-15s %-10s %s", "Tag #", "Date", "Time"));

        // Schedule periodic Firebase updates
        updateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                String raceName = raceNameField.getText().trim();
                if (!raceName.isEmpty()) {
                    flushDataToFirestore(raceName);
                } else {
                    System.err.println("Race name is empty. Cannot flush data to Firestore.");
                }
            }
        }, 10000, 10000); // Start after 10 seconds, repeat every 10 seconds
    }

    private JLabel createConnectionIndicator() {
        JLabel indicator = new JLabel();
        indicator.setOpaque(true);
        indicator.setBackground(Color.RED);
        indicator.setPreferredSize(new Dimension(20, 20));
        return indicator;
    }

    private void startReading() {
        if (reader1 == null) reader1 = new ImpinjReader();
        if (reader2 == null) reader2 = new ImpinjReader();

        new Thread(() -> startReader(reader1, "192.168.10.1", outputArea1, connectionIndicator1)).start();
        new Thread(() -> startReader(reader2, "192.168.10.2", outputArea2, connectionIndicator2)).start();
    }

    private void startReader(ImpinjReader reader, String hostname, JTextArea outputArea, JLabel connectionIndicator) {
        try {
            reader.connect(hostname);
            connectionIndicator.setBackground(Color.GREEN);
            appendOutput(outputArea, "Connected to the reader: " + hostname);

            Thread.sleep(100);

            Settings settings = reader.queryDefaultSettings();
            AntennaConfigGroup antennas = settings.getAntennas();
            antennas.disableAll();
            antennas.enableById(new short[]{1});

            double txPower = txPowerSlider.getValue();
            double rxSensitivity = rxSensitivitySlider.getValue();
            antennas.getAntenna(1).setTxPowerinDbm(txPower);
            antennas.getAntenna(1).setRxSensitivityinDbm(rxSensitivity);

            appendOutput(outputArea, "Transmit Power: " + txPower + " dBm, Receive Sensitivity: " + rxSensitivity + " dBm");

            reader.applySettings(settings);
            appendOutput(outputArea, "Settings applied for " + hostname + ".");

            reader.setTagReportListener((r, report) -> {
                for (Tag tag : report.getTags()) {
                    String epc = tag.getEpc().toString();
                    String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
                    String time = new SimpleDateFormat("HH:mm:ss").format(new Date());

                    if (!filterCheckbox.isSelected() || epc.length() <= 9) {
                        appendOutput(outputArea, String.format("%-15s %-10s %s", epc, date, time));
                        bufferTagData(epc, hostname, date, time); // Buffer data
                    }
                }
            });

            reader.start();
            appendOutput(outputArea, "Reading started on " + hostname + ". Press Stop Reading to stop.");

        } catch (OctaneSdkException | InterruptedException e) {
            e.printStackTrace();
            appendOutput(outputArea, "Error with " + hostname + ": " + e.getMessage());
        }
    }

    private void stopReading() {
        if (reader1 != null) {
            try {
                reader1.stop();
                reader1.disconnect();
                connectionIndicator1.setBackground(Color.RED);
                appendOutput(outputArea1, "Disconnected from Reader 1.");
            } catch (OctaneSdkException e) {
                e.printStackTrace();
            }
        }
        if (reader2 != null) {
            try {
                reader2.stop();
                reader2.disconnect();
                connectionIndicator2.setBackground(Color.RED);
                appendOutput(outputArea2, "Disconnected from Reader 2.");
            } catch (OctaneSdkException e) {
                e.printStackTrace();
            }
        }
    }

    private void appendOutput(JTextArea outputArea, String message) {
        outputArea.append(message + "\n");
        outputArea.setCaretPosition(outputArea.getDocument().getLength());
    }

    private void clearOutput() {
        outputArea1.setText("");
        outputArea2.setText("");
        appendOutput(outputArea1, String.format("%-15s %-10s %s", "EPC", "Date", "Time"));
        appendOutput(outputArea2, String.format("%-15s %-10s %s", "EPC", "Date", "Time"));
    }

    private void bufferTagData(String epc, String readerName, String date, String time) {
        synchronized (tagDataBuffer) {
            tagDataBuffer
                    .computeIfAbsent(readerName, k -> new HashMap<>())
                    .put(epc, Map.of("date", date, "time", time));
        }
    }

    private void ensureAncestorDocumentExists(Firestore db, String raceName) {
        DocumentReference ancestorDocRef = db.collection("RFIDReaders").document(raceName);
        try {
            ApiFuture<WriteResult> future = ancestorDocRef.set(Map.of("initialized", "true"), SetOptions.merge());
            future.get(); // Wait for the operation to complete
            System.out.println("Ancestor document ensured for race: " + raceName);
        } catch (Exception e) {
            System.err.println("Error ensuring ancestor document: " + e.getMessage());
        }
    }
    

    private void flushDataToFirestore(String raceName) {
        Firestore db = FirebaseInitializer.getFirestore();
    
        ensureAncestorDocumentExists(db, raceName); // Ensure ancestor document exists
    
        synchronized (tagDataBuffer) {
            for (String readerName : tagDataBuffer.keySet()) {
                Map<String, Map<String, String>> readerTags = tagDataBuffer.get(readerName);
                for (String epc : readerTags.keySet()) {
                    Map<String, String> tagData = new HashMap<>(readerTags.get(epc)); // Create a mutable copy
                    tagData.put("initialized", "true"); // Add the initialized field
    
                    DocumentReference tagDocRef = db.collection("RFIDReaders")
                            .document(raceName)
                            .collection(readerName)
                            .document(epc);
    
                    try {
                        ApiFuture<WriteResult> future = tagDocRef.set(tagData);
                        WriteResult result = future.get();
                        System.out.println("Tag data for " + epc + " added to race: " + raceName
                                + " and reader: " + readerName + " at " + result.getUpdateTime());
                    } catch (Exception e) {
                        System.err.println("Error adding tag data: " + e.getMessage());
                    }
                }
            }
            tagDataBuffer.clear(); // Clear the buffer after flushing
        }
    }
    
    

    public static void main(String[] args) {
        SwingUtilities.invokeLater(RFIDReaderGUI::new);
    }
}
