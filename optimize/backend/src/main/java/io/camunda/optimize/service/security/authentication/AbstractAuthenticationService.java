/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security.authentication;

import io.camunda.identity.sdk.authentication.dto.AuthCodeDto;
import io.camunda.optimize.dto.optimize.query.security.CredentialsRequestDto;
import io.camunda.optimize.service.security.AuthCookieService;
import io.camunda.optimize.service.security.SessionService;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

public abstract class AbstractAuthenticationService {

  protected final SessionService sessionService;
  protected final AuthCookieService authCookieService;

  public AbstractAuthenticationService(
      final SessionService sessionService, final AuthCookieService authCookieService) {
    this.sessionService = sessionService;
    this.authCookieService = authCookieService;
  }

  public abstract Response authenticateUser(
      @Context ContainerRequestContext requestContext, CredentialsRequestDto credentials);

  public abstract Response loginCallback(
      @Context ContainerRequestContext requestContext, AuthCodeDto authCode);

  public abstract Response logout(@Context ContainerRequestContext requestContext);

  public Response testAuthentication() {
    return Response.status(Response.Status.OK).entity("OK").build();
  }
}
