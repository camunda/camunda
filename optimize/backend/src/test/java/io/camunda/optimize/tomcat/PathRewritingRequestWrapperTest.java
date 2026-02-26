/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.tomcat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PathRewritingRequestWrapper}.
 *
 * <p>These tests verify the wrapper's behavior in both REQUEST and FORWARD dispatch scenarios,
 * ensuring Spring Security 7 compatibility without causing infinite forward loops.
 */
class PathRewritingRequestWrapperTest {

  @Nested
  class OnRequestDispatch {

    @Test
    void returnsRewrittenURI() {
      // given — no forward attribute set (normal REQUEST dispatch)
      final HttpServletRequest original = mock(HttpServletRequest.class);
<<<<<<< HEAD
      final HttpServletRequest wrapper =
=======
      final PathRewritingRequestWrapper wrapper =
>>>>>>> 9f2dcfda (fix: replace RequestDispatcher.forward with HttpServletRequestWrapper)
          new PathRewritingRequestWrapper(original, "/api/some-endpoint");

      // when / then
      assertThat(wrapper.getRequestURI()).isEqualTo("/api/some-endpoint");
    }

    @Test
    void requestURLReflectsRewrittenURI() {
      // given
      final HttpServletRequest original = mock(HttpServletRequest.class);
      when(original.getScheme()).thenReturn("https");
      when(original.getServerName()).thenReturn("example.com");
      when(original.getServerPort()).thenReturn(443);

<<<<<<< HEAD
      final HttpServletRequest wrapper =
=======
      final PathRewritingRequestWrapper wrapper =
>>>>>>> 9f2dcfda (fix: replace RequestDispatcher.forward with HttpServletRequestWrapper)
          new PathRewritingRequestWrapper(original, "/api/some-endpoint");

      // when / then
      assertThat(wrapper.getRequestURL().toString())
          .isEqualTo("https://example.com/api/some-endpoint");
    }

    @Test
    void servletPathDelegatesToOriginalRequest() {
      // given
      final HttpServletRequest original = mock(HttpServletRequest.class);
      when(original.getServletPath()).thenReturn("/original/servlet/path");

<<<<<<< HEAD
      final HttpServletRequest wrapper = new PathRewritingRequestWrapper(original, "/rewritten");
=======
      final PathRewritingRequestWrapper wrapper =
          new PathRewritingRequestWrapper(original, "/rewritten");
>>>>>>> 9f2dcfda (fix: replace RequestDispatcher.forward with HttpServletRequestWrapper)

      // when / then — getServletPath must NOT be overridden
      assertThat(wrapper.getServletPath()).isEqualTo("/original/servlet/path");
    }
  }

  /**
   * Regression tests for the forward dispatch loop issue. When Spring Boot's
   * WelcomePageHandlerMapping forwards "/" to "/index.html", Tomcat inserts an
   * ApplicationHttpRequest wrapper below our PathRewritingRequestWrapper. Our wrapper must detect
   * the forward dispatch and delegate getRequestURI() to the underlying request, or the
   * DispatcherServlet will see "/" again and re-trigger the forward infinitely
   * (StackOverflowError).
   */
  @Nested
  class OnForwardDispatch {

    @Test
    void delegatesToUnderlyingRequestWhenForwardAttributePresent() {
      // given — initial REQUEST dispatch
      final HttpServletRequest original = mock(HttpServletRequest.class);
      final PathRewritingRequestWrapper wrapper = new PathRewritingRequestWrapper(original, "/");

      // Verify initial behavior
      assertThat(wrapper.getRequestURI()).isEqualTo("/");

      // Simulate what Tomcat does on a FORWARD dispatch:
      // 1. ApplicationDispatcher inserts ApplicationHttpRequest below our wrapper
      // 2. ApplicationHttpRequest.getRequestURI() returns the forward target
      // 3. The FORWARD_REQUEST_URI attribute is set on the request
      final HttpServletRequest tomcatForwardWrapper = mock(HttpServletRequest.class);
      when(tomcatForwardWrapper.getRequestURI()).thenReturn("/index.html");
      when(tomcatForwardWrapper.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI))
          .thenReturn("/");
      wrapper.setRequest(tomcatForwardWrapper);

