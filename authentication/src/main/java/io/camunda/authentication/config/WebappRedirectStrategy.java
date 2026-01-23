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

public class WebappRedirectStrategy implements RedirectStrategy {

  private final ObjectMapper objectMapper;

  public WebappRedirectStrategy(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void sendRedirect(
      final HttpServletRequest request, final HttpServletResponse response, final String url)
      throws IOException {

    // TODO: fix return codes
    response.setHeader("Content-Type", "application/json");
    response.setStatus(OK.value());

    // must be last, because it will commit the response, and we cannot modify it afterwards
    objectMapper.writeValue(response.getWriter(), new RedirectUrl(url));
    response.getWriter().flush();
  }

  private record RedirectUrl(String url) {}
}
