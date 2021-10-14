/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.security.iam;

import static io.camunda.tasklist.webapp.security.TasklistURIs.IAM_CALLBACK_URI;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.camunda.iam.sdk.IamApi;
import io.camunda.iam.sdk.authentication.Tokens;
import io.camunda.iam.sdk.authentication.UserInfo;
import io.camunda.iam.sdk.authentication.dto.AuthCodeDto;
import io.camunda.iam.sdk.rest.exception.RestException;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.tasklist.webapp.security.util.JWTDecoder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
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

@Profile(TasklistURIs.IAM_AUTH_PROFILE)
@Component
@Scope(SCOPE_PROTOTYPE)
public class IAMAuthentication extends AbstractAuthenticationToken {

  public static final int DELAY_IN_MILLISECONDS = 500;
  public static final int MAX_ATTEMPTS = 10;
  public static final String READ_PERMISSION_VALUE = "read:*";
  public static final String WRITE_PERMISSION_VALUE = "write:*";

  protected final transient Logger logger = LoggerFactory.getLogger(this.getClass());

  @Autowired private transient IamApi iamApi;

  private DecodedJWT jwt;
  private Tokens tokens;
  private UserInfo userInfo;

  public IAMAuthentication() {
    super(null);
  }

  @Override
  public String getCredentials() {
    return tokens.getAccessToken();
  }

  @Override
  public Object getPrincipal() {
    return jwt.getSubject();
  }

  public UserInfo getUserInfo() {
    return userInfo;
  }

  @Override
  public String getName() {
    return userInfo.getFullName();
  }

  public String getId() {
    return userInfo.getId();
  }

  private boolean hasPermission(String permissionName) {
    if (jwt == null || !(jwt instanceof JWTDecoder)) {
      return false;
    }
    final JWTDecoder decoder = (JWTDecoder) jwt;
    if (decoder.getPayload() == null) {
      return false;
    }
    final Claim permissions = decoder.getPayloadObject().getClaim("permissions");
    if (permissions == null) {
      return false;
    }

    return Arrays.asList(permissions.asArray(String.class)).contains(permissionName);
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

  public void authenticate(final HttpServletRequest req, AuthCodeDto authCodeDto) throws Exception {
    tokens = retrieveTokens(req, authCodeDto);
    userInfo = retrieveUserInfo(tokens);
    jwt = new JWTDecoder(iamApi.authentication().verifyToken(tokens.getAccessToken()).getToken());
    if (!getPermissions().contains(Permission.READ)) {
      throw new InsufficientAuthenticationException("No read permissions");
    }
    setAuthenticated(true);
  }

  private Tokens retrieveTokens(final HttpServletRequest req, final AuthCodeDto authCodeDto)
      throws Exception {
    return requestWithRetry(
        () ->
            iamApi
                .authentication()
                .exchangeAuthCode(authCodeDto, getRedirectURI(req, IAM_CALLBACK_URI)));
  }

  private UserInfo retrieveUserInfo(final Tokens tokens) throws Exception {
    return requestWithRetry(() -> iamApi.authentication().userInfo(tokens));
  }

  private boolean hasExpired() {
    final Date expires = jwt.getExpiresAt();
    return expires == null || expires.before(new Date());
  }

  @Override
  public boolean isAuthenticated() {
    return super.isAuthenticated() && !hasExpired();
  }

  private <T> T requestWithRetry(final CheckedSupplier<T> operation) {
    final RetryPolicy<T> retryPolicy =
        new RetryPolicy<T>()
            .handle(RestException.class)
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
      result = redirectUri + redirectTo + "?uuid=" + clusterId;
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
