/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.handler;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

public class LoggingAuthenticationFailureHandler implements AuthenticationFailureHandler {

  private static final Logger LOG =
      LoggerFactory.getLogger(LoggingAuthenticationFailureHandler.class);

  private final AuthenticationFailureHandler delegate;

  public LoggingAuthenticationFailureHandler(final AuthenticationFailureHandler delegate) {
    this.delegate = delegate;
  }

  @Override
  public void onAuthenticationFailure(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AuthenticationException exception)
      throws IOException, ServletException {
    // Only AuthenticationServiceException will not be handled by default failure handlers,
    // instead it will be rethrown and bubble up to tomcat, causing an ERROR log.
    if (!AuthenticationServiceException.class.isAssignableFrom(exception.getClass())) {
      delegate.onAuthenticationFailure(request, response, exception);
      return;
    }

    LOG.warn("A technical authentication problem occurred", exception);

    // Setting this attributes will cause Tomcat to initiate
    // rendering an error response, ultimately calling our GlobalErrorController.
    request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, exception);
    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
  }
}
