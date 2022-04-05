/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.security;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public interface UserReader {

  String DEFAULT_ORGANIZATION = "null";
  String EMPTY = "";

  String DEFAULT_USER = "No name";

  default UserDTO getCurrentUser() {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof AnonymousAuthenticationToken) {
      throw new TasklistRuntimeException("User is not authenticated");
    }
    if (authentication instanceof JwtAuthenticationToken) {
      final JwtAuthenticationToken jwtAuthentication = ((JwtAuthenticationToken) authentication);
      final String name =
          jwtAuthentication.getName() == null ? DEFAULT_USER : jwtAuthentication.getName();
      return new UserDTO()
          .setUserId(name)
          .setDisplayName(name)
          .setApiUser(true)
          .setPermissions(List.of(Permission.WRITE));
    } else {
      final Optional<UserDTO> maybeUserDTO = getCurrentUserBy(authentication);
      return maybeUserDTO.orElseThrow(
          () ->
              new TasklistRuntimeException(
                  String.format(
                      "Could not build UserDTO from authentication authentication %s",
                      authentication)));
    }
  }

  Optional<UserDTO> getCurrentUserBy(final Authentication authentication);

  String getCurrentOrganizationId();

  default String getCurrentUserId() {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication.getPrincipal() == null) {
      return DEFAULT_USER;
    }
    return authentication.getPrincipal().toString();
  }

  List<UserDTO> getUsersByUsernames(List<String> usernames);
}
