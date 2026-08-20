/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security.util.definition;

import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT;

import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import io.camunda.optimize.dto.optimize.TenantDto;
import io.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(CCSaaSCondition.class)
@Component
public class CamundaSaaSDefinitionAuthorizationService
    implements DataSourceDefinitionAuthorizationService {

  public CamundaSaaSDefinitionAuthorizationService() {}

  @Override
  public boolean isAuthorizedToAccessDefinition(
      final String identityId,
      final IdentityType identityType,
      final String definitionKey,
      final DefinitionType definitionType,
      final List<String> tenantIds) {
    return true;
  }

  @Override
  public List<TenantDto> resolveAuthorizedTenantsForProcess(
      final String userId,
      final SimpleDefinitionDto definitionDto,
      final List<String> tenantIds,
      final Set<String> engines) {
    return Collections.singletonList(ZEEBE_DEFAULT_TENANT);
  }

  @Override
  public boolean isAuthorizedToAccessDefinition(
      final String userId, final String tenantId, final SimpleDefinitionDto definition) {
    return true;
  }

  @Override
  public <T extends DefinitionOptimizeResponseDto> boolean isAuthorizedToAccessDefinition(
      final String userId, final T definition) {
    return true;
  }
}
