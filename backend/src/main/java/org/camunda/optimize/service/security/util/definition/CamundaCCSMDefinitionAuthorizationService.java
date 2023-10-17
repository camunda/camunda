/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.util.definition;

import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.service.security.util.tenant.CamundaCCSMTenantAuthorizationService;
import org.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@AllArgsConstructor
@Conditional(CCSMCondition.class)
@Component
public class CamundaCCSMDefinitionAuthorizationService implements DataSourceDefinitionAuthorizationService {

  private final CamundaCCSMTenantAuthorizationService tenantAuthorizationService;

  @Override
  public List<TenantDto> resolveAuthorizedTenantsForProcess(final String userId,
                                                            final SimpleDefinitionDto definitionDto,
                                                            final List<String> tenantIds,
                                                            final Set<String> engines) {
    final Map<String, TenantDto> allUserAuthorizedTenants = tenantAuthorizationService.getCurrentUserTenantAuthorizations();
    return tenantIds
      .stream()
      .map(allUserAuthorizedTenants::get)
      .filter(Objects::nonNull)
      .sorted(Comparator.comparing(TenantDto::getId, Comparator.naturalOrder()))
      .toList();
  }

  @Override
  public boolean isAuthorizedToAccessDefinition(final String identityId,
                                                final IdentityType identityType,
                                                final String definitionKey,
                                                final DefinitionType definitionType,
                                                final List<String> tenantIds) {
    return StringUtils.isBlank(definitionKey) || tenantAuthorizationService.isAuthorizedToSeeAllTenants(
      identityId,
      identityType,
      tenantIds
    );
  }

  @Override
  public boolean isAuthorizedToAccessDefinition(final String userId,
                                                final String tenantId,
                                                final SimpleDefinitionDto definition) {
    return tenantAuthorizationService.isAuthorizedToSeeTenant(userId, IdentityType.USER, tenantId);
  }

  @Override
  public <T extends DefinitionOptimizeResponseDto> boolean isAuthorizedToAccessDefinition(final String userId,
                                                                                          final T definition) {
    return tenantAuthorizationService.isAuthorizedToSeeTenant(userId, IdentityType.USER, definition.getTenantId());
  }

}
