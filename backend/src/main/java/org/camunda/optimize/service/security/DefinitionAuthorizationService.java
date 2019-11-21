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
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.service.TenantService.TENANT_NOT_DEFINED;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.READ_HISTORY_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;

@Component
public class DefinitionAuthorizationService
  extends AbstractCachingAuthorizationService<Map<String, EngineAuthorizations>> {
  private static final List<String> RELEVANT_PERMISSIONS = ImmutableList.of(ALL_PERMISSION, READ_HISTORY_PERMISSION);
  private static final List<String> SINGLETON_LIST_NOT_DEFINED_TENANT =
    Collections.singletonList(TENANT_NOT_DEFINED.getId());

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
      userId, processDefinitionKey, DefinitionType.PROCESS, tenantId, engineAlias
    );
  }

  public boolean isAuthorizedToSeeDecisionDefinition(final String userId,
                                                     final String decisionDefinitionKey,
                                                     final String tenantId,
                                                     final String engineAlias) {
    return isAuthorizedToSeeFullyQualifiedDefinition(
      userId, decisionDefinitionKey, DefinitionType.DECISION, tenantId, engineAlias
    );
  }

  public boolean isAuthorizedToSeeDefinition(final String userId,
                                             final String definitionKey,
                                             final DefinitionType definitionType,
                                             final String tenantId) {
    return isAuthorizedToSeeDefinition(userId, definitionKey, definitionType, Collections.singletonList(tenantId));
  }

  public boolean isAuthorizedToSeeProcessDefinition(final String userId,
                                                    final String definitionKey,
                                                    final List<String> tenantIds) {
    return isAuthorizedToSeeDefinition(userId, definitionKey, DefinitionType.PROCESS, tenantIds);
  }

  public boolean isAuthorizedToSeeDecisionDefinition(final String userId,
                                                     final String definitionKey,
                                                     final List<String> tenantIds) {
    return isAuthorizedToSeeDefinition(userId, definitionKey, DefinitionType.DECISION, tenantIds);
  }

  private boolean isAuthorizedToSeeDefinition(final String userId,
                                              final String definitionKey,
                                              final DefinitionType definitionType,
                                              final List<String> tenantIds) {
    if (definitionKey == null || definitionKey.isEmpty() || definitionType == null) {
      // null key or type provided is considered authorized
      return true;
    }

    // empty tenant list or null should be replaced with the actual not defined tenant singleton list
    // as a lacking tenant entry defaults to the not defined tenant
    final List<String> nullSafeTenants = Optional.ofNullable(tenantIds).orElse(new ArrayList<>());
    final List<String> expectedTenants = nullSafeTenants.size() == 0 ? SINGLETON_LIST_NOT_DEFINED_TENANT : tenantIds;
    // user needs to be authorized for all considered tenants get access
    return filterAuthorizedTenantsForDefinition(
      userId, definitionKey, definitionType, expectedTenants
    ).size() == expectedTenants.size();
  }

  public List<String> filterAuthorizedTenantsForDefinition(final String userId,
                                                           final String definitionKey,
                                                           final DefinitionType definitionType,
                                                           final List<String> tenantIds) {
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
            return definitionExistsForEngine(definitionKey, definitionType, tenantAndEnginePair.getEngine());
          } else {
            return true;
          }
        })
      .filter(tenantAndEnginePair -> isAuthorizedToSeeFullyQualifiedDefinition(
        userId, definitionKey, definitionType, tenantAndEnginePair.getTenantId(), tenantAndEnginePair.getEngine()
      ))
      .map(TenantAndEnginePair::getTenantId)
      .distinct()
      .collect(Collectors.toList());

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
                                                            final DefinitionType definitionType,
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
          groupedEngineAuthorizations, RELEVANT_PERMISSIONS, mapToResourceType(definitionType)
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
    // for each tenant resolve the engines they belong to
    return engineContextFactory.getConfiguredEngines()
      .stream()
      .flatMap(engineContext -> tenantService
        .getTenantsByEngine(engineContext.getEngineAlias()).stream()
        .filter(tenantDto -> tenantIds.contains(tenantDto.getId()))
        .map(tenantDto -> new TenantAndEnginePair(tenantDto.getId(), engineContext.getEngineAlias()))
      )
      .collect(toList());
  }

  private boolean definitionExistsForEngine(final String definitionKey,
                                            final DefinitionType definitionType,
                                            final String engineAlias) {
    switch (definitionType) {
      case PROCESS:
        return processDefinitionReader.getProcessDefinitionByKeyAndEngine(definitionKey, engineAlias).isPresent();
      case DECISION:
        return decisionDefinitionReader.getDecisionDefinitionByKeyAndEngine(definitionKey, engineAlias).isPresent();
      default:
        throw new OptimizeRuntimeException("Unsupported definition type: " + definitionType);
    }
  }

  @Value
  private static class TenantAndEnginePair {
    String tenantId;
    String engine;
  }

}
