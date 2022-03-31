/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.security.identity;

import static io.camunda.tasklist.webapp.security.TasklistURIs.IDENTITY_CALLBACK_URI;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import com.auth0.jwt.interfaces.DecodedJWT;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.identity.sdk.authentication.Tokens;
import io.camunda.identity.sdk.authentication.UserDetails;
import io.camunda.identity.sdk.authentication.dto.AuthCodeDto;
import io.camunda.identity.sdk.exception.IdentityException;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.security.TasklistProfileService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.function.CheckedSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Component;

@Profile(TasklistProfileService.IDENTITY_AUTH_PROFILE)
@Component
@Scope(SCOPE_PROTOTYPE)
public class IdentityAuthentication extends AbstractAuthenticationToken {

  public static final int DELAY_IN_MILLISECONDS = 500;
  public static final int MAX_ATTEMPTS = 10;
  public static final String READ_PERMISSION_VALUE = "read:*";
  public static final String WRITE_PERMISSION_VALUE = "write:*";
  protected final transient Logger logger = LoggerFactory.getLogger(this.getClass());

  @Autowired private transient Identity identity;

  private AccessToken accessToken;
  private Tokens tokens;
  private UserDetails userDetails;

  public IdentityAuthentication() {
    super(null);
  }

  @Override
  public String getCredentials() {
    return tokens.getAccessToken();
  }

  @Override
  public Object getPrincipal() {
    return accessToken.getToken().getSubject();
  }

  public Tokens getTokens() {
    return tokens;
  }

  private boolean hasExpired() {
    final Date expires = accessToken.getToken().getExpiresAt();
    return expires == null || expires.before(new Date());
  }

  private boolean hasRefreshTokenExpired() {
    final DecodedJWT refreshToken = identity.authentication().decodeJWT(tokens.getRefreshToken());
    final Date expires = refreshToken.getExpiresAt();
    return expires == null || expires.before(new Date());
  }

  @Override
  public String getName() {
    return userDetails.getName().orElse("");
  }

  @Override
  public boolean isAuthenticated() {
    if (hasExpired()) {
      logger.info("Access token is expired");
      if (hasRefreshTokenExpired()) {
        setAuthenticated(false);
        logger.info("No refresh token available. Authentication is invalid.");
      } else {
        logger.info("Get a new access token by using refresh token");
        try {
          renewAccessToken();
        } catch (Exception e) {
          logger.error("Renewing access token failed with exception", e);
          setAuthenticated(false);
        }
      }
    }
    return super.isAuthenticated();
  }

  public String getId() {
    return userDetails.getId();
  }

  private boolean hasPermission(String permissionName) {
    return accessToken.hasPermissions(Set.of(permissionName));
  }

  private boolean hasReadPermission() {
    return hasPermission(READ_PERMISSION_VALUE);
  }

  private boolean hasWritePermission() {
    return hasPermission(WRITE_PERMISSION_VALUE);
  }

  public List<Permission> getPermissions() {
    final List<Permission> permissions = new ArrayList<>();
    if (hasReadPermission()) {
      permissions.add(Permission.READ);
    }
    if (hasWritePermission()) {
      permissions.add(Permission.WRITE);
    }

    return permissions;
  }

  public void authenticate(final HttpServletRequest req, AuthCodeDto authCodeDto) {
    authenticate(retrieveTokens(req, authCodeDto));
  }

  private void authenticate(final Tokens tokens) {
    this.tokens = tokens;
    accessToken = identity.authentication().verifyToken(tokens.getAccessToken());
    userDetails = accessToken.getUserDetails();
    if (!getPermissions().contains(Permission.READ)) {
      throw new InsufficientAuthenticationException("No read permissions");
    }
    setAuthenticated(true);
  }

  private void renewAccessToken() {
    authenticate(renewTokens(tokens.getRefreshToken()));
  }

  private Tokens retrieveTokens(final HttpServletRequest req, final AuthCodeDto authCodeDto) {
    return requestWithRetry(
        () ->
            identity
                .authentication()
                .exchangeAuthCode(authCodeDto, getRedirectURI(req, IDENTITY_CALLBACK_URI)));
  }

  private Tokens renewTokens(final String refreshToken) {
    return requestWithRetry(() -> identity.authentication().renewToken(refreshToken));
  }

  private <T> T requestWithRetry(final CheckedSupplier<T> operation) {
    final RetryPolicy<T> retryPolicy =
        new RetryPolicy<T>()
            .handle(IdentityException.class)
            .withDelay(Duration.ofMillis(DELAY_IN_MILLISECONDS))
            .withMaxAttempts(MAX_ATTEMPTS);
    return Failsafe.with(retryPolicy).get(operation);
  }

  public static String getRedirectURI(final HttpServletRequest req, final String redirectTo) {
    String redirectUri = req.getScheme() + "://" + req.getServerName();
    if ((req.getScheme().equals("http") && req.getServerPort() != 80)
        || (req.getScheme().equals("https") && req.getServerPort() != 443)) {
      redirectUri += ":" + req.getServerPort();
    }
    final String result;
    if (contextPathIsUUID(req.getContextPath())) {
      final String clusterId = req.getContextPath().replace("/", "");
      result = redirectUri + /* req.getContextPath()+ */ redirectTo + "?uuid=" + clusterId;
    } else {
      result = redirectUri + req.getContextPath() + redirectTo;
    }
    return result;
  }

  protected static boolean contextPathIsUUID(String contextPath) {
    try {
      UUID.fromString(contextPath.replace("/", ""));
      return true;
    } catch (Exception e) {
      // Assume it isn't a UUID
      return false;
    }
  }
}
