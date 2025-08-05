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
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.UsageMetricHandler.UsageMetricsBatch;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEntity;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEventType;
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

class UsageMetricHandlerTest {

  private static final String TENANT_1 = "tenant1";
  private static final String TENANT_2 = "tenant2";
  private static final String TENANT_3 = "tenant3";
  private static final String EVENT_KEY = "10";
  private static final String ID_PATTERN = "%s_%s";
  private static final String TU_ID_PATTERN = "%s_%s_%s";
  private static final long ASSIGNEE_HASH_1 = 1234567L;
  private static final long ASSIGNEE_HASH_2 = 7654321L;
  private static final int PARTITION_ID = 9;
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-usage-metrics";
  private final String tuIndexName = "test-usage-metrics-tu";
  private final UsageMetricHandler underTest = new UsageMetricHandler(indexName, tuIndexName);

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
  void shouldUpdateEntity() throws PersistenceException {
    // given
    final var now = OffsetDateTime.now().toEpochSecond();
    final var recordValue =
        ImmutableUsageMetricRecordValue.builder()
            .withEventType(EventType.RPI)
            .withIntervalType(IntervalType.ACTIVE)
            .withStartTime(now)
            .withCounterValues(Map.of(TENANT_3, 33L))
            .build();
    final var record =
        generateRecord(
            b ->
                b.withValue(recordValue)
                    .withKey(Long.parseLong(EVENT_KEY))
                    .withPartitionId(PARTITION_ID));
    final var usageMetricsBatch =
        new UsageMetricsBatch(EVENT_KEY, composeUsageMetrics(now, 11L, 22L), new ArrayList<>());

    // when
    underTest.updateEntity(record, usageMetricsBatch);

    // then
    final List<UsageMetricsEntity> variables = usageMetricsBatch.variables();
    final List<UsageMetricsTUEntity> tuVariables = usageMetricsBatch.tuVariables();

    assertThat(tuVariables).isEmpty();
    assertThat(variables)
        .extracting(UsageMetricsEntity::getId)
        .containsExactlyInAnyOrder(
            String.format(ID_PATTERN, EVENT_KEY, TENANT_1),
            String.format(ID_PATTERN, EVENT_KEY, TENANT_2),
            String.format(ID_PATTERN, EVENT_KEY, TENANT_3));
    assertThat(variables)
        .extracting(UsageMetricsEntity::getEventTime)
        .contains(DateUtil.toOffsetDateTime(now));
    assertThat(variables)
        .extracting(UsageMetricsEntity::getEventType)
        .containsOnly(UsageMetricsEventType.RPI);
    assertThat(variables).extracting(UsageMetricsEntity::getPartitionId).containsOnly(PARTITION_ID);
    assertThat(variables)
        .extracting(UsageMetricsEntity::getEventValue)
        .containsExactlyInAnyOrder(11L, 22L, 33L);
  }

  @Test
  void shouldUpdateTUEntity() throws PersistenceException {
    // given
    final var now = OffsetDateTime.now().toEpochSecond();
    final var recordValue =
        ImmutableUsageMetricRecordValue.builder()
            .withEventType(EventType.TU)
            .withIntervalType(IntervalType.ACTIVE)
            .withStartTime(now)
            .withSetValues(Map.of(TENANT_3, Set.of(ASSIGNEE_HASH_1, ASSIGNEE_HASH_2)))
            .build();
    final var record =
        generateRecord(
            b ->
                b.withValue(recordValue)
                    .withKey(Long.parseLong(EVENT_KEY))
                    .withPartitionId(PARTITION_ID));
    final var usageMetricsBatch =
        new UsageMetricsBatch(EVENT_KEY, null, composeUsageMetricsTU(now));

    // when
    underTest.updateEntity(record, usageMetricsBatch);

    // then
    final List<UsageMetricsEntity> variables = usageMetricsBatch.variables();
    final List<UsageMetricsTUEntity> tuVariables = usageMetricsBatch.tuVariables();

    assertThat(variables).isNull();

    assertThat(tuVariables)
        .extracting(UsageMetricsTUEntity::getId)
        .containsExactlyInAnyOrder(
            String.format(TU_ID_PATTERN, EVENT_KEY, TENANT_1, ASSIGNEE_HASH_1),
            String.format(TU_ID_PATTERN, EVENT_KEY, TENANT_2, ASSIGNEE_HASH_2),
            String.format(TU_ID_PATTERN, EVENT_KEY, TENANT_3, ASSIGNEE_HASH_1),
            String.format(TU_ID_PATTERN, EVENT_KEY, TENANT_3, ASSIGNEE_HASH_2));
    assertThat(tuVariables)
        .extracting(UsageMetricsTUEntity::getEventTime)
        .contains(DateUtil.toOffsetDateTime(now));
    assertThat(tuVariables)
        .extracting(UsageMetricsTUEntity::getPartitionId)
        .containsOnly(PARTITION_ID);
    assertThat(tuVariables)
        .extracting(UsageMetricsTUEntity::getAssigneeHash)
        .containsOnly(ASSIGNEE_HASH_1, ASSIGNEE_HASH_2);
  }

