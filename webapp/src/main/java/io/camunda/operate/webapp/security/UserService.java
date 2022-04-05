/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security;

import io.camunda.operate.webapp.rest.dto.UserDto;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

public interface UserService<T extends Authentication> {

  default UserDto getCurrentUser() {
    SecurityContext context = SecurityContextHolder.getContext();
    return createUserDtoFrom((T) context.getAuthentication());
  }

  UserDto createUserDtoFrom(T authentication);
}
