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
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.yak.minisoc.entity.security.IncidentStatus;
import com.yak.minisoc.entity.security.Severity;
import com.yak.minisoc.service.IncidentMonitoringService;
import com.yak.minisoc.service.IncidentMonitoringService.Alert;
import com.yak.minisoc.service.IncidentMonitoringService.Analyst;
import com.yak.minisoc.service.IncidentMonitoringService.Details;
import com.yak.minisoc.service.IncidentMonitoringService.Summary;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

@Route("soc/incidents")
@PageTitle("Incident Monitoring | Mini SOC")
@RolesAllowed({"ADMIN", "SOC_ANALYST"})
public class IncidentMonitoringView extends VerticalLayout {

    private final IncidentMonitoringService incidents;
    private final Grid<Summary> grid = new Grid<>(Summary.class, false);
    private final Select<IncidentStatus> status = new Select<>();
    private final Select<Severity> severity = new Select<>();
    private final Paragraph pageLabel = new Paragraph();
    private int page;
    private final Button previous = new Button("Previous", event -> loadPage(page - 1));
    private final Button next = new Button("Next", event -> loadPage(page + 1));

    public IncidentMonitoringView(IncidentMonitoringService incidents) {
        this.incidents = incidents;
        setSizeFull();

        status.setLabel("Incident Status");
        status.setItems(IncidentStatus.values());
        status.setEmptySelectionAllowed(true);
        status.setEmptySelectionCaption("All Statuses");
        status.addValueChangeListener(event -> loadPage(0));

        severity.setLabel("Severity");
        severity.setItems(Severity.values());
        severity.setEmptySelectionAllowed(true);
        severity.setEmptySelectionCaption("All Severities");
        severity.addValueChangeListener(event -> loadPage(0));

        var toolbar = new HorizontalLayout(status, severity,
            new Button("Refresh", event -> loadPage(page)),
            new Button("Back to Dashboard", event -> getUI().ifPresent(ui -> ui.navigate(""))));
        toolbar.setAlignItems(Alignment.END);
        toolbar.getStyle().set("flex-wrap", "wrap");

        configureGrid();
        var pager = new HorizontalLayout(previous, pageLabel, next);
        pager.setAlignItems(Alignment.CENTER);
        add(new H1("Incident Monitoring"), toolbar, grid, pager);
        expand(grid);
        loadPage(0);
    }

    private void configureGrid() {
        grid.addColumn(Summary::code).setHeader("Incident Code").setAutoWidth(true);
        grid.addColumn(Summary::title).setHeader("Title").setAutoWidth(true);
        grid.addColumn(Summary::type).setHeader("Attack Type").setAutoWidth(true);
        grid.addColumn(Summary::severity).setHeader("Severity").setAutoWidth(true);
        grid.addColumn(Summary::status).setHeader("Status").setAutoWidth(true);
        grid.addColumn(Summary::sourceIp).setHeader("Source IP").setAutoWidth(true);
        grid.addColumn(row -> row.assignee() == null ? "Unassigned" : row.assignee().label())
            .setHeader("Assigned To").setAutoWidth(true);
        grid.addColumn(Summary::alertCount).setHeader("Alerts").setAutoWidth(true);
        grid.addColumn(Summary::detectedAt).setHeader("Detected At").setAutoWidth(true);
        grid.addComponentColumn(row -> new Button("Details", event -> openDetails(row.code())))
            .setHeader("Actions").setAutoWidth(true);
    }

    private void loadPage(int requestedPage) {
        var results = incidents.list(status.getValue(), severity.getValue(), requestedPage);
        if (requestedPage > 0 && results.isEmpty()) {
            loadPage(Math.max(0, results.getTotalPages() - 1));
            return;
        }
        page = requestedPage;
        grid.setItems(results.getContent());
        previous.setEnabled(results.hasPrevious());
        next.setEnabled(results.hasNext());
        pageLabel.setText(results.isEmpty() ? "No matching incidents"
            : "Page %d of %d (%d incidents)".formatted(page + 1, results.getTotalPages(),
                results.getTotalElements()));
    }

    private void openDetails(String code) {
        Dialog dialog = new Dialog();
        dialog.setWidth("min(95vw, 1050px)");
        dialog.getFooter().add(new Button("Close", event -> dialog.close()));
        if (refreshDetails(dialog, code)) {
            dialog.open();
        }
    }

    private boolean refreshDetails(Dialog dialog, String code) {
        var result = incidents.details(code);
        if (result.isEmpty()) {
            dialog.close();
            Notification.show("This incident is no longer available.");
            return false;
        }
        Details details = result.get();
        Summary row = details.summary();
        dialog.setHeaderTitle(row.code() + " — " + row.title());
        dialog.removeAll();
        var body = new VerticalLayout();
        body.setPadding(false);
        body.add(new Paragraph("%s | %s | %s".formatted(row.type(), row.severity(), row.status())),
            new Paragraph("Source IP: " + (row.sourceIp() == null ? "Unknown" : row.sourceIp())),
            new Paragraph("Detected at: " + row.detectedAt()),
            new Paragraph("Assigned to: " + (row.assignee() == null ? "Unassigned" : row.assignee().label())));
        if (details.description() != null) {
            body.add(textBlock(details.description()));
        }
        if (details.resolution() != null) {
            body.add(new H3("Closure"), new Paragraph("Closed at: " + details.resolvedAt()),
                textBlock(details.resolution()));
        }
        addEvidence(body, details);
        if (row.status().isActive()) {
            addActions(body, dialog, details);
        }
        dialog.add(body);
        return true;
    }

