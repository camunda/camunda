/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.camunda.optimize.dto.engine.GroupDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class IdentityService implements ConfigurationReloadable, SessionListener {
  private static final int CACHE_MAXIMUM_SIZE = 10_000;

  private LoadingCache<String, List<GroupDto>> userGroupsCache;
  private final ApplicationAuthorizationService applicationAuthorizationService;
  private final ConfigurationService configurationService;
  private final EngineContextFactory engineContextFactory;

  public IdentityService(final ApplicationAuthorizationService applicationAuthorizationService,
                         final ConfigurationService configurationService,
                         final EngineContextFactory engineContextFactory) {
    this.applicationAuthorizationService = applicationAuthorizationService;
    this.configurationService = configurationService;
    this.engineContextFactory = engineContextFactory;

    initCache();
  }

  public List<GroupDto> getAllGroupsOfUser(final String userId) {
    return userGroupsCache.get(userId);
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    initCache();
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

  private void initCache() {
    userGroupsCache = Caffeine.newBuilder()
      .maximumSize(CACHE_MAXIMUM_SIZE)
      .expireAfterAccess(configurationService.getTokenLifeTimeMinutes(), TimeUnit.MINUTES)
      .build(this::fetchUserGroups);
  }

  private List<GroupDto> fetchUserGroups(final String userId) {
    final Set<GroupDto> result = new HashSet<>();
    applicationAuthorizationService.getAuthorizedEngines(userId)
      .forEach(engineAlias -> {
        final EngineContext engineContext = engineContextFactory.getConfiguredEngineByAlias(engineAlias);
        result.addAll(engineContext.getAllGroupsOfUser(userId));
      });
    return new ArrayList<>(result);
  }

}
