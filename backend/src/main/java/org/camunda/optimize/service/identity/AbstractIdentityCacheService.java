/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.identity;

import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import org.camunda.optimize.service.AbstractScheduledService;
import org.camunda.optimize.service.MaxEntryLimitHitException;
import org.camunda.optimize.service.SearchableIdentityCache;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.engine.IdentitySyncConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public abstract class AbstractIdentityCacheService extends AbstractScheduledService implements ConfigurationReloadable {
  protected final Logger log = LoggerFactory.getLogger(this.getClass());

  private final Supplier<IdentitySyncConfiguration> identitySyncConfigurationSupplier;
  private final BackoffCalculator backoffCalculator;
  private final List<IdentityCacheSyncListener> identityCacheSyncListeners;

  private SearchableIdentityCache activeIdentityCache;
  private CronExpression cronExpression;

  protected AbstractIdentityCacheService(final Supplier<IdentitySyncConfiguration> identitySyncConfigurationSupplier,
                                         final List<IdentityCacheSyncListener> identityCacheSyncListeners,
                                         final BackoffCalculator backoffCalculator) {
    this.identitySyncConfigurationSupplier = identitySyncConfigurationSupplier;
    this.identityCacheSyncListeners = identityCacheSyncListeners;
    this.backoffCalculator = backoffCalculator;
    this.activeIdentityCache = new SearchableIdentityCache(this.getIdentitySyncConfiguration().getMaxEntryLimit());
  }

  public IdentitySyncConfiguration getIdentitySyncConfiguration() {
    return identitySyncConfigurationSupplier.get();
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    initCronExpression();
    resetCache();
  }

  private void initCronExpression() {
    this.cronExpression = CronExpression.parse(getIdentitySyncConfiguration().getCronTrigger());
  }

  @PostConstruct
  public void init() {
    log.info("Initializing {} identity sync.", getCacheLabel());
    getIdentitySyncConfiguration().validate();
    initCronExpression();
    startScheduledSync();
  }

  public synchronized void startScheduledSync() {
    log.info("Scheduling {} identity sync", getCacheLabel());
    final boolean wasScheduled = startScheduling();
    if (wasScheduled) {
      this.taskScheduler.submit(this::syncIdentitiesWithRetry);
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
    } catch (Exception ex) {
      log.error("Could not sync {} identities with the engine, there was an error.", getCacheLabel(), ex);
    }
  }

  public void syncIdentitiesWithRetry() {
    synchronized (this) {
      final OffsetDateTime stopRetryingTime = Optional
        .ofNullable(cronExpression.next(LocalDateUtil.getCurrentDateTime()))
        .orElseThrow(() -> new OptimizeRuntimeException("Could not calculate next cron temporal."))
        .minusSeconds(backoffCalculator.getMaximumBackoffSeconds());
      boolean shouldRetry = true;
      while (shouldRetry) {
        try {
          synchronizeIdentities();
          log.info("Engine {} identity sync complete", getCacheLabel());
          shouldRetry = false;
        } catch (final Exception e) {
          if (LocalDateUtil.getCurrentDateTime().isAfter(stopRetryingTime)) {
            log.error(
              "Could not sync {} identities with the engine. Will stop retrying as next scheduled sync is approaching",
              getCacheLabel(), e
            );
            shouldRetry = false;
          } else {
            long timeToSleep = backoffCalculator.calculateSleepTime();
            log.error(
              "Error while syncing {} identities with the engine. Will retry in {} millis",
              getCacheLabel(), timeToSleep, e
            );
            try {
              this.wait(timeToSleep);
            } catch (InterruptedException ex) {
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
      final SearchableIdentityCache newIdentityCache = new SearchableIdentityCache(
        getIdentitySyncConfiguration().getMaxEntryLimit()
      );
      populateCache(newIdentityCache);
      replaceActiveCache(newIdentityCache);
      notifyCacheListeners(newIdentityCache);
    } catch (MaxEntryLimitHitException e) {
      log.error(
        "Could not synchronize {} identity cache as the limit of {} records was reached on refresh.\n {}",
        getCacheLabel(),
        IdentitySyncConfiguration.Fields.maxEntryLimit.name(),
        createIncreaseCacheLimitErrorMessage()
      );
      throw e;
    }
  }

  public void addIdentity(final IdentityWithMetadataResponseDto identity) {
    try {
      activeIdentityCache.addIdentity(identity);
    } catch (MaxEntryLimitHitException e) {
      log.warn(
        "Identity [{}] could not be added to active {} identity cache as the limit of {} records was reached.\n {}",
        identity,
        getCacheLabel(),
        getIdentitySyncConfiguration().getMaxEntryLimit(),
        createIncreaseCacheLimitErrorMessage()
      );
    }
  }

  public Optional<UserDto> getUserIdentityById(final String id) {
    return activeIdentityCache.getUserIdentityById(id);
  }

  public Optional<GroupDto> getGroupIdentityById(final String id) {
    return activeIdentityCache.getGroupIdentityById(id);
  }

  public IdentitySearchResultResponseDto searchIdentities(final String terms, final int resultLimit) {
    return activeIdentityCache.searchIdentities(terms, resultLimit);
  }

  public IdentitySearchResultResponseDto searchIdentities(final String terms,
                                                          final IdentityType[] identityTypes,
                                                          final int resultLimit) {
    return activeIdentityCache.searchIdentities(terms, identityTypes, resultLimit);
  }

  public IdentitySearchResultResponseDto searchIdentitiesAfter(final String terms,
                                                               final int resultLimit,
                                                               final IdentitySearchResultResponseDto searchAfter) {
    return activeIdentityCache.searchIdentitiesAfter(terms, resultLimit, searchAfter);
  }

  public IdentitySearchResultResponseDto searchIdentitiesAfter(final String terms,
                                                               final IdentityType[] identityTypes,
                                                               final int resultLimit,
                                                               final IdentitySearchResultResponseDto searchAfter) {
    return activeIdentityCache.searchIdentitiesAfter(terms, identityTypes, resultLimit, searchAfter);
  }

  protected abstract String getCacheLabel();

  protected abstract String createIncreaseCacheLimitErrorMessage();

  protected abstract void populateCache(final SearchableIdentityCache newIdentityCache);

  private synchronized void replaceActiveCache(final SearchableIdentityCache newIdentityCache) {
    final SearchableIdentityCache previousIdentityCache = activeIdentityCache;
    this.activeIdentityCache = newIdentityCache;
    previousIdentityCache.close();
  }

  private synchronized void resetCache() {
    if (activeIdentityCache != null) {
      activeIdentityCache.close();
      activeIdentityCache = new SearchableIdentityCache(getIdentitySyncConfiguration().getMaxEntryLimit());
    }
  }

  private void notifyCacheListeners(final SearchableIdentityCache newIdentityCache) {
    for (IdentityCacheSyncListener identityCacheSyncListener : identityCacheSyncListeners) {
      try {
        identityCacheSyncListener.onFinishIdentitySync(newIdentityCache);
      } catch (Exception e) {
        log.error(
          "Error while calling listener {} after identitySync.", identityCacheSyncListener.getClass().getSimpleName()
        );
      }
    }
  }

  @Override
  protected Trigger createScheduleTrigger() {
    return new CronTrigger(getIdentitySyncConfiguration().getCronTrigger());
  }

}
