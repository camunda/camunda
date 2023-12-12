/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.security.identity;

import com.auth0.jwt.interfaces.DecodedJWT;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.identity.sdk.authentication.Tokens;
import io.camunda.identity.sdk.authentication.UserDetails;
import io.camunda.identity.sdk.authentication.exception.TokenDecodeException;
import io.camunda.identity.sdk.impl.rest.exception.RestException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.SpringContextHolder;
import io.camunda.tasklist.webapp.security.OldUsernameAware;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.security.tenant.TasklistTenant;
import io.camunda.tasklist.webapp.security.tenant.TenantAwareAuthentication;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;

public class IdentityAuthentication extends AbstractAuthenticationToken
    implements OldUsernameAware, TenantAwareAuthentication {

  private static final Logger LOGGER = LoggerFactory.getLogger(IdentityAuthentication.class);
  private Tokens tokens;
  private String id;
  private String name;
  private String userDisplayName;
  private List<String> permissions;
  private String subject;
  private Date expires;
  private volatile List<TasklistTenant> tenants = Collections.emptyList();
  private IdentityAuthorization authorization;

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

  @Override
  public List<TasklistTenant> getTenants() {
    if (CollectionUtils.isEmpty(tenants)) {
      synchronized (this) {
        if (CollectionUtils.isEmpty(tenants)) {
          retrieveTenants();
        }
      }
    }
    return tenants;
  }

  private void retrieveTenants() {
    if (getTasklistProperties().getMultiTenancy().isEnabled()) {
      try {
        final var accessToken = tokens.getAccessToken();
        final var identityTenants = getIdentity().tenants().forToken(accessToken);

        if (CollectionUtils.isNotEmpty(identityTenants)) {
          tenants =
              identityTenants.stream()
                  .map((t) -> new TasklistTenant(t.getTenantId(), t.getName()))
                  .sorted(TENANT_NAMES_COMPARATOR)
                  .toList();
        } else {
          tenants = List.of();
        }
      } catch (RestException ex) {
        LOGGER.warn("Unable to retrieve tenants from Identity. Error: " + ex.getMessage(), ex);
        tenants = List.of();
      }
    } else {
      tenants = List.of();
    }
  }

  public Tokens getTokens() {
    return tokens;
  }

  private boolean hasExpired() {
    return expires == null || expires.before(new Date());
  }

  private boolean hasRefreshTokenExpired() {
    try {
      final DecodedJWT refreshToken =
          getIdentity().authentication().decodeJWT(tokens.getRefreshToken());
      final Date refreshTokenExpiresAt = refreshToken.getExpiresAt();
      LOGGER.info("Refresh token will expire at {}", refreshTokenExpiresAt);
      return refreshTokenExpiresAt == null || refreshTokenExpiresAt.before(new Date());
    } catch (final TokenDecodeException e) {
      LOGGER.info(
          "Refresh token is not a JWT and expire date can not be determined. Error message: {}",
          e.getMessage());
      return false;
    }
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public boolean isAuthenticated() {
    if (hasExpired()) {
      LOGGER.info("Access token is expired");
      if (hasRefreshTokenExpired()) {
        setAuthenticated(false);
        LOGGER.info("No refresh token available. Authentication is invalid.");
        throw new InsufficientAuthenticationException(
            "Access token and refresh token are expired.");
      } else {
        LOGGER.info("Get a new access token by using refresh token");
        try {
          renewAccessToken();
        } catch (Exception e) {
          LOGGER.error("Renewing access token failed with exception", e);
          setAuthenticated(false);
        }
      }
    }
    return super.isAuthenticated();
  }

  public String getId() {
    return id;
  }

  public List<Permission> getPermissions() {
    return permissions.stream()
        .map(PermissionConverter.getInstance()::convert)
        .collect(Collectors.toList());
  }

  public void authenticate(final Tokens tokens) {
    if (tokens != null) {
      this.tokens = tokens;
    }
    final AccessToken accessToken =
        getIdentity().authentication().verifyToken(this.tokens.getAccessToken());
    final UserDetails userDetails = accessToken.getUserDetails();
    id = userDetails.getId();
    name = retrieveName(userDetails);
    userDisplayName = retrieveUserDisplayName();
    permissions = accessToken.getPermissions();
    if (!getPermissions().contains(Permission.READ)) {
      throw new InsufficientAuthenticationException("No read permissions");
    }

    try {
      final TasklistProperties props = getTasklistProperties();
      if (props.getIdentity().isResourcePermissionsEnabled()) {
        authorization =
            new IdentityAuthorization(
                getIdentity().authorizations().forToken(this.tokens.getAccessToken()));
      }
    } catch (io.camunda.identity.sdk.exception.InvalidConfigurationException ice) {
      LOGGER.debug(
          "Base URL is not provided so it's not possible to get authorizations from Identity");
    } catch (Exception e) {
      LOGGER.debug("Identity and Tasklist misconfiguration.");
    }
    subject = accessToken.getToken().getSubject();
    expires = accessToken.getToken().getExpiresAt();
    if (!hasExpired()) {
      setAuthenticated(true);
    } else {
      setAuthenticated(false);
    }
  }

  @NotNull
  private static TasklistProperties getTasklistProperties() {
    return SpringContextHolder.getBean(TasklistProperties.class);
  }

  private String retrieveName(final UserDetails userDetails) {
    return userDetails.getUsername().orElse(userDetails.getId());
  }

  private void renewAccessToken() {
    authenticate(renewTokens(tokens.getRefreshToken()));
  }

  private Tokens renewTokens(final String refreshToken) {
    return IdentityService.requestWithRetry(
        () -> getIdentity().authentication().renewToken(refreshToken));
  }

  private Identity getIdentity() {
    return SpringContextHolder.getBean(Identity.class);
  }

  public IdentityAuthentication setExpires(final Date expires) {
    this.expires = expires;
    return this;
  }

  public IdentityAuthentication setPermissions(final List<String> permissions) {
    this.permissions = permissions;
    return this;
  }

  public String getUserDisplayName() {
    return userDisplayName;
  }

  private String retrieveUserDisplayName() {
    return getIdentity()
        .authentication()
        .verifyToken(this.tokens.getAccessToken())
        .getUserDetails()
        .getName()
        .orElse(this.name);
  }

  public IdentityAuthentication setUserDisplayName(String userDisplayName) {
    this.userDisplayName = userDisplayName;
    return this;
  }

  @Override
  public String getOldName() {
    return getId();
  }

  public IdentityAuthorization getAuthorizations() {
    return authorization;
  }

  public IdentityAuthentication setAuthorizations(IdentityAuthorization authorization) {
    this.authorization = authorization;
    return this;
  }
}
