/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security;

import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.rest.exception.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

public interface UserService<T extends Authentication> {
    Logger logger = LoggerFactory.getLogger(UserService.class);
  default UserDto getCurrentUser() {
    SecurityContext context = SecurityContextHolder.getContext();
    try{
        return createUserDtoFrom((T) context.getAuthentication());
    }catch (ClassCastException e){
        logger.error(String.format("Couldn't get matching authentication for %s. Throw UserNotFound exception.", context.getAuthentication()), e);
        throw new UserNotFoundException("Couldn't get authentication for user.");
    }
  }

  UserDto createUserDtoFrom(T authentication);
}
