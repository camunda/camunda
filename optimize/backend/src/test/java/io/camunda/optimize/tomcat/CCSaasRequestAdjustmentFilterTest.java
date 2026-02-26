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
 * Tests for {@link CCSaasRequestAdjustmentFilter}.
 *
 * <p>These tests verify that the filter uses {@link jakarta.servlet.http.HttpServletRequestWrapper}
 * to rewrite request paths (rather than {@link jakarta.servlet.RequestDispatcher#forward}), which
 * is required for compatibility with Spring Security 7's {@code PathPatternRequestMatcher} that
 * evaluates the original request URI.
 */
@ExtendWith(MockitoExtension.class)
public class CCSaasRequestAdjustmentFilterTest {

  private static final String CLUSTER_ID = "abc-123";

  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private FilterChain filterChain;
  @Mock private FilterConfig filterConfig;
  @Mock private ServletContext servletContext;

  @BeforeEach
  void setUp() throws Exception {
    when(filterConfig.getServletContext()).thenReturn(servletContext);
    // ExternalResourcesUtil.stripContextPath() calls getContextPath() which must not be null.
    // Lenient because the HomePageRedirect test short-circuits before reaching this call.
    lenient().when(request.getContextPath()).thenReturn("");
  }

  private CCSaasRequestAdjustmentFilter createFilter(final String clusterId) throws Exception {
    final CCSaasRequestAdjustmentFilter filter = new CCSaasRequestAdjustmentFilter(clusterId);
    filter.init(filterConfig);
    return filter;
  }

  /**
   * Regression tests for Spring Security 7 compatibility. The filter must wrap the request using
   * HttpServletRequestWrapper (not RequestDispatcher.forward) so that PathPatternRequestMatcher
   * sees the rewritten path.
   */
  @Nested
  class PathRewritingUsesRequestWrapper {

    /**
     * Core regression test: when a request arrives at /{clusterId}/some/path, the filter must pass
     * a wrapped request to the filter chain where getRequestURI() returns /some/path. This ensures
     * Spring Security's PathPatternRequestMatcher matches the stripped path.
     */
    @Test
    void wrapsRequestWithStrippedPath() throws Exception {
      // given
      final CCSaasRequestAdjustmentFilter filter = createFilter(CLUSTER_ID);
      when(request.getRequestURI()).thenReturn("/" + CLUSTER_ID + "/api/some-endpoint");

      // when
      filter.doFilter(request, response, filterChain);

      // then — the filter chain must receive a wrapped request, NOT a forwarded dispatch
      final ArgumentCaptor<ServletRequest> requestCaptor =
          ArgumentCaptor.forClass(ServletRequest.class);
      verify(filterChain).doFilter(requestCaptor.capture(), eq(response));

      final HttpServletRequest wrappedRequest = (HttpServletRequest) requestCaptor.getValue();
      assertThat(wrappedRequest).isNotSameAs(request);
      assertThat(wrappedRequest.getRequestURI()).isEqualTo("/api/some-endpoint");
    }
  }

  @Nested
  class ClusterIdStripping {

    @Test
    void rewritesToRoot() throws Exception {
      // given — request to /<clusterId>/ (with trailing slash, so not the redirect case)
      final CCSaasRequestAdjustmentFilter filter = createFilter(CLUSTER_ID);
      when(request.getRequestURI()).thenReturn("/" + CLUSTER_ID + "/");

      // when
      filter.doFilter(request, response, filterChain);

      // then
      final ArgumentCaptor<ServletRequest> requestCaptor =
          ArgumentCaptor.forClass(ServletRequest.class);
      verify(filterChain).doFilter(requestCaptor.capture(), eq(response));

      final HttpServletRequest wrappedRequest = (HttpServletRequest) requestCaptor.getValue();
      assertThat(wrappedRequest.getRequestURI()).isEqualTo("/");
    }
  }

  @Nested
  class ExternalApiRewrite {

    @Test
    void rewritesExternalApiToApiExternal() throws Exception {
      // given
      final CCSaasRequestAdjustmentFilter filter = createFilter("");
      when(request.getRequestURI()).thenReturn("/external/api/some-endpoint");

      // when
      filter.doFilter(request, response, filterChain);

      // then
      final ArgumentCaptor<ServletRequest> requestCaptor =
          ArgumentCaptor.forClass(ServletRequest.class);
      verify(filterChain).doFilter(requestCaptor.capture(), eq(response));

      final HttpServletRequest wrappedRequest = (HttpServletRequest) requestCaptor.getValue();
      assertThat(wrappedRequest.getRequestURI()).isEqualTo("/api/external/some-endpoint");
    }

    @Test
    void bothClusterIdAndExternalApiRewritten() throws Exception {
      // given — both transformations should apply in sequence
      final CCSaasRequestAdjustmentFilter filter = createFilter(CLUSTER_ID);
      when(request.getRequestURI()).thenReturn("/" + CLUSTER_ID + "/external/api/some-endpoint");

      // when
      filter.doFilter(request, response, filterChain);

      // then
      final ArgumentCaptor<ServletRequest> requestCaptor =
          ArgumentCaptor.forClass(ServletRequest.class);
      verify(filterChain).doFilter(requestCaptor.capture(), eq(response));

      final HttpServletRequest wrappedRequest = (HttpServletRequest) requestCaptor.getValue();
      assertThat(wrappedRequest.getRequestURI()).isEqualTo("/api/external/some-endpoint");
    }
  }

  @Nested
  class NoModification {

    @Test
    void passesOriginalRequestWhenNoClusterId() throws Exception {
      // given
      final CCSaasRequestAdjustmentFilter filter = createFilter("");
      when(request.getRequestURI()).thenReturn("/api/some-endpoint");

      // when
      filter.doFilter(request, response, filterChain);

      // then — original request is passed, not a wrapper
      verify(filterChain).doFilter(request, response);
    }

    @Test
    void passesOriginalRequestWithoutClusterIdPrefix() throws Exception {
      // given
      final CCSaasRequestAdjustmentFilter filter = createFilter(CLUSTER_ID);
      when(request.getRequestURI()).thenReturn("/api/some-endpoint");

      // when
      filter.doFilter(request, response, filterChain);

      // then — original request is passed, not a wrapper
      verify(filterChain).doFilter(request, response);
    }
  }

  @Nested
  class HomePageRedirect {

    @Test
    void redirectsWithTrailingSlash() throws Exception {
      // given
      final CCSaasRequestAdjustmentFilter filter = createFilter(CLUSTER_ID);
      when(request.getRequestURI()).thenReturn("/" + CLUSTER_ID);

      // when
      filter.doFilter(request, response, filterChain);

      // then
      verify(response).sendRedirect("/" + CLUSTER_ID + "/");
    }
  }
}
