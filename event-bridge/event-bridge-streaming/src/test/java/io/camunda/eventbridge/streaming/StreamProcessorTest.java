/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The stream processor drives any {@link Stage}'s lifecycle agnostically, per source partition, and
 * owns its shard through the injected {@link ShardDurability}: it restores its offset from it and
 * commits every stage's state atomically with the consumed offset — synchronously via {@link
 * StreamProcessor#commit(long)} or as a frozen cut via {@link StreamProcessor#freezeCut(long)}.
 */
final class StreamProcessorTest {

  private record Order(String region, String product, long amount, boolean completed) {}

  @Test
  void shouldDriveAnyCustomStageAgnostically() {
    // given — a stage that is not a projection/rollup at all, just counting records and lifecycle
    final long[] processed = {0};
    final boolean[] initialized = {false};
    final boolean[] closed = {false};
    final Stage<Order> counting =
        new Stage<>() {
          @Override
          public void init() {
            initialized[0] = true;
          }

          @Override
          public void process(final Order record) {
            processed[0]++;
          }

          @Override
          public void close() {
            closed[0] = true;
          }
        };

    final StreamProcessor<Order> processor =
        new StreamProcessor<Order>(new JournalingShard(new ArrayList<>())).add(counting);

    // when
    processor.init();
    processor.process(new Order("EU", "widget", 1, true));
    processor.process(new Order("US", "gadget", 2, true));
    processor.close();

    // then — the runtime drove the stage's lifecycle without knowing what it does
    assertThat(initialized[0]).isTrue();
    assertThat(processed[0]).isEqualTo(2L);
    assertThat(closed[0]).isTrue();
  }

  @Test
  void shouldRestoreTheOffsetFromItsShard() {
    // given — a shard that already committed offset 42
    final JournalingShard shard = new JournalingShard(new ArrayList<>());
    shard.offset = 42L;
    final StreamProcessor<Order> processor = new StreamProcessor<>(shard);

    // when / then — the processor's baseline is the shard's stored offset
    assertThat(processor.restore()).isEqualTo(42L);
  }

  @Test
  void shouldCommitStagesAndOffsetInOneShardTransaction() {
    // given — two journaling stages behind one shard
    final List<String> journal = new ArrayList<>();
    final JournalingShard shard = new JournalingShard(journal);
    final StreamProcessor<Order> processor =
        new StreamProcessor<Order>(shard)
            .add(new JournalingStage(journal, "a"))
            .add(new JournalingStage(journal, "b"));

    // when
    processor.commit(7L);

    // then — the offset and every stage's checkpoint landed inside the shard's one transaction
    assertThat(journal)
        .containsExactly("tx-begin", "offset:7", "checkpoint:a", "checkpoint:b", "tx-end");
    assertThat(shard.offset).isEqualTo(7L);
  }

  @Test
  void shouldFreezeACutThatPersistsStagesAndOffsetInOneShardTransaction() {
    // given — two freezable stages behind one shard
    final List<String> journal = new ArrayList<>();
    final JournalingShard shard = new JournalingShard(journal);
    final JournalingStage first = new JournalingStage(journal, "a");
    final JournalingStage second = new JournalingStage(journal, "b");
    first.freezable = true;
    second.freezable = true;
    final StreamProcessor<Order> processor =
        new StreamProcessor<Order>(shard).add(first).add(second);

    // when — the cut is frozen at the barrier and persisted (as the IO thread would)
    final CommitCut cut = processor.freezeCut(9L);
    cut.persist();
    cut.complete(true);

    // then — the freeze converged buffered output first, and the persist wrote every stage's
    // frozen delta and the barrier's offset inside the shard's one transaction
    assertThat(journal)
        .containsExactly(
            "flush:a",
            "flush:b",
            "freeze:a",
            "freeze:b",
            "tx-begin",
            "offset:9",
            "persist:a",
            "persist:b",
            "tx-end",
            "complete:a:true",
            "complete:b:true");
    assertThat(shard.offset).isEqualTo(9L);
  }

  @Test
  void shouldMergeAFailedCutBackOnEveryStage() {
    // given — a freezable stage whose cut fails to persist
    final List<String> journal = new ArrayList<>();
    final JournalingStage stage = new JournalingStage(journal, "a");
    stage.freezable = true;
    final StreamProcessor<Order> processor =
        new StreamProcessor<Order>(new JournalingShard(journal)).add(stage);

    // when — the runtime completes the cut unsuccessfully (persist failed)
    final CommitCut cut = processor.freezeCut(3L);
    cut.complete(false);

    // then — the failure is propagated to the stage so it merges its frozen delta back
    assertThat(journal).containsExactly("flush:a", "freeze:a", "complete:a:false");
  }

  @Test
  void shouldRefuseToFreezeWhenAnyStageLacksFrozenCheckpointSupport() {
    // given — one freezable and one non-freezable stage
    final List<String> journal = new ArrayList<>();
    final JournalingStage freezable = new JournalingStage(journal, "a");
    freezable.freezable = true;
    final JournalingStage synchronous = new JournalingStage(journal, "b");
    final StreamProcessor<Order> processor =
        new StreamProcessor<Order>(new JournalingShard(journal)).add(freezable).add(synchronous);

    // when / then — no cut: the runtime falls back to the synchronous commit, and no stage froze
    assertThat(processor.freezeCut(5L)).isNull();
    assertThat(journal).isEmpty();
  }

  /** A heap shard journaling its transaction boundary and offset writes. */
  private static final class JournalingShard implements ShardDurability {

    private final List<String> journal;
    private long offset = Task.NO_OFFSET;

    private JournalingShard(final List<String> journal) {
      this.journal = journal;
    }

    @Override
    public void runInTransaction(final Runnable operations) {
      journal.add("tx-begin");
      operations.run();
      journal.add("tx-end");
    }

    @Override
    public long readOffset() {
      return offset;
    }

    @Override
    public void persistOffset(final long committed) {
      journal.add("offset:" + committed);
      offset = committed;
    }
  }

  /** A stage journaling its lifecycle, optionally supporting the frozen-checkpoint trio. */
  private static final class JournalingStage implements Stage<Order> {

    private final List<String> journal;
    private final String name;
    private boolean freezable;

    private JournalingStage(final List<String> journal, final String name) {
      this.journal = journal;
      this.name = name;
    }

    @Override
    public void process(final Order record) {}

    @Override
    public void flush() {
      journal.add("flush:" + name);
    }

    @Override
    public void checkpoint() {
      journal.add("checkpoint:" + name);
    }

    @Override
    public boolean supportsFrozenCheckpoint() {
      return freezable;
    }

    @Override
    public void freezeCheckpoint() {
      journal.add("freeze:" + name);
    }

    @Override
    public void persistCheckpoint() {
      journal.add("persist:" + name);
    }

    @Override
    public void completeCheckpoint(final boolean success) {
      journal.add("complete:" + name + ":" + success);
    }
  }
}
