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
import io.camunda.exporter.handlers.UsageMetricTUHandler.UsageMetricsTUBatch;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsTUEntity;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UsageMetricTUHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-usage-metrics-tu";
  private final UsageMetricTUHandler underTest = new UsageMetricTUHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.USAGE_METRIC_TU);
  }

  private Record<UsageMetricRecordValue> generateRecord(
      final UnaryOperator<Builder<UsageMetricRecordValue>> fnBuild) {
    return factory.generateRecord(ValueType.USAGE_METRIC_TU, fnBuild, UsageMetricIntent.EXPORTED);
  }

  public static Stream<Arguments> provideHandleRecordParameters() {
    return Arrays.stream(EventType.values()).map(e -> Arguments.of(e, e == EventType.TU));
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
            .withEventType(EventType.TU)
            .withIntervalType(IntervalType.ACTIVE)
            .withSetValues(
                Map.of(
                    "tenant1",
                    Set.of(12L, 34L),
                    "tenant2",
                    Set.of(56L, 78L),
                    "tenant3",
                    Set.of(89L, 11L)))
            .build();
    final var record = generateRecord(b -> b.withValue(recordValue).withKey(10));

    // when
    final var idList = underTest.generateIds(record);

    // then
    assertThat(idList)
        .containsExactlyInAnyOrder(
            "10_tenant1_12",
            "10_tenant1_34",
            "10_tenant2_56",
            "10_tenant2_78",
            "10_tenant3_89",
            "10_tenant3_11");
  }

  @Test
  void shouldUpdateEntity() throws PersistenceException {
    // given
    final var now = OffsetDateTime.now().toEpochSecond();
    final var partitionId = 9;
    final var recordValue =
        ImmutableUsageMetricRecordValue.builder()
            .withEventType(EventType.TU)
            .withIntervalType(IntervalType.ACTIVE)
            .withStartTime(now)
            .withSetValues(Map.of("tenant3", Set.of(89L, 11L)))
            .build();
    final var record = generateRecord(b -> b.withValue(recordValue).withKey(10).withPartitionId(9));
    final var usageMetricsTUBatch =
        new UsageMetricsTUBatch(
            "batch_1",
            new ArrayList<>(
                List.of(
                    new UsageMetricsTUEntity()
                        .setId("10_tenant1_12")
                        .setAssigneeHash(12L)
                        .setPartitionId(partitionId)
                        .setEventTime(DateUtil.toOffsetDateTime(now)),
                    new UsageMetricsTUEntity()
                        .setId("10_tenant1_34")
                        .setAssigneeHash(34L)
                        .setPartitionId(partitionId)
                        .setEventTime(DateUtil.toOffsetDateTime(now)),
                    new UsageMetricsTUEntity()
                        .setId("10_tenant2_89")
                        .setAssigneeHash(89L)
                        .setPartitionId(partitionId)
                        .setEventTime(DateUtil.toOffsetDateTime(now)),
                    new UsageMetricsTUEntity()
                        .setId("10_tenant2_11")
                        .setAssigneeHash(11L)
                        .setPartitionId(partitionId)
                        .setEventTime(DateUtil.toOffsetDateTime(now)))));

    // when
    underTest.updateEntity(record, usageMetricsTUBatch);

    // then
    final List<UsageMetricsTUEntity> variables = usageMetricsTUBatch.variables();

    assertThat(variables)
        .extracting(UsageMetricsTUEntity::getId)
        .contains(
            "10_tenant1_12",
            "10_tenant1_34",
            "10_tenant2_89",
            "10_tenant2_11",
            "10_tenant3_89",
            "10_tenant3_11");
    assertThat(variables)
        .extracting(UsageMetricsTUEntity::getEventTime)
        .contains(DateUtil.toOffsetDateTime(now));
    assertThat(variables).extracting(UsageMetricsTUEntity::getPartitionId).containsOnly(9);
    assertThat(variables)
        .extracting(UsageMetricsTUEntity::getAssigneeHash)
        .contains(12L, 89L, 34L, 11L);
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
    final var usageMetricsBatch =
        new UsageMetricsTUBatch(
            "batch_1",
            List.of(
                new UsageMetricsTUEntity().setId("10_tenant3").setAssigneeHash(12L),
                new UsageMetricsTUEntity().setId("10_tenant1").setAssigneeHash(23L)));
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(usageMetricsBatch, mockRequest);

    // then
    verify(mockRequest).add(indexName, usageMetricsBatch);
  }
}
