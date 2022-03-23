/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CamundaPlatformCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.util.importing.EngineConstants.ACCESS_PERMISSION;
import static org.camunda.optimize.service.util.importing.EngineConstants.ALL_PERMISSION;
import static org.camunda.optimize.service.util.importing.EngineConstants.OPTIMIZE_APPLICATION_RESOURCE_ID;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_APPLICATION;

@Conditional(CamundaPlatformCondition.class)
@Component
@Slf4j
public class PlatformApplicationAuthorizationService extends AbstractCachingAuthorizationService<List<String>>
  implements ApplicationAuthorizationService {
  private static final List<String> RELEVANT_PERMISSIONS = List.of(ALL_PERMISSION, ACCESS_PERMISSION);

  public PlatformApplicationAuthorizationService(final EngineContextFactory engineContextFactory,
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
  @Override
  public boolean isUserAuthorizedToAccessOptimize(final String userId) {
    userAuthorizationLoadingCache.invalidate(userId);
    final List<String> authorizedEngines = getAuthorizedEnginesForUser(userId);
    return !authorizedEngines.isEmpty();
  }

  @Override
  public boolean isGroupAuthorizedToAccessOptimize(final String groupId) {
    userAuthorizationLoadingCache.invalidate(groupId);
    final List<String> authorizedEngines = getAuthorizedEnginesForGroup(groupId);
    return !authorizedEngines.isEmpty();
  }

  @Override
  public List<String> getAuthorizedEnginesForUser(final String userId) {
    return Optional
      .ofNullable(userAuthorizationLoadingCache.get(userId))
      .orElseGet(ArrayList::new);
  }

  @Override
  public List<String> getAuthorizedEnginesForGroup(final String groupId) {
    return Optional
      .ofNullable(groupAuthorizationLoadingCache.get(groupId))
      .orElseGet(ArrayList::new);
  }

  @Override
  protected List<String> fetchAuthorizationsForUserId(final String userId) {
    final List<String> result = new ArrayList<>();
    final List<EngineContext> failedEngines = new ArrayList<>();
    for (EngineContext engineContext : engineContextFactory.getConfiguredEngines()) {
      try {
        if (isUserAuthorizedToAccessOptimizeOnEngine(userId, engineContext)) {
          result.add(engineContext.getEngineAlias());
        }
      } catch (OptimizeRuntimeException e) {
        log.error(String.format(
          "Unable to check user [%s] authorization for engine [%s}",
          userId,
          engineContext.getEngineAlias()
        ));
        failedEngines.add(engineContext);
      }
    }
    if (failedEngines.containsAll(engineContextFactory.getConfiguredEngines())) {
      String errorMessage = "Failed to fetch user authorizations because all engines are down.";
      log.warn(errorMessage);
      throw new OptimizeRuntimeException(errorMessage);
    }
    return result;
  }

  @Override
  protected List<String> fetchAuthorizationsForGroupId(final String groupId) {
    final List<String> result = new ArrayList<>();
    final List<EngineContext> failedEngines = new ArrayList<>();
    for (EngineContext engineContext : engineContextFactory.getConfiguredEngines()) {
      try {
        if (isGroupAuthorizedToAccessOptimizeOnEngine(groupId, engineContext)) {
          result.add(engineContext.getEngineAlias());
        }
      } catch (OptimizeRuntimeException e) {
        log.error(String.format(
          "Unable to check group [%s] authorization for engine [%s}",
          groupId,
          engineContext.getEngineAlias()
        ));
        failedEngines.add(engineContext);
      }
    }
    if (failedEngines.containsAll(engineContextFactory.getConfiguredEngines())) {
      String errorMessage = "Failed to fetch group authorizations because all engines are down.";
      log.warn(errorMessage);
      throw new OptimizeRuntimeException(errorMessage);
    }
    return result;
  }

  private static boolean isUserAuthorizedToAccessOptimizeOnEngine(
    final String userId,
    final EngineContext engineContext) throws OptimizeRuntimeException {
    final List<AuthorizationDto> allAuthorizationsOfUser = engineContext.getAllApplicationAuthorizationsForUser(userId);
    final ResolvedResourceTypeAuthorizations resolvedApplicationAuthorizations = resolveUserResourceAuthorizations(
      engineContext.getEngineAlias(),
      allAuthorizationsOfUser,
      RELEVANT_PERMISSIONS,
      RESOURCE_TYPE_APPLICATION
    );
    return resolvedApplicationAuthorizations.isAuthorizedToAccessResource(OPTIMIZE_APPLICATION_RESOURCE_ID);
  }

  private static boolean isGroupAuthorizedToAccessOptimizeOnEngine(final String groupId,
                                                                   final EngineContext engineContext) {
    final List<AuthorizationDto> allAuthorizations;
    allAuthorizations = engineContext.getAllApplicationAuthorizations();
    final ResolvedResourceTypeAuthorizations resolvedApplicationAuthorizations = resolveResourceAuthorizations(
      engineContext.getEngineAlias(),
      allAuthorizations,
      RELEVANT_PERMISSIONS,
      Collections.singletonList(groupId),
      RESOURCE_TYPE_APPLICATION
    );
    return resolvedApplicationAuthorizations.isAuthorizedToAccessResource(OPTIMIZE_APPLICATION_RESOURCE_ID);
  }
}
