/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.identity;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.identity.sdk.authentication.Tokens;
import io.camunda.identity.sdk.authentication.UserDetails;
import io.camunda.identity.sdk.impl.rest.exception.RestException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.SpringContextHolder;
import io.camunda.operate.webapp.security.Permission;
import io.camunda.operate.webapp.security.tenant.OperateTenant;
import io.camunda.operate.webapp.security.tenant.TenantAwareAuthentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class IdentityAuthentication extends AbstractAuthenticationToken implements Serializable, TenantAwareAuthentication {

  private static final long serialVersionUID = 1L;

  private static final Logger logger = LoggerFactory.getLogger(IdentityAuthentication.class);

  private Tokens tokens;
  private String id;
  private String name;
  private List<String> permissions;
  @JsonIgnore
  private List<IdentityAuthorization> authorizations;
  private String subject;
  private Date expires;

  private List<OperateTenant> tenants;

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
    if(!StringUtils.hasText(tokens.getRefreshToken())) return true;
    final DecodedJWT refreshToken =
        getIdentity().authentication().decodeJWT(tokens.getRefreshToken());
    final Date refreshTokenExpiresAt = refreshToken.getExpiresAt();
    logger.info("Refresh token will expire at {}", refreshTokenExpiresAt);
    return refreshTokenExpiresAt == null || refreshTokenExpiresAt.before(new Date());
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public boolean isAuthenticated() {
    if (hasExpired()) {
      logger.info("Access token is expired");
      if (hasRefreshTokenExpired()) {
        setAuthenticated(false);
        logger.info("No refresh token available. Authentication is invalid.");
        throw new InsufficientAuthenticationException("Access token and refresh token are expired.");
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
    return id;
  }

  public List<Permission> getPermissions() {
    PermissionConverter permissionConverter = getPermissionConverter();
    return permissions.stream().map(permissionConverter::convert).collect(Collectors.toList());
  }

  public List<IdentityAuthorization> getAuthorizations() {
    if (authorizations == null) {
      synchronized (this) {
        if (authorizations == null) {
          retrieveResourcePermissions();
        }
      }
    }
    return authorizations;
  }

  @Override
  public List<OperateTenant> getTenants() {
    if (tenants == null) {
      synchronized (this) {
        if (tenants == null) {
          retrieveTenants();
        }
      }
    }
    return tenants;
  }

  private void retrieveResourcePermissions() {
    if (getOperateProperties().getIdentity().isResourcePermissionsEnabled()) {
      try {
        authorizations = IdentityAuthorization.createFrom(
            getIdentity().authorizations().forToken(this.tokens.getAccessToken()));
      } catch (RestException ex) {
        logger.warn("Unable to retrieve resource base permissions from Identity. Error: " + ex.getMessage(), ex);
        authorizations = new ArrayList<>();
      }
    }
  }

  private void retrieveTenants() {
    if (getOperateProperties().getMultiTenancy().isEnabled()) {
      try {
        final var accessToken = tokens.getAccessToken();
        final var identityTenants = getIdentity().tenants().forToken(accessToken);

        if (identityTenants != null) {
          tenants = identityTenants.stream()
              .map((t) -> new OperateTenant(t.getTenantId(), t.getName()))
              .collect(Collectors.toList());
        } else {
          tenants = new ArrayList<>();
        }

      } catch (RestException ex) {
        logger.warn("Unable to retrieve tenants from Identity. Error: " + ex.getMessage(), ex);
        tenants = new ArrayList<>();
      }
    }
  }

  public void authenticate(final Tokens tokens) {
    if (tokens != null) {
      this.tokens = tokens;
    }
    final AccessToken accessToken =
        getIdentity().authentication().verifyToken(this.tokens.getAccessToken());
    final UserDetails userDetails = accessToken.getUserDetails();
    id = userDetails.getId();
    retrieveName(userDetails);
    permissions = accessToken.getPermissions();
    retrieveResourcePermissions();
    if (!getPermissions().contains(Permission.READ)) {
      throw new InsufficientAuthenticationException("No read permissions");
    }

    retrieveTenants();

    subject = accessToken.getToken().getSubject();
    expires = accessToken.getToken().getExpiresAt();
    logger.info("Access token will expire at {}", expires);
    if (!hasExpired()) {
      setAuthenticated(true);
    } else {
      setAuthenticated(false);
    }
  }

  private void retrieveName(final UserDetails userDetails) {
    // Fallback is username e.g 'homer' otherwise empty string.
    final String username = userDetails.getUsername().orElse("");
    // Get display name like 'Homer Simpson' otherwise username e.g. 'homer'
    name = userDetails.getName().orElse(username);
  }

  private void renewAccessToken() throws Exception {
    authenticate(renewTokens(tokens.getRefreshToken()));
  }

  private Tokens renewTokens(final String refreshToken) throws Exception {
    return getIdentityRetryService().requestWithRetry(
            () -> getIdentity().authentication().renewToken(refreshToken), "IdentityAuthentication#renewTokens");
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
    return SpringContextHolder.getBean(Identity.class);
  }

  private OperateProperties getOperateProperties() {
    return SpringContextHolder.getBean(OperateProperties.class);
  }

  private IdentityRetryService getIdentityRetryService() {
    return SpringContextHolder.getBean(IdentityRetryService.class);
  }

  private PermissionConverter getPermissionConverter() {
    return SpringContextHolder.getBean(PermissionConverter.class);
  }
}
