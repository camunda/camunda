/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.identity;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.camunda.optimize.dto.optimize.GroupDto;
import io.camunda.optimize.dto.optimize.IdentityDto;
import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import io.camunda.optimize.dto.optimize.UserDto;
import io.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import io.camunda.optimize.rest.engine.EngineContextFactory;
import io.camunda.optimize.service.security.ApplicationAuthorizationService;
import io.camunda.optimize.service.security.IdentityAuthorizationService;
import io.camunda.optimize.service.security.SessionListener;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.CamundaPlatformCondition;
import jakarta.ws.rs.container.ContainerRequestContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Conditional(CamundaPlatformCondition.class)
public class PlatformIdentityService extends AbstractIdentityService implements SessionListener {
  private static final int CACHE_MAXIMUM_SIZE = 10_000;
  private final ApplicationAuthorizationService applicationAuthorizationService;
  private final IdentityAuthorizationService identityAuthorizationService;
  private final EngineContextFactory engineContextFactory;
  private final PlatformUserIdentityCache syncedIdentityCache;
  private LoadingCache<String, List<GroupDto>> userGroupsCache;

  public PlatformIdentityService(
      final ApplicationAuthorizationService applicationAuthorizationService,
      final IdentityAuthorizationService identityAuthorizationService,
      final ConfigurationService configurationService,
      final EngineContextFactory engineContextFactory,
      final PlatformUserIdentityCache syncedIdentityCache) {
    super(configurationService);
    this.applicationAuthorizationService = applicationAuthorizationService;
    this.identityAuthorizationService = identityAuthorizationService;
    this.engineContextFactory = engineContextFactory;
    this.syncedIdentityCache = syncedIdentityCache;
    initUserGroupCache();
  }

  @Override
  public Optional<UserDto> getUserById(final String userId) {
    return syncedIdentityCache
        .getUserIdentityById(userId)
        .map(Optional::of)
        .orElseGet(
            () -> {
              if (applicationAuthorizationService.isUserAuthorizedToAccessOptimize(userId)) {
                final Optional<UserDto> userDto =
                    engineContextFactory.getConfiguredEngines().stream()
                        .map(
                            engineContext ->
                                getIdentityIdIfExistsFromEngine(
                                    engineContext.getEngineAlias(),
                                    userId,
                                    () -> engineContext.getUserById(userId)))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .findFirst();
                userDto.ifPresent(this::addIdentity);
                return userDto;
              } else {
                return Optional.empty();
              }
            });
  }

  @Override
  public Optional<UserDto> getCurrentUserById(
      final String userId, final ContainerRequestContext requestContext) {
    return getUserById(userId);
  }

  @Override
  public Optional<GroupDto> getGroupById(final String groupId) {
    return syncedIdentityCache
        .getGroupIdentityById(groupId)
        .map(Optional::of)
        .orElseGet(
            () -> {
              if (applicationAuthorizationService.isGroupAuthorizedToAccessOptimize(groupId)) {
                final Optional<GroupDto> groupDto =
                    engineContextFactory.getConfiguredEngines().stream()
                        .map(
                            engineContext ->
                                getIdentityIdIfExistsFromEngine(
                                    engineContext.getEngineAlias(),
                                    groupId,
                                    () -> engineContext.getGroupById(groupId)))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .findFirst();
                groupDto.ifPresent(this::addIdentity);
                return groupDto;
              } else {
                return Optional.empty();
              }
            });
  }

  @Override
  public List<GroupDto> getAllGroupsOfUser(final String userId) {
    return userId != null ? userGroupsCache.get(userId) : Collections.emptyList();
  }

  @Override
  public boolean isUserAuthorizedToAccessIdentity(final String userId, final IdentityDto identity) {
    return identityAuthorizationService.isUserAuthorizedToSeeIdentity(
        userId, identity.getType(), identity.getId());
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    super.reloadConfiguration(context);
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

  public void addIdentity(final IdentityWithMetadataResponseDto identity) {
    syncedIdentityCache.addIdentity(identity);
  }

  @Override
  public IdentitySearchResultResponseDto searchForIdentitiesAsUser(
      final String userId,
      final String searchString,
      final int maxResults,
      final boolean excludeUserGroups) {
    final List<IdentityWithMetadataResponseDto> filteredIdentities = new ArrayList<>();
    final IdentityType[] identityTypesToSearch =
        excludeUserGroups ? new IdentityType[] {IdentityType.USER} : IdentityType.values();
    IdentitySearchResultResponseDto result =
        syncedIdentityCache.searchIdentities(searchString, identityTypesToSearch, maxResults);
    while (!result.getResult().isEmpty() && filteredIdentities.size() < maxResults) {
      // continue searching until either the maxResult number of hits has been found or
      // the end of the cache has been reached
      filteredIdentities.addAll(filterIdentitySearchResultByUserAuthorizations(userId, result));
      result =
          syncedIdentityCache.searchIdentitiesAfter(
              searchString, identityTypesToSearch, maxResults, result);
    }
    return new IdentitySearchResultResponseDto(filteredIdentities);
  }

  private void initUserGroupCache() {
    userGroupsCache =
        Caffeine.newBuilder()
            .maximumSize(CACHE_MAXIMUM_SIZE)
            .expireAfterAccess(
                configurationService.getAuthConfiguration().getTokenLifeTimeMinutes(),
                TimeUnit.MINUTES)
            .build(this::fetchUserGroups);
  }

  private void cleanUpUserGroupCache() {
    if (userGroupsCache != null) {
      userGroupsCache.invalidateAll();
    }
  }

  private List<GroupDto> fetchUserGroups(final String userId) {
    final Set<GroupDto> result = new HashSet<>();
    applicationAuthorizationService
        .getAuthorizedEnginesForUser(userId)
        .forEach(
            engineAlias ->
                engineContextFactory
                    .getConfiguredEngineByAlias(engineAlias)
                    .ifPresent(
                        engineContext -> result.addAll(engineContext.getAllGroupsOfUser(userId))));
    return new ArrayList<>(result);
  }

  private <T extends IdentityDto> Optional<T> getIdentityIdIfExistsFromEngine(
      final String engineAlias,
      final String identityId,
      final Supplier<Optional<T>> optionalSupplier) {
    try {
      return optionalSupplier.get();
    } catch (final Exception ex) {
      log.warn("Failed fetching identity with id {} from engine {}.", identityId, engineAlias);
      return Optional.empty();
    }
  }
}
