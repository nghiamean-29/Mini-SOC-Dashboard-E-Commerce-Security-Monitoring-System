package com.yak.minisoc.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Unit tests for the order state machine - no database involved.
 *
 * <p>Why test an enum? Because the state machine is the rule that protects the money:
 * a bug here means an order that was cancelled can be shipped, or a delivered order can be
 * cancelled twice. It is also the cheapest possible test (microseconds), which is exactly
 * the kind of rule that deserves exhaustive coverage.</p>
 */
class OrderStatusTest {

    @Test
    @DisplayName("the happy path of a normal order")
    void happyPath() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CONFIRMED)).isTrue();
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.PROCESSING)).isTrue();
        assertThat(OrderStatus.PROCESSING.canTransitionTo(OrderStatus.SHIPPED)).isTrue();
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.COMPLETED)).isTrue();
    }

    @Test
    @DisplayName("no skipping: an order cannot jump from PENDING to SHIPPED")
    void noSkipping() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.SHIPPED)).isFalse();
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.PROCESSING)).isFalse();
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.COMPLETED)).isFalse();
    }

    @Test
    @DisplayName("cancellation is only possible before packing starts")
    void cancellationWindow() {
        assertThat(OrderStatus.PENDING.isCancellable()).isTrue();
        assertThat(OrderStatus.CONFIRMED.isCancellable()).isTrue();
        assertThat(OrderStatus.PROCESSING.isCancellable()).isFalse();
        assertThat(OrderStatus.SHIPPED.isCancellable()).isFalse();
        assertThat(OrderStatus.COMPLETED.isCancellable()).isFalse();
        assertThat(OrderStatus.CANCELLED.isCancellable()).isFalse();
    }

    @Test
    @DisplayName("COMPLETED and CANCELLED are the end of the line")
    void terminalStates() {
        assertThat(OrderStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(OrderStatus.PENDING.isTerminal()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    @DisplayName("every state is defined in the map (a missing entry would be a NullPointerException)")
    void everyStatusHasAnEntry(OrderStatus status) {
        assertThat(status.allowedNextStatuses()).isNotNull();
    }

    @Test
    @DisplayName("a terminal state cannot go anywhere, and null is never a valid target")
    void nothingFromTerminalStates() {
        assertThat(OrderStatus.CANCELLED.canTransitionTo(null)).isFalse();
        assertThat(OrderStatus.PENDING.canTransitionTo(null)).isFalse();
        assertThatThrownBy(() -> OrderStatus.CANCELLED.allowedNextStatuses()
                .add(OrderStatus.PENDING))
                .as("the outside world must not be able to edit the state machine")
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
