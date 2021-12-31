/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.engine.TenantSpecificEngineDto;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.importing.engine.service.ImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class BackoffImportMediator<T extends EngineImportIndexHandler<?, ?>, DTO> implements ImportMediator {
  private final BackoffCalculator errorBackoffCalculator = new BackoffCalculator(10, 1000);
  protected Logger logger = LoggerFactory.getLogger(getClass());
  protected ConfigurationService configurationService;
  protected BackoffCalculator idleBackoffCalculator;
  protected T importIndexHandler;
  protected ImportService<DTO> importService;

  @Override
  public CompletableFuture<Void> runImport() {
    final CompletableFuture<Void> importCompleted = new CompletableFuture<>();
    boolean pageIsPresent = importNextPageRetryOnError(importCompleted);
    if (pageIsPresent) {
      idleBackoffCalculator.resetBackoff();
    } else {
      calculateNewDateUntilIsBlocked();
    }
    return importCompleted;
  }

  /**
   * Method is invoked by scheduler once no more jobs are created by factories
   * associated with import process from specific engine
   *
   * @return time to sleep for import process of an engine in general
   */
  public long getBackoffTimeInMs() {
    return idleBackoffCalculator.getTimeUntilNextRetry();
  }

  @Override
  public void resetBackoff() {
    idleBackoffCalculator.resetBackoff();
  }

  @Override
  public boolean canImport() {
    boolean canImportNewPage = idleBackoffCalculator.isReadyForNextRetry();
    logger.debug("can import next page [{}]", canImportNewPage);
    return canImportNewPage;
  }

  public T getImportIndexHandler() {
    return importIndexHandler;
  }

  @Override
  public boolean hasPendingImportJobs() {
    return importService.hasPendingImportJobs();
  }

  @Override
  public void shutdown() {
    importService.shutdown();
  }

  protected abstract boolean importNextPage(Runnable importCompleteCallback);

  private boolean importNextPageRetryOnError(final CompletableFuture<Void> importCompleteCallback) {
    Boolean result = null;
    try {
      while (result == null) {
        try {
          result = importNextPage(() -> importCompleteCallback.complete(null));
        } catch (IllegalStateException e) {
          throw e;
        } catch (final Exception e) {
          if (errorBackoffCalculator.isMaximumBackoffReached()) {
            // if max back-off is reached abort retrying and return true to indicate there is new data
            logger.error("Was not able to import next page and reached max backoff, aborting this run.", e);
            importCompleteCallback.complete(null);
            result = true;
          } else {
            long timeToSleep = errorBackoffCalculator.calculateSleepTime();
            logger.error("Was not able to import next page, retrying after sleeping for {}ms.", timeToSleep, e);
            Thread.sleep(timeToSleep);
          }
        }
      }
    } catch (InterruptedException e) {
      logger.warn("Was interrupted while importing next page.", e);
      Thread.currentThread().interrupt();
      return false;
    }
    errorBackoffCalculator.resetBackoff();

    return result;
  }

  private void calculateNewDateUntilIsBlocked() {
    if (idleBackoffCalculator.isMaximumBackoffReached()) {
      logger.debug(
        "Maximum idle backoff reached, this mediator will not backoff any further than {}ms.",
        idleBackoffCalculator.getMaximumBackoffMilliseconds()
      );
    }
    final long sleepTime = idleBackoffCalculator.calculateSleepTime();
    logger.debug("Was not able to produce a new job, sleeping for [{}] ms", sleepTime);
  }

  protected List<DTO> filterEntitiesFromExcludedTenants(List<DTO> allEntities) {
    // This check for configurationService being null is necessary, otherwise migration tests fail
    Optional<List<String>> excludedIds =
      Optional.ofNullable(configurationService)
        .flatMap(config -> {
          try {
            return config.getExcludedTenants(importIndexHandler.getEngineAlias());
          } catch (OptimizeConfigurationException e) {
            // This happens when the Engine configured in the importIndexHandler doesn't exist in the config. That's
            // not a problem, then we just simply have no excluded Tenants and don't do any filtering
            logger.info(String.format("Engine '%s' could not be found in the configuration",
                                      importIndexHandler.getEngineAlias()));
            return Optional.empty();
          }
        });
    Predicate<DTO> isTenantSpecific = TenantSpecificEngineDto.class::isInstance;
    excludedIds.ifPresent(tenantFilters ->
                            logger.info(String.format("Import filter by tenant is active - Excluding data " +
                                                        "from these tenants: %s", tenantFilters)));
    return excludedIds.map(tenantFilter -> allEntities
        .stream()
        .filter(isTenantSpecific.negate().or(isTenantSpecific.and(
          entity -> !tenantFilter.contains(((TenantSpecificEngineDto)entity).getTenantId().orElse("")))))
        .collect(Collectors.toList()))
      .orElse(allEntities);
  }
}
