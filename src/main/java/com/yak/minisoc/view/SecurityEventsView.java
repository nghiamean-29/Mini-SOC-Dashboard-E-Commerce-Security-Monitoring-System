
package com.yak.minisoc.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import com.yak.minisoc.entity.security.SecurityEvent;
import com.yak.minisoc.entity.security.SecurityEventType;
import com.yak.minisoc.entity.security.Severity;
import com.yak.minisoc.repository.SecurityEventRepository;
import com.yak.minisoc.repository.SecurityEventSpecifications;

import jakarta.annotation.security.RolesAllowed;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

@Route("soc/events")
@PageTitle("Security Events | Mini SOC")
@RolesAllowed({"ADMIN", "SOC_ANALYST"})
public class SecurityEventsView extends VerticalLayout {

    private static final int PAGE_SIZE = 20;

    private static final ZoneId ZONE =
        ZoneId.of("Asia/Ho_Chi_Minh");

    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(ZONE);

    private final SecurityEventRepository eventRepository;

    // ==========================================
    // GRID
    // ==========================================

    private final Grid<SecurityEvent> grid =
        new Grid<>(SecurityEvent.class, false);

    // ==========================================
    // FILTERS
    // ==========================================

    private final TextField ipFilter =
        new TextField("Source IP");

    private final TextField usernameFilter =
        new TextField("Username");

    private final ComboBox<SecurityEventType> typeFilter =
        new ComboBox<>("Event Type");

    private final ComboBox<Severity> severityFilter =
        new ComboBox<>("Severity");

    private final DatePicker fromDate =
        new DatePicker("Từ ngày");

    private final DatePicker toDate =
        new DatePicker("Đến ngày");

    // ==========================================
    // PAGINATION
    // ==========================================

    private final Span pageInfo = new Span();

    private final Button previousButton =
        new Button("Trang trước");

    private final Button nextButton =
        new Button("Trang sau");

    private int currentPage = 0;

    // ==========================================
    // CONSTRUCTOR
    // ==========================================

    public SecurityEventsView(
        SecurityEventRepository eventRepository) {

        this.eventRepository = eventRepository;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        H2 title = new H2("Security Events Monitoring");

        Span subtitle = new Span(
            "Mini SOC Dashboard - E-Commerce Security Monitoring"
        );

        subtitle.getStyle()
            .set("color", "#64748b");

        configureFilters();
        configureGrid();
        configurePagination();

        // ======================================
        // ACTION BUTTONS
        // ======================================

        Button searchButton = new Button(
            "Tìm kiếm",
            event -> {
                currentPage = 0;
                loadEvents();
            }
        );

        searchButton.getStyle()
            .set("background-color", "#2563eb")
            .set("color", "#ffffff");

        Button refreshButton = new Button(
            "Làm mới",
            event -> loadEvents()
        );

        Button clearButton = new Button(
            "Xóa bộ lọc",
            event -> {
                clearFilters();
                currentPage = 0;
                loadEvents();
            }
        );

        // ======================================
        // FILTER LAYOUT
        // ======================================


        HorizontalLayout filters = new HorizontalLayout(
            ipFilter,
            usernameFilter,
            typeFilter,
            severityFilter,
            fromDate,
            toDate
        );

        filters.setWidthFull();
        filters.setSpacing(true);
        filters.setPadding(false);
        filters.setAlignItems(Alignment.END);

// Tự động xuống dòng khi màn hình không đủ rộng
        filters.getStyle()
            .set("flex-wrap", "wrap")
            .set("gap", "12px");

        HorizontalLayout actions = new HorizontalLayout(
            searchButton,
            refreshButton,
            clearButton
        );

        actions.setAlignItems(Alignment.CENTER);

        HorizontalLayout pagination = new HorizontalLayout(
            previousButton,
            pageInfo,
            nextButton
        );

        pagination.setAlignItems(Alignment.CENTER);

        // ======================================
        // ADD COMPONENTS
        // ======================================

        add(
            title,
            subtitle,
            filters,
            actions,
            grid,
            pagination
        );

        expand(grid);

        loadEvents();
    }