    private void addEvidence(VerticalLayout body, Details details) {
        body.add(new H3("Related Alerts"));
        if (details.alerts().isEmpty()) {
            body.add(new Paragraph("No related alerts."));
        } else {
            Grid<Alert> alerts = new Grid<>(Alert.class, false);
            alerts.addColumn(Alert::id).setHeader("ID");
            alerts.addColumn(Alert::type).setHeader("Type").setAutoWidth(true);
            alerts.addColumn(Alert::severity).setHeader("Severity");
            alerts.addColumn(Alert::status).setHeader("Status");
            alerts.addColumn(Alert::sourceIp).setHeader("Source IP");
            alerts.addColumn(Alert::detectedAt).setHeader("Detected At").setAutoWidth(true);
            alerts.setItems(details.alerts());
            alerts.setHeight("220px");
            body.add(alerts);
        }

        body.add(new H3("Investigation Notes"));
        if (details.notes().isEmpty()) {
            body.add(new Paragraph("No investigation notes."));
        }
        details.notes().forEach(note -> body.add(
            new Paragraph((note.author() == null ? "Unknown author" : note.author())
                + " — " + note.createdAt()),
            textBlock(note.content())));
    }

    private void addActions(VerticalLayout body, Dialog dialog, Details details) {
        Summary row = details.summary();
        Select<Analyst> analyst = new Select<>();
        analyst.setLabel("Eligible SOC Analyst");
        analyst.setItemLabelGenerator(Analyst::label);
        var eligible = incidents.eligibleAnalysts();
        analyst.setItems(eligible);
        if (row.assignee() != null) {
            eligible.stream().filter(candidate -> candidate.id().equals(row.assignee().id()))
                .findFirst().ifPresent(analyst::setValue);
        }
        Button assign = new Button("Assign", event -> apply(dialog, row.code(), "Incident assigned.",
            () -> incidents.assign(row.id(), analyst.getValue().id())));
        assign.setEnabled(analyst.getValue() != null);
        analyst.addValueChangeListener(event -> assign.setEnabled(event.getValue() != null));

        Button start = new Button("Start Investigation", event -> apply(dialog, row.code(),
            "Investigation started.", () -> incidents.start(row.id())));
        start.setEnabled(row.status() == IncidentStatus.OPEN && row.assignee() != null);
        var assignment = new HorizontalLayout(analyst, assign, start);
        assignment.setAlignItems(Alignment.END);
        assignment.getStyle().set("flex-wrap", "wrap");
        body.add(new H3("Incident Actions"), assignment);
        if (eligible.isEmpty()) {
            body.add(new Paragraph("No active SOC analysts are available."));
        }

        if (details.canAddNote()) {
            TextArea note = actionText("Investigation Note");
            Button addNote = new Button("Add Note", event -> apply(dialog, row.code(), "Note added.",
                () -> incidents.addNote(row.id(), note.getValue())));
            requireText(note, addNote);
            body.add(note, addNote);
        }
        TextArea closure = actionText("Resolution or False-Positive Reason");
        Button resolve = new Button("Resolve", event -> apply(dialog, row.code(), "Incident resolved.",
            () -> incidents.resolve(row.id(), closure.getValue())));
        Button dismiss = new Button("Mark False Positive", event -> apply(dialog, row.code(),
            "Incident marked false positive.", () -> incidents.markFalsePositive(row.id(), closure.getValue())));
        requireText(closure, resolve, dismiss);
        body.add(closure, new HorizontalLayout(resolve, dismiss));
    }

    private static Paragraph textBlock(String text) {
        Paragraph paragraph = new Paragraph(text);
        paragraph.getStyle().set("white-space", "pre-wrap").set("overflow-wrap", "anywhere");
        return paragraph;
    }

    private static TextArea actionText(String label) {
        TextArea field = new TextArea(label);
        field.setWidthFull();
        field.setMaxLength(IncidentMonitoringService.MAX_TEXT_LENGTH);
        field.setValueChangeMode(ValueChangeMode.EAGER);
        return field;
    }

    private static void requireText(TextArea field, Button... buttons) {
        for (Button button : buttons) {
            button.setEnabled(false);
            field.addValueChangeListener(event -> button.setEnabled(!event.getValue().isBlank()));
        }
    }

    private void apply(Dialog dialog, String code, String success, Runnable action) {
        try {
            action.run();
        } catch (AccessDeniedException ex) {
            Notification.show("Your account is not authorized for this action.");
            return;
        } catch (OptimisticLockingFailureException ex) {
            Notification.show("The incident changed. Reopen the details to see the latest state.");
            return;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            Notification.show(ex.getMessage());
            return;
        }
        Notification.show(success);
        refreshDetails(dialog, code);
        loadPage(page);
    }
}