      // when / then — must delegate to Tomcat's wrapper, returning /index.html
      assertThat(wrapper.getRequestURI()).isEqualTo("/index.html");
    }
  }

  /**
   * Regression tests for relative-path request dispatcher resolution. When Spring Boot's
   * WelcomePageHandlerMapping forwards "/" to "index.html" (relative path, no leading '/'), {@link
   * jakarta.servlet.ServletRequestWrapper#getRequestDispatcher(String)} delegates to the original
   * (wrapped) request. The servlet container resolves the relative path against the original
   * request's URI (e.g., "/clusterId/"), producing "/clusterId/index.html" instead of
   * "/index.html". The wrapper must convert relative paths to absolute ones based on the rewritten
   * URI.
   */
  @Nested
  class OnRelativePathDispatch {

    @Test
    void convertsRelativePathToAbsolute() {
      // given — rewritten URI is "/", relative path is "index.html"
      final HttpServletRequest original = mock(HttpServletRequest.class);
      final RequestDispatcher expectedDispatcher = mock(RequestDispatcher.class);
      when(original.getRequestDispatcher("/index.html")).thenReturn(expectedDispatcher);

<<<<<<< HEAD
      final HttpServletRequest wrapper = new PathRewritingRequestWrapper(original, "/");
=======
      final PathRewritingRequestWrapper wrapper = new PathRewritingRequestWrapper(original, "/");
>>>>>>> 9f2dcfda (fix: replace RequestDispatcher.forward with HttpServletRequestWrapper)

      // when
      final RequestDispatcher result = wrapper.getRequestDispatcher("index.html");

      // then — resolves "index.html" relative to "/" → "/index.html"
      assertThat(result).isSameAs(expectedDispatcher);
      verify(original).getRequestDispatcher("/index.html");
    }

    @Test
    void convertsRelativePathUnderSubPath() {
      // given — rewritten URI has a nested path
      final HttpServletRequest original = mock(HttpServletRequest.class);
      final RequestDispatcher expectedDispatcher = mock(RequestDispatcher.class);
      when(original.getRequestDispatcher("/api/resource.html")).thenReturn(expectedDispatcher);

<<<<<<< HEAD
      final HttpServletRequest wrapper = new PathRewritingRequestWrapper(original, "/api/endpoint");
=======
      final PathRewritingRequestWrapper wrapper =
          new PathRewritingRequestWrapper(original, "/api/endpoint");
>>>>>>> 9f2dcfda (fix: replace RequestDispatcher.forward with HttpServletRequestWrapper)

      // when
      final RequestDispatcher result = wrapper.getRequestDispatcher("resource.html");

      // then — resolves "resource.html" relative to "/api/endpoint" → "/api/resource.html"
      assertThat(result).isSameAs(expectedDispatcher);
      verify(original).getRequestDispatcher("/api/resource.html");
    }

    @Test
    void passesAbsolutePathUnchanged() {
      // given — path already starts with '/'
      final HttpServletRequest original = mock(HttpServletRequest.class);
      final RequestDispatcher expectedDispatcher = mock(RequestDispatcher.class);
      when(original.getRequestDispatcher("/absolute/path")).thenReturn(expectedDispatcher);

<<<<<<< HEAD
      final HttpServletRequest wrapper = new PathRewritingRequestWrapper(original, "/");
=======
      final PathRewritingRequestWrapper wrapper = new PathRewritingRequestWrapper(original, "/");
>>>>>>> 9f2dcfda (fix: replace RequestDispatcher.forward with HttpServletRequestWrapper)

      // when
      final RequestDispatcher result = wrapper.getRequestDispatcher("/absolute/path");

      // then — absolute path passed through unchanged
      assertThat(result).isSameAs(expectedDispatcher);
      verify(original).getRequestDispatcher("/absolute/path");
    }
  }
}