    // ==========================================
    // CONFIGURE FILTERS
    // ==========================================

    private void configureFilters() {

        ipFilter.setPlaceholder("192.0.2.10");
        ipFilter.setClearButtonVisible(true);

        usernameFilter.setPlaceholder("customer01");
        usernameFilter.setClearButtonVisible(true);

        typeFilter.setItems(
            Arrays.asList(SecurityEventType.values())
        );

        typeFilter.setPlaceholder("Tất cả");
        typeFilter.setClearButtonVisible(true);

        severityFilter.setItems(
            Arrays.asList(Severity.values())
        );

        severityFilter.setPlaceholder("Tất cả");
        severityFilter.setClearButtonVisible(true);

        fromDate.setClearButtonVisible(true);
        toDate.setClearButtonVisible(true);

        fromDate.setLocale(Locale.forLanguageTag("vi-VN"));
        toDate.setLocale(Locale.forLanguageTag("vi-VN"));
    }

    // ==========================================
    // CONFIGURE GRID
    // ==========================================

    private void configureGrid() {

        grid.addColumn(SecurityEvent::getId)
            .setHeader("ID")
            .setAutoWidth(true)
            .setFlexGrow(0);

        grid.addColumn(event ->
                event.getEventType() == null
                    ? "-"
                    : event.getEventType().name()
            )
            .setHeader("Event Type")
            .setAutoWidth(true);

        grid.addColumn(event ->
                event.getSeverityHint() == null
                    ? "-"
                    : event.getSeverityHint().name()
            )
            .setHeader("Severity")
            .setAutoWidth(true);

        grid.addColumn(event ->
                display(event.getSourceIp())
            )
            .setHeader("Source IP")
            .setAutoWidth(true);

        grid.addColumn(event ->
                display(event.getUsername())
            )
            .setHeader("Username")
            .setAutoWidth(true);

        grid.addColumn(event ->
                display(event.getEndpoint())
            )
            .setHeader("Endpoint")
            .setAutoWidth(true);

        grid.addColumn(event ->
                formatTime(event.getOccurredAt())
            )
            .setHeader("Occurred At")
            .setAutoWidth(true);

        grid.addComponentColumn(event ->
                new Button(
                    "Chi tiết",
                    click -> showDetails(event)
                )
            )
            .setHeader("Action")
            .setAutoWidth(true);

        grid.setWidthFull();
        grid.setHeight("600px");

        grid.setEmptyStateText(
            "Không tìm thấy sự kiện bảo mật phù hợp."
        );
    }

    // ==========================================
    // CONFIGURE PAGINATION
    // ==========================================

    private void configurePagination() {

        previousButton.addClickListener(event -> {

            if (currentPage > 0) {
                currentPage--;
                loadEvents();
            }
        });

        nextButton.addClickListener(event -> {

            currentPage++;
            loadEvents();
        });
    }

    // ==========================================
    // LOAD SECURITY EVENTS
    // ==========================================

