/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.sso;

import com.auth0.client.auth.AuthAPI;
import com.auth0.exception.Auth0Exception;
import com.auth0.json.auth.TokenHolder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.net.TokenRequest;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.impl.rest.exception.RestException;
import io.camunda.config.operate.Auth0Properties;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.util.SpringContextHolder;
import io.camunda.operate.webapp.security.Permission;
import io.camunda.operate.webapp.security.identity.IdentityAuthorization;
import io.camunda.security.configuration.SecurityConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;

/**
 * This class may be created in two ways: user freshly authenticated or stored session is
 * deserialized. In the second case all the fields marked with JsonIgnore will be empty.
 */
public class TokenAuthentication extends AbstractAuthenticationToken {

  public static final String ORGANIZATION_ID = "id";
  public static final String ROLES_KEY = "roles";
  private static final Logger LOGGER = LoggerFactory.getLogger(TokenAuthentication.class);

  // Need to research further whether a lock shared between all instances is the desired behavior.
  // This was originally an Integer member variable (non-static) but due to integer pooling, it
  // was effectively a shared instance. This static object mirrors that behavior to keep consistent
  // behavior until we research further.
  @JsonIgnore private static final Object RESOURCE_PERMISSIONS_LOCK = new Object();

  private String claimName;
  private String organization;
  private String domain;
  private String clientId;
  private String clientSecret;
  private String idToken;
  private String refreshToken;
  private String accessToken;
  private String salesPlanType;
  private final List<Permission> permissions = new ArrayList<>();
  @JsonIgnore private List<IdentityAuthorization> authorizations;
  private Instant lastResourceBasedPermissionsUpdated = Instant.now();

  public TokenAuthentication() {
    super(null);
  }

  public TokenAuthentication(final Auth0Properties auth0Properties, final String organizationId) {
    this();
    claimName = auth0Properties.getClaimName();
    organization = organizationId;
    domain = auth0Properties.getDomain();
    clientId = auth0Properties.getClientId();
    clientSecret = auth0Properties.getClientSecret();
  }

  private boolean isIdEqualsOrganization(final Map<String, String> orgs) {
    return orgs.containsKey("id") && orgs.get("id").equals(organization);
  }

