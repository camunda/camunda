/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.identity;

import io.camunda.optimize.dto.optimize.GroupDto;
import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import io.camunda.optimize.dto.optimize.UserDto;
import io.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import io.camunda.optimize.rest.engine.EngineContext;
import io.camunda.optimize.service.AbstractScheduledService;
import io.camunda.optimize.service.SearchableIdentityCache;
import io.camunda.optimize.service.exceptions.MaxEntryLimitHitException;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import io.camunda.optimize.service.util.configuration.engine.IdentityCacheConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;

public abstract class AbstractPlatformIdentityCache extends AbstractScheduledService
    implements ConfigurationReloadable {

  protected final Logger log = LoggerFactory.getLogger(getClass());

  private final Supplier<IdentityCacheConfiguration> cacheConfigurationSupplier;
  private final BackoffCalculator backoffCalculator;
  private final List<IdentityCacheSyncListener> identityCacheSyncListeners;
  private SearchableIdentityCache activeIdentityCache;
  private CronExpression cronExpression;

  protected AbstractPlatformIdentityCache(
      final Supplier<IdentityCacheConfiguration> cacheConfigurationSupplier,
      final List<IdentityCacheSyncListener> identityCacheSyncListeners,
      final BackoffCalculator backoffCalculator) {
    this.cacheConfigurationSupplier = cacheConfigurationSupplier;
    this.identityCacheSyncListeners = identityCacheSyncListeners;
    this.backoffCalculator = backoffCalculator;
    activeIdentityCache =
        new SearchableIdentityCache(() -> getCacheConfiguration().getMaxEntryLimit());
  }

  protected abstract void populateCache(final SearchableIdentityCache newIdentityCache);

  protected abstract String getCacheLabel();

  public IdentityCacheConfiguration getCacheConfiguration() {
    return cacheConfigurationSupplier.get();
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    cronExpression = evaluateCronExpression();
    resetCache();
  }

  protected CronExpression evaluateCronExpression() {
    return CronExpression.parse(getCacheConfiguration().getCronTrigger());
  }

  @PostConstruct
  public void init() {
    log.info("Initializing {} identity sync.", getCacheLabel());
    getCacheConfiguration().validate();
    cronExpression = evaluateCronExpression();
    startScheduledSync();
  }

  public synchronized void startScheduledSync() {
    log.info("Scheduling {} identity sync", getCacheLabel());
    final boolean wasScheduled = startScheduling();
    if (wasScheduled) {
      taskScheduler.submit(this::syncIdentitiesWithRetry);
    }
  }

  @PreDestroy
  public synchronized void stopScheduledSync() {
    log.info("Stop scheduling {} identity sync.", getCacheLabel());
    stopScheduling();
  }

  @Override
  protected void run() {
    try {
      syncIdentitiesWithRetry();
    } catch (final Exception ex) {
      log.error("Could not sync {} identities, there was an error.", getCacheLabel(), ex);
    }
  }

  @Override
  protected Trigger createScheduleTrigger() {
    return new CronTrigger(cronExpression.toString());
  }

  public void syncIdentitiesWithRetry() {
    synchronized (this) {
      final OffsetDateTime stopRetryingTime =
          Optional.ofNullable(cronExpression.next(LocalDateUtil.getCurrentDateTime()))
              .orElseThrow(
                  () -> new OptimizeRuntimeException("Could not calculate next cron temporal."))
              .minusSeconds(backoffCalculator.getMaximumBackoffMilliseconds());
      boolean shouldRetry = true;
      while (shouldRetry) {
        try {
          synchronizeIdentities();
          log.info("{} identity sync complete", getCacheLabel());
          shouldRetry = false;
        } catch (final Exception e) {
          if (LocalDateUtil.getCurrentDateTime().isAfter(stopRetryingTime)) {
            log.error(
                "Could not sync {} identities. Will stop retrying as next scheduled sync is approaching",
                getCacheLabel(),
                e);
            shouldRetry = false;
          } else {
            final long timeToSleep = backoffCalculator.calculateSleepTime();
            log.error(
                "Error while syncing {} identities. Will retry in {} millis",
                getCacheLabel(),
                timeToSleep,
                e);
            try {
              wait(timeToSleep);
            } catch (final InterruptedException ex) {
              log.debug("Thread interrupted during sleep. Continuing.", ex);
              Thread.currentThread().interrupt();
            }
          }
        }
      }
      backoffCalculator.resetBackoff();
    }
  }

  public synchronized void synchronizeIdentities() {
    try {
      final SearchableIdentityCache newIdentityCache =
          new SearchableIdentityCache(() -> getCacheConfiguration().getMaxEntryLimit());
      populateCache(newIdentityCache);
      replaceActiveCache(newIdentityCache);
      notifyCacheListeners(newIdentityCache);
    } catch (final MaxEntryLimitHitException e) {
      log.error(
          "Could not synchronize {} identity cache as the limit of {} records was reached on refresh.\n {}",
          getCacheLabel(),
          IdentityCacheConfiguration.Fields.maxEntryLimit,
          createIncreaseCacheLimitErrorMessage());
      throw e;
    }
  }

  public void addIdentity(final IdentityWithMetadataResponseDto identity) {
    try {
      activeIdentityCache.addIdentity(identity);
    } catch (final MaxEntryLimitHitException e) {
      log.warn(
          "Identity [{}] could not be added to active {} identity cache as the limit of {} records was reached.\n {}",
          identity,
          getCacheLabel(),
          getCacheConfiguration().getMaxEntryLimit(),
          createIncreaseCacheLimitErrorMessage());
    }
  }

  public Optional<UserDto> getUserIdentityById(final String id) {
    return activeIdentityCache.getUserIdentityById(id);
  }

  public Optional<GroupDto> getGroupIdentityById(final String id) {
    return activeIdentityCache.getGroupIdentityById(id);
  }

  public IdentitySearchResultResponseDto searchIdentities(
      final String terms, final int resultLimit) {
    return activeIdentityCache.searchIdentities(terms, resultLimit);
  }

  public IdentitySearchResultResponseDto searchIdentities(
      final String terms, final IdentityType[] identityTypes, final int resultLimit) {
    return activeIdentityCache.searchIdentities(terms, identityTypes, resultLimit);
  }

  public IdentitySearchResultResponseDto searchIdentitiesAfter(
      final String terms,
      final IdentityType[] identityTypes,
      final int resultLimit,
      final IdentitySearchResultResponseDto searchAfter) {
    return activeIdentityCache.searchIdentitiesAfter(
        terms, identityTypes, resultLimit, searchAfter);
  }

  private synchronized void replaceActiveCache(final SearchableIdentityCache newIdentityCache) {
    final SearchableIdentityCache previousIdentityCache = activeIdentityCache;
    activeIdentityCache = newIdentityCache;
    previousIdentityCache.close();
  }

  public synchronized void resetCache() {
    if (activeIdentityCache != null) {
      activeIdentityCache.close();
      activeIdentityCache =
          new SearchableIdentityCache(() -> getCacheConfiguration().getMaxEntryLimit());
    }
  }

  protected SearchableIdentityCache getActiveIdentityCache() {
    return activeIdentityCache;
  }

  protected List<UserDto> fetchUsersById(
      final EngineContext engineContext, final Collection<String> userIdBatch) {
    if (getCacheConfiguration().isIncludeUserMetaData()) {
      final List<UserDto> users = engineContext.getUsersById(userIdBatch);
      final List<String> usersNotInEngine = new ArrayList<>(userIdBatch);
      usersNotInEngine.removeIf(
          userId -> users.stream().anyMatch(user -> user.getId().equals(userId)));
      users.addAll(usersNotInEngine.stream().map(UserDto::new).toList());
      return users;
    } else {
      return userIdBatch.stream().map(UserDto::new).toList();
    }
  }

  protected List<GroupDto> fetchGroupsById(
      final EngineContext engineContext, final Collection<String> groupIdBatch) {
    final List<GroupDto> groups = engineContext.getGroupsById(groupIdBatch);
    final List<String> groupsNotInEngine = new ArrayList<>(groupIdBatch);
    groupsNotInEngine.removeIf(
        groupId -> groups.stream().anyMatch(group -> group.getId().equals(groupId)));
    groups.addAll(groupsNotInEngine.stream().map(GroupDto::new).toList());
    return groups;
  }

  private void notifyCacheListeners(final SearchableIdentityCache newIdentityCache) {
    for (final IdentityCacheSyncListener identityCacheSyncListener : identityCacheSyncListeners) {
      try {
        identityCacheSyncListener.onFinishIdentitySync(newIdentityCache);
      } catch (final Exception e) {
        log.error(
            "Error while calling listener {} after identitySync.",
            identityCacheSyncListener.getClass().getSimpleName());
      }
    }
  }

  private String createIncreaseCacheLimitErrorMessage() {
    return String.format(
        "Please increase %s.%s in the configuration.",
        getCacheConfiguration().getConfigName(), IdentityCacheConfiguration.Fields.maxEntryLimit);
  }
}
