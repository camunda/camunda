/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import static io.camunda.service.authorization.Authorizations.COMPONENT_ACCESS_AUTHORIZATION;

import io.camunda.authentication.ConditionalOnAuthenticationMethod;
import io.camunda.authentication.entity.CamundaUserDTO;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.security.reader.ResourceAccessProvider;
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
  private final TmpServicesAbstraction tmpServicesAbstraction;

  public BasicCamundaUserService(
      final CamundaAuthenticationProvider authenticationProvider,
      final ResourceAccessProvider resourceAccessProvider,
      final TmpServicesAbstraction tmpServicesAbstraction
  ) {
    this.authenticationProvider = authenticationProvider;
    this.resourceAccessProvider = resourceAccessProvider;
    this.tmpServicesAbstraction = tmpServicesAbstraction;
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
    final var authorizedComponents = getAuthorizedComponents(authentication);
    return new CamundaUserDTO(
        user.username(),
        username,
        user.email(),
        authorizedComponents,
        tenants,
        groups,
        roles,
        null,
        Map.of(),
        true);
  }

  protected User getUser(final CamundaAuthentication authentication) {
    final var username = authentication.authenticatedUsername();
    return tmpServicesAbstraction.getUser(username);
  }

  protected List<String> getAuthorizedComponents(final CamundaAuthentication authentication) {
    final var componentAccess =
        resourceAccessProvider.resolveResourceAccess(
            authentication, COMPONENT_ACCESS_AUTHORIZATION);
    return componentAccess.allowed() ? componentAccess.authorization().resourceIds() : List.of();
  }

  private List<Tenant> getTenantsForCamundaAuthentication(
      final CamundaAuthentication authentication) {
    return Optional.ofNullable(authentication.authenticatedTenantIds())
        .filter(t -> !t.isEmpty())
        .map(tmpServicesAbstraction::getTenants)
        .orElseGet(List::of);
  }
}
