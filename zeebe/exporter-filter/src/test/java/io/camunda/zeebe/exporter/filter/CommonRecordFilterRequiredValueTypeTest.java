/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.exporter.filter.CommonFilterConfiguration.IndexConfig;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for the "required value type" behavior in {@link CommonRecordFilter}, which is implemented
 * via {@link io.camunda.zeebe.exporter.filter.RequiredValueTypeFilter}.
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
final class CommonRecordFilterRequiredValueTypeTest {

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
    final var filter =
        FilterBuilder.builder()
            .includeEnabledRecords(false)
            .normalFlag(valueType, enabledFlag)
            .build();

    @SuppressWarnings("unchecked")
    final Record<?> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("8.7.3");
    when(record.getRecordType()).thenReturn(RecordType.EVENT); // event enabled in builder
    when(record.getValueType()).thenReturn(valueType);
    when(record.getIntent()).thenReturn(mock(Intent.class));

    // when
    final var accepted = filter.acceptRecord(record);

    // then: required-vs-nonrequired is ignored for pre-8.8; we follow normal index flag
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
    final var filter =
        FilterBuilder.builder()
            .includeEnabledRecords(true)
            .normalFlag(valueType, enabledFlag)
            .build();

    @SuppressWarnings("unchecked")
    final Record<?> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("8.9.0");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(valueType);
    when(record.getIntent()).thenReturn(mock(Intent.class));

    // when
    final var accepted = filter.acceptRecord(record);

    // then: we use shouldIndexValueType, i.e. normal flag decides
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
    final var filter =
        FilterBuilder.builder()
            .includeEnabledRecords(false)
            .requiredFlag(valueType, expectedAccepted)
            .build();

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
    final var filter =
        FilterBuilder.builder()
            .includeEnabledRecords(false)
            .requiredFlag(ValueType.VARIABLE, true)
            .build();

    final VariableRecordValue variableValue = mock(VariableRecordValue.class);
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
    final var filter =
        FilterBuilder.builder()
            .includeEnabledRecords(false)
            .requiredFlag(ValueType.PROCESS_INSTANCE, false) // override required set
            .build();

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
    final var filter =
        FilterBuilder.builder()
            .includeEnabledRecords(false)
            // no specific flags needed; version parsing should fail first
            .build();

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

  /**
   * Builder for constructing a {@link CommonRecordFilter} backed by Mockito-mocked {@link
   * CommonFilterConfiguration} and {@link IndexConfig}, with configurable required/normal
   * value-type flags and includeEnabledRecords behavior.
   */
  private static final class FilterBuilder {
    private boolean includeEnabledRecords;
    private final Map<ValueType, Boolean> normalFlags = new EnumMap<>(ValueType.class);
    private final Map<ValueType, Boolean> requiredFlags = new EnumMap<>(ValueType.class);

    private FilterBuilder() {}

    static FilterBuilder builder() {
      return new FilterBuilder();
    }

    FilterBuilder includeEnabledRecords(final boolean includeEnabledRecords) {
      this.includeEnabledRecords = includeEnabledRecords;
      return this;
    }

    FilterBuilder normalFlag(final ValueType valueType, final boolean enabled) {
      normalFlags.put(valueType, enabled);
      return this;
    }

    FilterBuilder requiredFlag(final ValueType valueType, final boolean enabled) {
      requiredFlags.put(valueType, enabled);
      return this;
    }

    CommonRecordFilter build() {
      final var config = mock(CommonFilterConfiguration.class);
      final var indexConfig = mock(IndexConfig.class);

      // Index config: no variable-name/type rules; use empty lists instead of null
      when(config.filterIndexConfig()).thenReturn(indexConfig);
      when(indexConfig.isOptimizeModeEnabled()).thenReturn(false);

      // Record type: for these tests we always want EVENTs to be eligible
      when(config.shouldIndexRecordType(RecordType.EVENT)).thenReturn(true);
      when(config.shouldIndexRecordType(RecordType.COMMAND)).thenReturn(false);
      when(config.shouldIndexRecordType(RecordType.COMMAND_REJECTION)).thenReturn(false);

      // Global includeEnabledRecords flag
      when(config.getIsIncludeEnabledRecords()).thenReturn(includeEnabledRecords);

      // Normal value-type flags (used when includeAllEnabledRecords == true)
      normalFlags.forEach(
          (type, enabled) -> when(config.shouldIndexValueType(type)).thenReturn(enabled));

      // Required value-type flags (used when includeAllEnabledRecords == false)
      requiredFlags.forEach(
          (type, enabled) -> when(config.shouldIndexRequiredValueType(type)).thenReturn(enabled));

      return new CommonRecordFilter(config);
    }
  }
}
