/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.exporter.api.context.Context.RecordFilter;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for the "required value type" behavior in the OpenSearch exporter filter, which is
 * implemented via {@link io.camunda.zeebe.exporter.common.filter.RequiredValueTypeFilter} and wired
 * through {@link OpensearchExporter}.
 *
 * <p>Covered branches:
 *
 * <ul>
 *   <li>Pre-8.8 brokers (major == 8, minor &lt; 8) → use {@code shouldIndexValueType}
 *   <li>Any broker with {@code includeEnabledRecords = true} → use {@code shouldIndexValueType}
 *   <li>8.8+ with {@code includeEnabledRecords = false} → use {@code shouldIndexRequiredValueType}
 *   <li>Required value types honoring index flags (index.* can still disable them)
 *   <li>Invalid broker version string → IllegalArgumentException
 * </ul>
 */
final class OpensearchRecordFilterRequiredValueTypeTest {

  private RecordFilter createFilter(final OpensearchExporterConfiguration config) {
    final var context =
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("opensearch", config));
    final var exporter = new OpensearchExporter();
    exporter.configure(context);
    return context.getRecordFilter();
  }

  @ParameterizedTest(
      name =
          "pre-8.8 (8.7.x), valueType={0}, enabledFlag={1} -> accepted={1} (uses shouldIndexValueType)")
  @CsvSource(
      value = {
        // valueType       | enabledFlag
        "MESSAGE          | true",
        "MESSAGE          | false",
        "PROCESS_INSTANCE | true",
        "PROCESS_INSTANCE | false"
      },
      delimiter = '|')
  void shouldUseNormalValueTypeConfigForPre88Brokers(
      final ValueType valueType, final boolean enabledFlag) {

    // given: includeEnabledRecords=false, but broker is 8.7.x
    // -> includeAllEnabledRecords == true
    final var config = new OpensearchExporterConfiguration();
    config.setIncludeEnabledRecords(false);

    // configure the normal index flags per value type
    switch (valueType) {
      case MESSAGE -> config.index.message = enabledFlag;
      case PROCESS_INSTANCE -> config.index.processInstance = enabledFlag;
      default -> throw new IllegalArgumentException("Test not set up for value type: " + valueType);
    }

    final var filter = createFilter(config);

    @SuppressWarnings("unchecked")
    final Record<?> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("8.7.3");
    when(record.getRecordType()).thenReturn(RecordType.EVENT); // event is enabled by default
    when(record.getValueType()).thenReturn(valueType);
    when(record.getIntent()).thenReturn(mock(Intent.class));

    // when
    final var accepted = filter.acceptRecord(record);

    // then: required-vs-nonrequired is ignored for pre-8.8; we follow index.* flag
    assertThat(accepted).isEqualTo(enabledFlag);
  }

  @ParameterizedTest(
      name = "includeEnabledRecords=true (8.9.x), valueType={0}, enabledFlag={1} -> accepted={1}")
  @CsvSource(
      value = {
        // valueType       | enabledFlag
        "MESSAGE          | true",
        "MESSAGE          | false",
        "PROCESS_INSTANCE | true",
        "PROCESS_INSTANCE | false"
      },
      delimiter = '|')
  void shouldUseNormalValueTypeConfigWhenIncludeEnabledRecordsIsTrue(
      final ValueType valueType, final boolean enabledFlag) {

    // given: includeEnabledRecords=true
    // -> includeAllEnabledRecords == true for any 8.x version
    final var config = new OpensearchExporterConfiguration();
    config.setIncludeEnabledRecords(true);

    switch (valueType) {
      case MESSAGE -> config.index.message = enabledFlag;
      case PROCESS_INSTANCE -> config.index.processInstance = enabledFlag;
      default -> throw new IllegalArgumentException("Test not set up for value type: " + valueType);
    }

    final var filter = createFilter(config);

    @SuppressWarnings("unchecked")
    final Record<?> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("8.9.0");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(valueType);
    when(record.getIntent()).thenReturn(mock(Intent.class));

    // when
    final var accepted = filter.acceptRecord(record);

    // then: we use shouldIndexValueType, i.e. index.* flag decides
    assertThat(accepted).isEqualTo(enabledFlag);
  }

  @ParameterizedTest(
      name =
          "8.9.x, includeEnabledRecords=false, valueType={0} -> accepted={1} (uses shouldIndexRequiredValueType)")
  @CsvSource(
      value = {
        // Required value types (Optimize / analytics), excluding VARIABLE (handled separately)
        "DEPLOYMENT       | true",
        "PROCESS          | true",
        "INCIDENT         | true",
        "PROCESS_INSTANCE | true",
        "USER_TASK        | true",
        "JOB              | true",
        // Some non-required value types
        "MESSAGE          | false",
        "TIMER            | false",
        "DECISION         | false",
        "MESSAGE_SUBSCRIPTION | false"
      },
      delimiter = '|')
  void shouldFilterByRequiredValueTypesWhenIncludeEnabledRecordsDisabledOnNewBrokers(
      final ValueType valueType, final boolean expectedAccepted) {

    // given: 8.9.x broker, includeEnabledRecords=false
    // -> includeAllEnabledRecords == false
    // -> RequiredValueTypeFilter delegates to shouldIndexRequiredValueType
    final var config = new OpensearchExporterConfiguration();
    config.setIncludeEnabledRecords(false);

    final var filter = createFilter(config);

    @SuppressWarnings("unchecked")
    final Record<?> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("8.9.0");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(valueType);
    when(record.getIntent()).thenReturn(mock(Intent.class));

    // when
    final var accepted = filter.acceptRecord(record);

    // then
    assertThat(accepted).isEqualTo(expectedAccepted);
  }

  @Test
  void shouldTreatVariableAsRequiredWhenIncludeEnabledRecordsDisabledOnNewBrokers() {
    // given: VARIABLE is in the required set
    final var config = new OpensearchExporterConfiguration();
    config.setIncludeEnabledRecords(false);

    final var filter = createFilter(config);

    final VariableRecordValue variableValue = mock(VariableRecordValue.class);
    // Any simple name so variable-name filters, if present, don't exclude it
    when(variableValue.getName()).thenReturn("anyVariable");

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("8.9.0");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);
    when(record.getIntent()).thenReturn(mock(Intent.class));
    when(record.getValue()).thenReturn(variableValue);

    // when
    final var accepted = filter.acceptRecord(record);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldHonorIndexFlagsForRequiredValueTypesOnNewBrokers() {
    // given: 8.9.x, includeEnabledRecords=false -> required-value branch
    // but explicitly disable one required type (PROCESS_INSTANCE)
    final var config = new OpensearchExporterConfiguration();
    config.setIncludeEnabledRecords(false);
    config.index.processInstance = false; // override default

    final var filter = createFilter(config);

    @SuppressWarnings("unchecked")
    final Record<?> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("8.9.0");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.PROCESS_INSTANCE);
    when(record.getIntent()).thenReturn(mock(Intent.class));

    // when
    final var accepted = filter.acceptRecord(record);

    // then: even though PROCESS_INSTANCE is in the "required" set, index flag wins
    assertThat(accepted).isFalse();
  }

  @Test
  void shouldThrowOnNonSemanticBrokerVersion() {
    // given: invalid broker version string → RequiredValueTypeFilter should throw
    final var config = new OpensearchExporterConfiguration();
    config.setIncludeEnabledRecords(false);

    final var filter = createFilter(config);

    @SuppressWarnings("unchecked")
    final Record<?> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("not-a-version");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.PROCESS_INSTANCE);
    when(record.getIntent()).thenReturn(mock(Intent.class));

    // when / then
    assertThatThrownBy(() -> filter.acceptRecord(record))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported record broker version");
  }
}
