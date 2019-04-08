/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.engine.GroupDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_RESOURCES_RESOURCE_ID;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GLOBAL;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_REVOKE;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.READ_HISTORY_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;

@Component
public class DefinitionAuthorizationService implements SessionListener, ConfigurationReloadable {
  private static final int CACHE_MAXIMUM_SIZE = 1000;

  private final ApplicationAuthorizationService applicationAuthorizationService;
  private final EngineContextFactory engineContextFactory;
  private final ConfigurationService configurationService;

  private LoadingCache<String, DefinitionAuthorizations> authorizationLoadingCache;

  @Autowired
  public DefinitionAuthorizationService(final ApplicationAuthorizationService applicationAuthorizationService,
                                        final EngineContextFactory engineContextFactory,
                                        final ConfigurationService configurationService) {
    this.applicationAuthorizationService = applicationAuthorizationService;
    this.engineContextFactory = engineContextFactory;
    this.configurationService = configurationService;

    initAuthorizationsCache(configurationService);
  }


  public boolean isAuthorizedToSeeProcessDefinition(final String userId, final String processDefinitionKey) {
    return isAuthorizedToSeeDefinition(userId, processDefinitionKey, RESOURCE_TYPE_PROCESS_DEFINITION);
  }

  public boolean isAuthorizedToSeeDecisionDefinition(final String userId, final String decisionDefinitionKey) {
    return isAuthorizedToSeeDefinition(userId, decisionDefinitionKey, RESOURCE_TYPE_DECISION_DEFINITION);
  }

  @Override
  public void onSessionCreateOrRefresh(final String userId) {
    // invalidate to force removal of old entry synchronously
    authorizationLoadingCache.invalidate(userId);
    // trigger eager load of authorizations when new session is created
    authorizationLoadingCache.refresh(userId);
  }

  @Override
  public void onSessionDestroy(final String userId) {
    authorizationLoadingCache.invalidate(userId);
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    initAuthorizationsCache(configurationService);
  }

  // used to clear cache in test context
  @VisibleForTesting
  private void reset() {
    authorizationLoadingCache.invalidateAll();
  }

  private void initAuthorizationsCache(final ConfigurationService configurationService) {
    authorizationLoadingCache = Caffeine.newBuilder()
      .maximumSize(CACHE_MAXIMUM_SIZE)
      .expireAfterAccess(configurationService.getTokenLifeTimeMinutes(), TimeUnit.MINUTES)
      .build(this::fetchDefinitionAuthorizationsForUserId);
  }

  private DefinitionAuthorizations fetchDefinitionAuthorizationsForUserId(final String userId) {
    DefinitionAuthorizations result = null;
    for (EngineContext engineContext : engineContextFactory.getConfiguredEngines()) {
      if (applicationAuthorizationService.isAuthorizedToAccessOptimize(userId, engineContext)) {
        result = retrieveDefinitionAuthorizations(userId, engineContext);
      }
    }
    return Optional.ofNullable(result)
      .orElseThrow(() -> new RuntimeException("Failed to get authorizations from any engine for user " + userId));
  }

  private boolean isAuthorizedToSeeDefinition(final String userId, final String decisionDefinitionKey,
                                              final int resourceType) {
    if (decisionDefinitionKey == null || decisionDefinitionKey.isEmpty()) {
      return true;
    }

    final ResourceDefinitionAuthorizations resourceAuthorizations = buildDefinitionAuthorizations(userId, resourceType);
    return resourceAuthorizations.isAuthorizedToSeeDefinition(decisionDefinitionKey);
  }

  private ResourceDefinitionAuthorizations buildDefinitionAuthorizations(final String userId, final int resourceType) {
    final ResourceDefinitionAuthorizations authorizations = new ResourceDefinitionAuthorizations();

    Optional.ofNullable(authorizationLoadingCache.get(userId))
      .ifPresent(definitionAuthorizations -> {
        // NOTE: the order is essential here to make sure that
        // the revoking of definition permissions works correctly

        // global authorizations
        definitionAuthorizations.getAllDefinitionAuthorizations()
          .forEach(a -> addGloballyAuthorizedDefinition(a, authorizations, resourceType));

        // group authorizations
        addDefinitionAuthorizations(
          definitionAuthorizations.getGroupAuthorizations(), authorizations, resourceType
        );

        // user authorizations
        addDefinitionAuthorizations(
          definitionAuthorizations.getUserAuthorizations(), authorizations, resourceType
        );
      });

    return authorizations;
  }

