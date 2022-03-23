/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.util.definition;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CamundaCloudCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@Conditional(CamundaCloudCondition.class)
@Component
public class CamundaCloudDefinitionAuthorizationService implements DataSourceDefinitionAuthorizationService {

  private final ConfigurationService configurationService;

  @Override
  public List<TenantDto> resolveAuthorizedTenantsForProcess(final String userId,
                                                            final SimpleDefinitionDto definitionDto,
                                                            final List<String> tenantIds,
                                                            final Set<String> engines) {
    return Collections.singletonList(new TenantDto(null, null, configurationService.getConfiguredZeebe().getName()));
  }

  @Override
  public boolean isAuthorizedToAccessDefinition(final String identityId,
                                                final IdentityType identityType,
                                                final String definitionKey,
                                                final DefinitionType definitionType,
                                                final List<String> tenantIds) {
    return true;
  }

  @Override
  public boolean isAuthorizedToAccessDefinition(final String userId,
                                                final String tenantId,
                                                final SimpleDefinitionDto definition) {
    return true;
  }

  @Override
  public <T extends DefinitionOptimizeResponseDto> boolean isAuthorizedToAccessDefinition(final String userId,
                                                                                          final T definition) {
    return true;
  }

}
