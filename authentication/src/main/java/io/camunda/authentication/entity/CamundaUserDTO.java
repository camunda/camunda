/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.entity;

import io.camunda.security.entity.ClusterMetadata;
import io.camunda.service.TenantServices.TenantDTO;
import java.util.List;
import java.util.Map;

public record CamundaUserDTO(
    String userId,
    Long userKey,
    String displayName,
    String username,
    String email,
    List<String> authorizedApplications,
    List<TenantDTO> tenants,
    List<String> groups,
    List<String> roles,
    String salesPlanType,
    Map<ClusterMetadata.AppName, String> c8Links,
    boolean canLogout) {}
