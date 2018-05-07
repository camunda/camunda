package org.camunda.optimize.service.security;

import org.camunda.optimize.dto.optimize.query.security.CredentialsDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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

  public String authenticateUser(CredentialsDto credentials) {
    for (EngineContext engineContext : engineContextFactory.getConfiguredEngines()) {

      boolean authorizedInEngine = engineAuthenticationProvider.authenticate(credentials, engineContext);

      if (authorizedInEngine) {
        // Issue a token for the user
        String token =
          sessionService.createSessionAndReturnSecurityToken(
            credentials.getUsername(),
            engineContext
          );
        return token;
      }
    }
    // could not find an authorized authorized user, so throw an exception
    logger.error("Error during user authentication");
    throw new NotAuthorizedException("Can't authorize user [" + credentials.getUsername() + "]");
  }
}
