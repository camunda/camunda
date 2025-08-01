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
import io.camunda.authentication.entity.CamundaUser;
import io.camunda.authentication.entity.CamundaUserDTO;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.query.TenantQuery;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.service.TenantServices;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
@Profile("consolidated-auth")
public class BasicCamundaUserService implements CamundaUserService {

  private final CamundaAuthenticationProvider authenticationProvider;
  private final ResourceAccessProvider resourceAccessProvider;
  private final TenantServices tenantServices;

  public BasicCamundaUserService(
      final CamundaAuthenticationProvider authenticationProvider,
      final ResourceAccessProvider resourceAccessProvider,
      final TenantServices tenantServices) {
    this.authenticationProvider = authenticationProvider;
    this.resourceAccessProvider = resourceAccessProvider;
    this.tenantServices = tenantServices;
  }

  private Optional<CamundaUser> getCurrentCamundaUser() {
    return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
        .map(Authentication::getPrincipal)
        .map(principal -> principal instanceof final CamundaUser user ? user : null);
  }

  @Override
  public CamundaUserDTO getCurrentUser() {
    final var authorizedApplications = getAuthorizedApplications();
    return getCurrentCamundaUser()
        .map(
            user -> {
              final var auth = user.getAuthenticationContext();
              final var tenants = getTenantsForUser(user);
              return new CamundaUserDTO(
                  user.getDisplayName(),
                  auth.username(),
                  user.getEmail(),
                  authorizedApplications,
                  tenants,
                  auth.groups(),
                  auth.roles(),
                  user.getSalesPlanType(),
                  user.getC8Links(),
                  user.canLogout());
            })
        .orElse(null);
  }

  @Override
  public String getUserToken() {
    return null;
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

  private List<TenantEntity> getTenantsForUser(final CamundaUser camundaUser) {
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
