package org.camunda.optimize.service.security;

import org.camunda.optimize.dto.optimize.query.CredentialsDto;
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
    if (!configurationService.isEngineConnected() || !engineAuthenticationProvider.authenticate(credentials)) {
      // Authenticate the user using the credentials provided
      if (!elasticAuthenticationProvider.authenticate(credentials)) {
        throw new UnauthorizedUserException("Can't authorize user [" + credentials.getUsername() + "]");
      }
    }

    // Issue a token for the user
    String token = tokenService.issueToken(credentials.getUsername());
    return token;
  }
}
