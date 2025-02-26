/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.sso;

import static io.camunda.tasklist.util.CollectionUtil.map;
import static io.camunda.tasklist.webapp.security.TasklistProfileService.SSO_AUTH_PROFILE;

import com.auth0.jwt.interfaces.Claim;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.dto.C8AppLink;
import io.camunda.tasklist.webapp.dto.UserDTO;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.security.UserReader;
import io.camunda.tasklist.webapp.security.identity.UserGroupService;
import io.camunda.tasklist.webapp.security.sso.model.C8ConsoleService;
import io.camunda.tasklist.webapp.security.sso.model.ClusterMetadata;
import jakarta.json.Json;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@Profile(SSO_AUTH_PROFILE)
public class SSOUserReader implements UserReader {

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private C8ConsoleService c8ConsoleService;

  @Autowired private UserGroupService userGroupService;

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
      final ClusterMetadata clusterMetadata = c8ConsoleService.getClusterMetadata();
      List<C8AppLink> c8Links = List.of();
      if (clusterMetadata != null) {
        c8Links = clusterMetadata.getUrlsAsC8AppLinks();
      }
      return Optional.of(
          new UserDTO()
              // For testing assignee migration locally use 'authentication.getName()'
              .setUserId(/*authentication.getName()*/ email)
              .setDisplayName(name)
              .setGroups(userGroupService.getUserGroups())
              .setPermissions(tokenAuthentication.getPermissions())
              .setRoles(
                  tokenAuthentication.getRoles(tasklistProperties.getAuth0().getOrganizationsKey()))
              .setSalesPlanType(tokenAuthentication.getSalesPlanType())
              .setC8Links(c8Links));
    } else if (authentication instanceof JwtAuthenticationToken) {
      final JwtAuthenticationToken jwtAuthentication = ((JwtAuthenticationToken) authentication);
      final String name =
          jwtAuthentication.getName() == null ? DEFAULT_USER : jwtAuthentication.getName();
      return Optional.of(
          new UserDTO()
              .setUserId(name)
              .setDisplayName(name)
              // M2M token in the cloud always has WRITE permissions
              .setPermissions(List.of(Permission.WRITE)));
    }
    return Optional.empty();
  }

  @Override
  public String getCurrentOrganizationId() {
    return tasklistProperties.getAuth0().getOrganization();
  }

  @Override
  public List<UserDTO> getUsersByUsernames(final List<String> usernames) {
    return map(usernames, name -> new UserDTO().setDisplayName(name).setUserId(name));
  }

  @Override
  public Optional<String> getUserToken(final Authentication authentication) {
    if (authentication instanceof TokenAuthentication) {
      return Optional.of(
          Json.createValue(((TokenAuthentication) authentication).getAccessToken()).toString());
    } else {
      throw new UnsupportedOperationException(
          "Not supported for token class: " + authentication.getClass().getName());
    }
  }
}