  private static DefinitionAuthorizations retrieveDefinitionAuthorizations(String username, EngineContext engineContext) {
    final List<GroupDto> groups = engineContext.getAllGroupsOfUser(username);
    final List<AuthorizationDto> allDefinitionAuthorizations = ImmutableList.<AuthorizationDto>builder()
      .addAll(engineContext.getAllProcessDefinitionAuthorizations())
      .addAll(engineContext.getAllDecisionDefinitionAuthorizations())
      .build();

    final List<AuthorizationDto> groupAuthorizations = extractGroupAuthorizations(groups, allDefinitionAuthorizations);
    final List<AuthorizationDto> userAuthorizations = extractUserAuthorizations(username, allDefinitionAuthorizations);

    return new DefinitionAuthorizations(allDefinitionAuthorizations, groupAuthorizations, userAuthorizations);
  }

  private static List<AuthorizationDto> extractGroupAuthorizations(List<GroupDto> groupsOfUser,
                                                            List<AuthorizationDto> allAuthorizations) {
    final Set<String> groupIds = groupsOfUser.stream().map(GroupDto::getId).collect(Collectors.toSet());
    return allAuthorizations
      .stream()
      .filter(a -> groupIds.contains(a.getGroupId()))
      .collect(Collectors.toList());
  }

  private static List<AuthorizationDto> extractUserAuthorizations(String username,
                                                           List<AuthorizationDto> allAuthorizations) {
    return allAuthorizations
      .stream()
      .filter(a -> username.equals(a.getUserId()))
      .collect(Collectors.toList());
  }

  private static void addDefinitionAuthorizations(final List<AuthorizationDto> groupAuthorizations,
                                           final ResourceDefinitionAuthorizations resourceAuthorizations,
                                           final int resourceType) {
    groupAuthorizations.forEach(
      authDto -> removeAuthorizationForAllDefinitions(authDto, resourceAuthorizations, resourceType)
    );
    groupAuthorizations.forEach(
      authDto -> addAuthorizationForAllDefinitions(authDto, resourceAuthorizations, resourceType)
    );
    groupAuthorizations.forEach(
      authDto -> removeAuthorizationForProhibitedDefinition(authDto, resourceAuthorizations, resourceType)
    );
    groupAuthorizations.forEach(
      authDto -> addAuthorizationForDefinition(authDto, resourceAuthorizations, resourceType)
    );
  }

  private static void addGloballyAuthorizedDefinition(final AuthorizationDto authorization,
                                               final ResourceDefinitionAuthorizations resourceAuthorizations,
                                               final int resourceType) {
    boolean hasPermissions = hasCorrectPermissions(authorization);
    boolean globalGrantPermission = authorization.getType() == AUTHORIZATION_TYPE_GLOBAL;
    boolean processDefinitionResourceType = authorization.getResourceType() == resourceType;
    if (hasPermissions && globalGrantPermission && processDefinitionResourceType) {
      String resourceId = authorization.getResourceId();
      if (resourceId.trim().equals(ALL_RESOURCES_RESOURCE_ID)) {
        resourceAuthorizations.grantToSeeAllDefinitions();
      } else if (!resourceId.isEmpty()) {
        resourceAuthorizations.authorizeDefinition(resourceId);
      }
    }
  }

