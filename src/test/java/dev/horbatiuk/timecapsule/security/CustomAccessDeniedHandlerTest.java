package dev.horbatiuk.timecapsule.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class CustomAccessDeniedHandlerTest {

    @Test
    void handle_writesForbiddenJsonResponse() throws Exception {
        CustomAccessDeniedHandler handler = new CustomAccessDeniedHandler();

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        AccessDeniedException exception = new AccessDeniedException("Denied");

        // Локальний StringWriter для перевірки виводу
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        when(response.getWriter()).thenReturn(printWriter);

        handler.handle(request, response, exception);

        verify(response).setStatus(HttpStatus.FORBIDDEN.value());
        verify(response).setContentType("application/json");

        printWriter.flush();

        String actual = stringWriter.toString();

        assertTrue(actual.contains("\"status\": 403"));
        assertTrue(actual.contains("\"error\": \"Forbidden\""));
        assertTrue(actual.contains("\"message\": \"Access denied\""));
    }
}
