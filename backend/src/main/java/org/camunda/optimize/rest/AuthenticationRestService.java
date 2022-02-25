/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import io.camunda.identity.sdk.authentication.dto.AuthCodeDto;
// import io.camunda.identity.sdk.authentication.dto.LogoutRequestDto;
import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.security.CredentialsRequestDto;
import org.camunda.optimize.service.security.authentication.AbstractAuthenticationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

/**
 * Basic implementation of authentication tokens creation based on user credentials.
 * Please note that authentication token validation/refresh is performed in request filters.
 */
@AllArgsConstructor
@Path(AuthenticationRestService.AUTHENTICATION_PATH)
@Component
public class AuthenticationRestService {

  public static final String AUTHENTICATION_PATH = "/authentication";
  public static final String LOGOUT = "/logout";
  public static final String TEST = "/test";
  public static final String CALLBACK = "/callback";
  private static final String LOGOUT_CALLBACK = "/logout-callback";

  private final AbstractAuthenticationService authenticationService;

  /**
   * Authenticate a user given their credentials.
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
    return authenticationService.authenticateUser(requestContext, credentials);
  }

  /**
   * An endpoint to test if you are authenticated.
   *
   * @return Status code 200 (OK) if you are authenticated.
   */
  @GET
  @Path(TEST)
  public Response testAuthentication() {
    return authenticationService.testAuthentication();
  }

  @GET
  @Path(CALLBACK)
  public Response loginCallback(@Context ContainerRequestContext requestContext,
                                final @QueryParam("code") String code,
                                final @QueryParam("state") String state,
                                final @QueryParam("error") String error) {
    return authenticationService.loginCallback(requestContext, new AuthCodeDto(code, state, error));
  }

  /*
  @GET
  @Path(LOGOUT_CALLBACK)
  public Response logoutCallback(@Context ContainerRequestContext requestContext,
                                 final @QueryParam("logout_token") String logoutToken,
                                 final @QueryParam("redirect_uri") String redirectUri) {
    return authenticationService.logoutCallback(
      requestContext,
      new LogoutRequestDto(logoutToken, redirectUri)
    );
  }
*/
  /**
   * Logout yourself from Optimize.
   *
   * @return Status code 200 (OK) if the logout was successful.
   */
  @GET
  @Path(LOGOUT)
  public Response logoutUser(@Context ContainerRequestContext requestContext) {
    return authenticationService.logout(requestContext);
  }

}
