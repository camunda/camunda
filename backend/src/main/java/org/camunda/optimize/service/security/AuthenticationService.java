package org.camunda.optimize.service.security;

import org.camunda.optimize.dto.optimize.query.CredentialsDto;
import org.camunda.optimize.dto.optimize.query.EngineCredentialsDto;
import org.camunda.optimize.service.exceptions.UnauthorizedUserException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author Askar Akhmerov
 */
@Component
public class AuthenticationService {
  @Resource (name = "elasticAuthenticationProvider")
  private AuthenticationProvider elasticAuthenticationProvider;
  @Resource (name = "engineAuthenticationProvider")
  private AuthenticationProvider engineAuthenticationProvider;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private TokenService tokenService;

  public String authenticateUser(CredentialsDto credentials) throws UnauthorizedUserException {
    boolean authorizedInEngine = false;
    for (String engine : configurationService.getConfiguredEngines().keySet()) {
      authorizedInEngine = configurationService.isEngineConnected(engine) && engineAuthenticationProvider.authenticate(new EngineCredentialsDto(credentials, engine));
      if (authorizedInEngine) {
        break;
      }
    }

    if (!authorizedInEngine && !elasticAuthenticationProvider.authenticate(credentials)) {
      throw new UnauthorizedUserException("Can't authorize user [" + credentials.getUsername() + "]");
    }

    // Issue a token for the user
    String token = tokenService.issueToken(credentials.getUsername());
    return token;
  }
}
