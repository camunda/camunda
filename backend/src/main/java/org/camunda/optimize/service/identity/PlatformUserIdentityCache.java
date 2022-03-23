/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.identity;

import com.google.common.collect.Iterables;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import org.camunda.optimize.rest.engine.AuthorizedIdentitiesResult;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.SearchableIdentityCache;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CamundaPlatformCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Component
@Conditional(CamundaPlatformCondition.class)
public class PlatformUserIdentityCache extends AbstractPlatformIdentityCache implements UserIdentityCache {
  private final EngineContextFactory engineContextFactory;

  public PlatformUserIdentityCache(final ConfigurationService configurationService,
                                   final EngineContextFactory engineContextFactory,
                                   final List<IdentityCacheSyncListener> identityCacheSyncListeners,
                                   final BackoffCalculator backoffCalculator) {
    super(configurationService::getUserIdentityCacheConfiguration, identityCacheSyncListeners, backoffCalculator);
    this.engineContextFactory = engineContextFactory;
  }

  @Override
  protected String getCacheLabel() {
    return "platform user";
  }

  @Override
  protected void populateCache(final SearchableIdentityCache newIdentityCache) {
    engineContextFactory.getConfiguredEngines()
      .forEach(engineContext -> populateAllAuthorizedIdentitiesForEngineToCache(engineContext, newIdentityCache));
  }

  private void populateAllAuthorizedIdentitiesForEngineToCache(final EngineContext engineContext,
                                                               final SearchableIdentityCache identityCache) {
    final AuthorizedIdentitiesResult authorizedIdentities = engineContext.getApplicationAuthorizedIdentities();

    if (authorizedIdentities.isGlobalOptimizeGrant()) {
      populateGlobalAccessGrantedIdentitiesToCache(engineContext, identityCache, authorizedIdentities);
    } else {
      populateGrantedIdentitiesToCache(engineContext, identityCache, authorizedIdentities);
    }
  }

  private void populateGlobalAccessGrantedIdentitiesToCache(final EngineContext engineContext,
                                                            final SearchableIdentityCache identityCache,
                                                            final AuthorizedIdentitiesResult authorizedIdentities) {
    // add all non revoked groups
    consumeIdentitiesInBatches(
      identityCache::addIdentities,
      engineContext::fetchPageOfGroups,
      groupDto -> !authorizedIdentities.getRevokedGroupIds().contains(groupDto.getId())
        && identityCache.getGroupIdentityById(groupDto.getId()).isEmpty()
    );

    // collect all members of revoked groups to exclude them when adding all other users
    final Set<String> userIdsOfRevokedGroupMembers = authorizedIdentities.getRevokedGroupIds().stream()
      .flatMap(revokedGroupId -> {
        final Set<IdentityWithMetadataResponseDto> currentGroupIdentities = new HashSet<>();
        consumeIdentitiesInBatches(
          currentGroupIdentities::addAll,
          (pageStartIndex, pageLimit) -> engineContext.fetchPageOfUsers(pageStartIndex, pageLimit, revokedGroupId),
          userDto -> true
        );
        return currentGroupIdentities.stream();
      })
      .map(IdentityWithMetadataResponseDto::getId)
      .collect(Collectors.toSet());

    // then iterate all users... that's the price you pay for global access
    consumeIdentitiesInBatches(
      identityCache::addIdentities,
      engineContext::fetchPageOfUsers,
      userDto ->
        // @formatter:off
        // add them if not already present
        identityCache.getUserIdentityById(userDto.getId()).isEmpty()
        // and
        && (
          // if explicitly granted access
          authorizedIdentities.getGrantedUserIds().contains(userDto.getId())
          // or if they are not member of a revoked access group nor revoked by user id
          || (
            !userIdsOfRevokedGroupMembers.contains(userDto.getId())
              && !authorizedIdentities.getRevokedUserIds().contains(userDto.getId())
          )
        )
        // @formatter:on
    );
  }

  private void populateGrantedIdentitiesToCache(final EngineContext engineContext,
                                                final SearchableIdentityCache identityCache,
                                                final AuthorizedIdentitiesResult authorizedIdentities) {
    if (!authorizedIdentities.getGrantedGroupIds().isEmpty()) {
      // add all granted groups (as group grant wins over group revoke)
      // https://docs.camunda.org/manual/7.11/user-guide/process-engine/authorization-service/#authorization-precedence
      final Set<String> grantedGroupIdsNotYetImported = authorizedIdentities.getGrantedGroupIds().stream()
        .filter(groupId -> identityCache.getGroupIdentityById(groupId).isEmpty())
        .collect(Collectors.toSet());
      Iterables.partition(grantedGroupIdsNotYetImported, getCacheConfiguration().getMaxPageSize())
        .forEach(groupIdBatch -> identityCache.addIdentities(fetchGroupsById(engineContext, groupIdBatch)));

      // add all members of the authorized groups (as group grants win over group revoke) except explicit revoked users
      authorizedIdentities.getGrantedGroupIds()
        .forEach(groupId -> consumeIdentitiesInBatches(
          identityCache::addIdentities,
          (pageStartIndex, pageLimit) -> engineContext.fetchPageOfUsers(pageStartIndex, pageLimit, groupId),
          userDto -> !authorizedIdentities.getRevokedUserIds().contains(userDto.getId())
            && identityCache.getUserIdentityById(userDto.getId()).isEmpty()
        ));
    }

    // finally add explicitly granted users, not yet in the cache already
    final Set<String> grantedUserIdsNotYetImported = authorizedIdentities.getGrantedUserIds().stream()
      .filter(userId -> identityCache.getUserIdentityById(userId).isEmpty())
      .collect(Collectors.toSet());
    Iterables.partition(grantedUserIdsNotYetImported, getCacheConfiguration().getMaxPageSize())
      .forEach(userIdBatch -> identityCache.addIdentities(fetchUsersById(engineContext, userIdBatch)));
  }

  private <T extends IdentityWithMetadataResponseDto> void consumeIdentitiesInBatches(
    final Consumer<List<IdentityWithMetadataResponseDto>> identityBatchConsumer,
    final GetIdentityPageMethod<T> getIdentityPage,
    final Predicate<T> identityFilter) {
    final int maxPageSize = getCacheConfiguration().getMaxPageSize();
    int currentIndex = 0;
    List<T> currentPage;
    do {
      currentPage = getIdentityPage.getPageOfIdentities(currentIndex, maxPageSize);
      currentIndex += currentPage.size();
      final List<IdentityWithMetadataResponseDto> identities = currentPage.stream()
        .filter(identityFilter)
        .collect(Collectors.toList());
      identityBatchConsumer.accept(identities);
    } while (currentPage.size() >= maxPageSize);
  }

  @FunctionalInterface
  private interface GetIdentityPageMethod<T extends IdentityWithMetadataResponseDto> {
    List<T> getPageOfIdentities(int pageStartIndex, int pageLimit);
  }
}
