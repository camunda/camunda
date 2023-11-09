/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.tenant;

import org.camunda.optimize.dto.optimize.TenantDto;

import java.util.List;

public interface TenantService {

  boolean isAuthorizedToSeeTenant(final String userId, final String tenantId);

  default List<String> getTenantIdsForUser(final String userId) {
    return getTenantsForUser(userId).stream().map(TenantDto::getId).toList();
  }

  boolean isMultiTenantEnvironment();

  List<TenantDto> getTenantsForUser(final String userId);

}
