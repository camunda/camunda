/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

package io.camunda.operate.webapp.security.sso;

import static io.camunda.operate.util.CollectionUtil.map;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import com.auth0.Tokens;
import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.security.OperateURIs;
import io.camunda.operate.webapp.security.Role;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Component;

@Profile(OperateURIs.SSO_AUTH_PROFILE)
@Component
@Scope(SCOPE_PROTOTYPE)
public class TokenAuthentication extends AbstractAuthenticationToken {

  private static final transient String SSO_ROLES = "roles";
  private final transient Logger logger = LoggerFactory.getLogger(this.getClass());
  private DecodedJWT jwt;

  @Value("${" + OperateProperties.PREFIX + ".auth0.claimName}")
  private String claimName;

  @Value("${" + OperateProperties.PREFIX + ".auth0.organization}")
  private String organization;

  public TokenAuthentication() {
    super(null);
  }

  private boolean isIdEqualsOrganization(Map orgs) {
    return orgs.containsKey("id") && orgs.get("id").equals(organization);
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
  public boolean isAuthenticated() {
    return super.isAuthenticated() && !hasExpired();
  }

  public void authenticate(Tokens tokens) {
    jwt = JWT.decode(tokens.getIdToken());
    Claim claim = jwt.getClaim(claimName);
    tryAuthenticateAsListOfMaps(claim);
    if (!isAuthenticated()) {
      throw new InsufficientAuthenticationException(
          "No permission for operate - check your organization id");
    }
  }

  private void tryAuthenticateAsListOfMaps(Claim claim) {
    try {
      List<Map> claims = claim.asList(Map.class);
      if (claims != null) {
        setAuthenticated(claims.stream().anyMatch(this::isIdEqualsOrganization));
      }
    } catch (JWTDecodeException e) {
      logger.debug("Read organization claim as list of maps failed.", e);
    }
  }

  /**
   * Gets the claims for this JWT token. <br> For an ID token, claims represent user profile
   * information such as the user's name, profile, picture, etc. <br>
   *
   * @return a Map containing the claims of the token.
   * @see <a href="https://auth0.com/docs/tokens/id-token">ID Token
   * Documentation</a>
   */
  public Map<String, Claim> getClaims() {
    return jwt.getClaims();
  }

}
