/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * SPIKE (ADR-0038): CCSaaS webapp login gate, wired into CSL's OIDC webapp chain through its {@code
 * ObjectProvider<OidcUserService>} hook ({@code userInfoEndpoint().oidcUserService(...)}).
 *
 * <p>Replaces the legacy {@code CCSaaSSecurityConfigurerAdapter} {@code hasAccess} organization
 * membership check plus its {@code RoleValidator}: a user may only log in when the OIDC principal's
 * {@code https://camunda.com/orgs} claim contains the configured organization with at least one of
 * the allowed organization roles. Otherwise login is denied with {@code access_denied}.
 *
 * <p>The token-level cluster binding (public-API bearer path) is handled separately by the {@code
 * TokenValidatorFactory} override; this class only gates the interactive webapp login.
 */
public final class OptimizeCloudOidcUserService extends OidcUserService {

  /** Auth0 claim carrying the user's organizations, each a map of {@code id} + {@code roles}. */
  static final String ORGANIZATIONS_CLAIM = "https://camunda.com/orgs";

  /** Organization roles that grant Optimize access (mirrors the legacy adapter). */
  static final List<String> ALLOWED_ORG_ROLES =
      List.of("admin", "analyst", "owner", "supportagent");

  private static final Logger LOG = LoggerFactory.getLogger(OptimizeCloudOidcUserService.class);

  private final String organizationId;
  private final List<String> allowedRoles;

  public OptimizeCloudOidcUserService(
      final String organizationId, final List<String> allowedRoles) {
    this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
    this.allowedRoles = List.copyOf(allowedRoles);
  }

  @Override
  public OidcUser loadUser(final OidcUserRequest userRequest) throws OAuth2AuthenticationException {
    final OidcUser user = super.loadUser(userRequest);
    if (!hasRequiredOrgRole(user)) {
      LOG.debug(
          "Denying login for [{}]: not a member of organization [{}] with an allowed role {}",
          user.getName(),
          organizationId,
          allowedRoles);
      throw new OAuth2AuthenticationException(
          new OAuth2Error(
              "access_denied",
              "User is not a member of organization %s with a required role %s"
                  .formatted(organizationId, allowedRoles),
              null));
    }
    return user;
  }

  /**
   * True when the {@code https://camunda.com/orgs} claim contains the configured organization and
   * that organization grants at least one allowed role. Package-private for unit testing.
   */
  boolean hasRequiredOrgRole(final OidcUser user) {
    final Object claim = user.getClaims().get(ORGANIZATIONS_CLAIM);
    if (!(claim instanceof final Collection<?> organizations)) {
      return false;
    }
    return organizations.stream()
        .filter(Map.class::isInstance)
        .map(org -> (Map<?, ?>) org)
        .filter(org -> organizationId.equals(org.get("id")))
        .anyMatch(this::grantsAllowedRole);
  }

  private boolean grantsAllowedRole(final Map<?, ?> organization) {
    return organization.get("roles") instanceof final Collection<?> roles
        && roles.stream().anyMatch(allowedRoles::contains);
  }
}
