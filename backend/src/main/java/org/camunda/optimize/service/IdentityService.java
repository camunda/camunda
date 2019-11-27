/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.security.ApplicationAuthorizationService;
import org.camunda.optimize.service.security.SessionListener;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class IdentityService implements ConfigurationReloadable, SessionListener {
  private static final int CACHE_MAXIMUM_SIZE = 10_000;

  private LoadingCache<String, List<GroupDto>> userGroupsCache;

  private final ApplicationAuthorizationService applicationAuthorizationService;
  private final ConfigurationService configurationService;
  private final EngineContextFactory engineContextFactory;
  private final SyncedIdentityCacheService syncedIdentityCache;

  public IdentityService(final ApplicationAuthorizationService applicationAuthorizationService,
                         final ConfigurationService configurationService,
                         final EngineContextFactory engineContextFactory,
                         final SyncedIdentityCacheService syncedIdentityCache) {
    this.applicationAuthorizationService = applicationAuthorizationService;
    this.configurationService = configurationService;
    this.engineContextFactory = engineContextFactory;
    this.syncedIdentityCache = syncedIdentityCache;

    initUserGroupCache();
  }

  public void addIdentity(final IdentityDto identity) {

    syncedIdentityCache.addIdentity(identity);
  }

  public boolean isSuperUserIdentity(final String userId) {
    return configurationService.getSuperUserIds().contains(userId);
  }

  public List<GroupDto> getAllGroupsOfUser(final String userId) {
    return userGroupsCache.get(userId);
  }

  public Optional<UserDto> getUserById(final String userId) {
    return syncedIdentityCache.getUserIdentityById(userId)
      .map(Optional::of)
      .orElseGet(() -> engineContextFactory.getConfiguredEngines().stream()
        .map(engineContext -> engineContext.getUserById(userId))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .peek(this::addIdentity)
        .findFirst()
      );
  }

  public Optional<GroupDto> getGroupById(final String groupId) {
    return syncedIdentityCache.getGroupIdentityById(groupId)
      .map(Optional::of)
      .orElseGet(() -> engineContextFactory.getConfiguredEngines().stream()
        .map(engineContext -> engineContext.getGroupById(groupId))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .peek(this::addIdentity)
        .findFirst()
      );
  }

  public IdentitySearchResultDto searchForIdentities(final String searchString, final int maxResults) {
    return syncedIdentityCache.searchIdentities(searchString, maxResults);
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    cleanUpUserGroupCache();
  }

  @Override
  public void onSessionCreate(final String userId) {
    // NOOP
  }

  @Override
  public void onSessionRefresh(final String userId) {
    userGroupsCache.invalidate(userId);
  }

  @Override
  public void onSessionDestroy(final String userId) {
    userGroupsCache.invalidate(userId);
  }

  private void initUserGroupCache() {
    userGroupsCache = Caffeine.newBuilder()
      .maximumSize(CACHE_MAXIMUM_SIZE)
      .expireAfterAccess(configurationService.getTokenLifeTimeMinutes(), TimeUnit.MINUTES)
      .build(this::fetchUserGroups);
  }

  private void cleanUpUserGroupCache() {
    if (userGroupsCache != null) {
      userGroupsCache.invalidateAll();
    }
  }

  private List<GroupDto> fetchUserGroups(final String userId) {
    final Set<GroupDto> result = new HashSet<>();
    applicationAuthorizationService.getAuthorizedEnginesForUser(userId)
      .forEach(engineAlias -> {
        final EngineContext engineContext = engineContextFactory.getConfiguredEngineByAlias(engineAlias);
        result.addAll(engineContext.getAllGroupsOfUser(userId));
      });
    return new ArrayList<>(result);
  }

}