    private void loadEvents() {

        try {

            LocalDate start = fromDate.getValue();
            LocalDate end = toDate.getValue();

            if (start != null
                && end != null
                && start.isAfter(end)) {

                Notification.show(
                    "Ngày bắt đầu không được sau ngày kết thúc.",
                    4000,
                    Notification.Position.TOP_CENTER
                );

                return;
            }

            Instant fromTime = start == null
                ? null
                : start.atStartOfDay(ZONE).toInstant();

            // Inclusive end date:
            // [start of fromDate, start of day after toDate)
            Instant toTime = end == null
                ? null
                : end.plusDays(1)
                .atStartOfDay(ZONE)
                .toInstant();

            Page<SecurityEvent> result =
                eventRepository.findAll(
                    SecurityEventSpecifications.filter(
                        emptyToNull(ipFilter.getValue()),
                        emptyToNull(usernameFilter.getValue()),
                        typeFilter.getValue(),
                        severityFilter.getValue(),
                        fromTime,
                        toTime
                    ),
                    PageRequest.of(
                        currentPage,
                        PAGE_SIZE,
                        Sort.by(
                            Sort.Order.desc("occurredAt"),
                            Sort.Order.desc("id")
                        )
                    )
                );

            // Handle page number that no longer exists.
            if (result.getTotalPages() > 0
                && currentPage >= result.getTotalPages()) {

                currentPage = result.getTotalPages() - 1;

                loadEvents();
                return;
            }

            grid.setItems(result.getContent());

            int totalPages =
                Math.max(1, result.getTotalPages());

            pageInfo.setText(
                "Trang " + (currentPage + 1)
                    + " / " + totalPages
                    + " | Tổng: "
                    + result.getTotalElements()
                    + " sự kiện"
            );

            previousButton.setEnabled(currentPage > 0);

            nextButton.setEnabled(
                currentPage + 1 < result.getTotalPages()
            );

        } catch (Exception ex) {

            previousButton.setEnabled(false);
            nextButton.setEnabled(false);

            Notification.show(
                "Không thể tải Security Events. "
                    + "Vui lòng kiểm tra log máy chủ.",
                5000,
                Notification.Position.TOP_CENTER
            );
        }
    }

    // ==========================================
    // SHOW EVENT DETAILS
    // ==========================================

    private void showDetails(SecurityEvent event) {
        createDetailsDialog(event).open();
    }

    static Dialog createDetailsDialog(SecurityEvent event) {
        Dialog dialog = new Dialog();

        dialog.setWidth("650px");
        dialog.setMaxWidth("95vw");

        H3 title = new H3(
            "Security Event #" + event.getId()
        );

        VerticalLayout content = new VerticalLayout();

        content.setPadding(false);

        content.add(
            new Span(
                "Event Type: "
                    + displayEnum(event.getEventType())
            ),
            new Span(
                "Severity: "
                    + displayEnum(event.getSeverityHint())
            ),
            new Span(
                "Username: "
                    + display(event.getUsername())
            ),
            new Span(
                "Source IP: "
                    + display(event.getSourceIp())
            ),
            new Span(
                "Endpoint: "
                    + display(event.getEndpoint())
            ),
            new Span(
                "HTTP Method: "
                    + display(event.getHttpMethod())
            ),
            new Span(
                "Status Code: "
                    + (event.getStatusCode() == null
                    ? "-"
                    : event.getStatusCode())
            ),
            new Span(
                "Occurred At: "
                    + formatTime(event.getOccurredAt())
            ),
            new Span(
                "Request ID: "
                    + display(event.getRequestId())
            ),
            new Span(
                "Description: "
                    + display(event.getDescription())
            )
        );

        Map<String, Object> metadata =
            event.getMetadata();

        // Render as plain text, never as HTML.
        Pre metadataView = new Pre(
            metadata == null
                ? "{}"
                : metadata.toString()
        );

        metadataView.getStyle()
            .set("white-space", "pre-wrap")
            .set("overflow-wrap", "anywhere")
            .set("max-height", "250px")
            .set("overflow-y", "auto");

        content.add(
            new H3("Metadata (JSONB)"),
            metadataView
        );

        Button closeButton = new Button(
            "Đóng",
            click -> dialog.close()
        );

        dialog.add(
            title,
            content,
            closeButton
        );

        return dialog;
    }

    // ==========================================
    // CLEAR FILTERS
    // ==========================================

    private void clearFilters() {

        ipFilter.clear();
        usernameFilter.clear();

        typeFilter.clear();
        severityFilter.clear();

        fromDate.clear();
        toDate.clear();
    }

    // ==========================================
    // HELPERS
    // ==========================================

    private String emptyToNull(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private static String display(String value) {

        return value == null || value.isBlank()
            ? "-"
            : value;
    }

    private static String displayEnum(Enum<?> value) {

        return value == null
            ? "-"
            : value.name();
    }

    private static String formatTime(Instant instant) {

        return instant == null
            ? "-"
            : DATE_FORMAT.format(instant);
    }
}
