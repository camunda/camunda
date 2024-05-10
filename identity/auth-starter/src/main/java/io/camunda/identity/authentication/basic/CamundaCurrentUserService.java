/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.identity.authentication.basic;

import io.camunda.identity.authentication.CurrentUserService;
import io.camunda.identity.record.CamundaUser;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@Profile("identity-basic-auth")
public class CamundaCurrentUserService implements CurrentUserService {
  @Override
  public CamundaUser getCurrentUser() {
    final var authentication = getAuthentication();
    return new CamundaUser(
        authentication.getName(),
        authentication.getAuthorities().stream().map(Object::toString).toList());
  }

  private Authentication getAuthentication() {
    return SecurityContextHolder.getContext().getAuthentication();
  }
}
