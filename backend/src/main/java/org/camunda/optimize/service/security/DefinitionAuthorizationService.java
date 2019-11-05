/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import com.google.common.collect.ImmutableList;
import lombok.Value;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.TenantService;
import org.camunda.optimize.service.es.reader.DecisionDefinitionReader;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.TenantService.TENANT_NOT_DEFINED;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.READ_HISTORY_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;

@Component
public class DefinitionAuthorizationService
  extends AbstractCachingAuthorizationService<Map<String, EngineAuthorizations>> {
  private static final List<String> RELEVANT_PERMISSIONS = ImmutableList.of(ALL_PERMISSION, READ_HISTORY_PERMISSION);

  private final ApplicationAuthorizationService applicationAuthorizationService;
  private final TenantAuthorizationService tenantAuthorizationService;
  private final ProcessDefinitionReader processDefinitionReader;
  private final DecisionDefinitionReader decisionDefinitionReader;
  private final TenantService tenantService;

  public DefinitionAuthorizationService(final ApplicationAuthorizationService applicationAuthorizationService,
                                        final TenantAuthorizationService tenantAuthorizationService,
                                        final EngineContextFactory engineContextFactory,
                                        final ConfigurationService configurationService,
                                        final ProcessDefinitionReader processDefinitionReader,
                                        final DecisionDefinitionReader decisionDefinitionReader,
                                        final TenantService tenantService) {
    super(engineContextFactory, configurationService);
    this.applicationAuthorizationService = applicationAuthorizationService;
    this.tenantAuthorizationService = tenantAuthorizationService;
    this.processDefinitionReader = processDefinitionReader;
    this.decisionDefinitionReader = decisionDefinitionReader;
    this.tenantService = tenantService;
  }

  @Override
  protected Map<String, EngineAuthorizations> fetchAuthorizationsForUserId(final String userId) {
    final List<String> authorizedEngines = applicationAuthorizationService.getAuthorizedEngines(userId);
    final Map<String, EngineAuthorizations> result = new HashMap<>();
    engineContextFactory.getConfiguredEngines()
      .forEach(engineContext -> {
        if (authorizedEngines.contains(engineContext.getEngineAlias())) {
          result.put(engineContext.getEngineAlias(), fetchEngineAuthorizations(userId, engineContext));
        } else {
          result.put(engineContext.getEngineAlias(), new EngineAuthorizations(engineContext.getEngineAlias()));
        }
      });
    return result;
  }

  public boolean isAuthorizedToSeeProcessDefinition(final String userId,
                                                    final String processDefinitionKey,
                                                    final String tenantId,
                                                    final String engineAlias) {
    return isAuthorizedToSeeFullyQualifiedDefinition(
      userId, processDefinitionKey, RESOURCE_TYPE_PROCESS_DEFINITION, tenantId, engineAlias
    );
  }

  public boolean isAuthorizedToSeeDecisionDefinition(final String userId,
                                                     final String decisionDefinitionKey,
                                                     final String tenantId,
                                                     final String engineAlias) {
    return isAuthorizedToSeeFullyQualifiedDefinition(
      userId, decisionDefinitionKey, RESOURCE_TYPE_DECISION_DEFINITION, tenantId, engineAlias
    );
  }

  public boolean isAuthorizedToSeeDefinition(final String userId,
                                             final String definitionKey,
                                             final DefinitionType definitionType,
                                             final String tenantId) {
    return isAuthorizedToSeeDefinition(
      userId, definitionKey, mapToResourceType(definitionType), Collections.singletonList(tenantId)
    );
  }

  public boolean isAuthorizedToSeeProcessDefinition(final String userId,
                                                    final String definitionKey,
                                                    final List<String> tenantIds) {
    return isAuthorizedToSeeDefinition(
      userId, definitionKey, RESOURCE_TYPE_PROCESS_DEFINITION, tenantIds
    );
  }

  public boolean isAuthorizedToSeeDecisionDefinition(final String userId,
                                                     final String definitionKey,
                                                     final List<String> tenantIds) {
    return isAuthorizedToSeeDefinition(
      userId, definitionKey, RESOURCE_TYPE_DECISION_DEFINITION, tenantIds
    );
  }

  public boolean isAuthorizedToSeeDefinitionWithAtLeastOneTenantAuthorized(final String userId,
                                                                           final String definitionKey,
                                                                           final DefinitionType definitionType,
                                                                           final List<String> tenantIds) {
    // user needs to be authorized for at least one considered tenant & engine pairs to get access
    return isAuthorizedToSeeDefinition(
      userId, definitionKey, mapToResourceType(definitionType), tenantIds, Boolean::logicalOr
    );
  }

  private boolean isAuthorizedToSeeDefinition(final String userId,
                                              final String definitionKey,
                                              final int resourceType,
                                              final List<String> tenantIds) {
    // user needs to be authorized for all considered tenant & engine pairs to get access
    return isAuthorizedToSeeDefinition(userId, definitionKey, resourceType, tenantIds, Boolean::logicalAnd);
  }

  private boolean isAuthorizedToSeeDefinition(final String userId,
                                             final String definitionKey,
                                             final int resourceType,
                                             final List<String> tenantIds,
                                             final BinaryOperator<Boolean> combineTenantAuthorizationChecks) {
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
      .map(tenantAndEnginePair -> isAuthorizedToSeeFullyQualifiedDefinition(
        userId, definitionKey, resourceType, tenantAndEnginePair.getTenantId(), tenantAndEnginePair.getEngine()
      ))
      .reduce(combineTenantAuthorizationChecks)
      .orElse(false);
  }

  private int mapToResourceType(final DefinitionType definitionType) {
    switch (definitionType) {
      case PROCESS:
        return RESOURCE_TYPE_PROCESS_DEFINITION;
      case DECISION:
        return RESOURCE_TYPE_DECISION_DEFINITION;
      default:
        throw new OptimizeRuntimeException("Unsupported definition type: " + definitionType);
    }
  }

  private boolean isAuthorizedToSeeFullyQualifiedDefinition(final String userId,
                                                            final String definitionKey,
                                                            final int resourceType,
                                                            final String tenantId,
                                                            final String engineAlias) {
    if (!tenantAuthorizationService.isAuthorizedToSeeTenant(userId, tenantId, engineAlias)) {
      return false;
    }

    final Map<String, EngineAuthorizations> authorizationsByEngine = authorizationLoadingCache.get(userId);

    if (authorizationsByEngine == null) {
      return false;
    }

    final ResolvedResourceTypeAuthorizations resourceAuthorizations =
      Optional.of(authorizationsByEngine.get(engineAlias))
        .map(groupedEngineAuthorizations -> resolveResourceAuthorizations(
          groupedEngineAuthorizations, RELEVANT_PERMISSIONS, resourceType
        ))
        .filter(resolvedAuthorizations -> resolvedAuthorizations.isAuthorizedToAccessResource(definitionKey))
        .orElseGet(ResolvedResourceTypeAuthorizations::new);

    return resourceAuthorizations.isAuthorizedToAccessResource(definitionKey);
  }

  private static EngineAuthorizations fetchEngineAuthorizations(final String username,
                                                                final EngineContext engineContext) {
    final List<GroupDto> groups = engineContext.getAllGroupsOfUser(username);
    final List<AuthorizationDto> allAuthorizations = ImmutableList.<AuthorizationDto>builder()
      .addAll(engineContext.getAllProcessDefinitionAuthorizations())
      .addAll(engineContext.getAllDecisionDefinitionAuthorizations())
      .build();
    return mapToEngineAuthorizations(engineContext.getEngineAlias(), allAuthorizations, username, groups);
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
