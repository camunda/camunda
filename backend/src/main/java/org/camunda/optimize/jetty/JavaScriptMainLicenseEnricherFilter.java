/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.jetty;

import com.google.common.io.CharStreams;
import lombok.SneakyThrows;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public class JavaScriptMainLicenseEnricherFilter implements Filter {
  public static final String LICENSE_PATH = "OPTIMIZE-LICENSE.txt";
  private static final Pattern MAIN_JS_PATTERN = Pattern.compile(".*/main\\..*\\.chunk\\.js");

  // used as means to cache the main js content enriched with the license as its content is static anyway
  private String licensedContent;

  @Override
  public void init(final FilterConfig filterConfig) throws ServletException {
    // nothing to do here
  }

  @Override
  public void doFilter(final ServletRequest servletRequest,
                       final ServletResponse servletResponse,
                       final FilterChain filterChain) throws IOException, ServletException {
    final HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
    final String requestPath = httpServletRequest.getServletPath();
    if (MAIN_JS_PATTERN.matcher(requestPath).matches()) {
      // this wrapper allows to capture any response content written before writing the actual response
      final ContentCachingResponseWrapper cachingResponseWrapper =
        new ContentCachingResponseWrapper((HttpServletResponse) servletResponse);
      try {
        // let the request processing continue so the response content is available after
        filterChain.doFilter(servletRequest, cachingResponseWrapper);
        // then modify it
        enrichWithLicense(cachingResponseWrapper);
      } finally {
        // in any case forward the content to the response
        cachingResponseWrapper.copyBodyToResponse();
      }
    } else {
      filterChain.doFilter(servletRequest, servletResponse);
    }
  }

  @Override
  public void destroy() {
    // nothing to do here
  }

  private void enrichWithLicense(final ContentCachingResponseWrapper cachingResponseWrapper) throws IOException {
    if (licensedContent == null) {
      // no synchronization needed as in worst case multiple threads may update the field
      // but following calls will make use of the preprocessed value
      final String originalContent = new String(cachingResponseWrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
      this.licensedContent = readLicense() + originalContent;
    }
    cachingResponseWrapper.resetBuffer();
    cachingResponseWrapper.getWriter().write(this.licensedContent);
  }

  @SneakyThrows
  private String readLicense() {
    final InputStream inputStream = this.getClass()
      .getClassLoader()
      .getResourceAsStream(JavaScriptMainLicenseEnricherFilter.LICENSE_PATH);

    if (inputStream == null) {
      return "";
    }
    return CharStreams.toString(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
  }

}
