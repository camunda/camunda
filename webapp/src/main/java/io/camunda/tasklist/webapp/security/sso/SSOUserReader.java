/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.security.sso;

import static io.camunda.tasklist.util.CollectionUtil.map;
import static io.camunda.tasklist.webapp.security.TasklistProfileService.SSO_AUTH_PROFILE;

import com.auth0.jwt.interfaces.Claim;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.security.RolePermissionService;
import io.camunda.tasklist.webapp.security.UserReader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@Profile(SSO_AUTH_PROFILE)
public class SSOUserReader implements UserReader {

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private RolePermissionService rolePermissionService;

  @Override
  public Optional<UserDTO> getCurrentUserBy(final Authentication authentication) {
    if (authentication instanceof TokenAuthentication) {
      final TokenAuthentication tokenAuthentication = (TokenAuthentication) authentication;
      final Map<String, Claim> claims = tokenAuthentication.getClaims();
      String name = DEFAULT_USER;
      if (claims.containsKey(tasklistProperties.getAuth0().getNameKey())) {
        name = claims.get(tasklistProperties.getAuth0().getNameKey()).asString();
      }
      final String email = claims.get(tasklistProperties.getAuth0().getEmailKey()).asString();
      return Optional.of(
          new UserDTO()
              .setUserId(email)
              .setDisplayName(name)
              .setApiUser(false)
              // TODO to fix this later. Permissions will come from console - not role name
              .setPermissions(List.of(Permission.READ, Permission.WRITE)));
    }
    return Optional.empty();
  }

  @Override
  public String getCurrentOrganizationId() {
    return tasklistProperties.getAuth0().getOrganization();
  }

  @Override
  public List<UserDTO> getUsersByUsernames(List<String> usernames) {
    return map(
        usernames, name -> new UserDTO().setDisplayName(name).setUserId(name).setApiUser(false));
  }
}
