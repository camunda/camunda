/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor.POSITION;
import static io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate.FLOW_NODE_ID;
import static io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate.PROCESS_DEFINITION_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.utils.ExporterUtil;
import io.camunda.webapps.schema.entities.operate.IncidentEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableIncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class IncidentHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-incident";
  private final IncidentHandler underTest = new IncidentHandler(indexName, false);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.INCIDENT);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(IncidentEntity.class);
  }

  @Test
  void shouldHandleRecord() {
    // given
    final Record<IncidentRecordValue> incidentRecord =
        factory.generateRecord(ValueType.INCIDENT, r -> r.withIntent(IncidentIntent.CREATED));

    // when - then
    assertThat(underTest.handlesRecord(incidentRecord)).isTrue();
  }

  @Test
  void shouldNotHandleRecord() {
    // given
    final Record<IncidentRecordValue> incidentRecord =
        factory.generateRecord(ValueType.INCIDENT, r -> r.withIntent(IncidentIntent.RESOLVED));

    // when - then
    assertThat(underTest.handlesRecord(incidentRecord)).isFalse();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final long expectedId = 123;
    final IncidentRecordValue incidentRecordValue =
        ImmutableIncidentRecordValue.builder()
            .from(factory.generateObject(IncidentRecordValue.class))
            .build();

    final Record<IncidentRecordValue> incidentRecord =
        factory.generateRecord(
            ValueType.INCIDENT,
            r ->
                r.withIntent(ProcessIntent.CREATED)
                    .withValue(incidentRecordValue)
                    .withKey(expectedId));

    // when
    final var idList = underTest.generateIds(incidentRecord);

    // then
    assertThat(idList).containsExactly(String.valueOf(expectedId));
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
  void shouldAddEntityOnFlush() {
    // given
    final IncidentEntity inputEntity = new IncidentEntity();
    inputEntity.setPosition(1L);
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).upsert(indexName, "0", inputEntity, Map.of("position", 1L));
  }

  @Test
  void shouldAddEntityOnFlushWithScript() {
    // given
    final IncidentEntity inputEntity = new IncidentEntity();
    final var underTest = new IncidentHandler(indexName, true);
    inputEntity.setPosition(1L);
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1))
        .upsertWithScript(
            indexName, "0", inputEntity, concurrencyScriptMock(), Map.of("position", 1L));
  }

  @Test
  void shouldUpdateEntityFromRecord() {
    // given
    final long recordKey = 123L;
    final IncidentRecordValue incidentRecordValue =
        ImmutableIncidentRecordValue.builder()
            .from(factory.generateObject(IncidentRecordValue.class))
            .build();

    final Record<IncidentRecordValue> incidentRecord =
        factory.generateRecord(
            ValueType.INCIDENT,
            r ->
                r.withIntent(ProcessIntent.CREATED)
                    .withValue(incidentRecordValue)
                    .withKey(recordKey)
                    .withPartitionId(2)
                    .withPosition(100)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final IncidentEntity incidentEntity = new IncidentEntity();
    underTest.updateEntity(incidentRecord, incidentEntity);

    // then
    assertThat(incidentEntity.getId()).isEqualTo(String.valueOf(recordKey));
    assertThat(incidentEntity.getKey()).isEqualTo(123L);
    assertThat(incidentEntity.getPartitionId()).isEqualTo(incidentRecord.getPartitionId());
    assertThat(incidentEntity.getPosition()).isEqualTo(incidentRecord.getPosition());
    assertThat(incidentEntity.getFlowNodeId()).isEqualTo(incidentRecordValue.getElementId());
    assertThat(incidentEntity.getErrorMessage())
        .isEqualTo(ExporterUtil.trimWhitespace(incidentRecordValue.getErrorMessage()));
    assertThat(incidentEntity.getErrorType().name())
        .isEqualTo(incidentRecordValue.getErrorType().name());
    assertThat(incidentEntity.getBpmnProcessId()).isEqualTo(incidentRecordValue.getBpmnProcessId());
    assertThat(incidentEntity.getProcessDefinitionKey())
        .isEqualTo(incidentRecordValue.getProcessDefinitionKey());
    assertThat(incidentEntity.getJobKey()).isEqualTo(incidentRecordValue.getJobKey());
    assertThat(incidentEntity.getCreationTime())
        .isEqualTo(
            OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(incidentRecord.getTimestamp()), ZoneOffset.UTC));
    assertThat(incidentEntity.getErrorMessage())
        .isEqualTo(ExporterUtil.trimWhitespace(incidentRecordValue.getErrorMessage()));
    assertThat(incidentEntity.getFlowNodeInstanceKey())
        .isEqualTo(incidentRecordValue.getElementInstanceKey());
    assertThat(incidentEntity.getErrorMessageHash())
        .isEqualTo(incidentRecordValue.getErrorMessage().hashCode());
  }

  private String concurrencyScriptMock() {
    return String.format(
        "if (ctx._source.%s == null || ctx._source.%s < params.%s) { "
            + "ctx._source.%s = params.%s; " // position
            + "if (params.%s != null) {"
            + "   ctx._source.%s = params.%s; " // PROCESS_DEFINITION_KEY
            + "   ctx._source.%s = params.%s; " // BPMN_PROCESS_ID
            + "   ctx._source.%s = params.%s; " // FLOW_NODE_ID
            + "}"
            + "}",
        POSITION,
        POSITION,
        POSITION,
        POSITION,
        POSITION,
        PROCESS_DEFINITION_KEY,
        PROCESS_DEFINITION_KEY,
        PROCESS_DEFINITION_KEY,
        BPMN_PROCESS_ID,
        BPMN_PROCESS_ID,
        FLOW_NODE_ID,
        FLOW_NODE_ID);
  }
}
