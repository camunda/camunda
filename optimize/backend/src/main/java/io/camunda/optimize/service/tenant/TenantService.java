/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.tenant;

import io.camunda.optimize.dto.optimize.TenantDto;
import java.util.List;

public interface TenantService {

  boolean isAuthorizedToSeeTenant(final String userId, final String tenantId);

  default List<String> getTenantIdsForUser(final String userId) {
    return getTenantsForUser(userId).stream().map(TenantDto::getId).toList();
  }

  boolean isMultiTenantEnvironment();

  List<TenantDto> getTenantsForUser(final String userId);
}
