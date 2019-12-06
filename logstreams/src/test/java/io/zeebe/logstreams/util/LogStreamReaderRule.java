/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.rules.ExternalResource;

public class LogStreamReaderRule extends ExternalResource {

  private final LogStreamRule logStreamRule;
  private LogStreamReader logStreamReader;

  public LogStreamReaderRule(final LogStreamRule logStreamRule) {
    this.logStreamRule = logStreamRule;
  }

  @Override
  protected void before() {
    final SynchronousLogStream logStream = logStreamRule.getLogStream();
    logStreamReader = logStream.newLogStreamReader();
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

  public LogStreamReader resetReader() {
    logStreamReader = logStreamRule.newLogStreamReader();
    return logStreamReader;
  }
}
