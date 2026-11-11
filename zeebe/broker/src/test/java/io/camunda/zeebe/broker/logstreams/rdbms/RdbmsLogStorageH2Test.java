/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.broker.logstreams.rdbms.RdbmsLogStorage.Config;
import io.camunda.zeebe.logstreams.impl.log.LogStreamBatchReaderImpl;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.logstreams.storage.LogStorage.AppendListener;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Runs {@link RdbmsLogStorage} against the real schema and {@link MyBatisLogBlockRepository}, on
 * its own and underneath a real {@link LogStream}.
 */
final class RdbmsLogStorageH2Test {

  private static final String PREFIX = "TEST_";
  private static final int PARTITION_ID = 1;

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final List<AutoCloseable> resources = new ArrayList<>();
  private MyBatisLogBlockRepository repository;

  @BeforeEach
  void setUp() throws Exception {
    repository = new MyBatisLogBlockRepository(H2LogDatabase.create(PREFIX), PARTITION_ID);
  }

  @AfterEach
  void tearDown() throws Exception {
    for (final var resource : resources.reversed()) {
      resource.close();
    }
  }

  @Test
  void shouldCommitAppendedBlocksToTheDatabase() {
    // given
    final var storage = newStorage(1);
    final List<Long> committed = new CopyOnWriteArrayList<>();
    final AppendListener listener =
        new AppendListener() {
          @Override
          public void onCommit(final long index, final long highestPosition) {
            committed.add(highestPosition);
          }
        };

    // when
    storage.append(1, 3, data("first"), listener);
    storage.append(4, 6, data("second"), listener);

    // then
    await().until(() -> committed.size() == 2);
    assertThat(committed).containsExactly(3L, 6L);
    assertThat(repository.readBlocksAfter(Long.MIN_VALUE, 10))
        .extracting(block -> new String(block.data(), StandardCharsets.UTF_8))
        .containsExactly("first", "second");
  }

  @Test
  void shouldReadBackRecordsWrittenThroughLogStream() {
    // given
    final var logStream = newLogStream(newStorage(1));
    final var writer = logStream.newLogStreamWriter();

    // when
    writer.tryWrite(WriteContext.internal(), entry());
    writer.tryWrite(WriteContext.internal(), List.of(entry(), entry()), 1);

    // then
    final var reader = logStream.newLogStreamReader();
    resources.add(reader);
    await().until(() -> readPositions(reader).size() == 3);
    reader.seekToFirstEvent();
    assertThat(readEvents(reader))
        .containsExactly(new Event(1, -1), new Event(2, 1), new Event(3, 1));
  }

  @Test
  void shouldGroupRecordsWithSameSourceIntoOneBatch() {
    // given
    final var logStream = newLogStream(newStorage(1));
    final var writer = logStream.newLogStreamWriter();
    writer.tryWrite(WriteContext.internal(), entry());
    writer.tryWrite(WriteContext.internal(), List.of(entry(), entry()), 1);
    final var reader = logStream.newLogStreamReader();
    resources.add(reader);
    await().until(() -> readPositions(reader).size() == 3);

    // when
    final var batchReader = new LogStreamBatchReaderImpl(logStream.newLogStreamReader());
    resources.add(batchReader);
    batchReader.seekToNextBatch(-1);
    final var batches = new ArrayList<List<Long>>();
    while (batchReader.hasNext()) {
      final var batch = batchReader.next();
      final var positions = new ArrayList<Long>();
      batch.forEachRemaining(event -> positions.add(event.getPosition()));
      batches.add(positions);
    }

    // then
    assertThat(batches).containsExactly(List.of(1L), List.of(2L, 3L));
  }

  @Test
  void shouldContinueAfterExistingRecordsWhenNewLeaderStarts() throws Exception {
    // given
    final var firstStorage = newStorage(1);
    final var firstLogStream = newLogStream(firstStorage);
    firstLogStream
        .newLogStreamWriter()
        .tryWrite(WriteContext.internal(), List.of(entry(), entry()));
    final var firstReader = firstLogStream.newLogStreamReader();
    resources.add(firstReader);
    await().until(() -> readPositions(firstReader).size() == 2);
    firstLogStream.close();
    firstStorage.close();

    // when
    final var logStream = newLogStream(newStorage(2));
    final var position =
        logStream.newLogStreamWriter().tryWrite(WriteContext.internal(), entry()).get();

    // then
    assertThat(position).isEqualTo(3L);
    final var reader = logStream.newLogStreamReader();
    resources.add(reader);
    await().until(() -> readPositions(reader).size() == 3);
    assertThat(repository.readBlocksAfter(Long.MIN_VALUE, 10))
        .extracting(LogBlock::term)
        .containsExactly(1L, 2L);
  }

  private RdbmsLogStorage newStorage(final long term) {
    assertThat(repository.claimTerm(term)).isTrue();
    final var storage =
        RdbmsLogStorage.writable(
            "test-writer", repository, term, Config.DEFAULT, meterRegistry, e -> {});
    resources.add(storage);
    return storage;
  }

  private LogStream newLogStream(final RdbmsLogStorage storage) {
    final var logStream =
        LogStream.builder()
            .withPartitionId(PARTITION_ID)
            .withLogName("test-log")
            .withLogStorage(storage)
            .withClock(InstantSource.system())
            .withMeterRegistry(new SimpleMeterRegistry())
            .build();
    resources.add(logStream);
    return logStream;
  }

  private static List<Long> readPositions(final LogStreamReader reader) {
    reader.seekToFirstEvent();
    return readEvents(reader).stream().map(Event::position).toList();
  }

  /** Reads the remaining events, copying their positions since the reader reuses event objects. */
  private static List<Event> readEvents(final LogStreamReader reader) {
    final var events = new ArrayList<Event>();
    while (reader.hasNext()) {
      final var event = reader.next();
      events.add(new Event(event.getPosition(), event.getSourceEventPosition()));
    }
    return events;
  }

  private static LogAppendEntry entry() {
    return LogAppendEntry.of(
        new RecordMetadata()
            .recordType(RecordType.COMMAND)
            .valueType(ValueType.PROCESS_INSTANCE_CREATION)
            .intent(ProcessInstanceCreationIntent.CREATE),
        new UnifiedRecordValue(0));
  }

  private static DirectBufferWriter data(final String value) {
    return new DirectBufferWriter(new UnsafeBuffer(value.getBytes(StandardCharsets.UTF_8)));
  }

  private record Event(long position, long sourcePosition) {}
}
