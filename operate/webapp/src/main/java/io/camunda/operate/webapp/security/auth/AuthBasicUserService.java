/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.auth;

import io.camunda.identity.permissions.PermissionEnum;
import io.camunda.identity.security.CamundaUserDetails;
import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.security.AbstractUserService;
import io.camunda.operate.webapp.security.Permission;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@Profile("auth-basic")
public class AuthBasicUserService extends AbstractUserService<UsernamePasswordAuthenticationToken> {
  private static final List<String> CAMUNDA_READ_PERMISSIONS =
      List.of(PermissionEnum.READ_ALL.name());
  private static final List<String> CAMUNDA_WRITE_PERMISSIONS =
      List.of(
          PermissionEnum.CREATE_ALL.name(),
          PermissionEnum.UPDATE_ALL.name(),
          PermissionEnum.DELETE_ALL.name());

  @Override
  public UserDto createUserDtoFrom(final UsernamePasswordAuthenticationToken authentication) {
    final var camundaUserDetails = (CamundaUserDetails) authentication.getPrincipal();

    return new UserDto()
        .setUserId(String.valueOf(camundaUserDetails.getUserId()))
        .setDisplayName(camundaUserDetails.getDisplayName())
        .setCanLogout(false)
        .setPermissions(translatePermissions(camundaUserDetails.getPermissions()));
  }

  @Override
  public String getUserToken(final UsernamePasswordAuthenticationToken authentication) {
    throw new UnsupportedOperationException("Get token is not supported for basic authentication");
  }

  private List<Permission> translatePermissions(final List<String> permissions) {
    final List<Permission> translatedPermissions = new ArrayList<>();
    permissions.forEach(
        permissionString -> {
          if (CAMUNDA_READ_PERMISSIONS.contains(permissionString)
              && !translatedPermissions.contains(Permission.READ)) {
            translatedPermissions.add(Permission.READ);
          } else if (CAMUNDA_WRITE_PERMISSIONS.contains(permissionString)
              && !translatedPermissions.contains(Permission.WRITE)) {
            translatedPermissions.add(Permission.WRITE);
          }
        });

    return translatedPermissions;
  }
}
