/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import static io.camunda.service.authorization.Authorizations.COMPONENT_ACCESS_AUTHORIZATION;

import io.camunda.search.entities.UserEntity;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.api.model.user.CamundaUserDTO;
import io.camunda.security.core.port.in.CamundaUserPort;
import io.camunda.security.core.reader.ResourceAccessProvider;
import io.camunda.security.spring.annotation.ConditionalOnAuthenticationMethod;
import io.camunda.service.registry.ServiceRegistry;
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
public class BasicCamundaUserService implements CamundaUserPort {

  private final CamundaAuthenticationProvider authenticationProvider;
  private final ResourceAccessProvider resourceAccessProvider;
  private final ServiceRegistry serviceRegistry;

  public BasicCamundaUserService(
      final CamundaAuthenticationProvider authenticationProvider,
      final ResourceAccessProvider resourceAccessProvider,
      final ServiceRegistry serviceRegistry) {
    this.authenticationProvider = authenticationProvider;
    this.resourceAccessProvider = resourceAccessProvider;
    this.serviceRegistry = serviceRegistry;
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
    final var tenants = authentication.authenticatedTenantIds();
    final var authorizedComponents = getAuthorizedComponents(authentication);
    return new CamundaUserDTO(
        user.name(),
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

  protected UserEntity getUser(final CamundaAuthentication authentication) {
    final var username = authentication.authenticatedUsername();
    return serviceRegistry
        .userServices("default") // TODO replace with contextual physicalTenantId
        .getUser(username, CamundaAuthentication.anonymous());
  }

  protected List<String> getAuthorizedComponents(final CamundaAuthentication authentication) {
    final var componentAccess =
        resourceAccessProvider.resolveResourceAccess(
            authentication, COMPONENT_ACCESS_AUTHORIZATION);
    if (!componentAccess.allowed()) {
      return List.of();
    }
    final var resourceIds = componentAccess.authorization().resourceIds();
    if (resourceIds == null) {
      return List.of();
    }
    return resourceIds.stream().map(id -> "identity".equals(id) ? "admin" : id).distinct().toList();
  }
}
