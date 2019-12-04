/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import com.google.common.collect.Iterables;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityRestDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultDto;
import org.camunda.optimize.rest.engine.AuthorizedIdentitiesResult;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.engine.IdentitySyncConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.IDENTITY_SYNC_CONFIGURATION;

@Slf4j
@Component
public class SyncedIdentityCacheService extends AbstractScheduledService implements ConfigurationReloadable {

  private static final String ERROR_INCREASE_CACHE_LIMIT = String.format(
    "Please increase %s.%s in the configuration.",
    IDENTITY_SYNC_CONFIGURATION,
    IdentitySyncConfiguration.Fields.maxEntryLimit.name()
  );

  private SearchableIdentityCache activeIdentityCache;

  private final ConfigurationService configurationService;
  private final EngineContextFactory engineContextFactory;

  private List<SyncedIdentityCacheListener> syncedIdentityCacheListeners;

  public SyncedIdentityCacheService(final ConfigurationService configurationService,
                                    final EngineContextFactory engineContextFactory,
                                    final List<SyncedIdentityCacheListener> syncedIdentityCacheListeners) {
    this.configurationService = configurationService;
    this.engineContextFactory = engineContextFactory;
    this.syncedIdentityCacheListeners = syncedIdentityCacheListeners;
    this.activeIdentityCache = new SearchableIdentityCache(getIdentitySyncConfiguration().getMaxEntryLimit());
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    resetCache();
  }

  @PostConstruct
  public void init() {
    log.info("Initializing user sync.");
    final IdentitySyncConfiguration identitySyncConfiguration = getIdentitySyncConfiguration();
    identitySyncConfiguration.validate();
    startSchedulingUserSync();
  }

  public synchronized void startSchedulingUserSync() {
    log.info("Scheduling User Sync");
    final boolean wasScheduled = startScheduling();
    if (wasScheduled) {
      this.taskScheduler.submit(this::synchronizeIdentities);
    }
  }

  @PreDestroy
  public synchronized void stopSchedulingUserSync() {
    log.info("Stop scheduling user sync.");
    stopScheduling();
  }

  @Override
  protected void run() {
    synchronizeIdentities();
  }

  public synchronized void synchronizeIdentities() {
    try {
      final SearchableIdentityCache newIdentityCache = new SearchableIdentityCache(
        getIdentitySyncConfiguration().getMaxEntryLimit()
      );
      engineContextFactory.getConfiguredEngines()
        .forEach(engineContext -> populateAllAuthorizedIdentitiesForEngineToCache(engineContext, newIdentityCache));
      replaceActiveCache(newIdentityCache);
      for (SyncedIdentityCacheListener syncedIdentityCacheListener : syncedIdentityCacheListeners) {
        try {
          syncedIdentityCacheListener.onFinishIdentitySync(newIdentityCache);
        } catch (Exception e) {
          log.error(
            "Error while calling listener {} after identitySync.",
            syncedIdentityCacheListener.getClass().getSimpleName()
          );
        }
      }
    } catch (MaxEntryLimitHitException e) {
      log.error(String.format(
        "Could not synchronize identity cache as the limit of %s records was reached on refresh.\n"
          + ERROR_INCREASE_CACHE_LIMIT,
        IdentitySyncConfiguration.Fields.maxEntryLimit.name()
      ));
    }
  }

  public void addIdentity(final IdentityRestDto identity) {
    try {
      activeIdentityCache.addIdentity(identity);
    } catch (MaxEntryLimitHitException e) {
      log.warn(
        String.format(
          "Identity [%s] could not be added to active identity cache as the limit of %s records was reached.\n"
            + ERROR_INCREASE_CACHE_LIMIT,
          identity,
          getIdentitySyncConfiguration().getMaxEntryLimit()
        )
      );
    }
  }

  public Optional<UserDto> getUserIdentityById(final String id) {
    return activeIdentityCache.getUserIdentityById(id);
  }

  public Optional<GroupDto> getGroupIdentityById(final String id) {
    return activeIdentityCache.getGroupIdentityById(id);
  }

  public IdentitySearchResultDto searchIdentities(final String terms, final int resultLimit) {
    return activeIdentityCache.searchIdentities(terms, resultLimit);
  }

  private synchronized void replaceActiveCache(final SearchableIdentityCache newIdentityCache) {
    final SearchableIdentityCache previousIdentityCache = activeIdentityCache;
    this.activeIdentityCache = newIdentityCache;
    previousIdentityCache.close();
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
        && !identityCache.getGroupIdentityById(groupDto.getId()).isPresent()
    );

