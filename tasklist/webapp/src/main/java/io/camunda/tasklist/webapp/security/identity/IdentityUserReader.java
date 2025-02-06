/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.identity;

import static io.camunda.tasklist.util.CollectionUtil.map;
import static io.camunda.tasklist.webapp.security.TasklistProfileService.IDENTITY_AUTH_PROFILE;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.tasklist.webapp.dto.UserDTO;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.security.UserReader;
import io.camunda.tasklist.webapp.security.oauth.IdentityTenantAwareJwtAuthenticationToken;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@Profile(IDENTITY_AUTH_PROFILE)
public class IdentityUserReader implements UserReader {

  @Autowired private Identity identity;

  @Override
  public Optional<UserDTO> getCurrentUserBy(final Authentication authentication) {
    if (authentication instanceof final IdentityAuthentication identityAuthentication) {
      return Optional.of(
          new UserDTO()
              // For testing assignee migration locally use 'identityAuthentication.getId()'
              .setUserId(/*identityAuthentication.getId()*/ identityAuthentication.getName())
              .setDisplayName(identityAuthentication.getUserDisplayName())
              .setPermissions(identityAuthentication.getPermissions())
              .setTenants(identityAuthentication.getTenants())
              .setGroups(identityAuthentication.getGroups()));
    } else if (authentication
        instanceof final IdentityTenantAwareJwtAuthenticationToken identityTenantAwareToken) {
      final AccessToken accessToken =
          identity
              .authentication()
              .verifyToken(((Jwt) identityTenantAwareToken.getPrincipal()).getTokenValue());
      final List<Permission> permissions =
          accessToken.getPermissions().stream()
              .map(PermissionConverter.getInstance()::convert)
              .collect(Collectors.toList());

      final String userDisplayName =
          accessToken.getUserDetails().getName().orElse(identityTenantAwareToken.getName());

      return Optional.of(
          new UserDTO()
              .setUserId(identityTenantAwareToken.getName())
              .setDisplayName(userDisplayName)
              .setPermissions(permissions)
              .setTenants(identityTenantAwareToken.getTenants()));
    } else {
      return Optional.empty();
    }
  }

  @Override
  public String getCurrentOrganizationId() {
    return DEFAULT_ORGANIZATION;
  }

  @Override
  public List<UserDTO> getUsersByUsernames(final List<String> usernames) {
    return map(usernames, name -> new UserDTO().setUserId(name).setDisplayName(name));
  }

  @Override
  public Optional<String> getUserToken(final Authentication authentication) {
    throw new UnsupportedOperationException(
        "Get token is not supported for Identity authentication");
  }
}
