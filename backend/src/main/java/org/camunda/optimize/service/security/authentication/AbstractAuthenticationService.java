/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security.authentication;

import io.camunda.identity.sdk.authentication.dto.AuthCodeDto;
import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.security.CredentialsRequestDto;
import org.camunda.optimize.service.security.AuthCookieService;
import org.camunda.optimize.service.security.SessionService;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

@RequiredArgsConstructor
public abstract class AbstractAuthenticationService {

  protected final SessionService sessionService;
  protected final AuthCookieService authCookieService;

  public abstract Response authenticateUser(@Context ContainerRequestContext requestContext,
                                            CredentialsRequestDto credentials);

  public abstract Response loginCallback(@Context ContainerRequestContext requestContext, AuthCodeDto authCode);

  public abstract Response logout(@Context ContainerRequestContext requestContext);

  public Response testAuthentication() {
    return Response.status(Response.Status.OK).entity("OK").build();
  }

}
