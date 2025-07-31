/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import static io.camunda.service.authorization.Authorizations.APPLICATION_ACCESS_AUTHORIZATION;

import io.camunda.authentication.ConditionalOnAuthenticationMethod;
import io.camunda.authentication.entity.CamundaOAuthPrincipal;
import io.camunda.authentication.entity.CamundaOidcUser;
import io.camunda.authentication.entity.CamundaUserDTO;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.query.TenantQuery;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.security.entity.ClusterMetadata.AppName;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.service.TenantServices;
import jakarta.json.Json;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
@Profile("consolidated-auth")
public class OidcCamundaUserService implements CamundaUserService {
  private static final String SALES_PLAN_TYPE = "";

  // TODO: This needs to be set for SaaS purposes
  private static final Map<AppName, String> C8_LINKS = Map.of();

  private final CamundaAuthenticationProvider authenticationProvider;
  private final ResourceAccessProvider resourceAccessProvider;
  private final TenantServices tenantServices;

  public OidcCamundaUserService(
      final CamundaAuthenticationProvider authenticationProvider,
      final ResourceAccessProvider resourceAccessProvider,
      final TenantServices tenantServices) {
    this.authenticationProvider = authenticationProvider;
    this.resourceAccessProvider = resourceAccessProvider;
    this.tenantServices = tenantServices;
  }

  private Optional<CamundaOAuthPrincipal> getCamundaUser() {
    return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
        .map(Authentication::getPrincipal)
        .map(principal -> principal instanceof final CamundaOAuthPrincipal user ? user : null);
  }

  @Override
  public CamundaUserDTO getCurrentUser() {
    final var authorizedApplications = getAuthorizedApplications();
    return getCamundaUser()
        .map(
            user -> {
              final var auth = user.getAuthenticationContext();
              final var tenants = getTenantsForUser(user);
              return new CamundaUserDTO(
                  auth.username(),
                  null,
                  user.getDisplayName(),
                  auth.username(),
                  user.getEmail(),
                  authorizedApplications,
                  tenants,
                  auth.groups(),
                  auth.roles(),
                  SALES_PLAN_TYPE,
                  C8_LINKS,
                  true);
            })
        .orElse(null);
  }

  @Override
  public String getUserToken() {
    return getCamundaUser()
        .map(
            user -> {
              if (user instanceof final CamundaOidcUser camundaOAuthPrincipal) {
                // If the user has an access token, return it; otherwise, return the ID token to
                // match the fallback behavior of CamundaOidcUserService#loadUser.
                final var token =
                    camundaOAuthPrincipal.getAccessToken() != null
                        ? camundaOAuthPrincipal.getAccessToken()
                        : camundaOAuthPrincipal.getIdToken().getTokenValue();
                return Json.createValue(token).toString();
              }

              throw new UnsupportedOperationException(
                  "Not supported for token class: " + user.getClass().getName());
            })
        .orElseThrow(
            () ->
                new UnsupportedOperationException(
                    "User is not authenticated or does not have a valid token"));
  }

  protected List<String> getAuthorizedApplications() {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    final var applicationAccess =
        resourceAccessProvider.resolveResourceAccess(
            authentication, APPLICATION_ACCESS_AUTHORIZATION);
    return applicationAccess.allowed()
        ? applicationAccess.authorization().resourceIds()
        : List.of();
  }

  private List<TenantEntity> getTenantsForUser(final CamundaOAuthPrincipal camundaUser) {
    final var tenants = camundaUser.getAuthenticationContext().tenants();
    return Optional.ofNullable(tenants)
        .filter(t -> !t.isEmpty())
        .map(this::getTenants)
        .orElseGet(List::of);
  }

  private List<TenantEntity> getTenants(final List<String> tenantIds) {
    return tenantServices
        .withAuthentication(CamundaAuthentication.anonymous())
        .search(TenantQuery.of(q -> q.filter(f -> f.tenantIds(tenantIds)).unlimited()))
        .items();
  }
}
