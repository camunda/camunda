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
  private final Cache<String, Boolean> cache = Caffeine.newBuilder().build();
  private final RecentlyArchivedProcessInstances deduplicator =
      new RecentlyArchivedProcessInstances(cache);

  @Test
  public void shouldNotDeduplicateProcessInstanceBatchWhenNothingInCache() {
    // given
    final var batch = new ArchiveBatch("finished-date", List.of("1", "2", "3", "4", "5", "6"));

    // when
    final var deduplicated = deduplicator.deduplicate(batch);

    // then
    assertThat(deduplicated)
        .isEqualTo(new ArchiveBatch("finished-date", List.of("1", "2", "3", "4", "5", "6")));
  }

  @Test
  public void shouldDeduplicateProcessInstanceBatch() {
    // given
    cache.put("1", Boolean.TRUE);
    cache.put("3", Boolean.TRUE);
    cache.put("5", Boolean.TRUE);

    final var batch = new ArchiveBatch("finished-date", List.of("1", "2", "3", "4", "5", "6"));

    // when
    final var deduplicated = deduplicator.deduplicate(batch);

    // then
    assertThat(deduplicated).isEqualTo(new ArchiveBatch("finished-date", List.of("2", "4", "6")));
  }

  @Test
  public void shouldMarkProcessInstancesAsRecentlyArchived() {
    // given
    final var batch = new ArchiveBatch("finished-date", List.of("1", "2", "3", "4", "5", "6"));

    // when
    deduplicator.markRecentlyArchived(batch);

    // then
    assertThat(deduplicator.getRecentlyArchivedCount()).isEqualTo(6);
    assertThat(cache.asMap()).containsOnlyKeys("1", "2", "3", "4", "5", "6");
  }
}
