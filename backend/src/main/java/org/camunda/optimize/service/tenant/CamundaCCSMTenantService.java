/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.tenant;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.service.security.util.tenant.CamundaCCSMTenantAuthorizationService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

@AllArgsConstructor
@Component
@Conditional(CCSMCondition.class)
public class CamundaCCSMTenantService implements TenantService {

  private final CamundaCCSMTenantAuthorizationService tenantAuthorizationService;
  private final ConfigurationService configurationService;

  @Override
  public boolean isAuthorizedToSeeTenant(final String userId, final String tenantId) {
    // In CCSM, we can only retrieve tenant auths for the current user using the user's token
    return tenantAuthorizationService.isAuthorizedToSeeTenant(userId, IdentityType.USER, tenantId);
  }

  @Override
  public List<TenantDto> getTenantsForUser(final String userId) {
    // In CCSM, we can only retrieve tenant auths for the current user using the user's token
    return getTenantsForCurrentUser();

  }

  @Override
  public boolean isMultiTenantEnvironment() {
    return configurationService.isMultiTenancyEnabled();
  }

  private List<TenantDto> getTenantsForCurrentUser() {
    return tenantAuthorizationService.getCurrentUserAuthorizedTenants();
  }
}
