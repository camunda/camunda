/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.newwork;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;

public interface UserService<T extends Authentication> {
  Logger LOGGER = LoggerFactory.getLogger(UserService.class);

  UserServiceUserDto getCurrentUser();

  UserServiceUserDto createUserDtoFrom(final T authentication);

  String getUserToken();
}
