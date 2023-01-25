/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.util;

import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.Objects;
import org.assertj.core.api.AbstractObjectAssert;

public record TestEntry(
    long key, int sourceIndex, UnifiedRecordValue recordValue, RecordMetadata recordMetadata)
    implements LogAppendEntry {

  public static LogAppendEntry ofDefaults() {
    return new TestLogAppendEntryBuilder().build();
  }

  public static TestLogAppendEntryBuilder builder() {
    return new TestLogAppendEntryBuilder();
  }

  public static LogAppendEntry ofKey(final long key) {
    return new TestLogAppendEntryBuilder().withKey(key).build();
  }

  @Override
  public boolean needsProcessing() {
    return true;
  }

  public static final class TestEntryAssert
      extends AbstractObjectAssert<TestEntryAssert, LogAppendEntry> {

    private TestEntryAssert(final LogAppendEntry testEntry) {
      super(testEntry, TestEntryAssert.class);
    }

    public static TestEntryAssert assertThatEntry(final LogAppendEntry entry) {
      return new TestEntryAssert(entry);
    }

    public TestEntryAssert matchesLoggedEvent(final LoggedEvent loggedEvent) {
      if (actual.key() != -1 && actual.key() != loggedEvent.getKey()) {
        throw failureWithActualExpected(
            actual.key(),
            loggedEvent.getKey(),
            "Key <%s> was set on LogAppendEntry but LoggedEvent has key <%s>",
            actual.key(),
            loggedEvent.getKey());
      }

      final var loggedMetadata = new RecordMetadata();
      final var loggedValue = new UnifiedRecordValue();
      loggedEvent.readValue(loggedValue);
      loggedEvent.readMetadata(loggedMetadata);

      if (!Objects.equals(actual.recordValue(), loggedValue)) {
        throw failureWithActualExpected(
            actual.recordValue(),
            loggedValue,
            "LoggedEvent has a different value than LogAppendEntry");
      }

      if (!Objects.equals(actual.recordMetadata(), loggedMetadata)) {
        throw failureWithActualExpected(
            actual.recordMetadata(),
            loggedMetadata,
            "LoggedEvent has different metadata than LogAppendEntry");
      }
      return this;
    }
  }

  public static class TestLogAppendEntryBuilder {
    private long key = -1L;
    private int sourceIndex = -1;
    private UnifiedRecordValue recordValue = new UnifiedRecordValue();
    private RecordMetadata recordMetadata = new RecordMetadata().intent(Intent.UNKNOWN);

    public TestLogAppendEntryBuilder withKey(final long key) {
      this.key = key;
      return this;
    }

    public TestLogAppendEntryBuilder withSourceIndex(final int sourceIndex) {
      this.sourceIndex = sourceIndex;
      return this;
    }

    public TestLogAppendEntryBuilder withRecordValue(final UnifiedRecordValue recordValue) {
      this.recordValue = recordValue;
      return this;
    }

    public TestLogAppendEntryBuilder withRecordMetadata(final RecordMetadata recordMetadata) {
      this.recordMetadata = recordMetadata;
      return this;
    }

    public LogAppendEntry build() {
      return new TestEntry(key, sourceIndex, recordValue, recordMetadata);
    }
  }
}
