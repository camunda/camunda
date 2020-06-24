/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

public abstract class AbstractUserService implements UserService {

  @Override
  public String getCurrentUsername() {
    final SecurityContext context = SecurityContextHolder.getContext();
    final Authentication authentication = context.getAuthentication();
    return authentication.getName();
  }
}
