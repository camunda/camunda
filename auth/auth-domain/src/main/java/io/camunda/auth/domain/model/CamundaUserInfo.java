/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.model;

import java.util.List;
import java.util.Map;

/**
 * Represents the current authenticated user's information, including profile, authorization, and
 * membership details.
 */
public record CamundaUserInfo(
    String displayName,
    String username,
    String email,
    List<String> authorizedComponents,
    List<TenantInfo> tenants,
    List<String> groups,
    List<String> roles,
    boolean canLogout,
    Map<String, Object> metadata) {

  public CamundaUserInfo {
    authorizedComponents = authorizedComponents != null ? authorizedComponents : List.of();
    tenants = tenants != null ? tenants : List.of();
    groups = groups != null ? groups : List.of();
    roles = roles != null ? roles : List.of();
    metadata = metadata != null ? metadata : Map.of();
  }
}
