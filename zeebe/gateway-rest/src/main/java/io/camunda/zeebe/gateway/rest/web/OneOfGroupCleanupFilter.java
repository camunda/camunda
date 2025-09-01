package io.camunda.zeebe.gateway.rest.web;

import io.camunda.zeebe.gateway.validation.OneOfGroupValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Ensures the ThreadLocal used by {@link OneOfGroupValidator} does not leak across requests.
 * Also captures the matched branch id into MDC for logging correlation if present.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10) // run late to capture validation done in controllers
public class OneOfGroupCleanupFilter extends HttpFilter {

  private static final String HEADER_NAME = "X-OneOf-Matched-Branch"; // owner: validation
  private static final String MDC_KEY = "oneOfBranch"; // owner: validation

  @Override
  protected void doFilter(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain chain) throws IOException, ServletException {
    try {
      chain.doFilter(request, response);
    } finally {
      final String branch = OneOfGroupValidator.getLastMatchedBranchId();
      if (branch != null) {
        // Add header only on successful validation cases (avoid overwriting if already set)
        if (!response.isCommitted()) {
          response.setHeader(HEADER_NAME, branch);
        }
        MDC.put(MDC_KEY, branch);
      }
      OneOfGroupValidator.clearThreadLocal();
    }
  }
}
