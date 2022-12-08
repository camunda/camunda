/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.log;

import io.camunda.zeebe.logstreams.impl.log.Sequencer;
import io.camunda.zeebe.scheduler.ActorCondition;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.time.Duration;
import java.util.List;
import org.agrona.MutableDirectBuffer;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Mockito;

@SuppressWarnings("resource")
@Execution(ExecutionMode.CONCURRENT)
final class SequencerTest {

  @Test
  void notifiesConsumerOnWrite() {
    // given
    final var sequencer = new Sequencer(1, 0, 16);
    final var consumer = Mockito.mock(ActorCondition.class);

    // when
    sequencer.registerConsumer(consumer);
    sequencer.tryWrite(new TestLogAppendEntry());

    // then
    Mockito.verify(consumer).signal();
  }

  @Test
  void canPeekAfterWrite() {
    // given
    final var sequencer = new Sequencer(1, 1, 16);
    final var entry = new TestLogAppendEntry();

    // when
    sequencer.tryWrite(entry);

    // then
    final var peek = sequencer.peek();
    Assertions.assertThat(peek.entries()).containsExactly(entry);
  }

  @Test
  void canReadAfterWrite() {
    // given
    final var sequencer = new Sequencer(1, 1, 16);
    final var entry = new TestLogAppendEntry();

    // when
    sequencer.tryWrite(entry);

    // then
    final var read = sequencer.tryRead();
    Assertions.assertThat(read.entries()).containsExactly(entry);
  }

  @Test
  void peeksFirstWrittenEntry() {
    // given
    final var sequencer = new Sequencer(1, 1, 16 * 1024 * 1024);
    final var firstEntry = new TestLogAppendEntry(1);
    final var secondEntry = new TestLogAppendEntry(2);

    // when
    Assertions.assertThat(sequencer.tryWrite(firstEntry)).isPositive();
    Assertions.assertThat(sequencer.tryWrite(secondEntry)).isPositive();

    // then
    final var peek = sequencer.peek();
    Assertions.assertThat(peek.entries()).containsExactly(firstEntry);
  }

  @Test
  void readReturnsSameAsPeek() {
    // given
    final var sequencer = new Sequencer(1, 1, 16 * 1024 * 1024);
    final var firstEntry = new TestLogAppendEntry(1);
    final var secondEntry = new TestLogAppendEntry(2);

    // when
    Assertions.assertThat(sequencer.tryWrite(firstEntry)).isPositive();
    Assertions.assertThat(sequencer.tryWrite(secondEntry)).isPositive();

    // then
    final var peek = sequencer.peek();
    final var read = sequencer.tryRead();
    Assertions.assertThat(peek).isEqualTo(read);
  }

  @Test
  void cannotPeekEmpty() {
    // given
    final var sequencer = new Sequencer(1, 1, 16 * 1024 * 1024);

    // then
    final var peek = sequencer.peek();
    Assertions.assertThat(peek).isNull();
  }

  @Test
  void cannotReadEmpty() {
    // given
    final var sequencer = new Sequencer(1, 1, 16 * 1024 * 1024);

    // then
    final var read = sequencer.tryRead();
    Assertions.assertThat(read).isNull();
  }

  @Test
  void eventuallyRejectsWritesWithoutReader() {
    // given
    final var sequencer = new Sequencer(1, 1, 16 * 1024 * 1024);

    // then
    Awaitility.await("sequencer rejects writes")
        .pollInSameThread()
        .pollInterval(Duration.ZERO)
        .until(() -> sequencer.tryWrite(new TestLogAppendEntry()), (result) -> result <= 0);
  }

  @Test
  void writingSingleEntryIncreasesPositions() {
    // given
    final var initialPosition = 1;
    final var sequencer = new Sequencer(1, initialPosition, 16 * 1024 * 1024);

    // when
    final var result = sequencer.tryWrite(new TestLogAppendEntry());

    // then
    Assertions.assertThat(result).isPositive().isEqualTo(initialPosition);
  }

  @Test
  void writingMultipleEntriesIncreasesPositions() {
    // given
    final var initialPosition = 1;
    final var sequencer = new Sequencer(1, initialPosition, 16 * 1024 * 1024);
    final var entries =
        List.of(new TestLogAppendEntry(), new TestLogAppendEntry(), new TestLogAppendEntry());
    // when
    final var result = sequencer.tryWrite(entries);

    // then
    Assertions.assertThat(result).isPositive().isEqualTo(initialPosition + entries.size() - 1);
  }

  @Test
  void notifiesReaderWhenRejectingWriteDueToFullQueue() {
    // given
    final var sequencer = new Sequencer(1, 1, 16 * 1024 * 1024);
    Awaitility.await("sequencer rejects writes")
        .pollInSameThread()
        .pollInterval(Duration.ZERO)
        .until(() -> sequencer.tryWrite(new TestLogAppendEntry()), (result) -> result <= 0);
    final var consumer = Mockito.mock(ActorCondition.class);

    // when
    sequencer.registerConsumer(consumer);
    final var result = sequencer.tryWrite(new TestLogAppendEntry());

    // then
    Assertions.assertThat(result).isNegative();
    Mockito.verify(consumer).signal();
  }

  private record TestLogAppendEntry(
      long key, int sourceIndex, BufferWriter recordValue, BufferWriter recordMetadata)
      implements LogAppendEntry {

    TestLogAppendEntry(final int key) {
      this(key, -1, new Payload("value"), new Payload("metadata"));
    }

    TestLogAppendEntry() {
      this(1, -1, new Payload("value"), new Payload("metadata"));
    }

    private record Payload(String value) implements BufferWriter {
      @Override
      public int getLength() {
        return value.length();
      }

      @Override
      public void write(final MutableDirectBuffer buffer, final int offset) {
        buffer.putStringAscii(offset, value);
      }
    }
  }
}
