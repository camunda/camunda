/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security;

import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.rest.exception.UserNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

public abstract class AbstractUserService<T extends Authentication> implements UserService<T> {

  public UserDto getCurrentUser() {
    T authentication = getCurrentAuthentication();
    try{
      return createUserDtoFrom(authentication);
    }catch (ClassCastException e){
      logger.error(String.format("Couldn't get matching authentication for %s. Throw UserNotFound exception.",
          authentication), e);
      throw new UserNotFoundException("Couldn't get authentication for user.");
    }
  }

  protected T getCurrentAuthentication() {
    SecurityContext context = SecurityContextHolder.getContext();
    return (T) context.getAuthentication();
  }

  @Override
  public String getUserToken() {
    return getUserToken(getCurrentAuthentication());
  }

  public abstract String getUserToken(final T authentication);

}
