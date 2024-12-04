/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.permission;

import io.camunda.authentication.service.CamundaUserService;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.security.UserReader;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@Profile("auth-basic")
public class CamundaBasedUserReader implements UserReader {

  private final CamundaUserService userService;

  public CamundaBasedUserReader(final CamundaUserService userService) {
    this.userService = userService;
  }

  @Override
  public Optional<UserDTO> getCurrentUserBy(final Authentication authentication) {
    final var currentUser = userService.getCurrentUser();
    return Optional.of(
        new UserDTO()
            .setUserId(currentUser.userId())
            .setDisplayName(currentUser.displayName())
            .setPermissions(List.of(Permission.READ, Permission.WRITE))
            .setApiUser(false));
  }

  @Override
  public String getCurrentOrganizationId() {
    return DEFAULT_ORGANIZATION;
  }

  @Override
  public String getCurrentUserId() {
    return getCurrentUser().getUserId();
  }

  @Override
  public List<UserDTO> getUsersByUsernames(final List<String> usernames) {
    return List.of();
  }

  @Override
  public Optional<String> getUserToken(final Authentication authentication) {
    return Optional.empty();
  }
}
