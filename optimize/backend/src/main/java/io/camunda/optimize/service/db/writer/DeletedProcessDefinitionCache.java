/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import io.camunda.optimize.dto.optimize.query.job.EntityType;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.service.AbstractScheduledService;
import io.camunda.optimize.service.db.reader.JobRegistryReader;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.DeletedProcessDefinitionCacheConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

/**
 * Periodically syncs the set of process definition ids that have a DELETE job registry entry (any
 * status) into memory. Each refresh replaces the cached set wholesale with the newest {@code
 * caches.deletedProcessDefinitions.maxSize} matching entries.
 */
@Component
public class DeletedProcessDefinitionCache extends AbstractScheduledService {

  private static final Logger LOG = LoggerFactory.getLogger(DeletedProcessDefinitionCache.class);

  private final JobRegistryReader jobRegistryReader;
  private final AtomicBoolean initialLoadDone = new AtomicBoolean(false);
  private final int maxSize;
  private final Duration refreshInterval;
  private volatile Set<String> suppressedIds = Set.of();

  public DeletedProcessDefinitionCache(
      final JobRegistryReader jobRegistryReader, final ConfigurationService configurationService) {
    this.jobRegistryReader = jobRegistryReader;
    final DeletedProcessDefinitionCacheConfiguration config =
        configurationService.getCaches().getDeletedProcessDefinitions();
    maxSize = config.getMaxSize();
    refreshInterval = Duration.ofSeconds(config.getRefreshIntervalSeconds());
  }

  @PostConstruct
  public void init() {
    startScheduling();
  }

  @PreDestroy
  public void destroy() {
    stopScheduling();
  }

  public boolean isSuppressed(final String processDefinitionId) {
    ensureInitialLoad();
    return suppressedIds.contains(processDefinitionId);
  }

  private void ensureInitialLoad() {
    if (!initialLoadDone.get()) {
      synchronized (this) {
        if (!initialLoadDone.get()) {
          run();
        }
      }
    }
  }

  @Override
  public void run() {
    try {
      refresh();
    } catch (final Exception e) {
      LOG.error("Failed to refresh deleted process definition cache, will retry on next run.", e);
    } finally {
      initialLoadDone.set(true);
    }
  }

  @Override
  protected Trigger createScheduleTrigger() {
    return new PeriodicTrigger(refreshInterval);
  }

  private void refresh() {
    final List<String> entityIds =
        jobRegistryReader.findNewestEntityIds(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, maxSize);
    if (entityIds.size() >= maxSize) {
      LOG.warn(
          "Deleted process definition cache fetch returned the maximum of [{}] entries; older"
              + " matching job registry entries beyond this batch are not cached, and their"
              + " process definitions will not be suppressed from import.",
          maxSize);
    }
    suppressedIds = Set.copyOf(entityIds);
  }
}
