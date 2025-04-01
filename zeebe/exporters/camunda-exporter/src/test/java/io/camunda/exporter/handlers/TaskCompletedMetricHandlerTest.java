/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.handlers.TaskCompletedMetricHandler.EVENT_TASK_COMPLETED_BY_ASSIGNEE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.index.MetricIndex;
import io.camunda.webapps.schema.entities.MetricEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableUserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class TaskCompletedMetricHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-" + MetricIndex.INDEX_NAME;

  private final TaskCompletedMetricHandler underTest = new TaskCompletedMetricHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.USER_TASK);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(MetricEntity.class);
  }

  @Test
  void shouldHandleRecord() {
    // given
    final Record<UserTaskRecordValue> processInstanceRecord =
        generateRecord(UserTaskIntent.COMPLETED);

    // when - then
    assertThat(underTest.handlesRecord(processInstanceRecord)).isTrue();
  }

  @Test
  void shouldNotHandleRecord() {
    Arrays.stream(UserTaskIntent.values())
        .filter(intent -> intent != UserTaskIntent.COMPLETED)
        .map(this::generateRecord)
        .forEach(r -> assertThat(underTest.handlesRecord(r)).isFalse());
  }

  @Test
  void shouldGenerateIds() {
    // given
    final Record<UserTaskRecordValue> userTaskRecord = generateRecord(UserTaskIntent.COMPLETED);

    // when
    final var ids = underTest.generateIds(userTaskRecord);

    // then
    assertThat(ids).isNotNull();
    assertThat(ids).containsExactly(String.valueOf(userTaskRecord.getValue().getUserTaskKey()));
  }

  @Test
  void shouldCreateNewEntity() {
    // when
    final var entity = underTest.createNewEntity("id");

    // then
    assertThat(entity).isNotNull();
    assertThat(entity.getId()).isEqualTo("id");
  }

  @Test
  void shouldUpdateEntity() {
    // given
    final long timestamp = System.currentTimeMillis();
    final UserTaskRecordValue recordValue =
        ImmutableUserTaskRecordValue.builder()
            .from(factory.generateObject(UserTaskRecordValue.class))
            .build();
    final Record<UserTaskRecordValue> userTaskRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withIntent(UserTaskIntent.COMPLETED)
                    .withTimestamp(timestamp)
                    .withValue(recordValue));

    final MetricEntity entity = new MetricEntity();

    // when
    underTest.updateEntity(userTaskRecord, entity);

    // then
    assertThat(entity.getId()).isNull();
    assertThat(entity.getEvent()).isEqualTo(EVENT_TASK_COMPLETED_BY_ASSIGNEE);
    assertThat(entity.getValue()).isEqualTo(String.valueOf(recordValue.getAssignee()));
    assertThat(entity.getEventTime())
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC));
    assertThat(entity.getTenantId()).isEqualTo(recordValue.getTenantId());
  }

  @Test
  void shouldAddEntityOnFlush() {
    // given
    final MetricEntity inputEntity = new MetricEntity();
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(indexName, inputEntity);
  }

  private Record<UserTaskRecordValue> generateRecord(final UserTaskIntent intent) {
    final UserTaskRecordValue userTaskRecordValue =
        ImmutableUserTaskRecordValue.builder()
            .from(factory.generateObject(UserTaskRecordValue.class))
            .build();
    return factory.generateRecord(
        ValueType.USER_TASK, r -> r.withIntent(intent).withValue(userTaskRecordValue));
  }
}
