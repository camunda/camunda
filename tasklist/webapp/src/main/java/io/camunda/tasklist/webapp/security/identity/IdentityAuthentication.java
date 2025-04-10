/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.identity;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.identity.sdk.authentication.Tokens;
import io.camunda.identity.sdk.authentication.UserDetails;
import io.camunda.identity.sdk.authentication.exception.TokenDecodeException;
import io.camunda.identity.sdk.impl.rest.exception.RestException;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.SpringContextHolder;
import io.camunda.tasklist.webapp.security.OldUsernameAware;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.tenant.TasklistTenant;
import io.camunda.tasklist.webapp.tenant.TenantAwareAuthentication;
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
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class IdentityAuthentication extends AbstractAuthenticationToken
    implements OldUsernameAware, TenantAwareAuthentication {

  private static final Logger LOGGER = LoggerFactory.getLogger(IdentityAuthentication.class);
  private static final String POLLING_HEADER = "x-is-polling";
  private Tokens tokens;
  private String id;
  private String name;
  private String userDisplayName;
  private List<String> permissions;
  private String subject;
  private Date expires;
  private Date refreshTokenExpiresAt;
  private volatile List<TasklistTenant> tenants = Collections.emptyList();
  private IdentityAuthorization authorization;
  private List<String> groups;

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
    if (getSecurityConfiguration().getMultiTenancy().isEnabled()) {
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
      } catch (final RestException ex) {
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
        LOGGER.info("No refresh token available. Authentication is invalid.");
        setAuthenticated(false);
        getIdentity().authentication().revokeToken(tokens.getRefreshToken());
        return false;
      } else {
        try {
          LOGGER.info("Get a new access token by using refresh token");
          renewAccessToken();
        } catch (final Exception e) {
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

  public IdentityAuthentication setPermissions(final List<String> permissions) {
    this.permissions = permissions;
    return this;
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
      if (getSecurityConfiguration().getAuthorizations().isEnabled()) {
        authorization =
            new IdentityAuthorization(
                getIdentity().authorizations().forToken(this.tokens.getAccessToken()));
      }
    } catch (final io.camunda.identity.sdk.exception.InvalidConfigurationException ice) {
      LOGGER.debug(
          "Base URL is not provided so it's not possible to get authorizations from Identity");
    } catch (final Exception e) {
      LOGGER.debug("Identity and Tasklist misconfiguration.");
    }
    subject = accessToken.getToken().getSubject();
    expires = accessToken.getToken().getExpiresAt();
    groups = accessToken.getUserDetails().getGroups();
    if (!isPolling()) {
      try {
        refreshTokenExpiresAt =
            getIdentity().authentication().decodeJWT(this.tokens.getRefreshToken()).getExpiresAt();
      } catch (final TokenDecodeException e) {
        LOGGER.error(
            "Unable to decode refresh token {} with exception: {}",
            this.tokens.getRefreshToken(),
            e.getMessage());
      }
    }
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

  @NotNull
  private static SecurityConfiguration getSecurityConfiguration() {
    return SpringContextHolder.getBean(SecurityConfiguration.class);
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

  public String getUserDisplayName() {
    return userDisplayName;
  }

  public IdentityAuthentication setUserDisplayName(final String userDisplayName) {
    this.userDisplayName = userDisplayName;
    return this;
  }

  private String retrieveUserDisplayName() {
    return getIdentity()
        .authentication()
        .verifyToken(tokens.getAccessToken())
        .getUserDetails()
        .getName()
        .orElse(name);
  }

  @Override
  public String getOldName() {
    return getId();
  }

  public IdentityAuthorization getAuthorizations() {
    return authorization;
  }

  public IdentityAuthentication setAuthorizations(final IdentityAuthorization authorization) {
    this.authorization = authorization;
    return this;
  }

  public List<String> getGroups() {
    return groups;
  }

  public IdentityAuthentication setGroups(final List<String> groups) {
    this.groups = groups;
    return this;
  }

  private boolean isPolling() {
    final RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
    if (requestAttributes instanceof ServletRequestAttributes) {
      return RequestContextHolder.getRequestAttributes() != null
          && Boolean.TRUE.equals(
              Boolean.parseBoolean(
                  ((ServletRequestAttributes) requestAttributes)
                      .getRequest()
                      .getHeader(POLLING_HEADER)));
    } else {
      return false;
    }
  }
}
