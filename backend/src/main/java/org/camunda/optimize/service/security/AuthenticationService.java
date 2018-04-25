package org.camunda.optimize.service.security;

import org.camunda.optimize.dto.optimize.query.user.CredentialsDto;
import org.camunda.optimize.service.exceptions.UnauthorizedUserException;
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
  private TokenService tokenService;

  public String authenticateUser(CredentialsDto credentials) throws UnauthorizedUserException {
    boolean authorizedInEngine = engineAuthenticationProvider.authenticate(credentials);

    if (!authorizedInEngine && !elasticAuthenticationProvider.authenticate(credentials)) {
      throw new UnauthorizedUserException("Can't authorize user [" + credentials.getId() + "]");
    }

    // Issue a token for the user
    String token = tokenService.issueToken(credentials.getId());
    return token;
  }
}
