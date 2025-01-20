/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.newwork;

import io.camunda.optimize.service.exceptions.OptimizeUserOrGroupIdNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

public abstract class AbstractUserService<T extends Authentication> implements UserService<T> {

  @Override
  public UserServiceUserDto getCurrentUser() {
    final T authentication = getCurrentAuthentication();
    try {
      return createUserDtoFrom(authentication);
    } catch (final ClassCastException e) {
      LOGGER.error(
          String.format(
              "Couldn't get matching authentication for %s. Throw UserNotFound exception.",
              authentication),
          e);
      throw new OptimizeUserOrGroupIdNotFoundException("Couldn't get authentication for user.");
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
