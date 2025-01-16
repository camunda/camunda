/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.handlers.EventFromJobHandler.ID_PATTERN;
import static io.camunda.exporter.handlers.EventFromJobHandler.JOB_EVENTS;
import static io.camunda.webapps.schema.descriptors.operate.template.EventTemplate.JOB_CUSTOM_HEADERS;
import static io.camunda.webapps.schema.descriptors.operate.template.EventTemplate.JOB_KEY;
import static io.camunda.webapps.schema.descriptors.operate.template.EventTemplate.JOB_RETRIES;
import static io.camunda.webapps.schema.descriptors.operate.template.EventTemplate.JOB_TYPE;
import static io.camunda.webapps.schema.descriptors.operate.template.EventTemplate.JOB_WORKER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.operate.template.EventTemplate;
import io.camunda.webapps.schema.entities.event.EventEntity;
import io.camunda.webapps.schema.entities.event.EventMetadataEntity;
import io.camunda.webapps.schema.entities.event.EventSourceType;
import io.camunda.webapps.schema.entities.event.EventType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

final class EventFromJobHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = EventTemplate.INDEX_NAME;

  private final EventFromJobHandler underTest = new EventFromJobHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.JOB);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(EventEntity.class);
  }

  @Test
  void shouldHandleRecord() {
    JOB_EVENTS.stream().map(this::generateRecord).forEach(this::assertShouldHandleRecord);
  }

  @Test
  void shouldNotHandleRecord() {
    Stream.of(JobIntent.values())
        .filter(intent -> !JOB_EVENTS.contains(intent))
        .map(this::generateRecord)
        .forEach(this::assertShouldNotHandleRecord);
  }

  @Test
  void testGenerateIds() {
    // given
    final int processInstanceKey = 123;
    final int elementInstanceKey = 456;
    final var recordValue =
        ImmutableJobRecordValue.builder()
            .withProcessInstanceKey(processInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .build();
    final Record<JobRecordValue> record =
        factory.generateRecord(ValueType.JOB, r -> r.withValue(recordValue));

    // when
    final var ids = underTest.generateIds(record);

    // then
    assertThat(ids)
        .containsExactly(String.format(ID_PATTERN, processInstanceKey, elementInstanceKey));
  }

  @Test
  void testCreateNewEntity() {
    // given
    final String id = "id";

    // when
    final var entity = underTest.createNewEntity(id);

    // then
    assertThat(entity).isNotNull();
    assertThat(entity.getId()).isEqualTo(id);
  }

  @Test
  void testUpdateEntity() {
    // given
    final long position = 222;
    final long recordKey = 789;
    final int partitionId = 10;
    final int processInstanceKey = 123;
    final int elementInstanceKey = 456;
    final String elementId = "elementId";
    final String bpmnProcessId = "bpmnProcessId";
    final String tenantId = "tenantId";
    final String jobType = "jobType";
    final int retries = 3;
    final String jobWorker = "jobWorker";
    final var recordValue =
        ImmutableJobRecordValue.builder()
            .withProcessInstanceKey(processInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withElementId(elementId)
            .withBpmnProcessId(bpmnProcessId)
            .withType(jobType)
            .withRetries(retries)
            .withWorker(jobWorker)
            .withCustomHeaders(Map.of("key", "val"))
            .withTenantId(tenantId)
            .build();
    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withPosition(position)
                    .withIntent(JobIntent.CREATED)
                    .withKey(recordKey)
                    .withPartitionId(partitionId)
                    .withValueType(ValueType.JOB)
                    .withValue(recordValue));
    final var entity = new EventEntity();

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getId())
        .isEqualTo(String.format(ID_PATTERN, processInstanceKey, elementInstanceKey));
    assertThat(entity.getKey()).isEqualTo(recordKey);
    assertThat(entity.getPartitionId()).isEqualTo(partitionId);
    assertThat(entity.getEventSourceType()).isEqualTo(EventSourceType.JOB);
    assertThat(entity.getEventType()).isEqualTo(EventType.CREATED);
    assertThat(entity.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(entity.getFlowNodeInstanceKey()).isEqualTo(elementInstanceKey);
    assertThat(entity.getFlowNodeId()).isEqualTo(elementId);
    assertThat(entity.getBpmnProcessId()).isEqualTo(bpmnProcessId);
    assertThat(entity.getTenantId()).isEqualTo(tenantId);
    assertThat(entity.getPositionJob()).isEqualTo(position);
    assertThat(entity.getMetadata().getJobType()).isEqualTo(jobType);
    assertThat(entity.getMetadata().getJobRetries()).isEqualTo(retries);
    assertThat(entity.getMetadata().getJobWorker()).isEqualTo(jobWorker);
    assertThat(entity.getMetadata().getJobCustomHeaders()).isEqualTo(Map.of("key", "val"));
    assertThat(entity.getMetadata().getJobKey()).isEqualTo(recordKey);
  }

  @Test
  void shouldAddEntityOnFlush() {
    // given
    final String expectedIndexName = EventTemplate.INDEX_NAME;
    final String id = "555";
    final long positionJob = 456L;
    final long key = 333L;

    final EventMetadataEntity metadata = new EventMetadataEntity();
    metadata.setJobKey(key);
    metadata.setJobType("jobType");
    metadata.setJobRetries(3);
    metadata.setJobWorker("jobWorker");
    metadata.setJobCustomHeaders(Map.of("key", "val"));

    final EventEntity inputEntity =
        new EventEntity().setId(id).setKey(key).setPositionJob(positionJob).setMetadata(metadata);

    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put("key", key);
    expectedUpdateFields.put("positionJob", positionJob);
    expectedUpdateFields.put("dateTime", null);
    expectedUpdateFields.put("flowNodeId", null);
    expectedUpdateFields.put("eventSourceType", null);
    expectedUpdateFields.put("eventType", null);
    expectedUpdateFields.put("bpmnProcessId", null);
    expectedUpdateFields.put("processDefinitionKey", null);
    final Map<String, Object> metadataMap = new LinkedHashMap<>();
    metadataMap.put(JOB_KEY, metadata.getJobKey());
    metadataMap.put(JOB_TYPE, metadata.getJobType());
    metadataMap.put(JOB_RETRIES, metadata.getJobRetries());
    metadataMap.put(JOB_WORKER, metadata.getJobWorker());
    metadataMap.put(JOB_CUSTOM_HEADERS, metadata.getJobCustomHeaders());
    expectedUpdateFields.put("metadata", metadataMap);

    final BatchRequest mockRequest = Mockito.mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).upsert(expectedIndexName, id, inputEntity, expectedUpdateFields);
  }

  private Record<JobRecordValue> generateRecord(final JobIntent intent) {
    return factory.generateRecord(ValueType.JOB, r -> r.withIntent(intent));
  }

  private void assertShouldHandleRecord(final Record<JobRecordValue> record) {
    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  private void assertShouldNotHandleRecord(final Record<JobRecordValue> record) {
    assertThat(underTest.handlesRecord(record)).isFalse();
  }
}
