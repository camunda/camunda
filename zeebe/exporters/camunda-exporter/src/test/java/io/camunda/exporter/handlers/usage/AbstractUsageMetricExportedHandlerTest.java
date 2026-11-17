/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.usage;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.ImmutableRecord.Builder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UsageMetricIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableUsageMetricRecordValue;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.IntervalType;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Arrays;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

abstract class AbstractUsageMetricExportedHandlerTest<
    T extends AbstractUsageMetricExportedHandler<?, ?>> {
  protected static final String TENANT_1 = "tenant1";
  protected static final String TENANT_2 = "tenant2";
  protected static final String TENANT_3 = "tenant3";
  protected static final String EVENT_KEY = "10";
  protected final T underTest;
  private final ProtocolFactory factory = new ProtocolFactory();

  public AbstractUsageMetricExportedHandlerTest(final T underTest) {
    this.underTest = underTest;
  }

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.USAGE_METRIC);
  }

  protected Record<UsageMetricRecordValue> generateRecord(
      final UnaryOperator<Builder<UsageMetricRecordValue>> fnBuild) {
    return factory.generateRecord(ValueType.USAGE_METRIC, fnBuild, UsageMetricIntent.EXPORTED);
  }

  public static Stream<Arguments> provideHandleRecordParameters() {
    return Arrays.stream(EventType.values()).map(e -> Arguments.of(e, e != EventType.NONE));
  }

  @ParameterizedTest
  @MethodSource("provideHandleRecordParameters")
  void shouldHandleRecord(final EventType eventType, final boolean expected) {
    // given
    final var recordValue =
        ImmutableUsageMetricRecordValue.builder().withEventType(eventType).build();
    final var record = generateRecord(b -> b.withValue(recordValue));

    // when - then
    assertThat(underTest.handlesRecord(record)).isEqualTo(expected);
  }

  @Test
  void shouldNotHandleWrongIntent() {
    // when - then
    assertThat(
            underTest.handlesRecord(
                factory.generateRecord(
                    ValueType.PROCESS_INSTANCE, b -> b, ProcessInstanceIntent.ELEMENT_ACTIVATED)))
        .isFalse();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final var recordValue =
        ImmutableUsageMetricRecordValue.builder()
            .withEventType(EventType.RPI)
            .withIntervalType(IntervalType.ACTIVE)
            .withCounterValues(Map.of(TENANT_1, 1L, TENANT_2, 2L, TENANT_3, 3L))
            .build();
    final var record = generateRecord(b -> b.withValue(recordValue).withKey(10));

    // when
    final var idList = underTest.generateIds(record);

    // then
    assertThat(idList).containsOnly(EVENT_KEY);
  }

  @Test
  void shouldCreateNewEntity() {
    // when
    final var result = underTest.createNewEntity("id");

    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }
}
