/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security.util.definition;

import static io.camunda.optimize.service.tenant.CamundaPlatformTenantService.TENANT_NOT_DEFINED;
import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.toMap;

import io.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import io.camunda.optimize.dto.optimize.TenantDto;
import io.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import io.camunda.optimize.service.tenant.TenantService;
import io.camunda.optimize.service.util.configuration.condition.CamundaPlatformCondition;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Conditional(CamundaPlatformCondition.class)
@Component
public class CamundaPlatformDefinitionAuthorizationService
    implements DataSourceDefinitionAuthorizationService {

  private final EngineDefinitionAuthorizationService engineDefinitionAuthorizationService;
  private final TenantService tenantService;

  @Override
  public boolean isAuthorizedToAccessDefinition(
      final String identityId,
      final IdentityType identityType,
      final String definitionKey,
      final DefinitionType definitionType,
      final List<String> tenantIds) {
    if (StringUtils.isBlank(definitionKey)) {
      return true;
    }
    switch (definitionType) {
      case PROCESS:
        return engineDefinitionAuthorizationService.isAuthorizedToSeeProcessDefinition(
            identityId, identityType, definitionKey, tenantIds);
      case DECISION:
        return engineDefinitionAuthorizationService.isAuthorizedToSeeDecisionDefinition(
            identityId, identityType, definitionKey, tenantIds);
      default:
        throw new IllegalArgumentException("Unsupported definition type: " + definitionType);
    }
  }

  @Override
  public List<TenantDto> resolveAuthorizedTenantsForProcess(
      final String userId,
      final SimpleDefinitionDto definitionDto,
      final List<String> tenantIds,
      final Set<String> engines) {
    // load all authorized tenants at once to speedup mapping
    final Map<String, TenantDto> allAuthorizedTenants = getAuthorizedTenantDtosForUser(userId);

    List<String> tenantIdsToCheck = tenantIds;
    // we want all tenants to be available for shared engine definitions,
    // as technically there can be data for any of them
    final boolean hasNotDefinedTenant = tenantIds.contains(TENANT_NOT_DEFINED.getId());
    if (hasNotDefinedTenant) {
      tenantIdsToCheck =
          mergeTwoCollectionsWithDistinctValues(allAuthorizedTenants.keySet(), tenantIds);
    }

    return engineDefinitionAuthorizationService
        .filterAuthorizedTenantsForDefinition(
            userId,
            IdentityType.USER,
            definitionDto.getKey(),
            definitionDto.getType(),
            tenantIdsToCheck,
            engines)
        .stream()
        // resolve tenantDto for authorized tenantId
        .map(allAuthorizedTenants::get)
        .filter(Objects::nonNull)
        .sorted(Comparator.comparing(TenantDto::getId, Comparator.nullsFirst(naturalOrder())))
        .toList();
  }

  @Override
  public boolean isAuthorizedToAccessDefinition(
      final String userId, final String tenantId, final SimpleDefinitionDto definition) {
    return engineDefinitionAuthorizationService.isAuthorizedToSeeDefinition(
        userId,
        IdentityType.USER,
        definition.getKey(),
        definition.getType(),
        tenantId,
        definition.getEngines());
  }

  @Override
  public <T extends DefinitionOptimizeResponseDto> boolean isAuthorizedToAccessDefinition(
      final String userId, final T definition) {
    switch (definition.getType()) {
      case PROCESS:
        return isAuthorizedToAccessProcessDefinition(
            userId, (ProcessDefinitionOptimizeDto) definition);
      case DECISION:
        return isAuthorizedToAccessDecisionDefinition(
            userId, (DecisionDefinitionOptimizeDto) definition);
      default:
        throw new IllegalArgumentException("Unsupported definition type: " + definition.getType());
    }
  }

  private boolean isAuthorizedToAccessProcessDefinition(
      final String userId, final ProcessDefinitionOptimizeDto processDefinition) {
    if (processDefinition.getDataSource() instanceof EngineDataSourceDto) {
      return engineDefinitionAuthorizationService.isUserAuthorizedToSeeProcessDefinition(
          userId,
          processDefinition.getKey(),
          processDefinition.getTenantId(),
          processDefinition.getDataSource().getName());
    }
    return false;
  }

  private boolean isAuthorizedToAccessDecisionDefinition(
      final String userId, final DecisionDefinitionOptimizeDto decisionDefinition) {
    if (decisionDefinition.getDataSource() instanceof EngineDataSourceDto) {
      return engineDefinitionAuthorizationService.isUserAuthorizedToSeeDecisionDefinition(
          userId,
          decisionDefinition.getKey(),
          decisionDefinition.getTenantId(),
          decisionDefinition.getDataSource().getName());
    }
    return false;
  }

  private static <T> List<T> mergeTwoCollectionsWithDistinctValues(
      final Collection<T> firstCollection, final Collection<T> secondCollection) {
    return Stream.concat(secondCollection.stream(), firstCollection.stream()).distinct().toList();
  }

  private Map<String, TenantDto> getAuthorizedTenantDtosForUser(final String userId) {
    return tenantService.getTenantsForUser(userId).stream()
        .collect(toMap(TenantDto::getId, Function.identity()));
  }
}
