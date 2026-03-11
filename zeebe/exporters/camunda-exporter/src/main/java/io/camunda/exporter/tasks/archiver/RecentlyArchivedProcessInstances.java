/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.annotations.VisibleForTesting;
import io.camunda.exporter.tasks.archiver.ArchiveBatch.ProcessInstanceArchiveBatch;
import java.time.Duration;

public class RecentlyArchivedProcessInstances {
  private final Cache<Long, Boolean> recentlyArchived;

  public RecentlyArchivedProcessInstances(final int cacheSize) {
    this(
        Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(cacheSize)
            .build());
  }

  @VisibleForTesting
  public RecentlyArchivedProcessInstances(final Cache<Long, Boolean> recentlyArchived) {
    this.recentlyArchived = recentlyArchived;
  }

  public int getRecentlyArchivedCount() {
    return (int) recentlyArchived.estimatedSize();
  }

  public ProcessInstanceArchiveBatch deduplicate(final ProcessInstanceArchiveBatch batch) {
    final var processInstanceKeys =
        batch.processInstanceKeys().stream()
            .filter(key -> recentlyArchived.getIfPresent(key) == null)
            .toList();
    final var rootProcessInstanceKeys =
        batch.rootProcessInstanceKeys().stream()
            .filter(key -> recentlyArchived.getIfPresent(key) == null)
            .toList();

    return new ProcessInstanceArchiveBatch(
        batch.finishDate(), processInstanceKeys, rootProcessInstanceKeys);
  }

  public void markRecentlyArchived(final ProcessInstanceArchiveBatch batch) {
    batch.processInstanceKeys().forEach(key -> recentlyArchived.put(key, Boolean.TRUE));
    batch.rootProcessInstanceKeys().forEach(key -> recentlyArchived.put(key, Boolean.TRUE));
  }
}
