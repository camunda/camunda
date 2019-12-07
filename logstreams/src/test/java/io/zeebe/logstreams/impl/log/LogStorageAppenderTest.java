/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.spi.LogStorage.AppendListener;
import io.zeebe.logstreams.util.AtomixLogStorageRule;
import io.zeebe.protocol.Protocol;
import io.zeebe.util.ByteValue;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LogStorageAppenderTest {

  private static final int MAX_FRAGMENT_SIZE = 1024;
  private static final int PARTITION_ID = 0;

  @Rule public final ActorSchedulerRule schedulerRule = new ActorSchedulerRule();
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private final AtomixLogStorageRule logStorageRule =
      new AtomixLogStorageRule(temporaryFolder, PARTITION_ID);

  private Dispatcher dispatcher;
  private LogStorage logStorage;
  private LogStorageAppender appender;
  private LogStreamWriterImpl writer;
  private BufferedLogStreamReader reader;

  @Before
  public void setUp() throws Exception {
    logStorageRule.open(
        b -> b.withMaxSegmentSize(MAX_FRAGMENT_SIZE * 100).withMaxEntrySize(MAX_FRAGMENT_SIZE));
    logStorage = spy(logStorageRule.get());

    dispatcher =
        Dispatchers.create("0")
            .actorScheduler(schedulerRule.get())
            .bufferSize(ByteValue.ofMegabytes(100 * MAX_FRAGMENT_SIZE))
            .maxFragmentLength(ByteValue.ofBytes(MAX_FRAGMENT_SIZE))
            .initialPartitionId(0)
            .build();
    final var subscription = dispatcher.openSubscription("log");
    appender =
        new LogStorageAppender(
            "appender", PARTITION_ID, logStorage, subscription, MAX_FRAGMENT_SIZE);
    writer = new LogStreamWriterImpl(PARTITION_ID, dispatcher);
    reader = new BufferedLogStreamReader(logStorage);
  }

  @After
  public void tearDown() throws Exception {
    appender.close();
    dispatcher.close();
    logStorageRule.close();
  }

  @Test
  public void shouldAppendSingleEvent() throws InterruptedException {
    // given
    final var value = new Value(1);
    final var latch = new CountDownLatch(1);

    // when
    final var position = writer.valueWriter(value).tryWrite();
    logStorageRule.setPositionListener(i -> latch.countDown());
    schedulerRule.submitActor(appender).join();

    // then
    final Value expected = new Value();
    latch.await(5, TimeUnit.SECONDS);
    assertThat(reader.seek(position)).isTrue();
    assertThat(reader.hasNext()).isTrue();
    reader.next().readValue(expected);
    assertThat(expected).isEqualTo(value);
  }

  @Test
  public void shouldAppendMultipleEvents() throws InterruptedException {
    // given
    final var values = List.of(new Value(1), new Value(2));
    final var latch = new CountDownLatch(1);

    // when
    final var lowestPosition = writer.valueWriter(values.get(0)).tryWrite();
    final var highestPosition = writer.valueWriter(values.get(1)).tryWrite();
    logStorageRule.setPositionListener(i -> latch.countDown());
    schedulerRule.submitActor(appender).join();

    // then
    final Value expected = new Value();
    latch.await(5, TimeUnit.SECONDS);

    // make sure we read the correct lowest/highest positions
    verify(logStorage, timeout(1000).times(1))
        .append(
            eq(lowestPosition),
            eq(highestPosition),
            any(ByteBuffer.class),
            any(AppendListener.class));

    // ensure events were written properly
    assertThat(reader.seek(lowestPosition)).isTrue();
    for (final var value : values) {
      assertThat(reader.hasNext()).isTrue();
      reader.next().readValue(expected);
      assertThat(expected).isEqualTo(value);
    }
  }

  private static final class Value implements BufferWriter, BufferReader {
    private int value;

    private Value() {}

    private Value(final int value) {
      this.value = value;
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final Value value1 = (Value) o;
      return value == value1.value;
    }

    @Override
    public void wrap(final DirectBuffer buffer, final int offset, final int length) {
      value = buffer.getInt(offset, Protocol.ENDIANNESS);
    }

    @Override
    public int getLength() {
      return Integer.BYTES;
    }

    @Override
    public void write(final MutableDirectBuffer buffer, final int offset) {
      buffer.putInt(offset, value, Protocol.ENDIANNESS);
    }
  }
}
