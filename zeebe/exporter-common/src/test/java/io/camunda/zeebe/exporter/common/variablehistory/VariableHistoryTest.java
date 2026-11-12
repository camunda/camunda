/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.variablehistory;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.record.value.variable.VariableSourceRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VariableHistoryTest {

  private static final String COLLECTION = "[{\"a\":[1,2]}, \"x,]\\\"\" ,null]";

  private final ProtocolFactory factory = new ProtocolFactory();

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {"0|{\"a\":[1,2]}", "1|\"x,]\\\"\"", "2|null"})
  void shouldRecordOnlyTheItemOfTheFilledSlot(final int slot, final String item) {
    // given
    final var record = outputCollectionUpdate(COLLECTION, slot);

    // when
    final var value = VariableHistory.valueOf(record, true);

    // then
    assertThat(value).isEqualTo(item);
  }

  @Test
  void shouldRecordTheFullValueWhenSwitchedOff() {
    // given
    final var record = outputCollectionUpdate(COLLECTION, 1);

    // when
    final var value = VariableHistory.valueOf(record, false);

    // then
    assertThat(value).isEqualTo(COLLECTION);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {"[1,2]|-1", "[1,2]|2", "[]|0", "{\"a\":1}|0"})
  void shouldRecordTheFullValueWithoutAnItemInTheSlot(final String collection, final int slot) {
    // given
    final var record = outputCollectionUpdate(collection, slot);

    // when
    final var value = VariableHistory.valueOf(record, true);

    // then
    assertThat(value).isEqualTo(collection);
  }

  private Record<VariableRecordValue> outputCollectionUpdate(
      final String collection, final int slot) {
    final VariableRecordValue recordValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withValue(collection)
            .withSource(VariableSourceRecord.engine().setCollectionIndex(slot))
            .build();
    return factory.generateRecord(
        ValueType.VARIABLE, r -> r.withIntent(VariableIntent.UPDATED).withValue(recordValue));
  }
}
