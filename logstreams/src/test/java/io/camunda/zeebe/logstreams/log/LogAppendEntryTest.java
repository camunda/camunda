/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.log;

import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class LogAppendEntryTest {

  @Test
  public void shouldWrapValues() {
    // given
    final var recordMetadata = new RecordMetadata();
    final var unifiedRecordValue = new UnifiedRecordValue();

    // when
    final var logAppendEntry = LogAppendEntry.of(recordMetadata, unifiedRecordValue);

    // then
    Assertions.assertThat(logAppendEntry.recordValue()).isEqualTo(unifiedRecordValue);
    Assertions.assertThat(logAppendEntry.recordMetadata()).isEqualTo(recordMetadata);
  }

  @Test
  public void shouldNotBeProcessedPerDefault() {
    // given
    final var recordMetadata = new RecordMetadata();
    final var unifiedRecordValue = new UnifiedRecordValue();

    // when
    final var logAppendEntry = LogAppendEntry.of(recordMetadata, unifiedRecordValue);

    // then
    Assertions.assertThat(logAppendEntry.isProcessed()).isFalse();
    Assertions.assertThat(logAppendEntry.recordValue()).isEqualTo(unifiedRecordValue);
    Assertions.assertThat(logAppendEntry.recordMetadata()).isEqualTo(recordMetadata);
  }

  @Test
  public void shouldMarkEntryAsProcessed() {
    // given
    final var recordMetadata = new RecordMetadata();
    final var unifiedRecordValue = new UnifiedRecordValue();
    final var logAppendEntry = LogAppendEntry.of(recordMetadata, unifiedRecordValue);

    // when
    final var processedEntry = LogAppendEntry.ofProcessed(logAppendEntry);

    // then
    Assertions.assertThat(processedEntry.isProcessed()).isTrue();
    Assertions.assertThat(logAppendEntry.recordValue()).isEqualTo(unifiedRecordValue);
    Assertions.assertThat(logAppendEntry.recordMetadata()).isEqualTo(recordMetadata);
  }
}
