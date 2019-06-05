/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.engine.GroupDto;
import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionOptimizeDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

  public DefinitionAuthorizationService(final ApplicationAuthorizationService applicationAuthorizationService,
                                        final TenantAuthorizationService tenantAuthorizationService,
                                        final EngineContextFactory engineContextFactory,
                                        final ConfigurationService configurationService) {
    super(engineContextFactory, configurationService);
    this.applicationAuthorizationService = applicationAuthorizationService;
    this.tenantAuthorizationService = tenantAuthorizationService;
  }

  public boolean isAuthorizedToSeeProcessDefinition(final String userId,
                                                    final String processDefinitionKey,
                                                    final String tenantId,
                                                    final String engineAlias) {
    return isAuthorizedToSeeDefinition(
      userId, processDefinitionKey, RESOURCE_TYPE_PROCESS_DEFINITION, tenantId, engineAlias
    );
  }

  public boolean isAuthorizedToSeeDecisionDefinition(final String userId,
                                                     final String decisionDefinitionKey,
                                                     final String tenantId,
                                                     final String engineAlias) {
    return isAuthorizedToSeeDefinition(
      userId, decisionDefinitionKey, RESOURCE_TYPE_DECISION_DEFINITION, tenantId, engineAlias
    );
  }

  public boolean isAuthorizedToSeeDefinition(final String userId,
                                             final DefinitionOptimizeDto definition) {
    if (definition instanceof ProcessDefinitionOptimizeDto) {
      return isAuthorizedToSeeDefinition(
        userId, definition.getKey(), RESOURCE_TYPE_PROCESS_DEFINITION, definition.getTenantId(), definition.getEngine()
      );
    } else if (definition instanceof DecisionDefinitionOptimizeDto) {
      return isAuthorizedToSeeDefinition(
        userId, definition.getKey(), RESOURCE_TYPE_DECISION_DEFINITION, definition.getTenantId(), definition.getEngine()
      );
    } else {
      throw new OptimizeRuntimeException("Unsupported definition type: " + definition.getClass().getSimpleName());
    }
  }

  public boolean isAuthorizedToSeeDefinition(final String userId,
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

}
