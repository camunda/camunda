/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.springframework.http.HttpStatus.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.RedirectStrategy;

/**
 * {@link RedirectStrategy} implementation that adapts redirect handling for a web application
 * frontend.
 *
 * <p>Instead of issuing an HTTP 3xx redirect, this strategy:
 *
 * <ul>
 *   <li>Sends a {@code 204 No Content} response when the target URL is {@code null} or equal to
 *       {@value #DEFAULT_REDIRECT_URL}, without a response body.
 *   <li>Sends a {@code 200 OK} response with a JSON body containing the target URL for any other
 *       redirect destination.
 * </ul>
 *
 * <p>This allows a JavaScript client to decide how to handle navigation based on the returned
 * response.
 */
public class WebappRedirectStrategy implements RedirectStrategy {

  private static final String DEFAULT_REDIRECT_URL = "/";
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public void sendRedirect(
      final HttpServletRequest request, final HttpServletResponse response, final String url)
      throws IOException {

    if (url == null || DEFAULT_REDIRECT_URL.equals(url)) {
      response.setStatus(NO_CONTENT.value());
      return;
    }

    response.setHeader("Content-Type", "application/json");
    response.setStatus(OK.value());

    // must be last, because it will commit the response, and we cannot modify it afterwards
    objectMapper.writeValue(response.getWriter(), new RedirectResponse(url));
    response.getWriter().flush();
  }

  private record RedirectResponse(String url) {}
}