  private static void addAuthorizationForAllDefinitions(final AuthorizationDto authorization,
                                                 final ResourceDefinitionAuthorizations resourceAuthorizations,
                                                 final int resourceType) {
    boolean hasPermissions = hasCorrectPermissions(authorization);
    boolean grantPermission = authorization.getType() == AUTHORIZATION_TYPE_GRANT;
    boolean processDefinitionResourceType = authorization.getResourceType() == resourceType;
    if (hasPermissions && grantPermission && processDefinitionResourceType) {
      String resourceId = authorization.getResourceId();
      if (resourceId.trim().equals(ALL_RESOURCES_RESOURCE_ID)) {
        resourceAuthorizations.grantToSeeAllDefinitions();
      }
    }
  }

  private static void addAuthorizationForDefinition(final AuthorizationDto authorization,
                                             final ResourceDefinitionAuthorizations resourceAuthorizations,
                                             final int resourceType) {
    boolean hasPermissions = hasCorrectPermissions(authorization);
    boolean grantPermission = authorization.getType() == AUTHORIZATION_TYPE_GRANT;
    boolean processDefinitionResourceType = authorization.getResourceType() == resourceType;
    if (hasPermissions && grantPermission && processDefinitionResourceType) {
      String resourceId = authorization.getResourceId();
      if (!resourceId.isEmpty()) {
        resourceAuthorizations.authorizeDefinition(resourceId);
      }
    }
  }

  private static void removeAuthorizationForAllDefinitions(final AuthorizationDto authorization,
                                                    final ResourceDefinitionAuthorizations resourceAuthorizations,
                                                    final int resourceType) {
    boolean hasPermissions = hasCorrectPermissions(authorization);
    boolean revokePermission = authorization.getType() == AUTHORIZATION_TYPE_REVOKE;
    boolean processDefinitionResourceType = authorization.getResourceType() == resourceType;
    if (hasPermissions && revokePermission && processDefinitionResourceType) {
      String resourceId = authorization.getResourceId();
      if (resourceId.trim().equals(ALL_RESOURCES_RESOURCE_ID)) {
        resourceAuthorizations.revokeToSeeAllDefinitions();
      }
    }
  }

  private static void removeAuthorizationForProhibitedDefinition(final AuthorizationDto authorization,
                                                          final ResourceDefinitionAuthorizations resourceAuthorizations,
                                                          final int resourceType) {
    boolean hasPermissions = hasCorrectPermissions(authorization);
    boolean revokePermission = authorization.getType() == AUTHORIZATION_TYPE_REVOKE;
    boolean processDefinitionResourceType = authorization.getResourceType() == resourceType;
    if (hasPermissions && revokePermission && processDefinitionResourceType) {
      String resourceId = authorization.getResourceId();
      if (!resourceId.isEmpty()) {
        resourceAuthorizations.prohibitDefinition(resourceId);
      }
    }
  }

  private static boolean hasCorrectPermissions(final AuthorizationDto authorization) {
    List<String> permissions = authorization.getPermissions();
    return permissions.contains(ALL_PERMISSION) || permissions.contains(READ_HISTORY_PERMISSION);
  }

  private static class ResourceDefinitionAuthorizations {

    private boolean canSeeAll = false;
    private final Set<String> authorizedDefinitions = new HashSet<>();
    private final Set<String> prohibitedDefinitions = new HashSet<>();

    void grantToSeeAllDefinitions() {
      canSeeAll = true;
      prohibitedDefinitions.clear();
      authorizedDefinitions.clear();
    }

    void revokeToSeeAllDefinitions() {
      canSeeAll = false;
      authorizedDefinitions.clear();
      prohibitedDefinitions.clear();
    }

    void authorizeDefinition(final String authorizedDefinition) {
      authorizedDefinitions.add(authorizedDefinition);
      prohibitedDefinitions.remove(authorizedDefinition);
    }

    void prohibitDefinition(final String prohibitedDefinition) {
      prohibitedDefinitions.add(prohibitedDefinition);
      authorizedDefinitions.remove(prohibitedDefinition);
    }

    boolean isAuthorizedToSeeDefinition(final String definition) {
      if (canSeeAll) {
        return !prohibitedDefinitions.contains(definition);
      } else {
        return authorizedDefinitions.contains(definition);
      }
    }
  }

}
