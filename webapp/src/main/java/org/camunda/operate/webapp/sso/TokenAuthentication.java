/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.sso;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import com.auth0.Tokens;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;

public class TokenAuthentication extends AbstractAuthenticationToken {

  private static final Logger logger = LoggerFactory.getLogger(TokenAuthentication.class);

  private final DecodedJWT jwt;
  private boolean authenticated = false;
  private SSOWebSecurityConfig config;

  public TokenAuthentication(Tokens tokens, SSOWebSecurityConfig config) {
    super(null);
    this.jwt = JWT.decode(tokens.getIdToken());
    this.config = config;
  }

  protected void authenticate() throws InsufficientAuthenticationException {
      Claim claim = jwt.getClaim(config.getClaimName());
      List<String> claims = claim.asList(String.class);
      //null-check
      authenticated = claims.contains(config.getOrganization());
      if(authenticated) {
        SecurityContextHolder.getContext().setAuthentication(this);
      } else {
        throw new InsufficientAuthenticationException("No permission for operate - check your organization id");
      }
  }

  private boolean hasExpired() {
    return jwt.getExpiresAt().before(new Date());
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
    authenticated = false;
  }

  @Override
  public boolean isAuthenticated() {
    return authenticated && !hasExpired();
  }

  /**
   * Gets the claims for this JWT token. <br>
   * For an ID token, claims represent user profile information such as the
   * user's name, profile, picture, etc. <br>
   * 
   * @see <a href="https://auth0.com/docs/tokens/id-token">ID Token Documentation</a>
   * @return a Map containing the claims of the token.
   */
  public Map<String, Claim> getClaims() {
    return jwt.getClaims();
  }

}
