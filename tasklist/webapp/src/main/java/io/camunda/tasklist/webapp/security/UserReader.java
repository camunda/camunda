/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.webapp.dto.UserDTO;
import io.camunda.tasklist.webapp.rest.exception.UnauthenticatedUserException;
import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public interface UserReader {

  String DEFAULT_USER = "No name";

  default UserDTO getCurrentUser() {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof AnonymousAuthenticationToken) {
      throw new UnauthenticatedUserException("User is not authenticated");
    }

    return getCurrentUserBy(authentication)
        .orElseThrow(
            () ->
                new TasklistRuntimeException(
                    String.format(
                        "Could not build UserDTO from authentication %s", authentication)));
  }

  Optional<UserDTO> getCurrentUserBy(final Authentication authentication);

  default String getCurrentUserId() {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication.getPrincipal() == null) {
      return DEFAULT_USER;
    }

    if (authentication instanceof final JwtAuthenticationToken jwt) {
      return jwt.getName();
    }

    return authentication.getPrincipal().toString();
  }
}