  @Test
  void shouldUpdateBothEntities() throws PersistenceException {
    // given
    final var now = OffsetDateTime.now().toEpochSecond();
    final var recordValue =
        ImmutableUsageMetricRecordValue.builder()
            .withEventType(EventType.TU)
            .withIntervalType(IntervalType.ACTIVE)
            .withStartTime(now)
            .withSetValues(Map.of(TENANT_3, Set.of(ASSIGNEE_HASH_1, ASSIGNEE_HASH_2)))
            .build();
    final var record =
        generateRecord(
            b ->
                b.withValue(recordValue)
                    .withKey(Long.parseLong(EVENT_KEY))
                    .withPartitionId(PARTITION_ID));
    final var usageMetricsBatch =
        new UsageMetricsBatch(
            EVENT_KEY, composeUsageMetrics(now, 11L, 22L), composeUsageMetricsTU(now));

    // when
    underTest.updateEntity(record, usageMetricsBatch);

    // then
    final List<UsageMetricsEntity> variables = usageMetricsBatch.variables();
    final List<UsageMetricsTUEntity> tuVariables = usageMetricsBatch.tuVariables();

    assertThat(variables)
        .extracting(UsageMetricsEntity::getEventType)
        .containsOnly(UsageMetricsEventType.RPI);
    assertThat(variables)
        .extracting(UsageMetricsEntity::getId)
        .containsExactlyInAnyOrder(
            String.format(ID_PATTERN, EVENT_KEY, TENANT_1),
            String.format(ID_PATTERN, EVENT_KEY, TENANT_2));
    assertThat(variables)
        .extracting(UsageMetricsEntity::getEventTime)
        .contains(DateUtil.toOffsetDateTime(now));
    assertThat(variables).extracting(UsageMetricsEntity::getPartitionId).containsOnly(PARTITION_ID);
    assertThat(variables)
        .extracting(UsageMetricsEntity::getEventValue)
        .containsExactlyInAnyOrder(11L, 22L);

    assertThat(tuVariables)
        .extracting(UsageMetricsTUEntity::getId)
        .containsExactlyInAnyOrder(
            String.format(TU_ID_PATTERN, EVENT_KEY, TENANT_1, ASSIGNEE_HASH_1),
            String.format(TU_ID_PATTERN, EVENT_KEY, TENANT_2, ASSIGNEE_HASH_2),
            String.format(TU_ID_PATTERN, EVENT_KEY, TENANT_3, ASSIGNEE_HASH_1),
            String.format(TU_ID_PATTERN, EVENT_KEY, TENANT_3, ASSIGNEE_HASH_2));
    assertThat(tuVariables)
        .extracting(UsageMetricsTUEntity::getEventTime)
        .contains(DateUtil.toOffsetDateTime(now));
    assertThat(tuVariables)
        .extracting(UsageMetricsTUEntity::getPartitionId)
        .containsOnly(PARTITION_ID);
    assertThat(tuVariables)
        .extracting(UsageMetricsTUEntity::getAssigneeHash)
        .containsOnly(ASSIGNEE_HASH_1, ASSIGNEE_HASH_2);
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
    final var now = OffsetDateTime.now().toEpochSecond();
    final var usageMetricsBatch =
        new UsageMetricsBatch(
            EVENT_KEY, composeUsageMetrics(now, 11L, 22L), composeUsageMetricsTU(now));
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(usageMetricsBatch, mockRequest);

    // then
    usageMetricsBatch.variables().forEach(entity -> verify(mockRequest).add(indexName, entity));
    usageMetricsBatch
        .tuVariables()
        .forEach(tuEntity -> verify(mockRequest).add(tuIndexName, tuEntity));
    verifyNoMoreInteractions(mockRequest);
  }

  private ArrayList<UsageMetricsEntity> composeUsageMetrics(final long now, Long... eventValues) {
    return new ArrayList<>(
        List.of(
            new UsageMetricsEntity()
                .setId(String.format(ID_PATTERN, EVENT_KEY, TENANT_1))
                .setPartitionId(PARTITION_ID)
                .setEventTime(DateUtil.toOffsetDateTime(now))
                .setEventType(UsageMetricsEventType.RPI)
                .setEventValue(eventValues[0]),
            new UsageMetricsEntity()
                .setId(String.format(ID_PATTERN, EVENT_KEY, TENANT_2))
                .setEventType(UsageMetricsEventType.RPI)
                .setPartitionId(PARTITION_ID)
                .setEventTime(DateUtil.toOffsetDateTime(now))
                .setEventValue(eventValues[1])));
  }

  private ArrayList<UsageMetricsTUEntity> composeUsageMetricsTU(final long now) {
    return new ArrayList<>(
        List.of(
            new UsageMetricsTUEntity()
                .setId(String.format(TU_ID_PATTERN, EVENT_KEY, TENANT_1, ASSIGNEE_HASH_1))
                .setPartitionId(PARTITION_ID)
                .setTenantId(TENANT_1)
                .setEventTime(DateUtil.toOffsetDateTime(now))
                .setAssigneeHash(ASSIGNEE_HASH_1),
            new UsageMetricsTUEntity()
                .setId(String.format(TU_ID_PATTERN, EVENT_KEY, TENANT_2, ASSIGNEE_HASH_2))
                .setPartitionId(PARTITION_ID)
                .setTenantId(TENANT_2)
                .setEventTime(DateUtil.toOffsetDateTime(now))
                .setAssigneeHash(ASSIGNEE_HASH_2)));
  }
}
