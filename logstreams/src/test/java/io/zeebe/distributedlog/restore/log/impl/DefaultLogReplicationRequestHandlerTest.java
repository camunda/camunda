/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore.log.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.distributedlog.restore.log.LogReplicationRequest;
import io.zeebe.distributedlog.restore.log.LogReplicationResponse;
import io.zeebe.logstreams.impl.LoggedEventImpl;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.util.LogStreamReaderRule;
import io.zeebe.logstreams.util.LogStreamRule;
import io.zeebe.logstreams.util.LogStreamWriterRule;
import io.zeebe.test.util.MsgPackUtil;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.slf4j.helpers.NOPLogger;

public class DefaultLogReplicationRequestHandlerTest {
  private static final TemporaryFolder LOG_FOLDER = new TemporaryFolder();
  private static final LogStreamRule LOG_STREAM_RULE = new LogStreamRule(LOG_FOLDER);
  private static final LogStreamWriterRule LOG_STREAM_WRITER_RULE =
      new LogStreamWriterRule(LOG_STREAM_RULE);
  private static final LogStreamReaderRule LOG_STREAM_READER_RULE =
      new LogStreamReaderRule(LOG_STREAM_RULE);

  @ClassRule
  public static final RuleChain CHAIN =
      RuleChain.outerRule(LOG_FOLDER)
          .around(LOG_STREAM_RULE)
          .around(LOG_STREAM_WRITER_RULE)
          .around(LOG_STREAM_READER_RULE);

  private static final List<LoggedEvent> EVENTS = new ArrayList<>();

  @BeforeClass
  public static void prepareLog() {
    final DirectBuffer event = MsgPackUtil.asMsgPack("{}");
    LOG_STREAM_WRITER_RULE.writeEvents(10, event);
    EVENTS.addAll(LOG_STREAM_READER_RULE.readEvents());
  }

  @Test
  public void shouldReplicateRequestedEvents() {
    // given
    final EventRange events = new EventRange(EVENTS.subList(1, EVENTS.size()));
    final LogReplicationRequest request =
        new DefaultLogReplicationRequest(EVENTS.get(0).getPosition(), events.lastPosition);
    final DefaultLogReplicationRequestHandler handler =
        new DefaultLogReplicationRequestHandler(LOG_STREAM_RULE.getLogStream());

    // when
    final LogReplicationResponse response =
        handler.onReplicationRequest(request, NOPLogger.NOP_LOGGER);

    // then
    assertThat(response.getToPosition()).isEqualTo(events.lastPosition);
    assertThat(response.hasMoreAvailable()).isFalse();
    assertThat(response.getSerializedEvents()).isEqualTo(events.serialized);
    assertThat(response.isValid()).isTrue();
  }

  @Test
  public void shouldOnlyReplicateAsMuchAsFitsTheBuffer() {
    // given
    final EventRange events = new EventRange(EVENTS.subList(0, 5));
    final LogReplicationRequest request =
        new DefaultLogReplicationRequest(-1, EVENTS.get(5).getPosition());
    final DefaultLogReplicationRequestHandler handler =
        new DefaultLogReplicationRequestHandler(
            LOG_STREAM_RULE.getLogStream(), events.serialized.length + 1);

    // when
    final LogReplicationResponse response =
        handler.onReplicationRequest(request, NOPLogger.NOP_LOGGER);

    // then
    assertThat(response.getToPosition()).isEqualTo(events.lastPosition);
    assertThat(response.hasMoreAvailable()).isTrue();
    assertThat(response.getSerializedEvents()).isEqualTo(events.serialized);
    assertThat(response.isValid()).isTrue();
  }

  @Test
  public void shouldReplicateUpToRequestedPosition() {
    // given
    final int requestedCount = 8;
    final EventRange events = new EventRange(EVENTS.subList(0, requestedCount));
    final LogReplicationRequest request = new DefaultLogReplicationRequest(-1, events.lastPosition);
    final DefaultLogReplicationRequestHandler handler =
        new DefaultLogReplicationRequestHandler(LOG_STREAM_RULE.getLogStream());

    // when
    final LogReplicationResponse response =
        handler.onReplicationRequest(request, NOPLogger.NOP_LOGGER);

    // then
    assertThat(response.getToPosition()).isEqualTo(events.lastPosition);
    assertThat(response.hasMoreAvailable()).isFalse();
    assertThat(response.getSerializedEvents()).isEqualTo(events.serialized);
    assertThat(response.isValid()).isTrue();
  }

