/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.entity;

import io.camunda.search.entities.TenantEntity;
import io.camunda.security.entity.ClusterMetadata;
import java.util.List;
import java.util.Map;

public record CamundaUserDTO(
    String displayName,
    String username,
    String email,
    List<String> authorizedComponents,
    List<TenantEntity> tenants,
    List<String> groups,
    List<String> roles,
    String salesPlanType,
    Map<ClusterMetadata.AppName, String> c8Links,
    boolean canLogout) {

  public CamundaUserDTO {
    // initialize with empty collections as default in case null was passed on creation
    authorizedComponents = authorizedComponents != null ? authorizedComponents : List.of();
    tenants = tenants != null ? tenants : List.of();
    groups = groups != null ? groups : List.of();
    roles = roles != null ? roles : List.of();
    c8Links = c8Links != null ? c8Links : Map.of();
  }
}
