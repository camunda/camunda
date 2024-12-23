/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.entity;

import java.security.Principal;
import java.util.Set;
import javax.annotation.Nullable;
import org.springframework.security.core.Authentication;

public interface CamundaPrincipal {
  String DEFAULT_ORGANIZATION_ID = "null";

  String getEmail();

  String getDisplayName();

  AuthenticationContext getAuthenticationContext();

  default Set<String> getOrganizationIds() {
    return Set.of(DEFAULT_ORGANIZATION_ID);
  }

  static @Nullable CamundaPrincipal fromPrincipal(final Principal principal) {
    if (!(principal instanceof final Authentication auth)) {
      return null;
    }
    if (!(auth.getPrincipal() instanceof final CamundaPrincipal camundaPrincipal)) {
      return null;
    }
    return camundaPrincipal;
  }
}