  @Override
  public boolean isAuthenticated() {
    if (hasExpired()) {
      LOGGER.info("Tokens are expired");
      if (refreshToken == null) {
        setAuthenticated(false);
        LOGGER.info("No refresh token available. Authentication is invalid.");
      } else {
        LOGGER.info("Get a new tokens by using refresh token");
        getNewTokenByRefreshToken();
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
    final TokenAuthentication that = (TokenAuthentication) o;
    return claimName.equals(that.claimName)
        && organization.equals(that.organization)
        && domain.equals(that.domain)
        && clientId.equals(that.clientId)
        && clientSecret.equals(that.clientSecret)
        && idToken.equals(that.idToken)
        && Objects.equals(refreshToken, that.refreshToken)
        && Objects.equals(salesPlanType, that.salesPlanType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        claimName,
        organization,
        domain,
        clientId,
        clientSecret,
        idToken,
        refreshToken,
        salesPlanType);
  }

  public List<Permission> getPermissions() {
    return permissions;
  }

  public void addPermission(final Permission permission) {
    permissions.add(permission);
  }

  public List<IdentityAuthorization> getAuthorizations() {
    if (getIdentity() != null && (authorizations == null || needToUpdate())) {
      synchronized (RESOURCE_PERMISSIONS_LOCK) {
        updateResourcePermissions();
      }
    }
    return authorizations;
  }

  public TokenAuthentication setAuthorizations(final List<IdentityAuthorization> authorizations) {
    this.authorizations = authorizations;
    return this;
  }

  public boolean needToUpdate() {
    final Duration duration = Duration.between(lastResourceBasedPermissionsUpdated, Instant.now());
    return !duration
        .minusSeconds(getOperateProperties().getIdentity().getResourcePermissionsUpdatePeriod())
        .isNegative();
  }

  private void updateResourcePermissions() {
    if (getSecurityConfiguration().getAuthorizations().isEnabled() && getIdentity() != null) {
      try {
        final List<IdentityAuthorization> identityAuthorizations =
            IdentityAuthorization.createFrom(
                getIdentity()
                    .authorizations()
                    .forToken(accessToken, getOperateProperties().getCloud().getOrganizationId()));
        LOGGER.debug("Authorizations updated: " + identityAuthorizations);
        authorizations = identityAuthorizations;
        lastResourceBasedPermissionsUpdated = Instant.now();
      } catch (final RestException ex) {
        LOGGER.warn(
            "Unable to retrieve resource base permissions from Identity. Error: " + ex.getMessage(),
            ex);
        authorizations = new ArrayList<>();
      }
    } else {
      authorizations = new ArrayList<>();
    }
  }

  public String getNewTokenByRefreshToken() {
    try {
      final TokenRequest tokenRequest = getAuthAPI().renewAuth(refreshToken);
      final TokenHolder tokenHolder = tokenRequest.execute();
      authenticate(
          tokenHolder.getIdToken(), tokenHolder.getRefreshToken(), tokenHolder.getAccessToken());
      LOGGER.info("New tokens received and validated.");
      return accessToken;
    } catch (final Auth0Exception e) {
      LOGGER.error(e.getMessage(), e.getCause());
      setAuthenticated(false);
      return null;
    }
  }

  private AuthAPI getAuthAPI() {
    return new AuthAPI(domain, clientId, clientSecret);
  }

  public boolean hasExpired() {
    final Date expires = JWT.decode(idToken).getExpiresAt();
    return expires == null || expires.before(new Date());
  }

  public Date getExpiresAt() {
    return JWT.decode(idToken).getExpiresAt();
  }

  @Override
  public String getCredentials() {
    return JWT.decode(idToken).getToken();
  }

  @Override
  public Object getPrincipal() {
    return JWT.decode(idToken).getSubject();
  }

  public void authenticate(
      final String idToken, final String refreshToken, final String accessToken) {
    this.idToken = idToken;
    this.accessToken = accessToken;
    // Normally the refresh token will be issued only once
    // after first successfully getting the access token
    // ,so we need to avoid that the refreshToken will be overridden with null
    if (refreshToken != null) {
      this.refreshToken = refreshToken;
    }
    final Claim claim = JWT.decode(idToken).getClaim(claimName);
    tryAuthenticateAsListOfMaps(claim);
    if (!isAuthenticated()) {
      throw new InsufficientAuthenticationException(
          "No permission for Operate - check your organization id");
    }
  }

  private void tryAuthenticateAsListOfMaps(final Claim claim) {
    try {
      final List<? extends Map> claims = claim.asList(Map.class);
      if (claims != null) {
        setAuthenticated(claims.stream().anyMatch(this::isIdEqualsOrganization));
      }
    } catch (final JWTDecodeException e) {
      LOGGER.debug("Read organization claim as list of maps failed.", e);
    }
  }

  /**
   * Gets the claims for this JWT token. <br>
   * For an ID token, claims represent user profile information such as the user's name, profile,
   * picture, etc. <br>
   *
   * @return a Map containing the claims of the token.
   * @see <a href="https://auth0.com/docs/tokens/id-token">ID Token Documentation</a>
   */
  public Map<String, Claim> getClaims() {
    return JWT.decode(idToken).getClaims();
  }

  public List<String> getRoles(final String organizationsKey) {
    try {
      final Map<String, Claim> claims = getClaims();
      return findRolesForOrganization(claims, organizationsKey, organization);
    } catch (final Exception e) {
      LOGGER.error("Could not get roles. Return empty roles list.", e);
    }
    return List.of();
  }

  private List<String> findRolesForOrganization(
      final Map<String, Claim> claims, final String organizationsKey, final String organization) {
    try {
      final List<Map> orgInfos = claims.get(organizationsKey).asList(Map.class);
      if (orgInfos != null) {
        final Optional<Map> orgInfo =
            orgInfos.stream()
                .filter(oi -> oi.get(ORGANIZATION_ID).equals(organization))
                .findFirst();
        if (orgInfo.isPresent()) {
          return (List<String>) orgInfo.get().get(ROLES_KEY);
        }
      }
    } catch (final Exception e) {
      LOGGER.error(
          String.format(
              "Couldn't extract roles for organization '%s' in JWT claims. Return empty roles list.",
              organization),
          e);
    }
    return List.of();
  }

  public String getSalesPlanType() {
    return salesPlanType;
  }

  public void setSalesPlanType(final String salesPlanType) {
    this.salesPlanType = salesPlanType;
  }

  public String getAccessToken() {
    return accessToken;
  }

  private Identity getIdentity() {
    try {
      return SpringContextHolder.getBean(Identity.class);
    } catch (final NoSuchBeanDefinitionException ex) {
      return null;
    }
  }

  private OperateProperties getOperateProperties() {
    return SpringContextHolder.getBean(OperateProperties.class);
  }

  private SecurityConfiguration getSecurityConfiguration() {
    return SpringContextHolder.getBean(SecurityConfiguration.class);
  }
}
