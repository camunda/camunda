/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.identity;

import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
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
import org.camunda.optimize.service.util.configuration.engine.IdentityCacheConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

public abstract class AbstractIdentityCache extends AbstractScheduledService implements ConfigurationReloadable {
  protected final Logger log = LoggerFactory.getLogger(this.getClass());

  private final Supplier<IdentityCacheConfiguration> cacheConfigurationSupplier;
  private final BackoffCalculator backoffCalculator;
  private final List<IdentityCacheSyncListener> identityCacheSyncListeners;
  private SearchableIdentityCache activeIdentityCache;
  private CronExpression cronExpression;

  protected AbstractIdentityCache(final Supplier<IdentityCacheConfiguration> cacheConfigurationSupplier,
                                  final List<IdentityCacheSyncListener> identityCacheSyncListeners,
                                  final BackoffCalculator backoffCalculator) {
    this.cacheConfigurationSupplier = cacheConfigurationSupplier;
    this.identityCacheSyncListeners = identityCacheSyncListeners;
    this.backoffCalculator = backoffCalculator;
    this.activeIdentityCache = new SearchableIdentityCache(() -> this.getCacheConfiguration().getMaxEntryLimit());
  }

  protected abstract void populateCache(final SearchableIdentityCache newIdentityCache);

  protected abstract String getCacheLabel();

  public IdentityCacheConfiguration getCacheConfiguration() {
    return cacheConfigurationSupplier.get();
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    this.cronExpression = evaluateCronExpression();
    resetCache();
  }

  protected abstract CronExpression evaluateCronExpression();

  @PostConstruct
  public void init() {
    log.info("Initializing {} identity sync.", getCacheLabel());
    getCacheConfiguration().validate();
    this.cronExpression = evaluateCronExpression();
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
      log.error("Could not sync {} identities, there was an error.", getCacheLabel(), ex);
    }
  }

  public void syncIdentitiesWithRetry() {
    synchronized (this) {
      final OffsetDateTime stopRetryingTime = Optional
        .ofNullable(cronExpression.next(LocalDateUtil.getCurrentDateTime()))
        .orElseThrow(() -> new OptimizeRuntimeException("Could not calculate next cron temporal."))
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
              getCacheLabel(), e
            );
            shouldRetry = false;
          } else {
            long timeToSleep = backoffCalculator.calculateSleepTime();
            log.error(
              "Error while syncing {} identities. Will retry in {} millis",
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
        () -> getCacheConfiguration().getMaxEntryLimit()
      );
      populateCache(newIdentityCache);
      replaceActiveCache(newIdentityCache);
      notifyCacheListeners(newIdentityCache);
    } catch (MaxEntryLimitHitException e) {
      log.error(
        "Could not synchronize {} identity cache as the limit of {} records was reached on refresh.\n {}",
        getCacheLabel(),
        IdentityCacheConfiguration.Fields.maxEntryLimit,
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
        getCacheConfiguration().getMaxEntryLimit(),
        createIncreaseCacheLimitErrorMessage()
      );
    }
  }

  public Optional<IdentityWithMetadataResponseDto> getIdentityByIdAndType(final String id, final IdentityType type) {
    return activeIdentityCache.getIdentityByIdAndType(id, type);
  }

  public Optional<UserDto> getUserIdentityById(final String id) {
    return activeIdentityCache.getUserIdentityById(id);
  }

  public List<UserDto> getUserIdentitiesById(final Collection<String> ids) {
    return activeIdentityCache
      .getIdentities(ids.stream().map(id -> new IdentityDto(id, IdentityType.USER)).collect(Collectors.toSet()))
      .stream()
      .filter(UserDto.class::isInstance)
      .map(UserDto.class::cast)
      .collect(toList());
  }

  public List<UserDto> getUsersByEmail(final List<String> emails) {
    return activeIdentityCache.getUsersByEmail(emails);
  }

  public Optional<GroupDto> getGroupIdentityById(final String id) {
    return activeIdentityCache.getGroupIdentityById(id);
  }

  public List<GroupDto> getCandidateGroupIdentitiesById(final Collection<String> ids) {
    return activeIdentityCache
      .getIdentities(ids.stream().map(id -> new IdentityDto(id, IdentityType.GROUP)).collect(Collectors.toSet()))
      .stream()
      .filter(GroupDto.class::isInstance)
      .map(GroupDto.class::cast)
      .collect(toList());
  }

  public List<IdentityWithMetadataResponseDto> getIdentities(final Collection<IdentityDto> identities) {
    return activeIdentityCache.getIdentities(identities);
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

  public IdentitySearchResultResponseDto searchAmongIdentitiesWithIds(final String terms,
                                                                      final Collection<String> identityIds,
                                                                      final IdentityType[] identityTypes,
                                                                      final int resultLimit) {
    return activeIdentityCache.searchAmongIdentitiesWithIds(terms, identityIds, identityTypes, resultLimit);
  }

  private synchronized void replaceActiveCache(final SearchableIdentityCache newIdentityCache) {
    final SearchableIdentityCache previousIdentityCache = activeIdentityCache;
    this.activeIdentityCache = newIdentityCache;
    previousIdentityCache.close();
  }

  public synchronized void resetCache() {
    if (activeIdentityCache != null) {
      activeIdentityCache.close();
      activeIdentityCache = new SearchableIdentityCache(() -> getCacheConfiguration().getMaxEntryLimit());
    }
  }

  protected SearchableIdentityCache getActiveIdentityCache() {
    return activeIdentityCache;
  }

  @Override
  protected Trigger createScheduleTrigger() {
    return new CronTrigger(this.cronExpression.toString());
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

  private String createIncreaseCacheLimitErrorMessage() {
    return String.format(
      "Please increase %s.%s in the configuration.",
      getCacheConfiguration().getConfigName(),
      IdentityCacheConfiguration.Fields.maxEntryLimit
    );
  }

}
