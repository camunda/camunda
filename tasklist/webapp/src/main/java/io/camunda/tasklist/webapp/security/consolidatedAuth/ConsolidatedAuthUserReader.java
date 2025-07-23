/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.consolidatedAuth;

import io.camunda.authentication.entity.CamundaPrincipal;
import io.camunda.tasklist.webapp.dto.UserDTO;
import io.camunda.tasklist.webapp.security.UserReader;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class ConsolidatedAuthUserReader implements UserReader {

  @Override
  public Optional<UserDTO> getCurrentUserBy(final Authentication authentication) {
    if (authentication.getPrincipal() instanceof final CamundaPrincipal camundaPrincipal) {
      return Optional.of(
          new UserDTO()
              .setUserId(camundaPrincipal.getUsername())
              .setDisplayName(camundaPrincipal.getDisplayName()));
    }
    return Optional.empty();
  }
}
