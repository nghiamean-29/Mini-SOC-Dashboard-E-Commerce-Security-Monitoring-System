package com.yak.minisoc.view;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.ErrorParameter;
import com.vaadin.flow.router.HasErrorParameter;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AnonymousAllowed
public class ApplicationErrorView extends Div implements HasErrorParameter<Exception> {

    private static final Logger log = LoggerFactory.getLogger(ApplicationErrorView.class);

    @Override
    public int setErrorParameter(BeforeEnterEvent event, ErrorParameter<Exception> parameter) {
        Exception failure = parameter.getException();
        log.error("Vaadin view failed ({})", failure == null ? "unknown" : failure.getClass().getSimpleName());
        setText("Đã xảy ra lỗi. Vui lòng thử lại.");
        return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }
}
