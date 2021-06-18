/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security.util.tenant;

import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.service.util.configuration.CamundaCloudCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

@Conditional(CamundaCloudCondition.class)
@Component
public class CamundaCloudTenantAuthorizationService implements DataSourceTenantAuthorizationService {

  @Override
  public boolean isAuthorizedToSeeAllTenants(final String identityId, final IdentityType identityType,
                                             final List<String> tenantIds) {
    return true;
  }

  @Override
  public boolean isAuthorizedToSeeTenant(final String identityId, final IdentityType identityType,
                                         final String tenantId) {
    return true;
  }

  @Override
  public boolean isAuthorizedToSeeTenant(final String identityId, final IdentityType identityType,
                                         final String tenantId, final String dataSourceName) {
    return true;
  }

}
