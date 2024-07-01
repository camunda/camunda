/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.tenant;

import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_DATA_SOURCE;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;

import io.camunda.optimize.dto.optimize.TenantDto;
import io.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import java.util.Collections;
import java.util.List;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(CCSaaSCondition.class)
public class CamundaSaaSTenantService implements TenantService {

  @Override
  public boolean isAuthorizedToSeeTenant(final String userId, final String tenantId) {
    return true;
  }

  @Override
  public List<TenantDto> getTenantsForUser(final String userId) {
    return Collections.singletonList(
        new TenantDto(ZEEBE_DEFAULT_TENANT_ID, null, ZEEBE_DATA_SOURCE));
  }

  @Override
  public boolean isMultiTenantEnvironment() {
    return false;
  }
}
