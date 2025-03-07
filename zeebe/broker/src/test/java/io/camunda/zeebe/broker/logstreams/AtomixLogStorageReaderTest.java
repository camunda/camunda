/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.atomix.raft.storage.log.RaftLog;
import io.atomix.raft.storage.log.entry.ApplicationEntry;
import io.atomix.raft.storage.log.entry.RaftLogEntry;
import io.atomix.raft.zeebe.ZeebeLogAppender;
import io.camunda.zeebe.journal.JournalMetaStore;
import io.camunda.zeebe.logstreams.storage.LogStorage.AppendListener;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.nio.ByteBuffer;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AtomixLogStorageReaderTest {

  private RaftLog log;
  private AtomixLogStorage logStorage;
  private AtomixLogStorageReader reader;

  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @BeforeEach
  void beforeEach(@TempDir final File tempDir) {
    log =
        RaftLog.builder(meterRegistry)
            .withDirectory(tempDir)
            .withMetaStore(mock(JournalMetaStore.class))
            .build();
    final Appender appender = new Appender();
    logStorage = new AtomixLogStorage(log::openUncommittedReader, appender, (bound) -> {});
    reader = logStorage.newReader();
  }

  @AfterEach
  void afterEach() {
    CloseHelper.quietCloseAll(log, reader);
  }

  @Test
  void shouldBeInitializedAtFirstBlock() {
    // given
    appendIntegerBlock(1);
    appendIntegerBlock(2);

    // when
    // then
    assertThat(reader).hasNext();
    assertThat(reader.next()).isEqualTo(mapIntegerToBuffer(1));
  }

  @Test
  void shouldSeekToFirstBlock() {
    // given
    appendIntegerBlock(1);
    appendIntegerBlock(2);

    // when
    reader.seek(Long.MIN_VALUE);

    // then
    assertThat(reader).hasNext();
    assertThat(reader.next()).isEqualTo(mapIntegerToBuffer(1));
  }

  @Test
  void shouldSeekToLastBlock() {
    // given
    appendIntegerBlock(1);
    appendIntegerBlock(2);

    // when
    reader.seek(Long.MAX_VALUE);

    // then
    assertThat(reader).hasNext();
    assertThat(reader.next()).isEqualTo(mapIntegerToBuffer(2));
  }

  @Test
  void shouldSeekToHighestPositionThatIsLessThanTheGivenOne() {
    // given
    appendIntegerBlock(1);
    appendIntegerBlock(3);

    // when
    reader.seek(2);

    // then
    assertThat(reader).hasNext();
    assertThat(reader.next()).isEqualTo(mapIntegerToBuffer(1));
  }

  @Test
  void shouldSeekToBlockContainingPosition() {
    // given
    appendIntegerBlock(1);
    appendIntegerBlock(2, 4, 2);
    appendIntegerBlock(5);

    // when
    reader.seek(3);

    // then
    assertThat(reader).hasNext();
    assertThat(reader.next()).isEqualTo(mapIntegerToBuffer(2));
  }

  @Test
  void shouldNotHaveNextIfEndIsReached() {
    // given
    appendIntegerBlock(1);
    appendIntegerBlock(2);

    // when
    reader.seek(2);
    reader.next();

    // then
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  void shouldNotHaveNextIfEmpty() {
    // given an empty log
    // when
    // then
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  void shouldHaveNextAfterNewBlockAppended() {
    // given an empty log
    // when
    appendIntegerBlock(1);

    // then
    assertThat(reader).hasNext();
    assertThat(reader.next()).isEqualTo(mapIntegerToBuffer(1));
  }

  private void appendIntegerBlock(final int positionAndValue) {
    appendIntegerBlock(positionAndValue, positionAndValue, positionAndValue);
  }

  private void appendIntegerBlock(
      final long lowestPosition, final long highestPosition, final int value) {
    logStorage.append(
        lowestPosition,
        highestPosition,
        mapIntegerToBuffer(value).byteBuffer(),
        new AppendListener() {});
  }

  private DirectBuffer mapIntegerToBuffer(final int value) {
    return new UnsafeBuffer(ByteBuffer.allocateDirect(Integer.BYTES).putInt(0, value));
  }

  private final class Appender implements ZeebeLogAppender {

    @Override
    public void appendEntry(final ApplicationEntry entry, final AppendListener appendListener) {
      final var indexed = log.append(new RaftLogEntry(1, entry));
      appendListener.onWrite(indexed);
      log.setCommitIndex(indexed.index());
      appendListener.onCommit(indexed.index(), entry.highestPosition());
    }
  }
}
