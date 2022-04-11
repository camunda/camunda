/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.jetty;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.eclipse.jetty.http.MimeTypes;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Callable;

@Slf4j
@AllArgsConstructor
public class MaxRequestSizeFilter implements Filter {

  public static final String MESSAGE_NO_CONTENT_LENGTH = "No Content-Length header in request.";

  private final Callable<ObjectMapper> objectMapperProvider;
  private final Callable<Long> maxSizeProvider;

  @Override
  public void init(final FilterConfig filterConfig) throws ServletException {
    // noop
  }

  @Override
  public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
    throws IOException, ServletException {
    final HttpServletRequest httpRequest = (HttpServletRequest) request;
    final HttpServletResponse httpResponse = (HttpServletResponse) response;

    final Optional<Long> optionalContentLength = getContentLength(httpRequest);
    if (optionalContentLength.isPresent()) {
      final long contentLength = optionalContentLength.get();
      final double maxContentLength = getMaxContentLength();
      if (maxContentLength < contentLength) {
        final String errorMessage = String.format(
          "Request too large [%s], maximum content length is [%s].", contentLength, maxContentLength
        );
        logWarnWithUrlPath(httpRequest, errorMessage);
        writeErrorResponse(httpResponse, errorMessage, Response.Status.REQUEST_ENTITY_TOO_LARGE.getStatusCode());
      } else {
        chain.doFilter(httpRequest, httpResponse);
      }
    } else {
      logWarnWithUrlPath(httpRequest, MESSAGE_NO_CONTENT_LENGTH);
      writeErrorResponse(httpResponse, MESSAGE_NO_CONTENT_LENGTH, Response.Status.LENGTH_REQUIRED.getStatusCode());
    }
  }

  @SneakyThrows
  public long getMaxContentLength() {
    return maxSizeProvider.call();
  }

  @Override
  public void destroy() {
    // noop
  }

  @SneakyThrows
  public void writeErrorResponse(final HttpServletResponse httpResponse,
                                 final String errorMessage,
                                 final int statusCode) {
    httpResponse.reset();
    httpResponse.setContentType(MimeTypes.Type.APPLICATION_JSON_UTF_8.toString());
    httpResponse.getWriter().write(objectMapperProvider.call().writeValueAsString(new ErrorResponseDto(errorMessage)));
    httpResponse.setStatus(statusCode);
  }

  private void logWarnWithUrlPath(final HttpServletRequest httpRequest, final String errorMessage) {
    log.warn(errorMessage + " Path [{}].", httpRequest.getRequestURL());
  }

  private Optional<Long> getContentLength(final HttpServletRequest httpRequest) {
    final Integer contentLength = httpRequest.getContentLength();
    // -1 is default value if no header is present, see java doc of HttpServletRequest#getContentLength
    return Optional.of(contentLength).filter(value -> value != -1).map(Integer::longValue);
  }
}
