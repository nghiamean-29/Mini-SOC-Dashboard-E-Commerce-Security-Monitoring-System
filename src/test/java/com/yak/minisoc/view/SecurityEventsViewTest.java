package com.yak.minisoc.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Pre;
import com.yak.minisoc.entity.security.SecurityEvent;
import com.yak.minisoc.entity.security.SecurityEventType;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SecurityEventsViewTest {

    @ParameterizedTest
    @ValueSource(strings = {"Tín hiệu A & B", "</pre><img src=x onerror=alert(1)><script>alert(2)</script>"})
    void eventDetailsRenderStoredValuesAsTextNodes(String value) {
        SecurityEvent event = SecurityEvent.of(SecurityEventType.API_REQUEST, Instant.now(), "192.0.2.1", "/probe")
            .withDescription(value)
            .withMetadata(Map.of("detail", value));

        var dialog = SecurityEventsView.createDetailsDialog(event);
        var components = descendants(dialog).toList();
        var metadata = components.stream().filter(Pre.class::isInstance).map(Pre.class::cast)
            .findFirst().orElseThrow();
        assertThat(metadata.getText()).isEqualTo("{detail=" + value + "}");
        assertThat(metadata.getElement().getChildren()).allMatch(element -> element.isTextNode());
        var description = components.stream()
            .filter(component -> component.getElement().getTag().equals("span"))
            .filter(component -> component.getElement().getText().startsWith("Description: "))
            .findFirst().orElseThrow();
        assertThat(description.getElement().getText()).isEqualTo("Description: " + value);
        assertThat(description.getElement().getChildren()).allMatch(element -> element.isTextNode());
        assertThat(components).noneMatch(component -> component.getElement().hasProperty("innerHTML"));
    }

    private static Stream<Component> descendants(Component parent) {
        return Stream.concat(Stream.of(parent), parent.getChildren().flatMap(SecurityEventsViewTest::descendants));
    }
}
