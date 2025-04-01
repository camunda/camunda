/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.entity;

import io.camunda.search.entities.RoleEntity;
import io.camunda.service.TenantServices.TenantDTO;
import java.io.Serializable;
import java.util.List;

public record AuthenticationContext(
    String username,
    List<RoleEntity> roles,
    List<String> authorizedApplications,
    List<TenantDTO> tenants,
    List<String> groups)
    implements Serializable {}