  @Test
  public void shouldReplicateFromRequestedPositionExclusive() {
    // given
    final int requestedCount = 5;
    final EventRange events = new EventRange(EVENTS.subList(requestedCount, EVENTS.size()));
    final LogReplicationRequest request =
        new DefaultLogReplicationRequest(
            EVENTS.get(requestedCount - 1).getPosition(), events.lastPosition);
    final DefaultLogReplicationRequestHandler handler =
        new DefaultLogReplicationRequestHandler(LOG_STREAM_RULE.getLogStream());

    // when
    final LogReplicationResponse response =
        handler.onReplicationRequest(request, NOPLogger.NOP_LOGGER);

    // then
    assertThat(deserialize(response.getSerializedEvents()).get(0).getPosition())
        .isEqualTo(events.firstPosition);
    assertThat(response.getToPosition()).isEqualTo(events.lastPosition);
    assertThat(response.hasMoreAvailable()).isFalse();
    assertThat(response.getSerializedEvents()).isEqualTo(events.serialized);
    assertThat(response.isValid()).isTrue();
  }

  @Test
  public void shouldReplicateFromRequestedPositionInclusive() {
    // given
    final int requestedCount = 5;
    final EventRange events = new EventRange(EVENTS.subList(requestedCount, EVENTS.size()));
    final LogReplicationRequest request =
        new DefaultLogReplicationRequest(
            EVENTS.get(requestedCount).getPosition(), events.lastPosition, true);
    final DefaultLogReplicationRequestHandler handler =
        new DefaultLogReplicationRequestHandler(LOG_STREAM_RULE.getLogStream());

    // when
    final LogReplicationResponse response =
        handler.onReplicationRequest(request, NOPLogger.NOP_LOGGER);

    // then
    assertThat(deserialize(response.getSerializedEvents()).get(0).getPosition())
        .isEqualTo(events.firstPosition);
    assertThat(response.getToPosition()).isEqualTo(events.lastPosition);
    assertThat(response.hasMoreAvailable()).isFalse();
    assertThat(response.getSerializedEvents()).isEqualTo(events.serialized);
    assertThat(response.isValid()).isTrue();
  }

  @Test
  public void shouldHaveNoEventsIfFromPositionIsNotFound() {
    // given
    final EventRange events = new EventRange(EVENTS);
    final LogReplicationRequest request =
        new DefaultLogReplicationRequest(events.lastPosition + 1, events.lastPosition + 2);
    final DefaultLogReplicationRequestHandler handler =
        new DefaultLogReplicationRequestHandler(LOG_STREAM_RULE.getLogStream());

    // when
    final LogReplicationResponse response =
        handler.onReplicationRequest(request, NOPLogger.NOP_LOGGER);

    // then
    assertThat(response.hasMoreAvailable()).isFalse();
    assertThat(response.isValid()).isFalse();
    assertThat(response.getSerializedEvents()).isNullOrEmpty();
  }

  @Test
  public void shouldReplicateFromFirstPositionIfFromIsNegative() {
    // given
    final EventRange events = new EventRange(EVENTS);
    final LogReplicationRequest request = new DefaultLogReplicationRequest(-1, events.lastPosition);
    final DefaultLogReplicationRequestHandler handler =
        new DefaultLogReplicationRequestHandler(LOG_STREAM_RULE.getLogStream());

    // when
    final LogReplicationResponse response =
        handler.onReplicationRequest(request, NOPLogger.NOP_LOGGER);

    // then
    assertThat(response.getToPosition()).isEqualTo(events.lastPosition);
    assertThat(response.hasMoreAvailable()).isFalse();
    assertThat(response.getSerializedEvents()).isEqualTo(events.serialized);
    assertThat(response.isValid()).isTrue();
  }

  private List<LoggedEvent> deserialize(byte[] serialized) {
    final DirectBuffer buffer = new UnsafeBuffer(serialized);
    final List<LoggedEvent> events = new ArrayList<>();
    int offset = 0;

    do {
      final LoggedEventImpl event = new LoggedEventImpl();
      event.wrap(buffer, offset);
      events.add(event);
      offset += event.getFragmentLength();
    } while (offset < buffer.capacity());

    return events;
  }

  static class EventRange {
    final List<LoggedEvent> events;
    final long firstPosition;
    final long lastPosition;
    final byte[] serialized;

    EventRange(List<LoggedEvent> events) {
      this.events = events;
      this.firstPosition = events.get(0).getPosition();
      this.lastPosition = events.get(events.size() - 1).getPosition();
      this.serialized = serialize(events);
    }

    private byte[] serialize(List<LoggedEvent> events) {
      final int bufferSize = events.stream().mapToInt(LoggedEvent::getLength).sum();
      final byte[] buffer = new byte[bufferSize];
      final MutableDirectBuffer copyBuffer = new UnsafeBuffer(buffer);

      for (int i = 0, offset = 0; i < events.size(); offset += events.get(i).getLength(), i++) {
        events.get(i).write(copyBuffer, offset);
      }

      return buffer;
    }
  }
}
