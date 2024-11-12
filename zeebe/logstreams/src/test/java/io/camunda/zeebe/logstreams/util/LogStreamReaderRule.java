/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.rules.ExternalResource;

public final class LogStreamReaderRule extends ExternalResource {

  private final LogStreamRule logStreamRule;
  private LogStreamReader logStreamReader;

  public LogStreamReaderRule(final LogStreamRule logStreamRule) {
    this.logStreamRule = logStreamRule;
  }

  @Override
  protected void before() {
    final TestLogStream logStream = logStreamRule.getLogStream();
    logStreamReader = logStream.newLogStreamReader();
  }

  @Override
  protected void after() {
    logStreamReader.close();
  }

  public LogStreamReader getLogStreamReader() {
    return logStreamReader;
  }

  public LoggedEvent nextEvent() {
    assertThat(logStreamReader.hasNext()).isTrue();
    return logStreamReader.next();
  }

  public LoggedEvent readEventAtPosition(final long position) {
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
