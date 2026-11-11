package com.yak.minisoc.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.yak.minisoc.entity.security.AlertStatus;
import com.yak.minisoc.entity.security.AlertType;
import com.yak.minisoc.entity.security.Severity;
import com.yak.minisoc.service.AlertMonitoringService;
import com.yak.minisoc.service.AlertMonitoringService.Details;
import com.yak.minisoc.service.AlertMonitoringService.Evidence;
import com.yak.minisoc.service.AlertMonitoringService.Summary;
import jakarta.annotation.security.RolesAllowed;

@Route("soc/alerts")
@PageTitle("Alert Monitoring | Mini SOC")
@RolesAllowed({"ADMIN", "SOC_ANALYST"})
public class AlertMonitoringView extends VerticalLayout {

    private final AlertMonitoringService alerts;
    private final Grid<Summary> grid = new Grid<>(Summary.class, false);
    private final Select<Severity> severity = filter("Severity", Severity.values());
    private final Select<AlertStatus> status = filter("Alert Status", AlertStatus.values());
    private final Select<AlertType> type = filter("Attack Type", AlertType.values());
    private final TextField sourceIp = new TextField("Source IP");
    private final Paragraph pageLabel = new Paragraph();
    private int page;
    private final Button previous = new Button("Previous", event -> loadPage(page - 1));
    private final Button next = new Button("Next", event -> loadPage(page + 1));

    public AlertMonitoringView(AlertMonitoringService alerts) {
        this.alerts = alerts;
        setSizeFull();
        severity.addValueChangeListener(event -> loadPage(0));
        status.addValueChangeListener(event -> loadPage(0));
        type.addValueChangeListener(event -> loadPage(0));
        sourceIp.setPlaceholder("Exact IP address");
        sourceIp.setClearButtonVisible(true);
        sourceIp.setMaxLength(45);
        sourceIp.setValueChangeMode(ValueChangeMode.LAZY);
        sourceIp.addValueChangeListener(event -> loadPage(0));

        var toolbar = new HorizontalLayout(severity, status, type, sourceIp,
            new Button("Refresh", event -> loadPage(page)),
            new Button("Back to Dashboard", event -> getUI().ifPresent(ui -> ui.navigate(DashboardView.class))));
        toolbar.setAlignItems(Alignment.END);
        toolbar.getStyle().set("flex-wrap", "wrap");

        configureGrid();
        var pager = new HorizontalLayout(previous, pageLabel, next);
        pager.setAlignItems(Alignment.CENTER);
        pageLabel.getElement().setAttribute("aria-live", "polite");
        add(new H1("Alert Monitoring"), toolbar, grid, pager);
        expand(grid);
        loadPage(0);
    }

    private static <T> Select<T> filter(String label, T[] values) {
        Select<T> select = new Select<>();
        select.setLabel(label);
        select.setItems(values);
        select.setEmptySelectionAllowed(true);
        select.setEmptySelectionCaption("All");
        return select;
    }

    private void configureGrid() {
        grid.addColumn(Summary::id).setHeader("Alert ID").setAutoWidth(true);
        grid.addColumn(Summary::type).setHeader("Alert Type").setAutoWidth(true);
        grid.addColumn(Summary::severity).setHeader("Severity").setAutoWidth(true);
        grid.addColumn(Summary::status).setHeader("Status").setAutoWidth(true);
        grid.addColumn(Summary::sourceIp).setHeader("Source IP").setAutoWidth(true);
        grid.addColumn(Summary::ruleName).setHeader("Rule Name").setAutoWidth(true);
        grid.addColumn(Summary::detectedAt).setHeader("Detected At (UTC)").setAutoWidth(true);
        grid.addSelectionListener(event -> event.getFirstSelectedItem().ifPresent(row -> {
            grid.deselectAll();
            openDetails(row.id());
        }));
    }

    private void loadPage(int requestedPage) {
        var results = alerts.list(severity.getValue(), status.getValue(), type.getValue(),
            sourceIp.getValue(), requestedPage);
        if (requestedPage > 0 && results.isEmpty()) {
            loadPage(0);
            return;
        }
        page = requestedPage;
        grid.setItems(results.getContent());
        previous.setEnabled(results.hasPrevious());
        next.setEnabled(results.hasNext());
        pageLabel.setText(results.isEmpty() ? "No matching alerts"
            : "Page %d of %d (%d alerts)".formatted(page + 1, results.getTotalPages(),
                results.getTotalElements()));
    }

    private void openDetails(Long id) {
        alerts.details(id).ifPresentOrElse(this::showDetails,
            () -> Notification.show("This alert is no longer available."));
    }

    private void showDetails(Details details) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Alert Details");
        dialog.setWidth("min(95vw, 850px)");
        dialog.getFooter().add(new Button("Close", event -> dialog.close()));

        Summary alert = details.summary();
        var body = new VerticalLayout();
        body.setPadding(false);
        body.setSpacing(false);
        body.add(field("Alert ID", alert.id()), field("Alert Type", alert.type()),
            field("Severity", alert.severity()), field("Status", alert.status()),
            field("Source IP", alert.sourceIp()), field("Rule Name", alert.ruleName()),
            field("Detected At (UTC)", alert.detectedAt()), field("Username", details.username()),
            field("User ID", details.userId()), field("Related Events", details.relatedEventCount()),
            field("Deduplication Key", details.dedupKey()), field("Description", details.description()));

        var incident = details.incident();
        body.add(new H3("Linked Incident"));
        if (incident == null) {
            body.add(new Paragraph("No linked incident."));
        } else {
            body.add(field("Incident Code", incident.code()), field("Title", incident.title()),
                field("Incident Status", incident.status()));
        }

        if (!details.evidence().isEmpty()) {
            Grid<Evidence> evidence = new Grid<>(Evidence.class, false);
            evidence.addColumn(Evidence::id).setHeader("Event ID");
            evidence.addColumn(Evidence::type).setHeader("Event Type").setAutoWidth(true);
            evidence.addColumn(Evidence::occurredAt).setHeader("Occurred At (UTC)").setAutoWidth(true);
            evidence.addColumn(Evidence::endpoint).setHeader("Endpoint");
            evidence.setItems(details.evidence());
            evidence.setHeight("220px");
            body.add(new H3("Linked Events"), evidence);
        }
        dialog.add(body);
        dialog.open();
    }

    private static Paragraph field(String label, Object value) {
        Paragraph field = new Paragraph(label + ": " + (value == null ? "—" : value));
        field.getStyle().set("margin", "0.25rem 0").set("white-space", "pre-wrap")
            .set("overflow-wrap", "anywhere");
        return field;
    }
}
