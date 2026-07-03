/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.stage;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.projection.ProcessExecutionProjector;
import io.camunda.analytics.projection.SourceRecord;
import io.camunda.analytics.projection.StateBackedProjectionStore;
import io.camunda.eventbridge.streaming.ProjectionStage;
import io.camunda.eventbridge.streaming.StreamProcessor;
import io.camunda.eventbridge.streaming.Task;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProjectionShardTest {

  @Test
  void shouldOwnDurabilityAndRestoreItsOwnCommittedOffset() {
    // given — an in-memory shard (no RocksDB, no real publisher), counting produce-flushes
    final StateBackedProjectionStore store = StateBackedProjectionStore.inMemory();
    final ProcessExecutionProjector projector =
        new ProcessExecutionProjector(store, store.elementStarts());
    final StreamProcessor<SourceRecord> processor =
        new StreamProcessor<SourceRecord>().add(new ProjectionStage<>(projector, List.of()));
    final int[] produceFlushes = {0};
    final ProjectionShard shard =
        new ProjectionShard(
            2, store, processor, Runnable::run, () -> produceFlushes[0]++, () -> {});
    shard.init();

    // then — it owns its durability and has nothing committed yet
    assertThat(shard.ownsDurability()).isTrue();
    assertThat(shard.restore()).isEqualTo(Task.NO_OFFSET);

    // when — the runtime commits this partition at offset 42
    shard.commit(42L);

    // then — output was produced before the cut, and the offset is durable and restorable
    assertThat(produceFlushes[0]).isEqualTo(1);
    assertThat(shard.restore()).isEqualTo(42L);
  }
}
