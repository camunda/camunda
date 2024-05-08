/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security;

import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.rest.exception.UserNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

public abstract class AbstractUserService<T extends Authentication> implements UserService<T> {

  public UserDto getCurrentUser() {
    final T authentication = getCurrentAuthentication();
    try {
      return createUserDtoFrom(authentication);
    } catch (ClassCastException e) {
      LOGGER.error(
          String.format(
              "Couldn't get matching authentication for %s. Throw UserNotFound exception.",
              authentication),
          e);
      throw new UserNotFoundException("Couldn't get authentication for user.");
    }
  }

  @Override
  public String getUserToken() {
    return getUserToken(getCurrentAuthentication());
  }

  protected T getCurrentAuthentication() {
    final SecurityContext context = SecurityContextHolder.getContext();
    return (T) context.getAuthentication();
  }

  public abstract String getUserToken(final T authentication);
}
