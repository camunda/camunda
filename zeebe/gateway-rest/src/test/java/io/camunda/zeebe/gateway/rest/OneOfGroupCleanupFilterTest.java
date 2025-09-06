package io.camunda.zeebe.gateway.rest;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.rest.web.OneOfGroupCleanupFilter;
import io.camunda.zeebe.gateway.validation.OneOfGroupValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.MDC;

/** Simple unit test for {@link OneOfGroupCleanupFilter}. */
public class OneOfGroupCleanupFilterTest {

  @AfterEach
  void cleanup() {
    OneOfGroupValidator.clearThreadLocal();
    MDC.clear();
  }

  @Test
  void shouldSetHeaderAndClearThreadLocal() throws Exception {
    // Arrange: manually set the ThreadLocal via reflection
    final Field f = OneOfGroupValidator.class.getDeclaredField("LAST_MATCHED_BRANCH");
    f.setAccessible(true);
    final ThreadLocal<?> tl = (ThreadLocal<?>) f.get(null);
    @SuppressWarnings("unchecked")
    final ThreadLocal<String> cast = (ThreadLocal<String>) tl;
    cast.set("123");

    final OneOfGroupCleanupFilter filter = new OneOfGroupCleanupFilter();
    final HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
    final HttpServletResponse resp = Mockito.mock(HttpServletResponse.class);
    final FilterChain chain = new FilterChain() {
      @Override
      public void doFilter(final ServletRequest request, final ServletResponse response)
          throws IOException, ServletException {
        // no-op
      }
    };

    // Track header via Mockito
  Mockito.doAnswer(inv -> null)
    .when(resp)
    .setHeader(Mockito.eq("X-OneOf-Matched-Branch"), Mockito.anyString());

    // Act
    filter.doFilter(req, resp, chain);

    // Assert header set and ThreadLocal cleared
    Mockito.verify(resp).setHeader("X-OneOf-Matched-Branch", "123");
    assertThat(OneOfGroupValidator.getLastMatchedBranchId()).isNull();
    assertThat(MDC.get("oneOfBranch")).isEqualTo("123");
  }
}
