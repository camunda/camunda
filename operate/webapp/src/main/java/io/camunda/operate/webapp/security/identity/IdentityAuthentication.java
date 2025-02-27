/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.identity;

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
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;

public class IdentityAuthentication extends AbstractAuthenticationToken
    implements Serializable, TenantAwareAuthentication {

  @Serial private static final long serialVersionUID = 1L;

  private static final Logger LOGGER = LoggerFactory.getLogger(IdentityAuthentication.class);

  private Tokens tokens;
  private String id;
  private String name;
  private List<String> permissions;
  @JsonIgnore private List<IdentityAuthorization> authorizations;
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

  @Override
  public String getName() {
    return name;
  }

  @Override
  public boolean isAuthenticated() {
    if (hasExpired()) {
      LOGGER.info("Access token is expired");
      LOGGER.info("Get a new access token by using refresh token");
      try {
        renewAccessToken();
      } catch (final Exception e) {
        LOGGER.error("Renewing access token failed with exception", e);
        setAuthenticated(false);
      }
    }
    return super.isAuthenticated();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final IdentityAuthentication that = (IdentityAuthentication) o;
    return Objects.equals(tokens, that.tokens)
        && Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(permissions, that.permissions)
        && Objects.equals(authorizations, that.authorizations)
        && Objects.equals(subject, that.subject)
        && Objects.equals(expires, that.expires)
        && Objects.equals(tenants, that.tenants);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(), tokens, id, name, permissions, authorizations, subject, expires, tenants);
  }

  public String getId() {
    return id;
  }

  public List<Permission> getPermissions() {
    final PermissionConverter permissionConverter = getPermissionConverter();
    return permissions.stream().map(permissionConverter::convert).toList();
  }

  public IdentityAuthentication setPermissions(final List<String> permissions) {
    this.permissions = permissions;
    return this;
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
        authorizations =
            IdentityAuthorization.createFrom(
                getIdentity().authorizations().forToken(tokens.getAccessToken()));
      } catch (final RestException ex) {
        LOGGER.warn(
            "Unable to retrieve resource base permissions from Identity. Error: " + ex.getMessage(),
            ex);
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
          tenants =
              identityTenants.stream()
                  .map(t -> new OperateTenant(t.getTenantId(), t.getName()))
                  .toList();
        } else {
          tenants = new ArrayList<>();
        }

      } catch (final RestException ex) {
        LOGGER.warn("Unable to retrieve tenants from Identity. Error: " + ex.getMessage(), ex);
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
    LOGGER.info("Access token will expire at {}", expires);
    setAuthenticated(!hasExpired());
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
    return getIdentityRetryService()
        .requestWithRetry(
            () -> getIdentity().authentication().renewToken(refreshToken),
            "IdentityAuthentication#renewTokens");
  }

  public IdentityAuthentication setExpires(final Date expires) {
    this.expires = expires;
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