    // collect all members of revoked groups to exclude them when adding all other users
    final Set<String> userIdsOfRevokedGroupMembers = authorizedIdentities.getRevokedGroupIds().stream()
      .flatMap(revokedGroupId -> {
        final Set<IdentityRestDto> currentGroupIdentities = new HashSet<>();
        consumeIdentitiesInBatches(
          currentGroupIdentities::addAll,
          (pageStartIndex, pageLimit) -> engineContext.fetchPageOfUsers(pageStartIndex, pageLimit, revokedGroupId),
          userDto -> true
        );
        return currentGroupIdentities.stream();
      })
      .map(IdentityRestDto::getId)
      .collect(Collectors.toSet());

    // then iterate all users... that's the price you pay for global access
    consumeIdentitiesInBatches(
      identityCache::addIdentities,
      engineContext::fetchPageOfUsers,
      userDto ->
        // @formatter:off
        // add them if not already present
        !identityCache.getUserIdentityById(userDto.getId()).isPresent()
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
    if (authorizedIdentities.getGrantedGroupIds().size() > 0) {
      // add all granted groups (as group grant wins over group revoke)
      // https://docs.camunda.org/manual/7.11/user-guide/process-engine/authorization-service/#authorization-precedence
      final Set<String> grantedGroupIdsNotYetImported = authorizedIdentities.getGrantedGroupIds().stream()
        .filter(userId -> !identityCache.getGroupIdentityById(userId).isPresent())
        .collect(Collectors.toSet());
      Iterables.partition(grantedGroupIdsNotYetImported, getIdentitySyncConfiguration().getMaxPageSize())
        .forEach(userIdBatch -> identityCache.addIdentities(engineContext.getGroupsById(userIdBatch)));

      // add all members of the authorized groups (as group grants win over group revoke) except explicit revoked users
      authorizedIdentities.getGrantedGroupIds()
        .forEach(groupId -> consumeIdentitiesInBatches(
          identityCache::addIdentities,
          (pageStartIndex, pageLimit) -> engineContext.fetchPageOfUsers(pageStartIndex, pageLimit, groupId),
          userDto -> !authorizedIdentities.getRevokedUserIds().contains(userDto.getId())
            && !identityCache.getUserIdentityById(userDto.getId()).isPresent()
        ));
    }

    // finally add explicitly granted users, not yet in the cache already
    final Set<String> grantedUserIdsNotYetImported = authorizedIdentities.getGrantedUserIds().stream()
      .filter(userId -> !identityCache.getUserIdentityById(userId).isPresent())
      .collect(Collectors.toSet());
    Iterables.partition(grantedUserIdsNotYetImported, getIdentitySyncConfiguration().getMaxPageSize())
      .forEach(userIdBatch -> identityCache.addIdentities(fetchUsersById(engineContext, userIdBatch)));
  }

  private List<UserDto> fetchUsersById(final EngineContext engineContext, final List<String> userIdBatch) {
    if (getIdentitySyncConfiguration().isIncludeUserMetaData()) {
      return engineContext.getUsersById(userIdBatch);
    } else {
      return userIdBatch.stream().map(UserDto::new).collect(Collectors.toList());
    }
  }

  private <T extends IdentityRestDto> void consumeIdentitiesInBatches(final Consumer<List<IdentityRestDto>> identityBatchConsumer,
                                                                      final GetIdentityPageMethod<T> getIdentityPage,
                                                                      final Predicate<T> identityFilter) {
    final int maxPageSize = getIdentitySyncConfiguration().getMaxPageSize();
    int currentIndex = 0;
    List<T> currentPage;
    do {
      currentPage = getIdentityPage.getPageOfIdentities(currentIndex, maxPageSize);
      currentIndex += currentPage.size();
      final List<IdentityRestDto> identities = currentPage.stream()
        .filter(identityFilter)
        .collect(Collectors.toList());
      identityBatchConsumer.accept(identities);
    } while (currentPage.size() >= maxPageSize);
  }

  private synchronized void resetCache() {
    if (activeIdentityCache != null) {
      activeIdentityCache.close();
      activeIdentityCache = new SearchableIdentityCache(getIdentitySyncConfiguration().getMaxEntryLimit());
    }
  }

  private IdentitySyncConfiguration getIdentitySyncConfiguration() {
    return this.configurationService.getIdentitySyncConfiguration();
  }

  @Override
  protected Trigger getScheduleTrigger() {
    return new CronTrigger(getIdentitySyncConfiguration().getCronTrigger());
  }

  @FunctionalInterface
  private interface GetIdentityPageMethod<T extends IdentityRestDto> {
    List<T> getPageOfIdentities(int pageStartIndex, int pageLimit);
  }
}
