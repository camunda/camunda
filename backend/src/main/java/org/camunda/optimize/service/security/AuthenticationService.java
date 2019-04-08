/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import org.camunda.optimize.dto.engine.AuthenticationResultDto;
import org.camunda.optimize.dto.optimize.query.security.CredentialsDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotAuthorizedException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class AuthenticationService {

  private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);

  private final EngineAuthenticationProvider engineAuthenticationProvider;
  private final EngineContextFactory engineContextFactory;
  private final SessionService sessionService;
  private final ApplicationAuthorizationService applicationAuthorizationService;

  @Autowired
  public AuthenticationService(final EngineAuthenticationProvider engineAuthenticationProvider,
                               final EngineContextFactory engineContextFactory, final SessionService sessionService,
                               final ApplicationAuthorizationService applicationAuthorizationService) {
    this.engineAuthenticationProvider = engineAuthenticationProvider;
    this.engineContextFactory = engineContextFactory;
    this.sessionService = sessionService;
    this.applicationAuthorizationService = applicationAuthorizationService;
  }

  /**
   * Authenticates user and checks for optimize authorization.
   * 
   * @throws ForbiddenException
   *           if no engine that authenticates the user also authorizes the user
   * @throws NotAuthorizedException
   *           if no engine authenticates the user
   */
  public String authenticateUser(CredentialsDto credentials) throws ForbiddenException, NotAuthorizedException {
   
    List<AuthenticationResultDto> authenticationResults = new ArrayList<>();
    for (EngineContext engineContext : engineContextFactory.getConfiguredEngines()) {

      final AuthenticationResultDto authResult = engineAuthenticationProvider.performAuthenticationCheck(
        credentials, engineContext
      );
      authenticationResults.add(authResult);

      if (authResult.isAuthenticated()) {
        final boolean isAuthorized = applicationAuthorizationService.isAuthorizedToAccessOptimize(
          credentials.getUsername(), engineContext
        );
        if (isAuthorized) {
          return createUserSession(credentials);
        }
      }
    }

    boolean userWasAuthenticated = authenticationResults.stream().anyMatch(AuthenticationResultDto::isAuthenticated);
    if (userWasAuthenticated) {
      // could not find an engine that grants optimize permission
      String errorMessage = "The user [" + credentials.getUsername() + "] is not authorized to "
          + "access Optimize. Please check the Camunda Admin configuration to change user " 
          + "authorizations in at least one process engine.";
      logger.error(errorMessage);
      throw new ForbiddenException(errorMessage);
    } else {
      // could not find an engine that authenticates user
      String authenticationErrorMessage = createNotAuthenticatedErrorMessage(authenticationResults);
      logger.error(authenticationErrorMessage);
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

  private String createUserSession(CredentialsDto credentials) {
    return sessionService.createAuthToken(credentials.getUsername());
  }
}
