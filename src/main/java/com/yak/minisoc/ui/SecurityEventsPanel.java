
package com.yak.minisoc.ui;

import com.yak.minisoc.service.SecurityEventService;
import com.yak.minisoc.service.SecurityEventSimulatorService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import java.awt.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class SecurityEventsPanel extends JPanel {

    private final SecurityEventService eventService;
    private final SecurityEventSimulatorService simulatorService;
    private final boolean canSimulate;

    private final DefaultTableModel tableModel;
    private final JTable eventTable;

    private final JButton refreshButton;
    private final JButton simulateButton;
    private final JLabel statusLabel;

    private List<SecurityEventService.EventRow> currentEvents =
        List.of();

    private boolean busy = false;

    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public SecurityEventsPanel(
        SecurityEventService eventService,
        SecurityEventSimulatorService simulatorService,
        boolean canSimulate
    ) {

        this.eventService = eventService;
        this.simulatorService = simulatorService;
        this.canSimulate = canSimulate;

        setLayout(new BorderLayout(10, 15));

        setBorder(
            BorderFactory.createEmptyBorder(25, 25, 25, 25)
        );

        // ==========================================
        // HEADER
        // ==========================================

        JPanel header = new JPanel(new BorderLayout());

        JLabel title = new JLabel("Security Events");
        title.setFont(new Font("Arial", Font.BOLD, 24));

        JPanel actionPanel = new JPanel(
            new FlowLayout(FlowLayout.RIGHT, 10, 0)
        );

        simulateButton = new JButton("Tạo sự kiện demo");

        simulateButton.setVisible(canSimulate);

        simulateButton.addActionListener(
            e -> generateDemoEvents()
        );

        refreshButton = new JButton("Làm mới");

        refreshButton.addActionListener(
            e -> loadEvents()
        );

        actionPanel.add(simulateButton);
        actionPanel.add(refreshButton);

        header.add(title, BorderLayout.WEST);
        header.add(actionPanel, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        // ==========================================
        // EVENT TABLE
        // ==========================================

        String[] columns = {
            "Thời gian",
            "Loại sự kiện",
            "IP nguồn",
            "Mức độ",
            "Tài khoản",
            "Endpoint",
            "HTTP"
        };

        tableModel = new DefaultTableModel(columns, 0) {

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        eventTable = new JTable(tableModel);

        eventTable.setRowHeight(28);

        eventTable.setSelectionMode(
            ListSelectionModel.SINGLE_SELECTION
        );

        eventTable.setAutoCreateRowSorter(true);

        JScrollPane scrollPane =
            new JScrollPane(eventTable);

        add(scrollPane, BorderLayout.CENTER);

        // ==========================================
        // FOOTER
        // ==========================================

        JPanel footer = new JPanel(new BorderLayout());

        statusLabel = new JLabel("Chưa tải dữ liệu");

        JButton detailButton =
            new JButton("Xem chi tiết");

        detailButton.addActionListener(
            e -> showEventDetails()
        );

        footer.add(statusLabel, BorderLayout.WEST);
        footer.add(detailButton, BorderLayout.EAST);

        add(footer, BorderLayout.SOUTH);

        loadEvents();
    }

    // ==========================================
    // BUTTON STATES
    // ==========================================

    private void setBusy(boolean value) {

        busy = value;

        refreshButton.setEnabled(!busy);

        simulateButton.setEnabled(
            !busy && canSimulate
        );
    }

    // ==========================================
    // LOAD EVENTS
    // ==========================================

    public void loadEvents() {

        if (busy) {
            return;
        }

        setBusy(true);

        statusLabel.setText(
            "Đang tải sự kiện..."
        );

        SwingWorker<List<SecurityEventService.EventRow>, Void> worker =
            new SwingWorker<>() {

                @Override
                protected List<SecurityEventService.EventRow>
                doInBackground() {

                    return eventService.getLatestEvents();
                }

                @Override
                protected void done() {

                    setBusy(false);

                    try {

                        currentEvents = get();

                        tableModel.setRowCount(0);

                        for (SecurityEventService.EventRow event
                            : currentEvents) {

                            String time =
                                event.occurredAt() == null
                                    ? ""
                                    : DATE_FORMAT.format(
                                    event.occurredAt()
                                );

                            tableModel.addRow(
                                new Object[]{
                                    time,
                                    event.eventType(),
                                    event.sourceIp(),
                                    event.severity(),
                                    event.username(),
                                    event.endpoint(),
                                    event.statusCode()
                                }
                            );
                        }

                        statusLabel.setText(
                            "Đang hiển thị "
                                + currentEvents.size()
                                + " sự kiện mới nhất"
                        );

                    } catch (InterruptedException ex) {

                        Thread.currentThread().interrupt();

                        statusLabel.setText(
                            "Tải dữ liệu bị gián đoạn"
                        );

                    } catch (ExecutionException ex) {

                        statusLabel.setText(
                            "Không thể tải Security Events"
                        );

                        JOptionPane.showMessageDialog(
                            SecurityEventsPanel.this,
                            "Không thể truy vấn dữ liệu sự kiện.",
                            "Lỗi",
                            JOptionPane.ERROR_MESSAGE
                        );
                    }
                }
            };

        worker.execute();
    }

    // ==========================================
    // GENERATE DEMO EVENTS
    // ==========================================

    private void generateDemoEvents() {

        if (!canSimulate) {

            JOptionPane.showMessageDialog(
                this,
                "Bạn không có quyền tạo sự kiện demo.",
                "Không có quyền",
                JOptionPane.WARNING_MESSAGE
            );

            return;
        }

        if (busy) {
            return;
        }

        int confirmation = JOptionPane.showConfirmDialog(
            this,
            "Tạo 4 sự kiện bảo mật mô phỏng "
                + "và lưu vào PostgreSQL?",
            "Xác nhận tạo sự kiện demo",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );

        if (confirmation != JOptionPane.YES_OPTION) {
            return;
        }

        setBusy(true);

        statusLabel.setText(
            "Đang tạo sự kiện demo..."
        );

        SwingWorker<Integer, Void> worker =
            new SwingWorker<>() {

                @Override
                protected Integer doInBackground() {

                    throw new IllegalStateException(
                        "Chức năng mô phỏng chưa được kết nối với phiên đăng nhập."
                    );
                }

                @Override
                protected void done() {

                    setBusy(false);

                    try {

                        int created = get();

                        JOptionPane.showMessageDialog(
                            SecurityEventsPanel.this,
                            "Đã tạo " + created
                                + " sự kiện demo.",
                            "Thành công",
                            JOptionPane.INFORMATION_MESSAGE
                        );

                        loadEvents();

                    } catch (InterruptedException ex) {

                        Thread.currentThread().interrupt();

                        statusLabel.setText(
                            "Tạo sự kiện bị gián đoạn"
                        );

                    } catch (ExecutionException ex) {

                        statusLabel.setText(
                            "Không thể tạo sự kiện demo"
                        );

                        JOptionPane.showMessageDialog(
                            SecurityEventsPanel.this,
                            "Không thể lưu dữ liệu mô phỏng "
                                + "vào PostgreSQL.\n"
                                + "Kiểm tra log trong IntelliJ.",
                            "Lỗi",
                            JOptionPane.ERROR_MESSAGE
                        );

                        ex.printStackTrace();
                    }
                }
            };

        worker.execute();
    }

    // ==========================================
    // EVENT DETAILS
    // ==========================================

    private void showEventDetails() {

        int selectedRow =
            eventTable.getSelectedRow();

        if (selectedRow < 0) {

            JOptionPane.showMessageDialog(
                this,
                "Vui lòng chọn một sự kiện."
            );

            return;
        }

        int modelRow =
            eventTable.convertRowIndexToModel(
                selectedRow
            );

        if (modelRow >= currentEvents.size()) {
            return;
        }

        SecurityEventService.EventRow event =
            currentEvents.get(modelRow);

        String details =
            "Thời gian: " + event.occurredAt()
                + "\nLoại: " + event.eventType()
                + "\nIP nguồn: " + event.sourceIp()
                + "\nMức độ: " + event.severity()
                + "\nTài khoản: " + event.username()
                + "\nEndpoint: " + event.endpoint()
                + "\nHTTP: " + event.statusCode()
                + "\nMô tả: " + event.description();

        JTextArea textArea =
            new JTextArea(details, 12, 45);

        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);

        JOptionPane.showMessageDialog(
            this,
            new JScrollPane(textArea),
            "Chi tiết Security Event",
            JOptionPane.INFORMATION_MESSAGE
        );
    }
}
