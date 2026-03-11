/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.camunda.exporter.tasks.archiver.ArchiveBatch.ProcessInstanceArchiveBatch;
import java.time.Duration;

public class RecentlyArchivedProcessInstances {
  private final Cache<Long, Boolean> recentlyArchived;

  public RecentlyArchivedProcessInstances(final int batchSize) {
    this(
        CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(2))
            .maximumSize(5L * batchSize)
            .build());
  }

  @VisibleForTesting
  public RecentlyArchivedProcessInstances(final Cache<Long, Boolean> recentlyArchived) {
    this.recentlyArchived = recentlyArchived;
  }

  public int getRecentlyArchiveCount() {
    return (int) recentlyArchived.size();
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
