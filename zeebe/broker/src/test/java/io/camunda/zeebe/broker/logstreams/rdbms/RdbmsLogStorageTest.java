/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.broker.logstreams.rdbms.RdbmsLogStorage.WriterConfig;
import io.camunda.zeebe.logstreams.storage.LogStorage.AppendListener;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.agrona.MutableDirectBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class RdbmsLogStorageTest {

  private static final long TERM = 3;
  private static final WriterConfig CONFIG =
      new WriterConfig(Duration.ofMillis(500), Duration.ofMillis(5), Duration.ofMillis(20), 1024);

  private final InMemoryLogBlockRepository repository = new InMemoryLogBlockRepository();
  private final List<String> events = new CopyOnWriteArrayList<>();
  private final List<Throwable> failures = new CopyOnWriteArrayList<>();
  private RdbmsLogStorage storage;

  @BeforeEach
  void setUp() {
    repository.claimTerm(TERM);
    storage = RdbmsLogStorage.writable("test-writer", repository, TERM, CONFIG, failures::add);
    storage.addAppendedListener(position -> events.add("appended " + position));
    storage.addCommittedPositionListener(position -> events.add("committed position " + position));
    storage.addCommitListener(() -> events.add("commit"));
  }

  @AfterEach
  void tearDown() {
    storage.close();
  }

  @Test
  void shouldNotifyListenersInPositionOrderOnceCommitted() {
    // given
    repository.holdAppends();
    append(1, 3);
    await().until(repository::isAppendWaiting);
    append(4, 6);

    // when
    repository.releaseAppends();

    // then
    await().until(() -> events.size() == 10);
    assertThat(events)
        .containsExactly(
            "write 3",
            "appended 3",
            "commit 3",
            "committed position 3",
            "commit",
            "write 6",
            "appended 6",
            "commit 6",
            "committed position 6",
            "commit");
    assertThat(repository.blocks())
        .extracting(LogBlock::lowPosition, LogBlock::highPosition, LogBlock::term)
        .containsExactly(tuple(1L, 3L, TERM), tuple(4L, 6L, TERM));
  }

  @Test
  void shouldWriteQueuedBlocksInOneTransaction() {
    // given
    repository.holdAppends();
    append(1, 1);
    await().until(repository::isAppendWaiting);
    append(2, 2);
    append(3, 3);
    append(4, 4);

    // when
    repository.releaseAppends();

    // then
    await().until(() -> repository.blocks().size() == 4);
    assertThat(repository.appendCalls())
        .extracting(call -> call.stream().map(LogBlock::lowPosition).toList())
        .containsExactly(List.of(1L), List.of(2L, 3L, 4L));
  }

  @Test
  void shouldNotWriteMoreThanMaxBatchBytesInOneTransaction() {
    // given
    repository.holdAppends();
    append(1, 1);
    await().until(repository::isAppendWaiting);
    append(2, 2, 600);
    append(3, 3, 600);

    // when
    repository.releaseAppends();

    // then
    await().until(() -> repository.blocks().size() == 3);
    assertThat(repository.appendCalls())
        .extracting(call -> call.stream().map(LogBlock::lowPosition).toList())
        .containsExactly(List.of(1L), List.of(2L), List.of(3L));
  }

  @Test
  void shouldFailWithoutRetryingWhenAnotherLeaderClaimedThePartition() {
    // given
    repository.claimTerm(TERM + 1);

    // when
    append(1, 3);

    // then
    await().until(() -> !failures.isEmpty());
    assertThat(failures).singleElement().isInstanceOf(TermMismatchException.class);
    assertThat(repository.appendCalls()).hasSize(1);
    assertThat(events).isEmpty();
  }

  @Test
  void shouldRetryTransientFailure() {
    // given
    repository.failNextAppends(2);

    // when
    append(1, 3);

    // then
    await().until(() -> events.contains("commit 3"));
    assertThat(repository.appendCalls()).hasSize(3);
    assertThat(repository.blocks()).hasSize(1);
    assertThat(failures).isEmpty();
  }

  @Test
  void shouldFailWhenRetriesRunOut() {
    // given
    repository.failAllAppends();

    // when
    append(1, 3);

    // then
    await().until(() -> !failures.isEmpty());
    assertThat(failures).singleElement().isInstanceOf(LogBlockRepositoryException.class);
    assertThat(events).isEmpty();
  }

  @Test
  void shouldTreatRetryAsCommittedWhenEarlierAttemptWroteTheBlocks() {
    // given
    repository.failNextAppendAfterCommit();

    // when
    append(1, 3);

    // then
    await().until(() -> events.contains("commit 3"));
    assertThat(repository.appendCalls()).hasSize(2);
    assertThat(repository.blocks()).hasSize(1);
    assertThat(failures).isEmpty();
  }

  @Test
  void shouldDropAppendsOnceFailed() {
    // given
    repository.claimTerm(TERM + 1);
    append(1, 3);
    await().until(() -> !failures.isEmpty());

    // when
    append(4, 6);

    // then
    assertThat(repository.appendCalls()).hasSize(1);
    assertThat(events).isEmpty();
  }

  @Test
  void shouldDropAppendsOnceClosed() {
    // given
    storage.close();

    // when
    append(1, 3);

    // then
    assertThat(repository.appendCalls()).isEmpty();
    assertThat(events).isEmpty();
    assertThat(failures).isEmpty();
  }

  @Test
  void shouldRejectAppendsOnReadOnlyStorage() {
    // given
    final var readOnly = RdbmsLogStorage.readOnly(repository);

    // when / then
    assertThatThrownBy(() -> readOnly.append(1, 3, bytes(1), new RecordingListener()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private void append(final long low, final long high) {
    append(low, high, 1);
  }

  private void append(final long low, final long high, final int size) {
    storage.append(low, high, bytes(size), new RecordingListener());
  }

  private static BufferWriter bytes(final int size) {
    return new BufferWriter() {
      @Override
      public int getLength() {
        return size;
      }

      @Override
      public int write(final MutableDirectBuffer buffer, final int offset) {
        buffer.setMemory(offset, size, (byte) 1);
        return size;
      }
    };
  }

  private final class RecordingListener implements AppendListener {

    @Override
    public void onWrite(final long index, final long highestPosition) {
      events.add("write " + highestPosition);
    }

    @Override
    public void onCommit(final long index, final long highestPosition) {
      events.add("commit " + highestPosition);
    }
  }
}
