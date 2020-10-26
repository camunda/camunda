/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.security.CredentialsRequestDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.security.AuthCookieService;
import org.camunda.optimize.service.security.AuthenticationService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

/**
 * Basic implementation of authentication tokens creation based on user credentials.
 * Please note that authentication token validation/refresh is performed in request filters.
 */
@AllArgsConstructor
@Path("/authentication")
@Component
public class AuthenticationRestService {

  private final AuthenticationService authenticationService;
  private final AuthCookieService authCookieService;
  private final SessionService sessionService;

  /**
   * Authenticate an user given his credentials.
   *
   * @param credentials the credentials of the user.
   * @return Response code 200 (OK) if it was possible to authenticate the user, otherwise status code 401
   * (Unauthorized).
   */
  @POST
  @Produces("application/json")
  @Consumes("application/json")
  public Response authenticateUser(@Context ContainerRequestContext requestContext,
                                   CredentialsRequestDto credentials) {
    String securityToken = authenticationService.authenticateUser(credentials);
    // Return the token on the response
    return Response.ok(securityToken)
      .header(
        HttpHeaders.SET_COOKIE,
        authCookieService.createNewOptimizeAuthCookie(
          securityToken,
          requestContext.getUriInfo().getRequestUri().getScheme()
        )
      )
      .build();
  }

  /**
   * An endpoint to test if you are authenticated.
   *
   * @return Status code 200 (OK) if you are authenticated.
   */
  @Secured
  @GET
  @Path("test")
  public Response testAuthentication() {
    return Response.status(Response.Status.OK).entity("OK").build();
  }

  /**
   * Logout yourself from Optimize.
   *
   * @return Status code 200 (OK) if the logout was successful.
   */
  @Secured
  @GET
  @Path("logout")
  public Response logout(@Context ContainerRequestContext requestContext) {
    sessionService.invalidateSession(requestContext);
    return Response.status(Response.Status.OK)
      .entity("OK")
      .cookie(authCookieService.createDeleteOptimizeAuthCookie(requestContext.getUriInfo().getRequestUri().getScheme()))
      .build();
  }
}
