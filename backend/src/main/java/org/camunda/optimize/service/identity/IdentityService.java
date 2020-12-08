/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.identity;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizationType;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.security.ApplicationAuthorizationService;
import org.camunda.optimize.service.security.IdentityAuthorizationService;
import org.camunda.optimize.service.security.SessionListener;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static java.util.stream.Collectors.toList;

@Component
public class IdentityService implements ConfigurationReloadable, SessionListener {
  private static final int CACHE_MAXIMUM_SIZE = 10_000;
  private static final List<AuthorizationType> SUPERUSER_AUTHORIZATIONS =
    ImmutableList.copyOf(AuthorizationType.values());

  private LoadingCache<String, List<GroupDto>> userGroupsCache;

  private final ApplicationAuthorizationService applicationAuthorizationService;
  private final IdentityAuthorizationService identityAuthorizationService;
  private final ConfigurationService configurationService;
  private final EngineContextFactory engineContextFactory;
  private final UserIdentityCacheService syncedIdentityCache;

  public IdentityService(final ApplicationAuthorizationService applicationAuthorizationService,
                         final IdentityAuthorizationService identityAuthorizationService,
                         final ConfigurationService configurationService,
                         final EngineContextFactory engineContextFactory,
                         final UserIdentityCacheService syncedIdentityCache) {
    this.applicationAuthorizationService = applicationAuthorizationService;
    this.identityAuthorizationService = identityAuthorizationService;
    this.configurationService = configurationService;
    this.engineContextFactory = engineContextFactory;
    this.syncedIdentityCache = syncedIdentityCache;

    initUserGroupCache();
  }

  public void addIdentity(final IdentityWithMetadataResponseDto identity) {
    syncedIdentityCache.addIdentity(identity);
  }

  public boolean isSuperUserIdentity(final String userId) {
    return configurationService.getSuperUserIds().contains(userId);
  }

  public List<AuthorizationType> getUserAuthorizations(final String userId) {
    if (isSuperUserIdentity(userId)) {
      return SUPERUSER_AUTHORIZATIONS;
    }
    return Collections.emptyList();
  }

  public List<GroupDto> getAllGroupsOfUser(final String userId) {
    return userGroupsCache.get(userId);
  }

  public Optional<IdentityWithMetadataResponseDto> getIdentityWithMetadataForId(final String userOrGroupId) {
    return getUserById(userOrGroupId)
      .map(userDto -> (IdentityWithMetadataResponseDto) userDto)
      .map(Optional::of)
      .orElseGet(() -> Optional.ofNullable(getGroupById(userOrGroupId).orElse(null)));
  }

  public Optional<IdentityWithMetadataResponseDto> getIdentityWithMetadataForIdAsUser(final String userId,
                                                                                      final String userOrGroupId) {
    return getUserById(userOrGroupId)
      .map(userDto -> (IdentityWithMetadataResponseDto) userDto)
      .map(Optional::of)
      .orElseGet(() -> Optional.ofNullable(getGroupById(userOrGroupId).orElse(null)))
      .map(identityDto -> {
        if (!isUserAuthorizedToAccessIdentity(userId, identityDto)) {
          throw new ForbiddenException(String.format(
            "The user with ID %s is not authorized to access the identity with ID %s", userId, userOrGroupId
          ));
        }
        return identityDto;
      });
  }

  public Optional<UserDto> getUserById(final String userId) {
    return syncedIdentityCache.getUserIdentityById(userId)
      .map(Optional::of)
      .orElseGet(
        () -> {
          if (applicationAuthorizationService.isUserAuthorizedToAccessOptimize(userId)) {
            final Optional<UserDto> userDto = engineContextFactory.getConfiguredEngines().stream()
              .map(engineContext -> engineContext.getUserById(userId))
              .filter(Optional::isPresent)
              .map(Optional::get)
              .findFirst();
            userDto.ifPresent(this::addIdentity);
            return userDto;
          } else {
            return Optional.empty();
          }
        }
      );
  }

  public Optional<GroupDto> getGroupById(final String groupId) {
    return syncedIdentityCache.getGroupIdentityById(groupId)
      .map(Optional::of)
      .orElseGet(
        () -> {
          if (applicationAuthorizationService.isGroupAuthorizedToAccessOptimize(groupId)) {
            final Optional<GroupDto> groupDto = engineContextFactory.getConfiguredEngines().stream()
              .map(engineContext -> engineContext.getGroupById(groupId))
              .filter(Optional::isPresent)
              .map(Optional::get)
              .findFirst();
            groupDto.ifPresent(this::addIdentity);
            return groupDto;
          } else {
            return Optional.empty();
          }
        }
      );
  }

  public IdentitySearchResultResponseDto searchForIdentitiesAsUser(final String userId,
                                                                   final String searchString,
                                                                   final int maxResults) {
    final List<IdentityWithMetadataResponseDto> filteredIdentities = new ArrayList<>();
    IdentitySearchResultResponseDto result = syncedIdentityCache.searchIdentities(
      searchString, IdentityType.values(), maxResults
    );
    while (!result.getResult().isEmpty()
      && filteredIdentities.size() < maxResults) {
      // continue searching until either the maxResult number of hits has been found or
      // the end of the cache has been reached
      filteredIdentities.addAll(filterIdentitySearchResultByUserAuthorizations(userId, result));
      result = syncedIdentityCache.searchIdentitiesAfter(searchString, IdentityType.values(), maxResults, result);
    }
    return new IdentitySearchResultResponseDto(result.getTotal(), filteredIdentities);
  }

  private List<IdentityWithMetadataResponseDto> filterIdentitySearchResultByUserAuthorizations(
    final String userId,
    final IdentitySearchResultResponseDto result) {
    return result.getResult()
      .stream()
      .filter(identity -> isUserAuthorizedToAccessIdentity(userId, identity))
      .collect(toList());
  }

  public boolean isUserAuthorizedToAccessIdentity(final String userId, final IdentityDto identity) {
    return identityAuthorizationService.isUserAuthorizedToSeeIdentity(userId, identity.getType(), identity.getId());
  }

  public void validateUserAuthorizedToAccessRoleOrFail(final String userId, final IdentityDto identityDto) {
    if (!isUserAuthorizedToAccessIdentity(userId, identityDto)) {
      throw new ForbiddenException(
        String.format(
          "User with ID %s is not authorized to access identity with ID %s",
          userId,
          identityDto.getId()
        )
      );
    }
  }

  public boolean doesIdentityExist(final IdentityDto identity) {
    return getIdentityWithMetadataForId(identity.getId()).isPresent();
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

  public Optional<String> getIdentityNameById(final String identityId) {
    Optional<? extends IdentityWithMetadataResponseDto> identityDto = getIdentityWithMetadataForId(identityId);
    return identityDto.map(IdentityWithMetadataResponseDto::getName);
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
