/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.log;

import static dev.hegel.Generators.sampledFrom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.hegel.Generator;
import dev.hegel.HegelTest;
import dev.hegel.TestCase;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.Arrays;
import java.util.List;

final class LogAppendEntryMetadataTest {

  private static final Generator<RecordType> RECORD_TYPES =
      sampledFrom(
          Arrays.stream(RecordType.values()).filter(v -> v != RecordType.SBE_UNKNOWN).toList());
  private static final Generator<ValueType> VALUE_TYPES =
      sampledFrom(
          Arrays.stream(ValueType.values()).filter(v -> v != ValueType.SBE_UNKNOWN).toList());
  private static final Generator<Intent> INTENTS =
      sampledFrom(
          Intent.INTENT_CLASSES.stream()
              .<Intent>flatMap(clazz -> Arrays.stream(clazz.getEnumConstants()))
              .distinct()
              .toList());

  @HegelTest
  void shouldCopyMetadata(final TestCase tc) {
    // given
    final var recordType = tc.draw(RECORD_TYPES, "recordType");
    final var valueType = tc.draw(VALUE_TYPES, "valueType");
    final var intent = tc.draw(INTENTS, "intent");
    final var compatibleIntent = Intent.fromProtocolValue(valueType, intent.value());
    final var entries = List.of(createEntry(recordType, valueType, compatibleIntent));

    // when
    final var metadata = LogAppendEntryMetadata.copyMetadata(entries);

    // then
    assertThat(metadata.size()).isEqualTo(1);
    assertThat(metadata.recordType(0)).isEqualTo(recordType);
    assertThat(metadata.valueType(0)).isEqualTo(valueType);
    assertThat(metadata.intent(0)).isEqualTo(compatibleIntent);
  }

  private LogAppendEntry createEntry(
      final RecordType recordType, final ValueType valueType, final Intent intent) {
    final var metadata =
        new RecordMetadata().recordType(recordType).valueType(valueType).intent(intent);
    return LogAppendEntry.of(metadata, new UnifiedRecordValue(0));
  }
}
