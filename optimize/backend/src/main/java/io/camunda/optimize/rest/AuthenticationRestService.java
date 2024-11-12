/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import io.camunda.identity.sdk.authentication.dto.AuthCodeDto;
import io.camunda.optimize.dto.optimize.query.security.CredentialsRequestDto;
import io.camunda.optimize.service.security.authentication.AbstractAuthenticationService;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.springframework.stereotype.Component;

@Path(AuthenticationRestService.AUTHENTICATION_PATH)
@Component
public class AuthenticationRestService {

  public static final String AUTHENTICATION_PATH = "/authentication";
  public static final String LOGOUT = "/logout";
  public static final String TEST = "/test";
  public static final String CALLBACK = "/callback";

  private final AbstractAuthenticationService authenticationService;

  public AuthenticationRestService(final AbstractAuthenticationService authenticationService) {
    this.authenticationService = authenticationService;
  }

  @POST
  @Produces("application/json")
  @Consumes("application/json")
  public Response authenticateUser(
      @Context final ContainerRequestContext requestContext,
      final CredentialsRequestDto credentials) {
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
      @Context final ContainerRequestContext requestContext,
      final @QueryParam("code") String code,
      final @QueryParam("state") String state,
      final @QueryParam("error") String error) {
    return authenticationService.loginCallback(requestContext, new AuthCodeDto(code, state, error));
  }

  @GET
  @Path(LOGOUT)
  public Response logoutUser(@Context final ContainerRequestContext requestContext) {
    return authenticationService.logout(requestContext);
  }
}
