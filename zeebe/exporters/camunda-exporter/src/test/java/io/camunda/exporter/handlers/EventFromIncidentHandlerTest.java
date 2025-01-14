/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.handlers.EventFromIncidentHandler.ID_PATTERN;
import static io.camunda.exporter.utils.ExporterUtil.toOffsetDateTime;
import static io.camunda.webapps.schema.descriptors.operate.template.EventTemplate.INCIDENT_ERROR_MSG;
import static io.camunda.webapps.schema.descriptors.operate.template.EventTemplate.INCIDENT_ERROR_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.operate.template.EventTemplate;
import io.camunda.webapps.schema.entities.event.EventEntity;
import io.camunda.webapps.schema.entities.event.EventMetadataEntity;
import io.camunda.webapps.schema.entities.event.EventSourceType;
import io.camunda.webapps.schema.entities.event.EventType;
import io.camunda.webapps.schema.entities.operate.ErrorType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableIncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

final class EventFromIncidentHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = EventTemplate.INDEX_NAME;

  private final EventFromIncidentHandler underTest = new EventFromIncidentHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.INCIDENT);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(EventEntity.class);
  }

  @Test
  void shouldHandleRecord() {
    Stream.of(IncidentIntent.CREATED, IncidentIntent.RESOLVED)
        .map(this::generateRecord)
        .forEach(this::assertShouldHandleRecord);
  }

  @Test
  void shouldNotHandleRecord() {
    Stream.of(IncidentIntent.values())
        .filter(intent -> intent != IncidentIntent.CREATED && intent != IncidentIntent.RESOLVED)
        .map(this::generateRecord)
        .forEach(this::assertShouldNotHandleRecord);
  }

  @Test
  void testGenerateIds() {
    // given
    final int processInstanceKey = 123;
    final int elementInstanceKey = 456;
    final var recordValue =
        ImmutableIncidentRecordValue.builder()
            .withProcessInstanceKey(processInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .build();
    final Record<IncidentRecordValue> record =
        factory.generateRecord(ValueType.INCIDENT, r -> r.withValue(recordValue));

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
    final int partitionNumber = 10;
    final int processInstanceKey = 123;
    final int elementInstanceKey = 456;
    final String elementId = "elementId";
    final String bpmnProcessId = "bpmnProcessId";
    final String errorMessage = "errorMessage";
    final String tenantId = "tenantId";
    final var recordValue =
        ImmutableIncidentRecordValue.builder()
            .withProcessInstanceKey(processInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withElementId(elementId)
            .withBpmnProcessId(bpmnProcessId)
            .withErrorMessage(errorMessage)
            .withTenantId(tenantId)
            .build();
    final Record<IncidentRecordValue> record =
        factory.generateRecord(
            ValueType.INCIDENT,
            r ->
                r.withPosition(position)
                    .withIntent(IncidentIntent.CREATED)
                    .withKey(recordKey)
                    .withPartitionId(partitionNumber)
                    .withValueType(ValueType.INCIDENT)
                    .withValue(recordValue));
    final var entity = new EventEntity();

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getId())
        .isEqualTo(String.format(ID_PATTERN, processInstanceKey, elementInstanceKey));
    assertThat(entity.getKey()).isEqualTo(recordKey);
    assertThat(entity.getPartitionId()).isEqualTo(partitionNumber);
    assertThat(entity.getEventSourceType()).isEqualTo(EventSourceType.INCIDENT);
    assertThat(entity.getEventType()).isEqualTo(EventType.CREATED);
    assertThat(entity.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(entity.getFlowNodeInstanceKey()).isEqualTo(elementInstanceKey);
    assertThat(entity.getFlowNodeId()).isEqualTo(elementId);
    assertThat(entity.getBpmnProcessId()).isEqualTo(bpmnProcessId);
    assertThat(entity.getTenantId()).isEqualTo(tenantId);
    assertThat(entity.getMetadata().getIncidentErrorMessage()).isEqualTo(errorMessage);
    assertThat(entity.getPositionIncident()).isEqualTo(position);
    assertThat(entity.getDateTime())
        .isEqualTo(toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
  }

  @Test
  void shouldAddEntityOnFlush() {
    // given
    final String expectedIndexName = EventTemplate.INDEX_NAME;
    final String id = "555";
    final long positionIncident = 456L;
    final long key = 333L;

    final EventMetadataEntity metadata = new EventMetadataEntity();
    metadata.setIncidentErrorMessage("errorMessage");
    metadata.setIncidentErrorType(ErrorType.UNKNOWN);

    final EventEntity inputEntity =
        new EventEntity()
            .setId(id)
            .setKey(key)
            .setPositionIncident(positionIncident)
            .setMetadata(metadata);

    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put("key", key);
    expectedUpdateFields.put("positionIncident", positionIncident);
    expectedUpdateFields.put("dateTime", null);
    expectedUpdateFields.put("flowNodeId", null);
    expectedUpdateFields.put("eventSourceType", null);
    expectedUpdateFields.put("eventType", null);
    expectedUpdateFields.put("bpmnProcessId", null);
    expectedUpdateFields.put("processDefinitionKey", null);

    final Map<String, Object> metadataMap = new LinkedHashMap<>();
    metadataMap.put(INCIDENT_ERROR_MSG, metadata.getIncidentErrorMessage());
    metadataMap.put(INCIDENT_ERROR_TYPE, metadata.getIncidentErrorType());
    expectedUpdateFields.put("metadata", metadataMap);

    final BatchRequest mockRequest = Mockito.mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).upsert(expectedIndexName, id, inputEntity, expectedUpdateFields);
  }

  private Record<IncidentRecordValue> generateRecord(final IncidentIntent intent) {
    return factory.generateRecord(ValueType.INCIDENT, r -> r.withIntent(intent));
  }

  private void assertShouldHandleRecord(final Record<IncidentRecordValue> record) {
    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  private void assertShouldNotHandleRecord(
      final Record<IncidentRecordValue> incidentRecordValueRecord) {
    assertThat(underTest.handlesRecord(incidentRecordValueRecord)).isFalse();
  }
}
