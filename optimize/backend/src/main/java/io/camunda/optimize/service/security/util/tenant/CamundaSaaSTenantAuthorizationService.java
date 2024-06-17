/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security.util.tenant;

import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import java.util.List;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(CCSaaSCondition.class)
@Component
public class CamundaSaaSTenantAuthorizationService implements DataSourceTenantAuthorizationService {

  @Override
  public boolean isAuthorizedToSeeAllTenants(
      final String identityId, final IdentityType identityType, final List<String> tenantIds) {
    return true;
  }

  @Override
  public boolean isAuthorizedToSeeTenant(
      final String identityId, final IdentityType identityType, final String tenantId) {
    return true;
  }

  @Override
  public boolean isAuthorizedToSeeTenant(
      final String identityId,
      final IdentityType identityType,
      final String tenantId,
      final String dataSourceName) {
    return true;
  }
}
