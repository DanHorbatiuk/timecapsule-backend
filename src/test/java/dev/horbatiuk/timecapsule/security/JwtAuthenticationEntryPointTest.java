package dev.horbatiuk.timecapsule.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class JwtAuthenticationEntryPointTest {

    private JwtAuthenticationEntryPoint entryPoint;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private AuthenticationException authException;

    @BeforeEach
    void setUp() {
        entryPoint = new JwtAuthenticationEntryPoint();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        authException = mock(AuthenticationException.class);
    }

    @Test
    void commence_setsUnauthorizedStatusAndWritesJson() throws Exception {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        entryPoint.commence(request, response, authException);

        verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
        verify(response).setContentType("application/json");

        printWriter.flush();

        String actual = stringWriter.toString().trim();

        assertTrue(actual.contains("\"status\": 401"));
        assertTrue(actual.contains("\"error\": \"Unauthorized\""));
        assertTrue(actual.contains("\"message\": \"Invalid or missing JWT token\""));
    }

}
