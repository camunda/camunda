/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security.util.definition;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.service.util.configuration.CamundaCloudCondition;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
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
  public boolean isAuthorizedToAccessDefinition(final String userId, final DefinitionType type,
                                                final String definitionKey, final List<String> tenantIds) {
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
