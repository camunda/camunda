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
import java.time.Duration;

public class RecentlyArchivedProcessInstances {
  private final Cache<String, Boolean> recentlyArchived;

  public RecentlyArchivedProcessInstances(final int cacheSize) {
    this(
        Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(cacheSize)
            .build());
  }

  @VisibleForTesting
  public RecentlyArchivedProcessInstances(final Cache<String, Boolean> recentlyArchived) {
    this.recentlyArchived = recentlyArchived;
  }

  public int getRecentlyArchivedCount() {
    return (int) recentlyArchived.estimatedSize();
  }

  public ArchiveBatch deduplicate(final ArchiveBatch batch) {
    final var processInstanceKeys =
        batch.ids().stream().filter(key -> recentlyArchived.getIfPresent(key) == null).toList();

    return new ArchiveBatch(batch.finishDate(), processInstanceKeys);
  }

  public void markRecentlyArchived(final ArchiveBatch batch) {
    batch.ids().forEach(key -> recentlyArchived.put(key, Boolean.TRUE));
  }
}
