package org.camunda.optimize.service.security;

import org.camunda.optimize.rest.util.AuthenticationUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.NewCookie;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.camunda.optimize.rest.util.AuthenticationUtil.OPTIMIZE_AUTHORIZATION;

@Component
public class AuthCookieService {
  private static final Logger logger = LoggerFactory.getLogger(AuthCookieService.class);

  private final ConfigurationService configurationService;

  @Autowired
  AuthCookieService(final ConfigurationService configurationService) {
    this.configurationService = configurationService;
  }


  public NewCookie createDeleteOptimizeAuthCookie() {
    logger.trace("Deleting Optimize authentication cookie.");
    return new NewCookie(
      OPTIMIZE_AUTHORIZATION,
      "",
      "/",
      null,
      "delete cookie",
      0,
      configurationService.isHttpDisabled(),
      true
    );
  }

  public NewCookie createNewOptimizeAuthCookie(final String securityToken) {
    logger.trace("Creating Optimize authentication cookie.");
    return new NewCookie(
      OPTIMIZE_AUTHORIZATION,
      AuthenticationUtil.createOptimizeAuthCookieValue(securityToken),
      "/",
      null,
      1,
      null,
      -1,
      AuthenticationUtil.getTokenIssuedAt(securityToken)
        .map(Date::toInstant)
        .map(issuedAt -> issuedAt.plus(configurationService.getTokenLifeTimeMinutes(), ChronoUnit.MINUTES))
        .map(Date::from)
        .orElse(null),
      configurationService.isHttpDisabled(),
      true
    );
  }

}
