/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.exporter.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class MetricsExporterTest {

  /** Defines a combination of a RecordType and a ValueType. */
  static class TypeCombination {
    RecordType recordType;
    ValueType valueType;

    public TypeCombination(final RecordType recordType, final ValueType valueType) {
      this.recordType = recordType;
      this.valueType = valueType;
    }

    public RecordType recordType() {
      return recordType;
    }

    public ValueType valueType() {
      return valueType;
    }

    @Override
    public int hashCode() {
      return Objects.hash(recordType, valueType);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final TypeCombination that = (TypeCombination) o;
      return recordType == that.recordType && valueType == that.valueType;
    }

    @Override
    public String toString() {
      return "TypeCombination{" + "recordType=" + recordType + ", valueType=" + valueType + '}';
    }
  }

  @TestInstance(Lifecycle.PER_CLASS)
  @Nested
  @DisplayName("MetricsExporter should configure a Filter")
  class FilterTest {

    Stream<TypeCombination> acceptedCombinations() {
      return Stream.of(
          new TypeCombination(RecordType.EVENT, ValueType.JOB),
          new TypeCombination(RecordType.EVENT, ValueType.JOB_BATCH),
          new TypeCombination(RecordType.EVENT, ValueType.PROCESS_INSTANCE));
    }

    /** Returns the inverse of {@link #acceptedCombinations()}. */
    Stream<TypeCombination> rejectedCombinations() {
      return allCombinations().filter(any -> acceptedCombinations().noneMatch(any::equals));
    }

    Stream<TypeCombination> allCombinations() {
      return Arrays.stream(RecordType.values())
          .flatMap(
              recordType ->
                  Arrays.stream(ValueType.values())
                      .map(valueType -> new TypeCombination(recordType, valueType)));
    }

    @ParameterizedTest
    @DisplayName("accepting records of specific RecordType and ValueType")
    @MethodSource("acceptedCombinations")
    void shouldConfigureFilterAccepting(final TypeCombination combination) throws Exception {
      // given
      final var recordType = combination.recordType();
      final var valueType = combination.valueType();
      final var context = new ExporterTestContext();

      // when
      new MetricsExporter().configure(context);

      // then
      final var recordFilter = context.getRecordFilter();
      assertThat(recordFilter.acceptType(recordType) && recordFilter.acceptValue(valueType))
          .describedAs(
              "Expect RecordFilter to accept record of RecordType %s and ValueType %s",
              recordType, valueType)
          .isTrue();
    }

    @ParameterizedTest
    @DisplayName("rejecting records of specific RecordType and ValueType")
    @MethodSource("rejectedCombinations")
    void shouldConfigureFilterRejecting(final TypeCombination combination) throws Exception {
      // given
      final var recordType = combination.recordType();
      final var valueType = combination.valueType();
      final var context = new ExporterTestContext();

      // when
      new MetricsExporter().configure(context);

      // then
      final var recordFilter = context.getRecordFilter();
      assertThat(recordFilter.acceptType(recordType) && recordFilter.acceptValue(valueType))
          .describedAs(
              "Expect RecordFilter to reject record of RecordType %s and ValueType %s",
              recordType, valueType)
          .isFalse();
    }
  }
}
