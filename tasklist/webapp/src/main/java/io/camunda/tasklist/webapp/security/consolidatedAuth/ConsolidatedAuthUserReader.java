/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.consolidatedAuth;

import static io.camunda.tasklist.webapp.security.TasklistProfileService.CONSOLIDATED_AUTH_PROFILE;

import io.camunda.authentication.entity.CamundaPrincipal;
import io.camunda.search.entities.RoleEntity;
import io.camunda.tasklist.webapp.dto.UserDTO;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.security.UserReader;
import io.camunda.tasklist.webapp.security.se.Role;
import io.camunda.tasklist.webapp.security.se.RolePermissionService;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@Profile(CONSOLIDATED_AUTH_PROFILE)
public class ConsolidatedAuthUserReader implements UserReader {

  private final RolePermissionService rolePermissionService;

  public ConsolidatedAuthUserReader(final RolePermissionService rolePermissionService) {
    this.rolePermissionService = rolePermissionService;
  }

  @Override
  public Optional<UserDTO> getCurrentUserBy(final Authentication authentication) {
    if (authentication == null) {
      return Optional.empty();
    }
    if (authentication.getPrincipal() instanceof final CamundaPrincipal camundaPrincipal) {
      return Optional.of(
          new UserDTO()
              .setUserId(camundaPrincipal.getUsername())
              .setDisplayName(camundaPrincipal.getDisplayName())
              .setPermissions(getPermissions(camundaPrincipal)));
    }
    return Optional.empty();
  }

  @Override
  public String getCurrentOrganizationId() {
    return DEFAULT_ORGANIZATION;
  }

  @Override
  public List<UserDTO> getUsersByUsernames(final List<String> usernames) {
    return List.of();
  }

  @Override
  public Optional<String> getUserToken(final Authentication authentication) {
    throw new UnsupportedOperationException(
        "Get token is not supported for consolidated-auth authentication");
  }

  private List<Permission> getPermissions(final CamundaPrincipal principal) {
    if (principal.getAuthenticationContext() == null) {
      return List.of();
    }

    return rolePermissionService.getPermissions(
        principal.getAuthenticationContext().roles().stream()
            .map(RoleEntity::name)
            .map(Role::fromString)
            .toList());
  }
}
