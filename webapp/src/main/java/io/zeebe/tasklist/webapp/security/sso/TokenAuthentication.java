/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.security.sso;

import static io.zeebe.tasklist.webapp.security.TasklistURIs.SSO_AUTH_PROFILE;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import com.auth0.Tokens;
import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.zeebe.tasklist.property.TasklistProperties;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Component;

@Profile(SSO_AUTH_PROFILE)
@Component
@Scope(SCOPE_PROTOTYPE)
public class TokenAuthentication extends AbstractAuthenticationToken {

  protected final transient Logger logger = LoggerFactory.getLogger(this.getClass());

  private DecodedJWT jwt;
  private boolean authenticated = false;

  @Autowired private transient TasklistProperties tasklistProperties;

  private final transient Predicate<Map> idEqualsOrganization =
      new Predicate<>() {
        @Override
        public boolean test(Map orgs) {
          return orgs.containsKey("id")
              && orgs.get("id").equals(tasklistProperties.getAuth0().getOrganization());
        }
      };

  public TokenAuthentication() {
    super(null);
  }

  private boolean hasExpired() {
    final Date expires = jwt.getExpiresAt();
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
  public boolean isAuthenticated() {
    return authenticated && !hasExpired();
  }

  @Override
  public void setAuthenticated(boolean authenticated) {
    if (authenticated) {
      throw new IllegalArgumentException("Create a new Authentication object to authenticate");
    }
    this.authenticated = false;
  }

  public void authenticate(Tokens tokens) {
    jwt = JWT.decode(tokens.getIdToken());
    final Claim claim = jwt.getClaim(tasklistProperties.getAuth0().getClaimName());
    tryAuthenticateAsListOfMaps(claim);
    if (!authenticated) {
      throw new InsufficientAuthenticationException(
          "No permission for Zeebe Tasklist - check your organization id");
    }
  }

  private void tryAuthenticateAsListOfMaps(Claim claim) {
    try {
      final List<Map> claims = claim.asList(Map.class);
      if (claims != null) {
        authenticated = claims.stream().anyMatch(idEqualsOrganization);
      }
    } catch (JWTDecodeException e) {
      logger.warn("Read organization claim as list of maps failed.", e);
    }
  }

  /**
   * Gets the claims for this JWT token. <br>
   * For an ID token, claims represent user profile information such as the user's name, profile,
   * picture, etc. <br>
   *
   * @return a Map containing the claims of the token.
   * @see <a href="https://auth0.com/docs/tokens/id-token">ID Token Documentation</a>
   */
  public Map<String, Claim> getClaims() {
    return jwt.getClaims();
  }
}
