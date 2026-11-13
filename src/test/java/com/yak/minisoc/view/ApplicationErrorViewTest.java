package com.yak.minisoc.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.ErrorParameter;
import org.junit.jupiter.api.Test;

class ApplicationErrorViewTest {

    @Test
    void navigationFailureHidesWrappedExceptionAndCustomMessage() {
        var view = new ApplicationErrorView();
        var exception = new IllegalStateException("exception-detail", new RuntimeException("root-cause-detail"));

        int status = view.setErrorParameter(mock(BeforeEnterEvent.class),
            new ErrorParameter<>(Exception.class, exception, "custom-detail"));

        assertThat(status).isEqualTo(500);
        assertThat(view.getText()).isEqualTo("Đã xảy ra lỗi. Vui lòng thử lại.");
        assertThat(view.getElement().getChildren()).allMatch(element -> element.isTextNode());
    }
}
