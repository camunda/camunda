/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import io.camunda.identity.sdk.authentication.dto.AuthCodeDto;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.security.CredentialsRequestDto;
import org.camunda.optimize.service.security.authentication.AbstractAuthenticationService;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Path(AuthenticationRestService.AUTHENTICATION_PATH)
@Component
public class AuthenticationRestService {

  public static final String AUTHENTICATION_PATH = "/authentication";
  public static final String LOGOUT = "/logout";
  public static final String TEST = "/test";
  public static final String CALLBACK = "/callback";

  private final AbstractAuthenticationService authenticationService;

  @POST
  @Produces("application/json")
  @Consumes("application/json")
  public Response authenticateUser(
      @Context ContainerRequestContext requestContext, CredentialsRequestDto credentials) {
    return authenticationService.authenticateUser(requestContext, credentials);
  }

  @GET
  @Path(TEST)
  public Response testAuthentication() {
    return authenticationService.testAuthentication();
  }

  @GET
  @Path(CALLBACK)
  public Response loginCallback(
      @Context ContainerRequestContext requestContext,
      final @QueryParam("code") String code,
      final @QueryParam("state") String state,
      final @QueryParam("error") String error) {
    return authenticationService.loginCallback(requestContext, new AuthCodeDto(code, state, error));
  }

  @GET
  @Path(LOGOUT)
  public Response logoutUser(@Context ContainerRequestContext requestContext) {
    return authenticationService.logout(requestContext);
  }
}
