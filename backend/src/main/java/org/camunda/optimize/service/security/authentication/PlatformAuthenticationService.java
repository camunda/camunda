/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security.authentication;

import io.camunda.identity.sdk.authentication.dto.AuthCodeDto;
// import io.camunda.identity.sdk.authentication.dto.LogoutRequestDto;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.AuthenticationResultDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.query.security.CredentialsRequestDto;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.security.ApplicationAuthorizationService;
import org.camunda.optimize.service.security.AuthCookieService;
import org.camunda.optimize.service.security.EngineAuthenticationProvider;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.util.configuration.condition.CamundaPlatformCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotSupportedException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Slf4j
@Conditional(CamundaPlatformCondition.class)
public class PlatformAuthenticationService extends AbstractAuthenticationService {

  private final EngineAuthenticationProvider engineAuthenticationProvider;
  private final EngineContextFactory engineContextFactory;
  private final ApplicationAuthorizationService engineAuthorizationService;

  public PlatformAuthenticationService(final SessionService sessionService,
                                       final AuthCookieService authCookieService,
                                       final EngineAuthenticationProvider engineAuthenticationProvider,
                                       final EngineContextFactory engineContextFactory,
                                       final ApplicationAuthorizationService applicationAuthorizationService) {
    super(sessionService, authCookieService);
    this.engineAuthenticationProvider = engineAuthenticationProvider;
    this.engineContextFactory = engineContextFactory;
    this.engineAuthorizationService = applicationAuthorizationService;
  }

  @Override
  public Response authenticateUser(@Context ContainerRequestContext requestContext,
                                   CredentialsRequestDto credentials) {
    final String securityToken = authenticateUser(credentials);
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

  @Override
  public Response loginCallback(final ContainerRequestContext requestContext, final AuthCodeDto authCode) {
    throw new NotSupportedException("Login callback called but not supported");
  }

  /*
  @Override
  public Response logoutCallback(final ContainerRequestContext requestContext,
                                 final LogoutRequestDto logoutRequestDto) {
    throw new NotSupportedException("Logout callback called but not supported");
  }

   */

  @Override
  public Response logout(@Context ContainerRequestContext requestContext) {
    sessionService.invalidateSession(requestContext);
    return Response.status(Response.Status.OK)
      .entity("OK")
      .cookie(authCookieService.createDeleteOptimizeAuthCookie(requestContext.getUriInfo().getRequestUri().getScheme()))
      .build();
  }

  /**
   * Authenticates user and checks for optimize authorization.
   *
   * @throws ForbiddenException     if no engine that authenticates the user also authorizes the user
   * @throws NotAuthorizedException if no engine authenticates the user
   */
  private String authenticateUser(final CredentialsRequestDto credentials) throws
                                                                           ForbiddenException,
                                                                           NotAuthorizedException {
    final List<AuthenticationResultDto> authenticationResults = new ArrayList<>();
    final Optional<String> authenticatedUserId = engineContextFactory.getConfiguredEngines().stream()
      .map(engineContext -> engineAuthenticationProvider.performAuthenticationCheck(credentials, engineContext))
      .peek(authenticationResults::add)
      .filter(AuthenticationResultDto::isAuthenticated)
      .map(authenticationResultDto -> engineContextFactory
        .getConfiguredEngineByAlias(authenticationResultDto.getEngineAlias())
        .flatMap(engineContext -> engineContext.getUserById(authenticationResultDto.getAuthenticatedUser()))
      )
      .filter(Optional::isPresent)
      .map(Optional::get)
      .map(IdentityDto::getId)
      .findFirst();

    if (authenticatedUserId.isPresent()) {
      final boolean isAuthorizedToOptimizeByAnyEngine = engineAuthorizationService.isUserAuthorizedToAccessOptimize(
        authenticatedUserId.get()
      );
      if (isAuthorizedToOptimizeByAnyEngine) {
        return sessionService.createAuthToken(authenticatedUserId.get());
      } else {
        // could not find an engine that grants optimize permission
        String errorMessage = "The user [" + credentials.getUsername() + "] is not authorized to "
          + "access Optimize from any of the connected engines.\n "
          + "Please check the Camunda Admin configuration to change user "
          + "authorizations in at least one process engine.";
        log.warn(errorMessage);
        throw new ForbiddenException(errorMessage);
      }
    } else {
      // could not find an engine that authenticates user
      String authenticationErrorMessage = createNotAuthenticatedErrorMessage(authenticationResults);
      log.warn(authenticationErrorMessage);
      throw new NotAuthorizedException(authenticationErrorMessage, "ignored");
    }
  }

  private String createNotAuthenticatedErrorMessage(List<AuthenticationResultDto> authenticationResults) {
    String authenticationErrorMessage = "No engine is configured. Can't authenticate a user.";
    if (!authenticationResults.isEmpty()) {
      authenticationErrorMessage =
        "Could not log you in. \n" +
          "Error messages from engines: \n";
      authenticationErrorMessage +=
        authenticationResults.stream()
          .map(r -> r.getEngineAlias() + ": " + r.getErrorMessage() + " \n")
          .collect(Collectors.joining());
    }
    return authenticationErrorMessage;
  }

}
