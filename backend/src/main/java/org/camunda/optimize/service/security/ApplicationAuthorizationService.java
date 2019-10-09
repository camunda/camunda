/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.engine.EngineGroupDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ACCESS_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.OPTIMIZE_APPLICATION_RESOURCE_ID;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_APPLICATION;

@Component
public class ApplicationAuthorizationService extends AbstractCachingAuthorizationService<List<String>> {
  private static final List<String> RELEVANT_PERMISSIONS = ImmutableList.of(ALL_PERMISSION, ACCESS_PERMISSION);

  public ApplicationAuthorizationService(final EngineContextFactory engineContextFactory,
                                         final ConfigurationService configurationService) {
    super(engineContextFactory, configurationService);
  }

  @Override
  public void onSessionCreate(final String userId) {
    // noop
    // no reaction to create events as a created session requires authorizations to be fetched already
    // and thus a cache refresh would be redundant
  }

  /**
   * Checks for a given engine if the user is authorized for optimize access.
   *
   * @return True, if and only if there is a global grant (but no user/group
   * revoke) or a group grant (but no user revoke) or a user grant.
   * Notice that this implies that false is returned also if there is no
   * grant nor revoke.
   */
  public boolean isAuthorizedToAccessOptimize(final String userId) {
    authorizationLoadingCache.invalidate(userId);
    final List<String> authorizedEngines = getAuthorizedEngines(userId);
    return !authorizedEngines.isEmpty();
  }

  public List<String> getAuthorizedEngines(final String userId) {
    return Optional
      .ofNullable(authorizationLoadingCache.get(userId))
      .orElseGet(ArrayList::new);
  }

  @Override
  protected List<String> fetchAuthorizationsForUserId(final String userId) {
    final List<String> result = new ArrayList<>();
    for (EngineContext engineContext : engineContextFactory.getConfiguredEngines()) {
      if (isAuthorizedToAccessOptimizeOnEngine(userId, engineContext)) {
        result.add(engineContext.getEngineAlias());
      }
    }
    return result;
  }

  private static boolean isAuthorizedToAccessOptimizeOnEngine(final String username,
                                                              final EngineContext engineContext) {
    final List<EngineGroupDto> groups = engineContext.getAllGroupsOfUser(username);
    final List<AuthorizationDto> allAuthorizations = engineContext.getAllApplicationAuthorizations();
    final ResolvedResourceTypeAuthorizations resolvedApplicationAuthorizations = resolveResourceAuthorizations(
      engineContext.getEngineAlias(),
      allAuthorizations,
      RELEVANT_PERMISSIONS,
      username,
      groups,
      RESOURCE_TYPE_APPLICATION
    );
    return resolvedApplicationAuthorizations.isAuthorizedToAccessResource(OPTIMIZE_APPLICATION_RESOURCE_ID);
  }
}
