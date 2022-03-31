/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.security.es;

import static io.camunda.tasklist.webapp.security.TasklistProfileService.IAM_AUTH_PROFILE;
import static io.camunda.tasklist.webapp.security.TasklistProfileService.IDENTITY_AUTH_PROFILE;
import static io.camunda.tasklist.webapp.security.TasklistProfileService.SSO_AUTH_PROFILE;

import io.camunda.tasklist.util.CollectionUtil;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.security.RolePermissionService;
import io.camunda.tasklist.webapp.security.UserReader;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@Profile("!" + SSO_AUTH_PROFILE + " & !" + IAM_AUTH_PROFILE + " & !" + IDENTITY_AUTH_PROFILE)
public class ElasticsearchUserReader implements UserReader {

  @Autowired private UserStorage userStorage;

  @Autowired private RolePermissionService rolePermissionService;

  @Override
  public Optional<UserDTO> getCurrentUserBy(final Authentication authentication) {
    final Object principal = authentication.getPrincipal();
    if (principal instanceof User) {
      final User user = (User) principal;
      return Optional.of(
          new UserDTO()
              .setUserId(user.getUserId())
              .setDisplayName(user.getDisplayName())
              .setPermissions(rolePermissionService.getPermissions(user.getRoles()))
              .setApiUser(false));
    }
    return Optional.empty();
  }

  @Override
  public String getCurrentOrganizationId() {
    return DEFAULT_ORGANIZATION;
  }

  @Override
  public List<UserDTO> getUsersByUsernames(List<String> userIds) {
    return CollectionUtil.map(
        userStorage.getUsersByUserIds(userIds),
        userEntity ->
            new UserDTO()
                .setUserId(userEntity.getUserId())
                .setDisplayName(userEntity.getDisplayName())
                .setApiUser(false));
  }
}
