/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.internals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.streaming.CommitCut;
import io.camunda.eventbridge.streaming.Task;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * The changelog stage's ordering invariant (streaming ADR 0009 Decisions 1/2), tested directly
 * against {@link PartitionCommitter#persistCut} rather than through the whole runtime: {@code
 * publish()} (which a changelogging task's cut uses to append + await its changelog) always runs to
 * completion, or throws, strictly before {@code persist()} and the chained source-offset commit —
 * so a changelog publish failure fails the cut as a whole, with nothing local committed and no
 * source-offset commit sent.
 */
final class PartitionCommitterOrderingTest {

  private static final String TOPIC = "source";

  @Test
  void shouldNotPersistOrCommitTheOffsetWhenPublishFails() {
    // given a cut whose publish() (standing in for a changelog append) fails
    final List<String> journal = new ArrayList<>();
    final Consumer consumer = mock(Consumer.class);
    final PartitionCommitter<String> committer = new PartitionCommitter<>(consumer, TOPIC);
    final Partition<String> partition = partition(journal, /* failPublish= */ true);

    // when
    assertThatThrownBy(() -> committer.persistCut(partition, 5L, partition.task().freezeCut(5L)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("injected changelog publish failure");

    // then — neither the local transaction nor the source-offset commit ever ran
    assertThat(journal).containsExactly("publish-failed");
    verify(consumer, never()).commitOffset(any(), anyInt(), anyLong());
  }

  @Test
  void shouldPersistOnlyAfterThePublishAckAndThenCommitTheOffset() {
    // given a cut whose publish() succeeds (the changelog ack lands)
    final List<String> journal = new ArrayList<>();
    final Consumer consumer = mock(Consumer.class);
    when(consumer.commitOffset(any(), anyInt(), anyLong()))
        .thenAnswer(
            invocation -> {
              journal.add("commitOffset:" + invocation.getArgument(2, Long.class));
              return CompletableFuture.completedFuture(null);
            });
    final PartitionCommitter<String> committer = new PartitionCommitter<>(consumer, TOPIC);
    final Partition<String> partition = partition(journal, /* failPublish= */ false);

    // when
    committer.persistCut(partition, 5L, partition.task().freezeCut(5L)).join();

    // then — publish (the changelog ack), then persist (the local transaction), then the
    // chained source-offset commit, in that exact order
    assertThat(journal).containsExactly("publish", "persist", "commitOffset:5");
  }

  @Test
  void shouldRecarryTheSameDeltaOnTheNextCutAfterAPublishFailure() {
    // given a first cut whose publish fails — nothing local committed, nothing offset-committed
    final List<String> journal = new ArrayList<>();
    final Consumer consumer = mock(Consumer.class);
    when(consumer.commitOffset(any(), anyInt(), anyLong()))
        .thenAnswer(
            invocation -> {
              journal.add("commitOffset:" + invocation.getArgument(2, Long.class));
              return CompletableFuture.completedFuture(null);
            });
    final PartitionCommitter<String> committer = new PartitionCommitter<>(consumer, TOPIC);
    final Partition<String> partition = partition(journal, /* failPublish= */ true);
    assertThatThrownBy(() -> committer.persistCut(partition, 5L, partition.task().freezeCut(5L)));
    journal.clear();

    // when the next cut retries with the (merged-back) delta and its changelog publish succeeds
    final Partition<String> retried = partition(journal, /* failPublish= */ false);
    committer.persistCut(retried, 5L, retried.task().freezeCut(5L)).join();

    // then this time the cut lands, in the same order, carrying the same key/offset
    assertThat(journal).containsExactly("publish", "persist", "commitOffset:5");
  }

  private Partition<String> partition(final List<String> journal, final boolean failPublish) {
    final PartitionQueue<String> queue = new PartitionQueue<>(4);
    final Task<String> task = new RecordingTask(journal, failPublish);
    return new Partition<>(1, queue, task, Task.NO_OFFSET, System.nanoTime());
  }

  /** A minimal task whose cut's publish()/persist() record into a shared journal in order. */
  private static final class RecordingTask implements Task<String> {

    private final List<String> journal;
    private final boolean failPublish;

    private RecordingTask(final List<String> journal, final boolean failPublish) {
      this.journal = journal;
      this.failPublish = failPublish;
    }

    @Override
    public void process(final String record) {}

    @Override
    public CommitCut freezeCut(final long offset) {
      return new CommitCut() {
        @Override
        public void publish() {
          if (failPublish) {
            journal.add("publish-failed");
            throw new IllegalStateException("injected changelog publish failure");
          }
          journal.add("publish");
        }

        @Override
        public void persist() {
          journal.add("persist");
        }

        @Override
        public void complete(final boolean success) {
          journal.add("complete:" + success);
        }
      };
    }
  }
}
