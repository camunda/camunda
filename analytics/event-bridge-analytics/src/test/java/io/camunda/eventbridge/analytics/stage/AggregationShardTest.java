/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.stage;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.shuffle.MergingRollup;
import io.camunda.analytics.shuffle.Partial;
import io.camunda.eventbridge.streaming.StreamProcessor;
import io.camunda.eventbridge.streaming.Task;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.memory.InMemoryKeyValueStore;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class AggregationShardTest {

  @Test
  void shouldOwnDurabilityAndRestoreItsOwnCommittedOffset() {
    // given — an in-memory shard (no RocksDB, no mergers)
    final KeyValueStore<DbInt, DbLong> factsOffsets =
        new InMemoryKeyValueStore<>(new DbInt(), new DbLong());
    final Map<Integer, MergingRollup<?, ?>> mergers = new HashMap<>();
    final StreamProcessor<Partial> processor =
        new StreamProcessor<Partial>().add(new MergeStage(mergers));
    final AggregationShard shard =
        new AggregationShard(3, factsOffsets, processor, Runnable::run, () -> {});
    shard.init();

    // then — it owns its durability and has nothing committed yet
    assertThat(shard.ownsDurability()).isTrue();
    assertThat(shard.restore()).isEqualTo(Task.NO_OFFSET);

    // when — the runtime commits this facts partition at offset 7
    shard.commit(7L);

    // then — the offset is durable and restorable for this partition
    assertThat(shard.restore()).isEqualTo(7L);
  }
}
