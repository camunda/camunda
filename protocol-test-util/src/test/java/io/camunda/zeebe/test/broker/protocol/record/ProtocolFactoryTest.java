/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.broker.protocol.record;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordAssert;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.util.ValueTypeMapping;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@Execution(ExecutionMode.CONCURRENT)
final class ProtocolFactoryTest {
  @Test
  void shouldUseADifferentSeedOnConstruction() {
    // given
    final var factoryA = new ProtocolFactory();
    final var factoryB = new ProtocolFactory();

    // when
    final var seedA = factoryA.getSeed();
    final var seedB = factoryB.getSeed();

    // then
    assertThat(seedA).isNotEqualTo(seedB);
  }

  @Test
  void shouldGenerateRecordDeterministically() {
    // given
    final var factoryA = new ProtocolFactory();
    final var factoryB = new ProtocolFactory(factoryA.getSeed());

    // when
    final var recordA = factoryA.generateRecord();
    final var recordB = factoryB.generateRecord();

    // then
    assertThat(recordA).isEqualTo(recordB);
  }

  @Test
  void shouldGenerateRecordsDeterministically() {
    // given
    final var factoryA = new ProtocolFactory();
    final var factoryB = new ProtocolFactory(factoryA.getSeed());

    // when
    final var recordsA = factoryA.generateRecords().limit(5).collect(Collectors.toList());
    final var recordsB = factoryB.generateRecords().limit(5).collect(Collectors.toList());

    // then
    assertThat(recordsA).containsExactlyElementsOf(recordsB);
  }

  @ParameterizedTest
  @MethodSource("provideValueTypes")
  void shouldSetAllPropertiesOfGeneratedRecordValue(final ValueType valueType) {
    // given
    final var factory = new ProtocolFactory();
    final var valueClass = ValueTypeMapping.get(valueType).getValueClass();

    // when
    final var recordValue = factory.generateObject(valueClass);

    // then
    assertThat(recordValue).hasNoNullFieldsOrProperties();
  }

  @Test
  void shouldSetAllPropertiesOfGeneratedRecord() {
    // given
    final var factory = new ProtocolFactory();

    // when
    final var record = factory.generateRecord();

    // then
    assertThat(record).hasNoNullFieldsOrProperties();
  }

  @Test
  void shouldGenerateForAllAcceptedValueTypes() {
    // given
    final var factory = new ProtocolFactory();
    final var acceptedValueTypes = ValueTypeMapping.getAcceptedValueTypes();

    // when
    final var records = factory.generateForAllValueTypes().toList();

    // then
    assertThat(records)
        .as("should generate one record for each value type")
        .map(Record::getValueType)
        .containsExactlyInAnyOrderElementsOf(acceptedValueTypes);
  }

  @ParameterizedTest
  @MethodSource("provideValueTypes")
  void shouldGenerateRecordWithCorrectValueAndIntentTypes(final ValueType valueType) {
    // given
    final var factory = new ProtocolFactory();
    final var valueTypeMapping = ValueTypeMapping.get(valueType);

    // when
    final var record = factory.generateRecord(valueType);

    // then
    RecordAssert.assertThat(record).hasValueType(valueType);
    assertThat(record.getValue()).isInstanceOf(valueTypeMapping.getValueClass());
    assertThat(record.getIntent()).isInstanceOf(valueTypeMapping.getIntentClass());
  }

  private static Stream<ValueType> provideValueTypes() {
    return ValueTypeMapping.getAcceptedValueTypes().stream();
  }
}
