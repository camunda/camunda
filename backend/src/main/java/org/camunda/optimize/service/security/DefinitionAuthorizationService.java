/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import org.camunda.optimize.service.TenantService;
import org.camunda.optimize.service.es.reader.DefinitionReader;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.service.TenantService.TENANT_NOT_DEFINED;

@RequiredArgsConstructor
@Component
public class DefinitionAuthorizationService {
  private final EngineDefinitionAuthorizationService engineDefinitionAuthorizationService;
  private final EventProcessAuthorizationService eventProcessAuthorizationService;
  private final TenantService tenantService;
  private final DefinitionReader definitionReader;

  public List<TenantDto> resolveAuthorizedTenantsForProcess(final String userId,
                                                            final String definitionKey,
                                                            final DefinitionType type,
                                                            final List<String> tenantIds) {
    final Boolean isEventProcess = isEventProcessDefinition(definitionKey);
    return resolveAuthorizedTenantsForProcess(
      userId,
      SimpleDefinitionDto.builder().key(definitionKey).isEventProcess(isEventProcess).type(type).build(),
      tenantIds
    );
  }

  public List<TenantDto> resolveAuthorizedTenantsForProcess(final String userId,
                                                            final SimpleDefinitionDto definitionDto,
                                                            final List<String> tenantIds) {

    if (definitionDto.getIsEventProcess()) {
      return eventProcessAuthorizationService.isAuthorizedToEventProcess(userId, definitionDto.getKey())
        ? Collections.singletonList(TENANT_NOT_DEFINED)
        : Collections.emptyList();
    } else {
      // load all authorized tenants at once to speedup mapping
      final Map<String, TenantDto> allAuthorizedTenants = getAuthorizedTenantDtosForUser(userId);

      List<String> tenantIdsToCheck = tenantIds;
      // we want all tenants to be available for shared engine definitions,
      // as technically there can be data for any of them
      final boolean hasNotDefinedTenant = tenantIds.contains(TENANT_NOT_DEFINED.getId());
      if (hasNotDefinedTenant) {
        tenantIdsToCheck = mergeTwoCollectionsWithDistinctValues(allAuthorizedTenants.keySet(), tenantIds);
      }

      return engineDefinitionAuthorizationService
        .filterAuthorizedTenantsForDefinition(
          userId,
          IdentityType.USER,
          definitionDto.getKey(),
          definitionDto.getType(),
          tenantIdsToCheck
        )
        .stream()
        // resolve tenantDto for authorized tenantId
        .map(allAuthorizedTenants::get)
        .filter(Objects::nonNull)
        .sorted(Comparator.comparing(TenantDto::getId, Comparator.nullsFirst(naturalOrder())))
        .collect(toList());
    }
  }

  public <T extends DefinitionOptimizeDto> boolean isAuthorizedToReadDefinition(final DefinitionType type,
                                                                                final String userId,
                                                                                final T definition) {
    switch (type) {
      case PROCESS:
        return isAuthorizedToReadProcessDefinition(userId, (ProcessDefinitionOptimizeDto) definition);
      case DECISION:
        return isAuthorizedToReadDecisionDefinition(userId, (DecisionDefinitionOptimizeDto) definition);
      default:
        throw new IllegalStateException("Unknown DefinitionType:" + type);
    }
  }

  public boolean isAuthorizedToReadProcessDefinition(final String userId,
                                                     final ProcessDefinitionOptimizeDto processDefinition) {
    return processDefinition.getIsEventBased()
      ? eventProcessAuthorizationService.isAuthorizedToEventProcess(userId, processDefinition.getKey())
      : engineDefinitionAuthorizationService
      .isUserAuthorizedToSeeProcessDefinition(
        userId, processDefinition.getKey(), processDefinition.getTenantId(), processDefinition.getEngine()
      );
  }

  public boolean isAuthorizedToReadDecisionDefinition(final String userId,
                                                      final DecisionDefinitionOptimizeDto decisionDefinition) {
    return engineDefinitionAuthorizationService.isUserAuthorizedToSeeDecisionDefinition(
      userId, decisionDefinition.getKey(), decisionDefinition.getTenantId(), decisionDefinition.getEngine()
    );
  }

  public boolean isAuthorizedToAccessDefinition(final String userId,
                                                final String tenantId,
                                                final SimpleDefinitionDto definition) {
    if (definition.getIsEventProcess()) {
      return eventProcessAuthorizationService.isAuthorizedToEventProcess(userId, definition.getKey());
    } else {
      return engineDefinitionAuthorizationService.isAuthorizedToSeeDefinition(
        userId, IdentityType.USER, definition.getKey(), definition.getType(), tenantId
      );
    }
  }

  public Boolean isEventProcessDefinition(final String key) {
    Optional<DefinitionWithTenantIdsDto> definitionOpt = definitionReader.getDefinition(PROCESS, key);
    return definitionOpt.map(SimpleDefinitionDto::getIsEventProcess).orElse(false);
  }

  private static <T> List<T> mergeTwoCollectionsWithDistinctValues(final Collection<T> firstCollection,
                                                                   final Collection<T> secondCollection) {
    return Stream.concat(secondCollection.stream(), firstCollection.stream())
      .distinct()
      .collect(toList());
  }

  private Map<String, TenantDto> getAuthorizedTenantDtosForUser(final String userId) {
    return tenantService.getTenantsForUser(userId).stream()
      .collect(toMap(
        TenantDto::getId,
        Function.identity()
      ));
  }

}
