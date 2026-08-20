/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security.util.tenant;

import io.camunda.optimize.dto.optimize.IdentityType;
import java.util.List;

public interface DataSourceTenantAuthorizationService {

  boolean isAuthorizedToSeeAllTenants(
      final String identityId, final IdentityType identityType, final List<String> tenantIds);

  boolean isAuthorizedToSeeTenant(
      final String identityId, final IdentityType identityType, final String tenantId);

  boolean isAuthorizedToSeeTenant(
      final String identityId,
      final IdentityType identityType,
      final String tenantId,
      final String dataSourceName);
}
