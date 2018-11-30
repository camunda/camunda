package org.camunda.optimize.service.security;

import org.camunda.optimize.dto.optimize.query.security.CredentialsDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotAuthorizedException;

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
   
    boolean userAuthenticated = false;

    for (EngineContext engineContext : engineContextFactory.getConfiguredEngines()) {

      boolean isValidUser = engineAuthenticationProvider.authenticate(credentials, engineContext);
      userAuthenticated |= isValidUser;

      if (isValidUser) {
        boolean isAuthorized =
          applicationAuthorizationService.isAuthorizedToAccessOptimize(credentials.getUsername(), engineContext);
        if (isAuthorized) {
          return createUserSession(credentials, engineContext);
        }

      }
    }
    
    if (userAuthenticated) {
      // could not find an engine that grants optimize permission
      String errorMessage = "The user [" + credentials.getUsername() + "] is not authorized to "
          + "access Optimize. Please check the Camunda Admin configuration to change user " 
          + "authorizations in at least one process engine.";
      logger.error(errorMessage);
      throw new ForbiddenException(errorMessage);
    } else {
      // could not find an engine that authenticates user
      logger.error("Error during user authentication");
      throw new NotAuthorizedException("Could not log you in. Please check your username and password.", "ignored");
    }
  }

  private String createUserSession(CredentialsDto credentials, EngineContext engineContext) {
    // Issue a token for the user
    return sessionService.createSessionAndReturnSecurityToken(
      credentials.getUsername(),
      engineContext
    );
  }
}
