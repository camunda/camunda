/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.engine.GroupDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.es.reader.DecisionDefinitionReader;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.READ_HISTORY_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;

@Component
public class ReportAuthorizationService
  extends AbstractCachingAuthorizationService<Map<String, EngineAuthorizations>> {
  private static final List<String> RELEVANT_PERMISSIONS = ImmutableList.of(ALL_PERMISSION, READ_HISTORY_PERMISSION);

  private final ApplicationAuthorizationService applicationAuthorizationService;
  private final TenantAuthorizationService tenantAuthorizationService;
  private final ProcessDefinitionReader processDefinitionReader;
  private final DecisionDefinitionReader decisionDefinitionReader;

  public ReportAuthorizationService(final ApplicationAuthorizationService applicationAuthorizationService,
                                    final TenantAuthorizationService tenantAuthorizationService,
                                    final EngineContextFactory engineContextFactory,
                                    final ConfigurationService configurationService,
                                    final ProcessDefinitionReader processDefinitionReader,
                                    final DecisionDefinitionReader decisionDefinitionReader) {
    super(engineContextFactory, configurationService);
    this.applicationAuthorizationService = applicationAuthorizationService;
    this.tenantAuthorizationService = tenantAuthorizationService;
    this.processDefinitionReader = processDefinitionReader;
    this.decisionDefinitionReader = decisionDefinitionReader;
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

  private static EngineAuthorizations fetchEngineAuthorizations(final String username,
                                                                final EngineContext engineContext) {
    final List<GroupDto> groups = engineContext.getAllGroupsOfUser(username);
    final List<AuthorizationDto> allAuthorizations = ImmutableList.<AuthorizationDto>builder()
      .addAll(engineContext.getAllProcessDefinitionAuthorizations())
      .addAll(engineContext.getAllDecisionDefinitionAuthorizations())
      .build();
    return mapToEngineAuthorizations(engineContext.getEngineAlias(), allAuthorizations, username, groups);
  }

  private boolean isAuthorizedToSeeDefinition(final String userId,
                                              final String definitionKey,
                                              final int resourceType,
                                              final List<String> tenantIds) {
    if (definitionKey == null || definitionKey.isEmpty()) {
      return true;
    }

    if (!tenantAuthorizationService.isAuthorizedToSeeAllTenants(userId, tenantIds)) {
      return false;
    }

    final Map<String, EngineAuthorizations> authorizationsByEngine = authorizationLoadingCache.get(userId);

    if (authorizationsByEngine == null) {
      return false;
    }

    final ResolvedResourceTypeAuthorizations resourceAuthorizations = authorizationsByEngine.values().stream()
      .map(groupedEngineAuthorizations -> resolveResourceAuthorizations(
        groupedEngineAuthorizations, RELEVANT_PERMISSIONS, resourceType
      ))
      .filter(resolvedAuthorizations -> resolvedAuthorizations.isAuthorizedToAccessResource(definitionKey))
      .filter(resolvedAuthorizations ->
                // if there is only one engine configured, no further checks are needed
                engineContextFactory.getConfiguredEngines().size() == 1
                  // if there are more than one engine we need to verify this key exists in the engine that granted
                  // access as it could be the case an engine grants access to the all resources
                  // while the key actually originates from another engine
                  || definitionExistsForEngine(definitionKey, resourceType, resolvedAuthorizations.getEngine())
      )
      .findFirst()
      .orElseGet(ResolvedResourceTypeAuthorizations::new);

    return resourceAuthorizations.isAuthorizedToAccessResource(definitionKey);
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

}
