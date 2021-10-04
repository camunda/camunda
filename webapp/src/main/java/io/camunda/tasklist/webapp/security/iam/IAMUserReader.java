/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.security.iam;

import static io.camunda.tasklist.util.CollectionUtil.map;

import io.camunda.iam.sdk.authentication.UserInfo;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.security.Role;
import io.camunda.tasklist.webapp.security.RolePermissionService;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.tasklist.webapp.security.UserReader;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@Profile(TasklistURIs.IAM_AUTH_PROFILE)
public class IAMUserReader implements UserReader {

  @Autowired private RolePermissionService rolePermissionService;

  @Override
  public Optional<UserDTO> getCurrentUserBy(final Authentication authentication) {
    if (authentication instanceof IAMAuthentication) {
      final IAMAuthentication tokenAuth = (IAMAuthentication) authentication;
      final UserInfo userInfo = tokenAuth.getUserInfo();
      return Optional.of(
          new UserDTO()
              .setFirstname(userInfo.getFirstName())
              .setLastname(userInfo.getLastName())
              .setUsername(userInfo.getUsername())
              .setPermissions(
                  rolePermissionService.getPermissions(
                      map(userInfo.getRoles(), Role::fromString))));
    }
    return Optional.empty();
  }

  @Override
  public String getCurrentOrganizationId() {
    return DEFAULT_ORGANIZATION;
  }

  @Override
  public List<UserDTO> getUsersByUsernames(final List<String> usernames) {
    return map(
        usernames, name -> new UserDTO().setUsername(name).setFirstname(EMPTY).setLastname(name));
  }
}
