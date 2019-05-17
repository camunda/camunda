/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.providers;

import lombok.AllArgsConstructor;
import org.camunda.optimize.service.security.AuthCookieService;
import org.camunda.optimize.service.security.SessionService;
import org.glassfish.jersey.server.ContainerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.net.URI;
import java.net.URISyntaxException;

@AllArgsConstructor
@Secured
@Provider
@Priority(Priorities.AUTHENTICATION)
@Component
public class RequestAuthenticationFilter implements ContainerRequestFilter {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private static final String CSV_SUFFIX = ".csv";
  private static final String LOG_IN = "/login";
  private static final String STATUS = "status";

  private final SessionService sessionService;
  private final AuthCookieService authCookieService;

  @Override
  public void filter(ContainerRequestContext requestContext) {
    String path = retrievePath(requestContext);
    if (path != null && path.toLowerCase().startsWith(STATUS)) {
      // we need that for the status web socket requests to work
      return;
    }
    try {
      if (!sessionService.hasValidSession(requestContext)) {
        handleInvalidToken(requestContext);
      }
    } catch (Exception e) {
      logger.debug("Error during issuing of security token!", e);
      handleInvalidToken(requestContext);
    }
  }

  private String retrievePath(ContainerRequestContext requestContext) {
    return ((ContainerRequest) requestContext).getPath(false);
  }

  private void handleInvalidToken(ContainerRequestContext requestContext) {
    String path = retrievePath(requestContext);

    if (isCSVRequest(path)) {
      redirectToLoginPage(requestContext);
    } else {
      requestContext.abortWith(
        Response.status(Response.Status.UNAUTHORIZED)
          .cookie(authCookieService.createDeleteOptimizeAuthCookie())
          .build()
      );
    }
  }

  private void redirectToLoginPage(ContainerRequestContext requestContext) {
    URI loginUri = null;
    try {
      loginUri = new URI(LOG_IN);
    } catch (URISyntaxException e) {
      logger.debug("can't build URI to login", e);
    }
    requestContext.abortWith(
      Response.temporaryRedirect(loginUri)
        .cookie(authCookieService.createDeleteOptimizeAuthCookie())
        .build()
    );
  }

  private boolean isCSVRequest(String path) {
    return path.endsWith(CSV_SUFFIX);
  }

}
