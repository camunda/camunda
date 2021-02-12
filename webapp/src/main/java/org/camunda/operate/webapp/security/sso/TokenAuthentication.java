/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.security.sso;

import com.auth0.Tokens;
import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.camunda.operate.webapp.security.OperateURIs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

@Profile(OperateURIs.SSO_AUTH_PROFILE)
@Component
@Scope(SCOPE_PROTOTYPE)
public class TokenAuthentication extends AbstractAuthenticationToken {

  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  private DecodedJWT jwt;
  private boolean authenticated = false;

  @Autowired
  private SSOWebSecurityConfig config;

  private final Predicate<Map> idEqualsOrganization = new Predicate<>() {
    @Override public boolean test(Map orgs) {
      return orgs.containsKey("id") && orgs.get("id").equals(config.getOrganization());
    }
  };

  public TokenAuthentication() {
    super(null);
  }

  private boolean hasExpired() {
    Date expires = jwt.getExpiresAt();
    return expires == null || expires.before(new Date());
  }

  @Override
  public String getCredentials() {
    return jwt.getToken();
  }

  @Override
  public Object getPrincipal() {
    return jwt.getSubject();
  }

  @Override
  public void setAuthenticated(boolean authenticated) {
    if (authenticated) {
      throw new IllegalArgumentException("Create a new Authentication object to authenticate");
    }
    this.authenticated = false;
  }

  @Override
  public boolean isAuthenticated() {
    return authenticated && !hasExpired();
  }

  public void authenticate(Tokens tokens) {
    jwt = JWT.decode(tokens.getIdToken());
    Claim claim = jwt.getClaim(config.getClaimName());
    tryAuthenticateAsListOfStrings(claim);
    if (!authenticated) {
      tryAuthenticateAsListOfMaps(claim);
    }
    if (!authenticated) {
      throw new InsufficientAuthenticationException("No permission for operate - check your organization id");
    }
  }

  private void tryAuthenticateAsListOfMaps(Claim claim) {
    try {
      List<Map> claims = claim.asList(Map.class);
      if (claims != null) {
        authenticated = claims.stream().anyMatch(idEqualsOrganization);
      }
    } catch (JWTDecodeException e) {
      logger.debug("Read organization claim as list of maps failed.", e);
    }
  }

  @Deprecated
  private void tryAuthenticateAsListOfStrings(Claim claim) {
    try {
      List<String> claims = claim.asList(String.class);
      if (claims != null) {
        authenticated = claims.contains(config.getOrganization());
      }
    } catch (JWTDecodeException e) {
      logger.debug("Read organization claim as list of strings failed.", e);
    }
  }

  /**
   * Gets the claims for this JWT token. <br>
   * For an ID token, claims represent user profile information such as the
   * user's name, profile, picture, etc. <br>
   *
   * @see <a href="https://auth0.com/docs/tokens/id-token">ID Token
   *      Documentation</a>
   * @return a Map containing the claims of the token.
   */
  public Map<String, Claim> getClaims() {
    return jwt.getClaims();
  }

}
