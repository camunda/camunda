/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.tomcat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for {@link CCSMRequestAdjustmentFilter}.
 *
 * <p>These tests verify that the filter uses {@link PathRewritingRequestWrapper} to rewrite request
 * paths (rather than {@link jakarta.servlet.RequestDispatcher#forward}), which is required for
 * compatibility with Spring Security 7's {@code PathPatternRequestMatcher} that evaluates the
 * original request URI.
 */
@ExtendWith(MockitoExtension.class)
public class CCSMRequestAdjustmentFilterTest {

  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private FilterChain filterChain;
  @Mock private FilterConfig filterConfig;
  @Mock private ServletContext servletContext;

  @BeforeEach
  void setUp() throws Exception {
    when(filterConfig.getServletContext()).thenReturn(servletContext);
    // ExternalResourcesUtil.stripContextPath() calls getContextPath() which must not be null.
    // Lenient because tests that short-circuit before this call would otherwise fail.
    lenient().when(request.getContextPath()).thenReturn("");
  }

  private CCSMRequestAdjustmentFilter createFilter() throws Exception {
    final CCSMRequestAdjustmentFilter filter = new CCSMRequestAdjustmentFilter();
    filter.init(filterConfig);
    return filter;
  }

  /**
   * Regression tests for Spring Security 7 compatibility. The filter must wrap the request using
   * PathRewritingRequestWrapper (not RequestDispatcher.forward) so that PathPatternRequestMatcher
   * sees the rewritten path.
   */
  @Nested
  class ExternalApiRewriteUsesRequestWrapper {

    /**
     * Core regression test: /external/api/... must be rewritten to /api/external/... using a
     * request wrapper, NOT RequestDispatcher.forward().
     */
    @Test
    void wrapsRequestWithRewrittenPath() throws Exception {
      // given
      final CCSMRequestAdjustmentFilter filter = createFilter();
      when(request.getRequestURI()).thenReturn("/external/api/some-endpoint");

      // when
      filter.doFilter(request, response, filterChain);

      // then — the filter chain must receive a wrapped request, NOT a forwarded dispatch
      final ArgumentCaptor<ServletRequest> requestCaptor =
          ArgumentCaptor.forClass(ServletRequest.class);
      verify(filterChain).doFilter(requestCaptor.capture(), eq(response));

      final HttpServletRequest wrappedRequest = (HttpServletRequest) requestCaptor.getValue();
      assertThat(wrappedRequest).isNotSameAs(request);
      assertThat(wrappedRequest).isInstanceOf(PathRewritingRequestWrapper.class);
      assertThat(wrappedRequest.getRequestURI()).isEqualTo("/api/external/some-endpoint");
    }
  }

  @Nested
  class NoModification {

    @Test
    void passesOriginalRequestForNonExternalApiPath() throws Exception {
      // given
      final CCSMRequestAdjustmentFilter filter = createFilter();
      when(request.getRequestURI()).thenReturn("/api/some-endpoint");

      // when
      filter.doFilter(request, response, filterChain);

      // then — original request is passed, not a wrapper
      verify(filterChain).doFilter(request, response);
    }

    @Test
    void passesOriginalRequestForRootPath() throws Exception {
      // given
      final CCSMRequestAdjustmentFilter filter = createFilter();
      when(request.getRequestURI()).thenReturn("/");

      // when
      filter.doFilter(request, response, filterChain);

      // then
      verify(filterChain).doFilter(request, response);
    }
  }

  @Nested
  class ContextPathHandling {

    @Test
    void contextPathIsKeptOnRewrite() throws Exception {
      // given — request has a context path that should be kept on rewrite
      final CCSMRequestAdjustmentFilter filter = createFilter();
      when(request.getContextPath()).thenReturn("/optimize");
      when(request.getRequestURI()).thenReturn("/optimize/external/api/some-endpoint");

      // when
      filter.doFilter(request, response, filterChain);

      // then
      final ArgumentCaptor<ServletRequest> requestCaptor =
          ArgumentCaptor.forClass(ServletRequest.class);
      verify(filterChain).doFilter(requestCaptor.capture(), eq(response));

      final HttpServletRequest wrappedRequest = (HttpServletRequest) requestCaptor.getValue();
      assertThat(wrappedRequest.getRequestURI())
          .describedAs("Rewritten URI must retain the context path")
          .isEqualTo("/optimize/api/external/some-endpoint");
    }
  }
}
