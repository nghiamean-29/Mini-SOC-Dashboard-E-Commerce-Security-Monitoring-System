package com.yak.minisoc.view;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.yak.minisoc.service.DashboardAnalyticsService;
import com.yak.minisoc.service.DashboardAnalyticsService.Analytics;
import com.yak.minisoc.service.DashboardAnalyticsService.Count;
import com.yak.minisoc.service.DashboardAnalyticsService.Window;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DashboardAnalyticsPanel extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(DashboardAnalyticsPanel.class);
    private static final DateTimeFormatter DATE_TIME =
        DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DAY =
        DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

    private final DashboardAnalyticsService analyticsService;
    private final Select<Window> window = new Select<>();
    private final Div results = new Div();

    DashboardAnalyticsPanel(DashboardAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
        setPadding(false);
        setWidthFull();

        window.setLabel("Analytics period");
        window.setItems(Window.values());
        window.setItemLabelGenerator(Window::label);
        window.setValue(Window.LAST_24_HOURS);
        window.addValueChangeListener(event -> refresh());

        results.setWidthFull();
        results.getElement().setAttribute("aria-live", "polite");
        add(new H2("SOC Analytics"), window, results);
    }

    void refresh() {
        results.removeAll();
        try {
            Window selected = window.getValue();
            Analytics analytics = analyticsService.getAnalytics(selected);
            DateTimeFormatter bucketFormat = selected == Window.LAST_24_HOURS ? DATE_TIME : DAY;
            List<Count> timeline = analytics.timeline().stream()
                .map(bucket -> new Count(bucketFormat.format(bucket.start()), bucket.total()))
                .toList();

            Div charts = new Div(
                chart("Security events timeline (UTC)", timeline),
                chart("Event severity distribution", analytics.severity()),
                chart("Incident status in this period", analytics.incidentStatuses()),
                chart("Suspected attack types by alert count", analytics.attackTypes()),
                chart("Top 10 suspicious source IPs by alert count", analytics.sourceIps())
            );
            charts.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fit, minmax(min(100%, 18rem), 1fr))")
                .set("align-items", "start")
                .set("gap", "1rem");

            results.add(
                new Paragraph(DATE_TIME.format(analytics.from()) + " to "
                    + DATE_TIME.format(analytics.to()) + " UTC"),
                charts
            );
        } catch (RuntimeException ex) {
            log.warn("Unable to load SOC analytics", ex);
            results.add(new Paragraph("Unable to load analytics. Refresh the dashboard to retry."));
        }
    }

    private Div chart(String title, List<Count> counts) {
        H3 heading = new H3(title);
        heading.getStyle().set("margin", "0 0 0.75rem");
        Div chart = new Div(heading);
        chart.getStyle()
            .set("min-width", "0")
            .set("padding", "1rem")
            .set("border", "1px solid var(--lumo-contrast-20pct, #94a3b8)")
            .set("border-radius", "0.75rem");

        if (counts.isEmpty()) {
            chart.add(new Paragraph("No records in this period."));
            return chart;
        }

        Div rows = new Div();
        rows.getStyle()
            .set("max-height", "20rem")
            .set("overflow-y", "auto")
            .set("scrollbar-gutter", "stable");
        rows.getElement().setAttribute("tabindex", "0");
        rows.getElement().setAttribute("role", "group");
        rows.getElement().setAttribute("aria-label", title);
        chart.add(rows);

        long max = counts.stream().mapToLong(Count::total).max().orElse(0);
        for (Count count : counts) {
            Span label = new Span(count.label().replace('_', ' '));
            label.getStyle().set("overflow-wrap", "anywhere");
            Span value = new Span(Long.toString(count.total()));
            value.getStyle().set("font-variant-numeric", "tabular-nums");
            Div bar = new Div();
            bar.getStyle()
                .set("height", "0.4rem")
                .set("width", (max == 0 ? 0 : 100.0 * count.total() / max) + "%")
                .set("background", "var(--lumo-primary-color, #2563eb)")
                .set("border-radius", "0.25rem");
            Div track = new Div(bar);
            track.getElement().setAttribute("aria-hidden", "true");
            track.getStyle()
                .set("grid-column", "1 / -1")
                .set("border-radius", "0.25rem")
                .set("background", "var(--lumo-contrast-10pct, #94a3b84d)");
            Div row = new Div(label, value, track);
            row.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "minmax(0, 1fr) auto")
                .set("gap", "0.2rem 0.75rem")
                .set("margin-bottom", "0.5rem");
            rows.add(row);
        }
        return chart;
    }
}
