package org.camunda.operate.webapp.sso;
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;

@Profile(SSOWebSecurityConfig.SSO_AUTH_PROFILE)
public class TokenAuthentication extends AbstractAuthenticationToken {

  protected static final List<? extends GrantedAuthority> EMPTY_GRANTED_AUTHORITY_LIST = Collections.emptyList();
  protected  final Logger logger = LoggerFactory.getLogger(this.getClass());
  private final DecodedJWT jwt;
  private boolean authenticated = false;

  SSOWebSecurityConfig config;

  public TokenAuthentication(DecodedJWT jwt, SSOWebSecurityConfig config) {
    super(EMPTY_GRANTED_AUTHORITY_LIST);
    if(jwt == null) throw new IllegalArgumentException("Given DecodedJWT (jwt) should not be null");
    if(config == null) throw new IllegalArgumentException("Given SSOWebSecurityConfig (config) should not be null");
    this.jwt = jwt;
    this.config = config;
    authenticate();
  }

  protected void authenticate() {
    try {
      Claim claim = jwt.getClaim(config.getClaimName());
      List<String> claims = claim.asList(String.class);
      authenticated = claims.contains(config.getOrganization());
      logger.info("Authenticate succeedeed ");
    }catch (Throwable t) {
      logger.error("Authenticate failed: ",t);
      authenticated = false;
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
