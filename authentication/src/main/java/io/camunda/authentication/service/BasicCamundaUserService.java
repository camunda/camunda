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
import io.camunda.authentication.entity.CamundaUserDTO;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.query.TenantQuery;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.service.TenantServices;
import io.camunda.service.UserServices;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
@ConditionalOnSecondaryStorageEnabled
@Profile("consolidated-auth")
public class BasicCamundaUserService implements CamundaUserService {

  private final CamundaAuthenticationProvider authenticationProvider;
  private final ResourceAccessProvider resourceAccessProvider;
  private final UserServices userServices;
  private final TenantServices tenantServices;

  public BasicCamundaUserService(
      final CamundaAuthenticationProvider authenticationProvider,
      final ResourceAccessProvider resourceAccessProvider,
      final UserServices userServices,
      final TenantServices tenantServices) {
    this.authenticationProvider = authenticationProvider;
    this.resourceAccessProvider = resourceAccessProvider;
    this.userServices = userServices;
    this.tenantServices = tenantServices;
  }

  @Override
  public CamundaUserDTO getCurrentUser() {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return Optional.ofNullable(authentication)
        .filter(a -> !a.isAnonymous())
        .map(this::getCurrentUser)
        .orElse(null);
  }

  @Override
  public String getUserToken() {
    return null;
  }

  protected CamundaUserDTO getCurrentUser(final CamundaAuthentication authentication) {
    final var user = getUser(authentication);
    final var username = authentication.authenticatedUsername();
    final var groups = authentication.authenticatedGroupIds();
    final var roles = authentication.authenticatedRoleIds();
    final var tenants = getTenantsForCamundaAuthentication(authentication);
    final var authorizedApplications = getAuthorizedApplications(authentication);
    return new CamundaUserDTO(
        user.name(),
        username,
        user.email(),
        authorizedApplications,
        tenants,
        groups,
        roles,
        null,
        Map.of(),
        true);
  }

  protected UserEntity getUser(final CamundaAuthentication authentication) {
    final var username = authentication.authenticatedUsername();
    return userServices.withAuthentication(CamundaAuthentication.anonymous()).getUser(username);
  }

  protected List<String> getAuthorizedApplications(final CamundaAuthentication authentication) {
    final var applicationAccess =
        resourceAccessProvider.resolveResourceAccess(
            authentication, APPLICATION_ACCESS_AUTHORIZATION);
    return applicationAccess.allowed()
        ? applicationAccess.authorization().resourceIds()
        : List.of();
  }

  private List<TenantEntity> getTenantsForCamundaAuthentication(
      final CamundaAuthentication authentication) {
    return Optional.ofNullable(authentication.authenticatedTenantIds())
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
