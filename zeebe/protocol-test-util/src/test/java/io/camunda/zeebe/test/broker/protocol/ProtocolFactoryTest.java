/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.broker.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.ImmutableProtocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordAssert;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.ValueTypeMapping;
import java.util.EnumSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@Execution(ExecutionMode.CONCURRENT)
final class ProtocolFactoryTest {
  private final ProtocolFactory factory = new ProtocolFactory();

  @Test
  void shouldUseSameSeedOnConstruction() {
    // given
    final var factoryB = new ProtocolFactory();

    // when
    final var seedA = factory.getSeed();
    final var seedB = factoryB.getSeed();

    // then
    assertThat(seedA).isEqualTo(seedB);
  }

  @Test
  void shouldGenerateRecordDeterministically() {
    // given
    final var factoryB = new ProtocolFactory(factory.getSeed());

    // when
    final var recordA = factory.generateRecord();
    final var recordB = factoryB.generateRecord();

    // then
    assertThat(recordA).isEqualTo(recordB);
  }

  @Test
  void shouldGenerateRecordsDeterministically() {
    // given
    final var factoryB = new ProtocolFactory(factory.getSeed());

    // when
    final var recordsA = factory.generateRecords().limit(5).collect(Collectors.toList());
    final var recordsB = factoryB.generateRecords().limit(5).collect(Collectors.toList());

    // then
    assertThat(recordsA).containsExactlyElementsOf(recordsB);
  }

  @Test
  void shouldRandomizeRecordsWithDifferentSeeds() {
    // given
    final var factoryB = new ProtocolFactory(1L);

    // when
    final var recordA = factory.generateRecord();
    final var recordB = factoryB.generateRecord();

    // then
    assertThat(recordA).isNotEqualTo(recordB);
  }

  @Test
  void shouldRandomizeRecords() {
    // given

    // then
    assertThat(factory.generateRecord())
        .isNotEqualTo(factory.generateRecord())
        .isNotEqualTo(factory.generateRecord());
  }

  @ParameterizedTest
  @MethodSource("provideValueTypes")
  void shouldSetAllPropertiesOfGeneratedRecordValue(final ValueType valueType) {
    // given
    final var valueClass = ValueTypeMapping.get(valueType).getValueClass();

    // when
    final var recordValue = factory.generateObject(valueClass);

    // then
    assertThat(recordValue).hasNoNullFieldsOrProperties();
  }

  @Test
  void shouldSetAllPropertiesOfGeneratedRecord() {
    // given

    // when
    final var record = factory.generateRecord();

    // then
    assertThat(record).hasNoNullFieldsOrProperties();
  }

  @Test
  void shouldGenerateForAllAcceptedValueTypes() {
    // given
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
    final var valueTypeMapping = ValueTypeMapping.get(valueType);

    // when
    final var record = factory.generateRecord(valueType);

    // then
    RecordAssert.assertThat(record).hasValueType(valueType);
    assertThat(record.getValue()).isInstanceOf(valueTypeMapping.getValueClass());
    assertThat(record.getIntent()).isInstanceOf(valueTypeMapping.getIntentClass());
  }

  @ParameterizedTest
  @MethodSource("provideProtocolClasses")
  void shouldGenerateForAllProtocolClasses(final Class<?> protocolClass) {
    // given

    // when - Record cannot be generated directly due to its generic parameter
    final Object generatedObject =
        Record.class.equals(protocolClass)
            ? factory.generateRecord()
            : factory.generateObject(protocolClass);

    // then
    assertThat(generatedObject).isInstanceOf(protocolClass);
    assertThat(generatedObject.getClass())
        .as("should be an Immutable implementation of the protocol class")
        .isNotEqualTo(protocolClass)
        .hasAnnotation(ImmutableProtocol.Type.class);
  }

  @Test
  void shouldOnlyGeneratedAcceptedRecordType() {
    // given
    final var acceptedRecordTypes =
        EnumSet.complementOf(EnumSet.of(RecordType.NULL_VAL, RecordType.SBE_UNKNOWN));

    // when
    final var records = factory.generateRecords().limit(10).toList();

    // then
    assertThat(records).extracting(Record::getRecordType).hasSameElementsAs(acceptedRecordTypes);
  }

  @Test
  void shouldNeverProduceANegativeLong() {
    // given

    // when
    final var generatedValues =
        IntStream.range(0, 10)
            .mapToLong(ignored -> factory.generateObject(Long.class))
            .boxed()
            .toList();

    // then
    assertThat(generatedValues).allSatisfy(value -> assertThat(value).isNotNegative());
  }

  @Test
  void shouldNeverProduceANegativePrimitiveLong() {
    // given

    // when
    final var generatedValues =
        IntStream.range(0, 10)
            .mapToLong(ignored -> factory.generateObject(long.class))
            .boxed()
            .toList();

    // then
    assertThat(generatedValues).allSatisfy(value -> assertThat(value).isNotNegative());
  }

  private static Stream<ValueType> provideValueTypes() {
    return ValueTypeMapping.getAcceptedValueTypes().stream();
  }

  private static Stream<Class<?>> provideProtocolClasses() {
    return ProtocolFactory.findProtocolTypes().loadClasses().stream();
  }
}
