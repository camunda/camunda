/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.logstreams.impl.LoggedEventImpl;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.rules.ExternalResource;

public class LogStreamReaderRule extends ExternalResource {

  private final LogStreamRule logStreamRule;
  private final LogStreamReader logStreamReader;

  public LogStreamReaderRule(final LogStreamRule logStreamRule) {
    this.logStreamRule = logStreamRule;
    logStreamReader = new BufferedLogStreamReader();
  }

  @Override
  protected void before() {
    final LogStream logStream = logStreamRule.getLogStream();
    logStreamReader.wrap(logStream);
  }

  @Override
  protected void after() {
    logStreamReader.close();
  }

  public LogStreamReader getLogStreamReader() {
    return logStreamReader;
  }

  public LoggedEvent assertEvents(final int eventCount, final DirectBuffer event) {
    LoggedEvent lastEvent = null;

    for (int i = 1; i <= eventCount; i++) {
      lastEvent = nextEvent();
      assertThat(lastEvent.getKey()).isEqualTo(i);
      assertThat(eventValue(lastEvent)).isEqualTo(event);
    }

    return lastEvent;
  }

  public LoggedEvent nextEvent() {
    assertThat(logStreamReader.hasNext()).isTrue();
    return logStreamReader.next();
  }

  public LoggedEvent readEventAtPosition(long position) {
    while (logStreamReader.hasNext()) {
      final LoggedEvent event = logStreamReader.next();
      if (event.getPosition() == position) {
        return event;
      }
    }
    return null;
  }

  private DirectBuffer eventValue(final LoggedEvent event) {
    assertThat(event).isNotNull();
    return new UnsafeBuffer(event.getValueBuffer(), event.getValueOffset(), event.getValueLength());
  }

  public List<LoggedEvent> readEvents() {
    return readEvents(-1, -1);
  }

  public List<LoggedEvent> readEvents(long from, long to) {
    final List<LoggedEvent> events = new ArrayList<>();

    if (from > 0) {
      logStreamReader.seek(from);
    } else {
      logStreamReader.seekToFirstEvent();
    }

    while (logStreamReader.hasNext()) {
      final LoggedEvent event = logStreamReader.next();
      if (to > 0 && event.getPosition() > to) {
        break;
      }

      if (event.getPosition() >= from) {
        events.add(copyEvent(event));
      }
    }

    return events;
  }

  private LoggedEvent copyEvent(LoggedEvent event) {
    final LoggedEventImpl copy = new LoggedEventImpl();
    final MutableDirectBuffer copyBuffer = new UnsafeBuffer(new byte[event.getLength()]);
    event.write(copyBuffer, 0);
    copy.wrap(copyBuffer, 0);

    return copy;
  }
}
