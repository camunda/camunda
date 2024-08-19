/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.tenant;

import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.dto.optimize.TenantDto;
import io.camunda.optimize.service.security.util.tenant.CamundaCCSMTenantAuthorizationService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import java.util.List;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(CCSMCondition.class)
public class CamundaCCSMTenantService implements TenantService {

  private final CamundaCCSMTenantAuthorizationService tenantAuthorizationService;
  private final ConfigurationService configurationService;

  public CamundaCCSMTenantService(
      final CamundaCCSMTenantAuthorizationService tenantAuthorizationService,
      final ConfigurationService configurationService) {
    this.tenantAuthorizationService = tenantAuthorizationService;
    this.configurationService = configurationService;
  }

  @Override
  public boolean isAuthorizedToSeeTenant(final String userId, final String tenantId) {
    // In CCSM, we can only retrieve tenant auths for the current user using the user's token
    return tenantAuthorizationService.isAuthorizedToSeeTenant(userId, IdentityType.USER, tenantId);
  }

  @Override
  public boolean isMultiTenantEnvironment() {
    return configurationService.isMultiTenancyEnabled();
  }

  @Override
  public List<TenantDto> getTenantsForUser(final String userId) {
    // In CCSM, we can only retrieve tenant auths for the current user using the user's token
    return getTenantsForCurrentUser();
  }

  private List<TenantDto> getTenantsForCurrentUser() {
    return tenantAuthorizationService.getCurrentUserAuthorizedTenants();
  }
}
