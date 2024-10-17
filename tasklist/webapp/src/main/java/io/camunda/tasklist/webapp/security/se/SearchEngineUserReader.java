/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.se;

import static io.camunda.tasklist.webapp.security.TasklistProfileService.IDENTITY_AUTH_PROFILE;
import static io.camunda.tasklist.webapp.security.TasklistProfileService.SSO_AUTH_PROFILE;

import io.camunda.authentication.entity.CamundaUser;
import io.camunda.tasklist.util.CollectionUtil;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.security.UserReader;
import io.camunda.tasklist.webapp.security.se.store.UserStore;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@Profile("!" + SSO_AUTH_PROFILE + " & !" + IDENTITY_AUTH_PROFILE)
public class SearchEngineUserReader implements UserReader {

  @Autowired private UserStore userStore;

  @Autowired private RolePermissionService rolePermissionService;

  @Override
  public Optional<UserDTO> getCurrentUserBy(final Authentication authentication) {
    final Object principal = authentication.getPrincipal();
    if (principal instanceof CamundaUser) {
      final CamundaUser user = (CamundaUser) principal;
      return Optional.of(
          new UserDTO()
              .setUserId(user.getUserId())
              .setDisplayName(user.getDisplayName())
              .setPermissions(
                  rolePermissionService.getPermissions(
                      user.getRoles().stream().map(Role::fromString).toList()))
              .setApiUser(false));
    }
    return Optional.empty();
  }

  @Override
  public String getCurrentOrganizationId() {
    return DEFAULT_ORGANIZATION;
  }

  @Override
  public List<UserDTO> getUsersByUsernames(final List<String> userIds) {
    return CollectionUtil.map(
        userStore.getUsersByUserIds(userIds),
        userEntity ->
            new UserDTO()
                .setUserId(userEntity.getUserId())
                .setDisplayName(userEntity.getDisplayName())
                .setApiUser(false));
  }

  @Override
  public Optional<String> getUserToken(final Authentication authentication) {
    throw new UnsupportedOperationException(
        "Get token is not supported for Identity authentication");
  }
}
