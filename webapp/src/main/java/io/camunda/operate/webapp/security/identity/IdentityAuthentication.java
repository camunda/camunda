/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.identity;

import com.auth0.jwt.interfaces.DecodedJWT;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.identity.sdk.authentication.Tokens;
import io.camunda.identity.sdk.authentication.UserDetails;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.security.OperateProfileService;
import io.camunda.operate.webapp.security.Permission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Component;

import java.util.*;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

@Profile(OperateProfileService.IDENTITY_AUTH_PROFILE)
@Component
@Scope(SCOPE_PROTOTYPE)
public class IdentityAuthentication extends AbstractAuthenticationToken {

  public static final String READ_PERMISSION_VALUE = "read:*";
  public static final String WRITE_PERMISSION_VALUE = "write:*";

  private static Logger logger;
  private transient Identity identity;

  @Value("${" + OperateProperties.PREFIX + ".identity.issuer.url}")
  private String issuerUrl;

  @Value("${" + OperateProperties.PREFIX + ".identity.issuer.backend.url}")
  private String issuerBackendUrl;

  @Value("${" + OperateProperties.PREFIX + ".identity.client.id}")
  private String clientId;

  @Value("${" + OperateProperties.PREFIX + ".identity.client.secret}")
  private String clientSecret;

  @Value("${" + OperateProperties.PREFIX + ".identity.audience}")
  private String audience;

  private Tokens tokens;
  private String id;
  private String name;
  private List<String> permissions;
  private String subject;
  private Date expires;

  public IdentityAuthentication() {
    super(null);
  }

  @Override
  public String getCredentials() {
    return tokens.getAccessToken();
  }

  @Override
  public Object getPrincipal() {
    return subject;
  }

  public Tokens getTokens() {
    return tokens;
  }

  private boolean hasExpired() {
    return expires == null || expires.before(new Date());
  }

  private boolean hasRefreshTokenExpired() {
    final DecodedJWT refreshToken =
        getIdentity().authentication().decodeJWT(tokens.getRefreshToken());
    final Date refreshTokenExpiresAt = refreshToken.getExpiresAt();
    return refreshTokenExpiresAt == null || refreshTokenExpiresAt.before(new Date());
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public boolean isAuthenticated() {
    if (hasExpired()) {
      getLogger().info("Access token is expired");
      if (hasRefreshTokenExpired()) {
        setAuthenticated(false);
        getLogger().info("No refresh token available. Authentication is invalid.");
      } else {
        getLogger().info("Get a new access token by using refresh token");
        try {
          renewAccessToken();
        } catch (Exception e) {
          getLogger().error("Renewing access token failed with exception", e);
          setAuthenticated(false);
        }
      }
    }
    return super.isAuthenticated();
  }

  public String getId() {
    return id;
  }

  private boolean hasPermission(String permissionName) {
    return permissions.containsAll(Set.of(permissionName));
  }

  private boolean hasReadPermission() {
    return hasPermission(READ_PERMISSION_VALUE);
  }

  private boolean hasWritePermission() {
    return hasPermission(WRITE_PERMISSION_VALUE);
  }

  public List<Permission> getPermissions() {
    List<Permission> permissions = new ArrayList<>();
    if (hasReadPermission()) {
      permissions.add(Permission.READ);
    }
    if (hasWritePermission()) {
      permissions.add(Permission.WRITE);
    }

    return permissions;
  }

  public void authenticate(final Tokens tokens) {
    if (tokens != null) {
      this.tokens = tokens;
    }
    final AccessToken accessToken =
        getIdentity().authentication().verifyToken(this.tokens.getAccessToken());
    final UserDetails userDetails = accessToken.getUserDetails();
    id = userDetails.getId();
    name = userDetails.getName().orElse("");
    permissions = accessToken.getPermissions();
    if (!getPermissions().contains(Permission.READ)) {
      throw new InsufficientAuthenticationException("No read permissions");
    }
    subject = accessToken.getToken().getSubject();
    expires = accessToken.getToken().getExpiresAt();
    if (!hasExpired()) {
      setAuthenticated(true);
    }
  }

  private void renewAccessToken() throws Exception {
    authenticate(renewTokens(tokens.getRefreshToken()));
  }

  private Tokens renewTokens(final String refreshToken) throws Exception {
    return IdentityService.requestWithRetry(
        () -> getIdentity().authentication().renewToken(refreshToken));
  }

  public IdentityAuthentication setExpires(final Date expires) {
    this.expires = expires;
    return this;
  }

  public IdentityAuthentication setPermissions(final List<String> permissions) {
    this.permissions = permissions;
    return this;
  }

  private Identity getIdentity() {
    if (identity == null) {
      identity =
          new Identity(
              new IdentityConfiguration(
                  issuerUrl, issuerBackendUrl, clientId, clientSecret, audience));
    }
    return identity;
  }

  private static Logger getLogger() {
    if (logger == null) {
      logger = LoggerFactory.getLogger(IdentityAuthentication.class);
    }
    return logger;
  }
}
