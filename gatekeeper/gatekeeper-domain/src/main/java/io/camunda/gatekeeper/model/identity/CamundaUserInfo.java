/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.model.identity;

import java.util.List;

/**
 * Represents the current authenticated user's information, including profile, authorization, and
 * membership details.
 */
public record CamundaUserInfo(
    String displayName,
    String username,
    String email,
    List<String> authorizedComponents,
    List<String> tenants,
    List<String> groups,
    List<String> roles,
    boolean canLogout) {

  public CamundaUserInfo {
    authorizedComponents =
        authorizedComponents != null ? List.copyOf(authorizedComponents) : List.of();
    tenants = tenants != null ? List.copyOf(tenants) : List.of();
    groups = groups != null ? List.copyOf(groups) : List.of();
    roles = roles != null ? List.copyOf(roles) : List.of();
  }
}
