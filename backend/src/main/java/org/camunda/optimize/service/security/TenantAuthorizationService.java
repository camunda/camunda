/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.engine.GroupDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.READ_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_TENANT;

@Component
public class TenantAuthorizationService extends AbstractCachingAuthorizationService<ResolvedResourceTypeAuthorizations> {
  private static final List<String> RELEVANT_PERMISSIONS = ImmutableList.of(ALL_PERMISSION, READ_PERMISSION);

  private Set<String> defaultTenantIds;

  @Autowired
  public TenantAuthorizationService(final ApplicationAuthorizationService applicationAuthorizationService,
                                    final EngineContextFactory engineContextFactory,
                                    final ConfigurationService configurationService) {
    super(applicationAuthorizationService, engineContextFactory, configurationService);
  }

  public boolean isAuthorizedToSeeAllTenants(final String userId, final List<String> tenantIds) {
    boolean isAuthorized = true;
    for (String tenantId : tenantIds) {
      isAuthorized = isAuthorizedToSeeTenant(userId, tenantId);
      if (!isAuthorized) {
        break;
      }
    }
    return isAuthorized;
  }

  public boolean isAuthorizedToSeeTenant(final String userId, final String tenantId) {
    if (tenantId == null || tenantId.isEmpty() || defaultTenantIds.contains(tenantId)) {
      return true;
    }

    final ResolvedResourceTypeAuthorizations resourceAuthorizations = Optional
      .ofNullable(authorizationLoadingCache.get(userId))
      .orElseGet(ResolvedResourceTypeAuthorizations::new);
    return resourceAuthorizations.isAuthorizedToAccessResource(tenantId);
  }

  @Override
  protected void initState() {
    super.initState();
    initDefaultTenantIds();
  }

  @Override
  protected ResolvedResourceTypeAuthorizations fetchAuthorizationsForUserId(final String userId) {
    ResolvedResourceTypeAuthorizations result = null;
    for (EngineContext engineContext : engineContextFactory.getConfiguredEngines()) {
      if (applicationAuthorizationService.isAuthorizedToAccessOptimize(userId, engineContext)) {
        result = resolveUserAuthorizations(userId, engineContext);
      }
    }
    return Optional.ofNullable(result)
      .orElseThrow(() -> new RuntimeException("Failed to get tenant authorizations from any engine for user " + userId));
  }

  private void initDefaultTenantIds() {
    this.defaultTenantIds = configurationService.getConfiguredEngines().values().stream()
      .filter(engineConfiguration -> engineConfiguration.getDefaultTenantId().isPresent())
      .map(engineConfiguration -> engineConfiguration.getDefaultTenantId().get())
      .collect(ImmutableSet.toImmutableSet());
  }

  private static ResolvedResourceTypeAuthorizations resolveUserAuthorizations(String username,
                                                                              EngineContext engineContext) {
    final List<GroupDto> groups = engineContext.getAllGroupsOfUser(username);
    final List<AuthorizationDto> allAuthorizations = engineContext.getAllTenantAuthorizations();
    return resolveResourceAuthorizations(
      allAuthorizations, RELEVANT_PERMISSIONS, username, groups, RESOURCE_TYPE_TENANT
    );
  }

}
