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

  public String authenticateUser(CredentialsDto credentials) {
    for (EngineContext engineContext : engineContextFactory.getConfiguredEngines()) {

      boolean isValidUser = engineAuthenticationProvider.authenticate(credentials, engineContext);

      if (isValidUser) {
        boolean isAuthorized = applicationAuthorizationService.isAuthorized(credentials, engineContext);
        if (isAuthorized) {
          return createUserSession(credentials, engineContext);
        } else {
          throwForbiddenException(credentials);
        }

      }
    }
    // could not find an authorized authorized user, so throw an exception
    logger.error("Error during user authentication");
    throw new NotAuthorizedException("Can't authorize user [" + credentials.getUsername() + "]");
  }

  private void throwForbiddenException(CredentialsDto credentialsDto) {
    String errorMessage = "The user [" + credentialsDto.getUsername() + "] is not authorized to " +
      "access Optimize! Please check the Camunda Admin configuration to change user authorizations!";
    logger.error(errorMessage);
    throw new ForbiddenException(errorMessage);
  }

  private String createUserSession(CredentialsDto credentials, EngineContext engineContext) {
    // Issue a token for the user
    return sessionService.createSessionAndReturnSecurityToken(
      credentials.getUsername(),
      engineContext
    );
  }
}
