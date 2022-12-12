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
import io.camunda.zeebe.util.ReflectUtil;
import org.assertj.core.api.AbstractObjectAssert;
import org.assertj.core.api.Assertions;

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

  public static final class TestEntryAssert
      extends AbstractObjectAssert<TestEntryAssert, LogAppendEntry> {

    private TestEntryAssert(final LogAppendEntry testEntry, final Class<?> selfType) {
      super(testEntry, selfType);
    }

    public static TestEntryAssert assertThatEntry(final LogAppendEntry entry) {
      return new TestEntryAssert(entry, TestEntryAssert.class);
    }

    public TestEntryAssert matchesLoggedEvent(final LoggedEvent loggedEvent) {
      if (actual.key() != -1) {
        Assertions.assertThat(actual.key()).isEqualTo(loggedEvent.getKey());
      }
      final var loggedValue = ReflectUtil.newInstance(actual.recordValue().getClass());
      loggedValue.wrap(
          loggedEvent.getValueBuffer(), loggedEvent.getValueOffset(), loggedEvent.getValueLength());
      final var loggedMetadata = new RecordMetadata();
      loggedMetadata.wrap(
          loggedEvent.getMetadata(),
          loggedEvent.getMetadataOffset(),
          loggedEvent.getMetadataLength());

      Assertions.assertThat(actual.recordValue()).isEqualTo(loggedValue);
      Assertions.assertThat(actual.recordMetadata()).isEqualTo(loggedMetadata);
      return this;
    }
  }

  public static class TestLogAppendEntryBuilder {
    private long key = -1L;
    private int sourceIndex = -1;
    private UnifiedRecordValue recordValue = new TestRecordValue();
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

  public static final class TestRecordValue extends UnifiedRecordValue {}
}
