/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecentlyArchivedProcessInstancesTest {
  private final Cache<Long, Boolean> cache = Caffeine.newBuilder().build();
  private final RecentlyArchivedProcessInstances deduplicator =
      new RecentlyArchivedProcessInstances(cache);

  @Test
  public void shouldNotDeduplicateProcessInstanceBatchWhenNothingInCache() {
    // given
    final var batch =
        new ArchiveBatch.ProcessInstanceArchiveBatch(
            "finished-date", List.of(1L, 2L, 3L), List.of(4L, 5L, 6L));

    // when
    final var deduplicated = deduplicator.deduplicate(batch);

    // then
    assertThat(deduplicated)
        .isEqualTo(
            new ArchiveBatch.ProcessInstanceArchiveBatch(
                "finished-date", List.of(1L, 2L, 3L), List.of(4L, 5L, 6L)));
  }

  @Test
  public void shouldDeduplicateProcessInstanceBatch() {
    // given
    cache.put(1L, Boolean.TRUE);
    cache.put(3L, Boolean.TRUE);
    cache.put(5L, Boolean.TRUE);

    final var batch =
        new ArchiveBatch.ProcessInstanceArchiveBatch(
            "finished-date", List.of(1L, 2L, 3L), List.of(4L, 5L, 6L));

    // when
    final var deduplicated = deduplicator.deduplicate(batch);

    // then
    assertThat(deduplicated)
        .isEqualTo(
            new ArchiveBatch.ProcessInstanceArchiveBatch(
                "finished-date", List.of(2L), List.of(4L, 6L)));
  }

  @Test
  public void shouldMarkProcessInstancesAsRecentlyArchived() {
    // given
    final var batch =
        new ArchiveBatch.ProcessInstanceArchiveBatch(
            "finished-date", List.of(1L, 2L, 3L), List.of(4L, 5L, 6L));

    // when
    deduplicator.markRecentlyArchived(batch);

    // then
    assertThat(deduplicator.getRecentlyArchivedCount()).isEqualTo(6);
    assertThat(cache.asMap()).containsOnlyKeys(1L, 2L, 3L, 4L, 5L, 6L);
  }
}
