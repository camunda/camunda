/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.tenant;

import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.ZEEBE_DATA_SOURCE;

@Component
@Conditional(CCSaaSCondition.class)
public class CamundaSaaSTenantService implements TenantService {
  @Override
  public boolean isAuthorizedToSeeTenant(final String userId, final String tenantId) {
    return true;
  }

  @Override
  public List<TenantDto> getTenantsForUser(final String userId) {
    return Collections.singletonList(new TenantDto(ZEEBE_DEFAULT_TENANT_ID, null, ZEEBE_DATA_SOURCE));
  }

  @Override
  public boolean isMultiTenantEnvironment() {
    return false;
  }
}
