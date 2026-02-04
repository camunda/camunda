/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.zeebe.exporter.filter.config.TestConfiguration;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import org.junit.jupiter.api.Test;

final class DefaultRecordFilterTest {

  @Test
  void shouldAcceptOnlyConfiguredRecordTypes() {
    // given
    final var configuration =
        new TestConfiguration()
            .withIndexedRecordType(RecordType.EVENT)
            .withIndexedRecordType(RecordType.COMMAND);

    final var filter = new DefaultRecordFilter(configuration);

    // when / then
    assertThat(filter.acceptType(RecordType.EVENT)).isTrue();
    assertThat(filter.acceptType(RecordType.COMMAND)).isTrue();

    assertThat(filter.acceptType(RecordType.COMMAND_REJECTION)).isFalse();
  }

  @Test
  void shouldAcceptOnlyConfiguredValueTypes() {
    // given
    final var configuration =
        new TestConfiguration()
            .withIndexedValueType(ValueType.VARIABLE)
            .withIndexedValueType(ValueType.PROCESS_INSTANCE);

    final var filter = new DefaultRecordFilter(configuration);

    // when / then
    assertThat(filter.acceptValue(ValueType.VARIABLE)).isTrue();
    assertThat(filter.acceptValue(ValueType.PROCESS_INSTANCE)).isTrue();

    assertThat(filter.acceptValue(ValueType.JOB)).isFalse();
  }

  @Test
  void shouldNotTreatRequiredOnlyValueTypesAsNormalIndexed() {
    // given
    final var configuration =
        new TestConfiguration()
            // Only mark JOB as "required", not normal
            .withRequiredValueType(ValueType.JOB);

    final var filter = new DefaultRecordFilter(configuration);

    // when / then
    assertThat(filter.acceptValue(ValueType.JOB))
        .as("Required-only value types should not be accepted as normal indexed types")
        .isFalse();

    assertThat(configuration.shouldIndexRequiredValueType(ValueType.JOB)).isTrue();
  }

  @Test
  void shouldAcceptAllRecordsWithEmptyFilterChain() {
    // given
    final var configuration = new TestConfiguration();
    final var filter = new DefaultRecordFilter(configuration);
    final Record<?> record = mock(Record.class);

    // when
    final boolean accepted = filter.acceptRecord(record);

    // then
    assertThat(accepted).isTrue();
  }
}
