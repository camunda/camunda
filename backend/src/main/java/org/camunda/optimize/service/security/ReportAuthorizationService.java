/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import lombok.Value;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.TenantService;
import org.camunda.optimize.service.es.reader.DecisionDefinitionReader;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.TenantService.TENANT_NOT_DEFINED;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;

@Component
public class ReportAuthorizationService {

  private final EngineContextFactory engineContextFactory;
  private final DefinitionAuthorizationService definitionAuthorizationService;
  private final ProcessDefinitionReader processDefinitionReader;
  private final DecisionDefinitionReader decisionDefinitionReader;
  private final TenantService tenantService;

  public ReportAuthorizationService(final EngineContextFactory engineContextFactory,
                                    final DefinitionAuthorizationService definitionAuthorizationService,
                                    final ProcessDefinitionReader processDefinitionReader,
                                    final DecisionDefinitionReader decisionDefinitionReader,
                                    final TenantService tenantService) {
    this.engineContextFactory = engineContextFactory;
    this.definitionAuthorizationService = definitionAuthorizationService;

    this.processDefinitionReader = processDefinitionReader;
    this.decisionDefinitionReader = decisionDefinitionReader;
    this.tenantService = tenantService;
  }

  public boolean isAuthorizedToSeeProcessReport(final String userId,
                                                final String processDefinitionKey,
                                                final List<String> tenantIds) {
    return isAuthorizedToSeeDefinition(userId, processDefinitionKey, RESOURCE_TYPE_PROCESS_DEFINITION, tenantIds);
  }

  public boolean isAuthorizedToSeeDecisionReport(final String userId,
                                                 final String decisionDefinitionKey,
                                                 final List<String> tenantIds) {
    return isAuthorizedToSeeDefinition(userId, decisionDefinitionKey, RESOURCE_TYPE_DECISION_DEFINITION, tenantIds);
  }

  private boolean isAuthorizedToSeeDefinition(final String userId,
                                              final String definitionKey,
                                              final int resourceType,
                                              final List<String> tenantIds) {
    if (definitionKey == null || definitionKey.isEmpty()) {
      return true;
    }

    final List<TenantAndEnginePair> availableTenantEnginePairs = resolveTenantAndEnginePairs(tenantIds);
    return availableTenantEnginePairs
      .stream()
      .filter(
        tenantAndEnginePair -> {
          if (Objects.equals(tenantAndEnginePair.getTenantId(), TENANT_NOT_DEFINED.getId())
            && engineContextFactory.getConfiguredEngines().size() > 1) {
            // in case of the default NONE tenant and a multi-engine scenario,
            // the definition needs to exist in a particular engine to allow it granting access,
            // this ensures an ALL ("*") resource grant in one engine does not grant access to definitions
            // of other engines
            return definitionExistsForEngine(definitionKey, resourceType, tenantAndEnginePair.getEngine());
          } else {
            return true;
          }
        })
      .map(tenantAndEnginePair -> definitionAuthorizationService.isAuthorizedToSeeDefinition(
        userId, definitionKey, resourceType, tenantAndEnginePair.getTenantId(), tenantAndEnginePair.getEngine()
      ))
      // user needs to be authorized for all considered tenant & engine pairs to get access
      .reduce(Boolean::logicalAnd)
      .orElse(false);
  }

  private List<TenantAndEnginePair> resolveTenantAndEnginePairs(final List<String> tenantIds) {
    final List<String> tenantIdsWithAtLeastDefaultTenant = new ArrayList<>(tenantIds);
    if (tenantIdsWithAtLeastDefaultTenant.isEmpty()) {
      tenantIdsWithAtLeastDefaultTenant.add(TENANT_NOT_DEFINED.getId());
    }

    // for each tenant resolve the engines they belong to
    return engineContextFactory.getConfiguredEngines()
      .stream()
      .flatMap(engineContext -> tenantService
        .getTenantsByEngine(engineContext.getEngineAlias()).stream()
        .filter(tenantDto -> tenantIdsWithAtLeastDefaultTenant.contains(tenantDto.getId()))
        .map(tenantDto -> new TenantAndEnginePair(tenantDto.getId(), engineContext.getEngineAlias()))
      )
      .collect(Collectors.toList());
  }

  private boolean definitionExistsForEngine(final String definitionKey,
                                            final int definitionResourceType,
                                            final String engineAlias) {
    switch (definitionResourceType) {
      case RESOURCE_TYPE_PROCESS_DEFINITION:
        return processDefinitionReader.getProcessDefinitionByKeyAndEngine(definitionKey, engineAlias).isPresent();
      case RESOURCE_TYPE_DECISION_DEFINITION:
        return decisionDefinitionReader.getDecisionDefinitionByKeyAndEngine(definitionKey, engineAlias).isPresent();
      default:
        throw new OptimizeRuntimeException("Unsupported definition resource type: " + definitionResourceType);
    }
  }

  @Value
  private static class TenantAndEnginePair {
    String tenantId;
    String engine;
  }

}
