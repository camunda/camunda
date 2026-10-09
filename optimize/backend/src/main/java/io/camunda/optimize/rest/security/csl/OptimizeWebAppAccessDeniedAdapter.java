/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.spring.spi.WebAppAccessDeniedHandlerPort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;

/**
 * Answers a denied request the way Optimize always has: an API call gets a 401 so the single page
 * app reloads and lands on the login, a navigation gets a 403, which the error controller renders
 * as the "no authorization to access Optimize" page.
 *
 * <p>Replaces CSL's default handler, which redirects to {@code /optimize/forbidden}. Optimize
 * serves no such page.
 */
public final class OptimizeWebAppAccessDeniedAdapter implements WebAppAccessDeniedHandlerPort {

  @Override
  public void handle(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final String webApp,
      final CamundaAuthentication authentication)
      throws IOException {
    if (OptimizeApiRequests.isApiRequest(request)) {
      response.sendError(HttpStatus.UNAUTHORIZED.value());
    } else {
      response.sendError(HttpStatus.FORBIDDEN.value());
    }
  }
}
