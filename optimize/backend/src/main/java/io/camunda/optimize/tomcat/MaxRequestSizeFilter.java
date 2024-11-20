/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.tomcat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.slf4j.Logger;

public class MaxRequestSizeFilter implements Filter {

  public static final String MESSAGE_NO_CONTENT_LENGTH = "No Content-Length header in request.";
  private static final String APPLICATION_JSON_UTF_8 = "application/json; charset=UTF-8";
  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(MaxRequestSizeFilter.class);

  private final Callable<ObjectMapper> objectMapperProvider;
  private final Callable<Long> maxSizeProvider;

  public MaxRequestSizeFilter(
      final Callable<ObjectMapper> objectMapperProvider, final Callable<Long> maxSizeProvider) {
    this.objectMapperProvider = objectMapperProvider;
    this.maxSizeProvider = maxSizeProvider;
  }

  @Override
  public void init(final FilterConfig filterConfig) throws ServletException {
    // noop
  }

  @Override
  public void doFilter(
      final ServletRequest request, final ServletResponse response, final FilterChain chain)
      throws IOException, ServletException {
    final HttpServletRequest httpRequest = (HttpServletRequest) request;
    final HttpServletResponse httpResponse = (HttpServletResponse) response;

    final Optional<Long> optionalContentLength = getContentLength(httpRequest);
    if (optionalContentLength.isPresent()) {
      final long contentLength = optionalContentLength.get();
      final double maxContentLength = getMaxContentLength();
      if (maxContentLength < contentLength) {
        final String errorMessage =
            String.format(
                "Request too large [%s], maximum content length is [%s].",
                contentLength, maxContentLength);
        logWarnWithUrlPath(httpRequest, errorMessage);
        writeErrorResponse(
            httpResponse, errorMessage, Response.Status.REQUEST_ENTITY_TOO_LARGE.getStatusCode());
      } else {
        chain.doFilter(httpRequest, httpResponse);
      }
    } else {
      logWarnWithUrlPath(httpRequest, MESSAGE_NO_CONTENT_LENGTH);
      writeErrorResponse(
          httpResponse, MESSAGE_NO_CONTENT_LENGTH, Response.Status.LENGTH_REQUIRED.getStatusCode());
    }
  }

  @Override
  public void destroy() {
    // noop
  }

  public long getMaxContentLength() {
    try {
      return maxSizeProvider.call();
    } catch (final Exception e) {
      throw new OptimizeRuntimeException(e);
    }
  }

  public void writeErrorResponse(
      final HttpServletResponse httpResponse, final String errorMessage, final int statusCode) {
    httpResponse.reset();
    httpResponse.setContentType(APPLICATION_JSON_UTF_8);
    try {
      httpResponse
          .getWriter()
          .write(
              objectMapperProvider.call().writeValueAsString(new ErrorResponseDto(errorMessage)));
    } catch (final Exception e) {
      throw new OptimizeRuntimeException(e);
    }
    httpResponse.setStatus(statusCode);
  }

  private void logWarnWithUrlPath(final HttpServletRequest httpRequest, final String errorMessage) {
    LOG.warn(errorMessage + " Path [{}].", httpRequest.getRequestURL());
  }

  private Optional<Long> getContentLength(final HttpServletRequest httpRequest) {
    final Integer contentLength = httpRequest.getContentLength();
    // -1 is default value if no header is present, see java doc of
    // HttpServletRequest#getContentLength
    return Optional.of(contentLength).filter(value -> value != -1).map(Integer::longValue);
  }
}
