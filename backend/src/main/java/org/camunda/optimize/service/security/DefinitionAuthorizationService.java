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
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.READ_HISTORY_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;

@Component
public class DefinitionAuthorizationService extends AbstractCachingAuthorizationService<EngineAuthorizations> {
  private static final List<String> RELEVANT_PERMISSIONS = ImmutableList.of(ALL_PERMISSION, READ_HISTORY_PERMISSION);

  private final TenantAuthorizationService tenantAuthorizationService;

  @Autowired
  public DefinitionAuthorizationService(final ApplicationAuthorizationService applicationAuthorizationService,
                                        final TenantAuthorizationService tenantAuthorizationService,
                                        final EngineContextFactory engineContextFactory,
                                        final ConfigurationService configurationService) {
    super(applicationAuthorizationService, engineContextFactory, configurationService);
    this.tenantAuthorizationService = tenantAuthorizationService;
  }

  public boolean isAuthorizedToSeeProcessDefinition(final String userId,
                                                    final String processDefinitionKey,
                                                    final String tenantId) {
    return isAuthorizedToSeeProcessDefinition(
      userId,
      processDefinitionKey,
      Optional.ofNullable(tenantId).map(ImmutableList::of).orElse(ImmutableList.of())
    );
  }

  public boolean isAuthorizedToSeeProcessDefinition(final String userId,
                                                    final String processDefinitionKey,
                                                    final List<String> tenantIds) {
    return isAuthorizedToSeeDefinition(userId, processDefinitionKey, RESOURCE_TYPE_PROCESS_DEFINITION, tenantIds);
  }

  public boolean isAuthorizedToSeeDecisionDefinition(final String userId,
                                                     final String decisionDefinitionKey,
                                                     final String tenantId) {
    return isAuthorizedToSeeDecisionDefinition(
      userId,
      decisionDefinitionKey,
      Optional.ofNullable(tenantId).map(ImmutableList::of).orElse(ImmutableList.of())
    );
  }

  public boolean isAuthorizedToSeeDecisionDefinition(final String userId,
                                                     final String decisionDefinitionKey,
                                                     final List<String> tenantIds) {
    return isAuthorizedToSeeDefinition(userId, decisionDefinitionKey, RESOURCE_TYPE_DECISION_DEFINITION, tenantIds);
  }

  @Override
  protected EngineAuthorizations fetchAuthorizationsForUserId(final String userId) {
    EngineAuthorizations result = null;
    for (EngineContext engineContext : engineContextFactory.getConfiguredEngines()) {
      if (applicationAuthorizationService.isAuthorizedToAccessOptimize(userId, engineContext)) {
        result = fetchEngineAuthorizations(userId, engineContext);
      }
    }
    return Optional.ofNullable(result)
      .orElseThrow(() -> new RuntimeException("Failed to get authorizations from any engine for user " + userId));
  }

  private static EngineAuthorizations fetchEngineAuthorizations(final String username,
                                                                final EngineContext engineContext) {
    final List<GroupDto> groups = engineContext.getAllGroupsOfUser(username);
    final List<AuthorizationDto> allAuthorizations = ImmutableList.<AuthorizationDto>builder()
      .addAll(engineContext.getAllProcessDefinitionAuthorizations())
      .addAll(engineContext.getAllDecisionDefinitionAuthorizations())
      .build();
    return mapToEngineAuthorizations(allAuthorizations, username, groups);
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

    final ResolvedResourceTypeAuthorizations resourceAuthorizations = Optional
      .ofNullable(authorizationLoadingCache.get(userId))
      .map(groupedEngineAuthorizations -> resolveResourceAuthorizations(
        groupedEngineAuthorizations, RELEVANT_PERMISSIONS, resourceType
      ))
      .orElseGet(ResolvedResourceTypeAuthorizations::new);
    return resourceAuthorizations.isAuthorizedToAccessResource(definitionKey);
  }

}
