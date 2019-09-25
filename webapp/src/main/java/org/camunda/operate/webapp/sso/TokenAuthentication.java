/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.sso;

import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.auth0.AuthenticationController;
import com.auth0.IdentityVerificationException;
import com.auth0.Tokens;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;

@Profile(SSOWebSecurityConfig.SSO_AUTH_PROFILE)
@Component
public class TokenAuthentication extends AbstractAuthenticationToken {

  public TokenAuthentication() {
    super(null);
  }

  private DecodedJWT jwt;
  private boolean authenticated = false;

  @Autowired
  private SSOWebSecurityConfig config;

  @Autowired
  private AuthenticationController authenticator;

  public TokenAuthentication setTokens(Tokens tokens) {
    this.jwt = JWT.decode(tokens.getIdToken());
    return this;
  }
  
  protected void authenticate() throws InsufficientAuthenticationException {
    Claim claim = jwt.getClaim(config.getClaimName());
    List<String> claims = claim.asList(String.class);
    if (claims != null) {
      authenticated = claims.contains(config.getOrganization());
    }
    if (authenticated) {
      SecurityContextHolder.getContext().setAuthentication(this);
    } else {
      throw new InsufficientAuthenticationException("No permission for operate - check your organization id");
    }
  }
  
  public String getAuthorizeUrl(final HttpServletRequest req,String redirectUri) {
    String authorizeUrl = authenticator.buildAuthorizeUrl(req, redirectUri)
        .withAudience(String.format("https://%s/userinfo", config.getBackendDomain())) // get user profile
        .withScope("openid profile email") // which info we request
        .build();
    return authorizeUrl;
  }

  private boolean hasExpired() {
    Date expires = jwt.getExpiresAt();
    return expires != null && expires.before(new Date());
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
  
  public void authenticate(final HttpServletRequest req) throws IdentityVerificationException {
    setTokens(authenticator.handle(req));
    authenticate();
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
