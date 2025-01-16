/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.handlers.MetricFromProcessInstanceHandler.EMPTY_PARENT_PROCESS_INSTANCE_ID;
import static io.camunda.exporter.handlers.MetricFromProcessInstanceHandler.EVENT_PROCESS_INSTANCE_STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.index.MetricIndex;
import io.camunda.webapps.schema.entities.MetricEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import org.junit.jupiter.api.Test;

final class MetricFromProcessInstanceHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = MetricIndex.INDEX_NAME;

  private final MetricFromProcessInstanceHandler underTest =
      new MetricFromProcessInstanceHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.PROCESS_INSTANCE);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(MetricEntity.class);
  }

  @Test
  void shouldHandleRecord() {
    // given
    final Record<ProcessInstanceRecordValue> processInstanceRecord =
        generateRecord(ProcessInstanceIntent.ELEMENT_ACTIVATING);

    // when - then
    assertThat(underTest.handlesRecord(processInstanceRecord)).isTrue();
  }

  @Test
  void shouldNotHandleRecord() {
    Arrays.stream(ProcessInstanceIntent.values())
        .filter(intent -> intent != ProcessInstanceIntent.ELEMENT_ACTIVATING)
        .map(this::generateRecord)
        .forEach(this::assertShouldNotHandleRecordWithIntent);
  }

  @Test
  void shouldNotHandleRecordWithParentProcessInstance() {
    // given
    final Record<ProcessInstanceRecordValue> processInstanceRecord =
        generateRecord(1, ProcessInstanceIntent.ELEMENT_ACTIVATING);

    // when - then
    assertThat(underTest.handlesRecord(processInstanceRecord)).isFalse();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final Record<ProcessInstanceRecordValue> processInstanceRecord =
        generateRecord(ProcessInstanceIntent.ELEMENT_ACTIVATING);
    final long processInstanceKey = processInstanceRecord.getValue().getProcessInstanceKey();

    // when
    final var ids = underTest.generateIds(processInstanceRecord);

    // then
    assertThat(ids).isNotNull();
    assertThat(ids).containsExactly(String.valueOf(processInstanceKey));
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
    final String tenantId = "tenantId";
    final int processInstanceKey = 123;
    final long timestamp = new Date().getTime();
    final ProcessInstanceRecordValue recordValue =
        ImmutableProcessInstanceRecordValue.builder()
            .withTenantId(tenantId)
            .withProcessInstanceKey(processInstanceKey)
            .build();
    final Record<ProcessInstanceRecordValue> processInstanceRecord =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r ->
                r.withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                    .withTimestamp(timestamp)
                    .withValue(recordValue));

    final MetricEntity entity = new MetricEntity();

    // when
    underTest.updateEntity(processInstanceRecord, entity);

    // then
    assertThat(entity.getId()).isNull();
    assertThat(entity.getEvent()).isEqualTo(EVENT_PROCESS_INSTANCE_STARTED);
    assertThat(entity.getValue()).isEqualTo(String.valueOf(processInstanceKey));
    assertThat(entity.getEventTime())
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC));
    assertThat(entity.getTenantId()).isEqualTo(tenantId);
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

  private void assertShouldNotHandleRecordWithIntent(
      final Record<ProcessInstanceRecordValue> processInstanceRecord) {
    assertThat(underTest.handlesRecord(processInstanceRecord)).isFalse();
  }

  private Record<ProcessInstanceRecordValue> generateRecord(final ProcessInstanceIntent intent) {
    return generateRecord(EMPTY_PARENT_PROCESS_INSTANCE_ID, intent);
  }

  private Record<ProcessInstanceRecordValue> generateRecord(
      final long parentProcessInstanceKey, final ProcessInstanceIntent intent) {
    final ProcessInstanceRecordValue processInstanceRecordValue =
        ImmutableProcessInstanceRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceRecordValue.class))
            .withParentProcessInstanceKey(parentProcessInstanceKey)
            .build();
    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE,
        r -> r.withIntent(intent).withValue(processInstanceRecordValue));
  }
}
