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

  private Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private EngineAuthenticationProvider engineAuthenticationProvider;

  @Autowired
  private EngineContextFactory engineContextFactory;

  @Autowired
  private SessionService sessionService;

  @Autowired
  private ApplicationAuthorizationService applicationAuthorizationService;

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

      AuthenticationResultDto authResult =
        engineAuthenticationProvider.performAuthenticationCheck(credentials, engineContext);
      authenticationResults.add(authResult);

      if (authResult.isAuthenticated()) {
        boolean isAuthorized =
          applicationAuthorizationService.isAuthorizedToAccessOptimize(credentials.getUsername(), engineContext);
        if (isAuthorized) {
          return createUserSession(credentials, engineContext);
        }

      }
    }

    boolean userWasAuthenticated =
      authenticationResults.stream().anyMatch(AuthenticationResultDto::isAuthenticated);
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

  private String createUserSession(CredentialsDto credentials, EngineContext engineContext) {
    // Issue a token for the user
    return sessionService.createSessionAndReturnSecurityToken(
      credentials.getUsername(),
      engineContext
    );
  }
}
