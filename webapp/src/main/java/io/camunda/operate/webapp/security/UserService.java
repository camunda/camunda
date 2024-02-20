/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security;

import io.camunda.operate.webapp.rest.dto.UserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;

public interface UserService<T extends Authentication> {
  Logger logger = LoggerFactory.getLogger(UserService.class);

  UserDto getCurrentUser();

  UserDto createUserDtoFrom(final T authentication);

  String getUserToken();
}
