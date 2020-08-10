/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.AuthenticationResultDto;
import org.camunda.optimize.dto.optimize.query.security.CredentialsDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.camunda.optimize.service.util.configuration.EngineConstants.USER_VALIDATION_ENDPOINT;

@RequiredArgsConstructor
@Component
@Slf4j
public class EngineAuthenticationProvider {

  public static final String INVALID_CREDENTIALS_ERROR_MESSAGE =
    "The provided credentials are invalid. Please check your username and password.";
  private static final String CONNECTION_WAS_REFUSED_ERROR =
    "Connection to engine was refused! Please check if the engine is still running.";

  private final ConfigurationService configurationService;

  public AuthenticationResultDto performAuthenticationCheck(CredentialsDto credentialsDto,
                                                            EngineContext engineContext) {
    try {
      final Response response = engineContext.getEngineClient()
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine(engineContext.getEngineAlias()))
        .path(USER_VALIDATION_ENDPOINT)
        .request(MediaType.APPLICATION_JSON)
        .post(Entity.json(credentialsDto));

      if (responseIsSuccessful(response)) {
        AuthenticationResultDto authResult = response.readEntity(AuthenticationResultDto.class);
        authResult.setEngineAlias(engineContext.getEngineAlias());
        if (!authResult.isAuthenticated()) {
          authResult.setErrorMessage(INVALID_CREDENTIALS_ERROR_MESSAGE);
        }
        return authResult;
      } else {
        log.error(
          "Could not validate user [{}] against the engine [{}]. " +
            "Maybe you did not provide a user or password or the user is locked",
          credentialsDto.getUsername(),
          engineContext.getEngineAlias()
        );
        // read Exception from the engine response
        // and rethrow it to forward the error message to the client
        // e.g when the user is locked, the error message will contain corresponding information
        Exception runtimeException = response.readEntity(RuntimeException.class);
        return getAuthenticationResultFromError(credentialsDto, engineContext, runtimeException);
      }
    } catch (ProcessingException e) {
      String errorMessage =
        String.format(
          "Could not authenticated against engine [%s]. " + CONNECTION_WAS_REFUSED_ERROR,
          engineContext.getEngineAlias()
        );
      OptimizeRuntimeException optimizeEx = new OptimizeRuntimeException(errorMessage, e);
      return getAuthenticationResultFromError(credentialsDto, engineContext, optimizeEx);
    }
  }

  private AuthenticationResultDto getAuthenticationResultFromError(final CredentialsDto credentialsDto,
                                                                   final EngineContext engineContext,
                                                                   final Exception exception) {

    return AuthenticationResultDto.builder()
      .isAuthenticated(false)
      .authenticatedUser(credentialsDto.getUsername())
      .engineAlias(engineContext.getEngineAlias())
      .errorMessage(exception.getMessage())
      .build();
  }

  private boolean responseIsSuccessful(Response response) {
    return response.getStatus() < 300;
  }
}
