/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.log;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.Arrays;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

final class LogAppendEntryMetadataTest {

  @Property
  void shouldCopyMetadata(
      @ForAll("recordTypes") final RecordType recordType,
      @ForAll("valueTypes") final ValueType valueType,
      @ForAll("intents") final Intent intent) {
    // given
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

  @Provide
  Arbitrary<RecordType> recordTypes() {
    return Arbitraries.of(
        Arrays.stream(RecordType.values()).filter(v -> v != RecordType.SBE_UNKNOWN).toList());
  }

  @Provide
  Arbitrary<ValueType> valueTypes() {
    return Arbitraries.of(
        Arrays.stream(ValueType.values()).filter(v -> v != ValueType.SBE_UNKNOWN).toList());
  }

  @Provide
  Arbitrary<Intent> intents() {
    return Arbitraries.of(
        Intent.INTENT_CLASSES.stream()
            .flatMap(clazz -> Arrays.stream(clazz.getEnumConstants()))
            .distinct()
            .toList());
  }
}
