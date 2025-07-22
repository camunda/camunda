/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEntity;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEventType;
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
import io.camunda.zeebe.util.DateUtil;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UsageMetricHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-usage-metrics";
  private final UsageMetricHandler underTest = new UsageMetricHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.USAGE_METRIC);
  }

  private Record<UsageMetricRecordValue> generateRecord(
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
        .isEqualTo(false);
  }

  @Test
  void shouldGenerateIds() {
    // given
    final var recordValue =
        ImmutableUsageMetricRecordValue.builder()
            .withEventType(EventType.RPI)
            .withIntervalType(IntervalType.ACTIVE)
            .withCounterValues(Map.of("tenant1", 1L, "tenant2", 2L, "tenant3", 3L))
            .build();
    final var record = generateRecord(b -> b.withValue(recordValue).withKey(10));

    // when
    final var idList = underTest.generateIds(record);

    // then
    assertThat(idList).containsExactlyInAnyOrder("10_tenant1", "10_tenant2", "10_tenant3");
  }

  @Test
  void shouldUpdateEntity() throws PersistenceException {
    // given
    final var now = OffsetDateTime.now().toEpochSecond();
    final var recordValue =
        ImmutableUsageMetricRecordValue.builder()
            .withEventType(EventType.RPI)
            .withIntervalType(IntervalType.ACTIVE)
            .withStartTime(now)
            .withCounterValues(Map.of("tenant1", 11L, "tenant2", 22L, "tenant3", 33L))
            .build();
    final var record = generateRecord(b -> b.withValue(recordValue).withKey(10).withPartitionId(9));
    final var entity =
        new UsageMetricsEntity()
            .setId("10_tenant3")
            .setEventType(UsageMetricsEventType.RPI)
            .setEventValue(5L);

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getEventTime()).isEqualTo(DateUtil.toOffsetDateTime(now));
    assertThat(entity.getEventType()).isEqualTo(UsageMetricsEventType.RPI);
    assertThat(entity.getTenantId()).isEqualTo("tenant3");
    assertThat(entity.getPartitionId()).isEqualTo(9);
    assertThat(entity.getEventValue()).isEqualTo(33L);
  }

  @Test
  void shouldCreateNewEntity() {
    // when
    final var result = underTest.createNewEntity("id");

    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  void shouldAddEntityOnFlush() throws PersistenceException {
    // given
    final var entity =
        new UsageMetricsEntity()
            .setId("111")
            .setEventType(UsageMetricsEventType.RPI)
            .setEventValue(5L);
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(entity, mockRequest);

    // then
    verify(mockRequest).add(indexName, entity);
  }
}
