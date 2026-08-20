/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security.util.definition;

import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import io.camunda.optimize.dto.optimize.TenantDto;
import io.camunda.optimize.service.security.util.tenant.CamundaCCSMTenantAuthorizationService;
import io.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(CCSMCondition.class)
@Component
public class CamundaCCSMDefinitionAuthorizationService
    implements DataSourceDefinitionAuthorizationService {

  private final CamundaCCSMTenantAuthorizationService tenantAuthorizationService;

  public CamundaCCSMDefinitionAuthorizationService(
      final CamundaCCSMTenantAuthorizationService tenantAuthorizationService) {
    this.tenantAuthorizationService = tenantAuthorizationService;
  }

  @Override
  public boolean isAuthorizedToAccessDefinition(
      final String identityId,
      final IdentityType identityType,
      final String definitionKey,
      final DefinitionType definitionType,
      final List<String> tenantIds) {
    return StringUtils.isBlank(definitionKey)
        || tenantAuthorizationService.isAuthorizedToSeeAllTenants(
            identityId, identityType, tenantIds);
  }

  @Override
  public List<TenantDto> resolveAuthorizedTenantsForProcess(
      final String userId,
      final SimpleDefinitionDto definitionDto,
      final List<String> tenantIds,
      final Set<String> engines) {
    final Map<String, TenantDto> allUserAuthorizedTenants =
        tenantAuthorizationService.getCurrentUserTenantAuthorizations();
    return tenantIds.stream()
        .map(allUserAuthorizedTenants::get)
        .filter(Objects::nonNull)
        .sorted(Comparator.comparing(TenantDto::getId, Comparator.naturalOrder()))
        .toList();
  }

  @Override
  public boolean isAuthorizedToAccessDefinition(
      final String userId, final String tenantId, final SimpleDefinitionDto definition) {
    return tenantAuthorizationService.isAuthorizedToSeeTenant(userId, IdentityType.USER, tenantId);
  }

  @Override
  public <T extends DefinitionOptimizeResponseDto> boolean isAuthorizedToAccessDefinition(
      final String userId, final T definition) {
    return tenantAuthorizationService.isAuthorizedToSeeTenant(
        userId, IdentityType.USER, definition.getTenantId());
  }
}
