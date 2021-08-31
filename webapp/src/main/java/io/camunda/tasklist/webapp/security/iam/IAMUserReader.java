/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.security.iam;

import io.camunda.iam.sdk.authentication.UserInfo;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.util.CollectionUtil;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.tasklist.webapp.security.UserReader;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@Profile(TasklistURIs.IAM_AUTH_PROFILE)
public class IAMUserReader implements UserReader {

  @Override
  public UserDTO getCurrentUser() {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof AnonymousAuthenticationToken) {
      throw new TasklistRuntimeException("User is not authenticated");
    }
    final IAMAuthentication tokenAuth =
        (IAMAuthentication) SecurityContextHolder.getContext().getAuthentication();
    return buildUserDtoFrom(tokenAuth.getUserInfo());
  }

  @Override
  public String getCurrentOrganizationId() {
    return DEFAULT_ORGANIZATION;
  }

  @Override
  public List<UserDTO> getUsersByUsernames(final List<String> usernames) {
    return CollectionUtil.map(
        usernames, name -> new UserDTO().setUsername(name).setFirstname(EMPTY).setLastname(name));
  }

  private UserDTO buildUserDtoFrom(UserInfo userInfo) {
    return new UserDTO()
        .setFirstname(userInfo.getFirstName())
        .setLastname(userInfo.getLastName())
        .setUsername(userInfo.getUsername());
  }
}
