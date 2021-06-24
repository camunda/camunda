/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.security.es;

import static io.camunda.tasklist.webapp.security.TasklistURIs.SSO_AUTH_PROFILE;

import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.security.UserReader;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@Profile("!" + SSO_AUTH_PROFILE)
public class ElasticsearchUserReader implements UserReader {

  @Autowired private UserStorage userStorage;

  @Override
  public UserDTO getCurrentUser() {
    final SecurityContext context = SecurityContextHolder.getContext();
    final Authentication authentication = context.getAuthentication();
    if (authentication instanceof JwtAuthenticationToken) {
      return UserDTO.buildFromJWTAuthenticationToken((JwtAuthenticationToken) authentication);
    } else {
      return UserDTO.buildFromAuthentication(authentication);
    }
  }

  @Override
  public String getCurrentUserId() {
    return getCurrentUser().getUsername();
  }

  @Override
  public String getCurrentOrganizationId() {
    return DEFAULT_ORGANIZATION;
  }

  @Override
  public List<UserDTO> getUsersByUsernames(List<String> usernames) {
    return UserDTO.createFrom(userStorage.getUsersByUsernames(usernames));
  }

  private Authentication getAuthentication() {
    return SecurityContextHolder.getContext().getAuthentication();
  }
}
