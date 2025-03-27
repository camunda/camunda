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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableIncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class FlowNodeInstanceFromIncidentHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-flownode-instance";
  private final FlowNodeInstanceFromIncidentHandler underTest =
      new FlowNodeInstanceFromIncidentHandler(indexName);

  @Test
  public void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.INCIDENT);
  }

  @Test
  public void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(FlowNodeInstanceEntity.class);
  }

  @Test
  public void shouldHandleRecord() {
    final Record<IncidentRecordValue> incidentRecord = factory.generateRecord(ValueType.INCIDENT);
    assertThat(underTest.handlesRecord(incidentRecord)).isTrue();
  }

  @Test
  public void shouldGenerateIds() {
    // given
    final IncidentRecordValue incidentRecordValue =
        factory.generateObject(ImmutableIncidentRecordValue.Builder.class).build();
    final Record<IncidentRecordValue> incidentRecord =
        factory.generateRecord(ValueType.INCIDENT, i -> i.withValue(incidentRecordValue));

    // when
    final var idList = underTest.generateIds(incidentRecord);

    // then
    assertThat(idList)
        .containsExactly(String.valueOf(incidentRecord.getValue().getElementInstanceKey()));
  }

  @Test
  public void shouldCreateNewEntity() {
    // when
    final var result = underTest.createNewEntity("id");
    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  public void shouldUpsertEntityOnFlush() {
    // given
    final FlowNodeInstanceEntity inputEntity =
        new FlowNodeInstanceEntity()
            .setId("123")
            .setKey(567)
            .setPartitionId(3)
            .setFlowNodeId("A")
            .setProcessInstanceKey(345L)
            .setProcessDefinitionKey(789L)
            .setBpmnProcessId("someProcess")
            .setTenantId("tenantId")
            .setIncidentKey(987L);

    final BatchRequest mockRequest = mock(BatchRequest.class);

    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put(FlowNodeInstanceTemplate.INCIDENT_KEY, inputEntity.getIncidentKey());

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1))
        .upsert(indexName, inputEntity.getId(), inputEntity, expectedUpdateFields);
  }

  @Test
  public void shouldUpdateEntityFromRecord() {
    final Set<IncidentIntent> intents2Handle =
        Set.of(IncidentIntent.CREATED, IncidentIntent.MIGRATED);

    intents2Handle.stream()
        .forEach(
            intent -> {
              // given
              final IncidentRecordValue incidentRecordValue =
                  factory
                      .generateObject(ImmutableIncidentRecordValue.Builder.class)
                      .withElementInstanceKey(666)
                      .build();
              final Record<IncidentRecordValue> incidentRecord =
                  factory.generateRecord(
                      ValueType.INCIDENT, r -> r.withIntent(intent).withValue(incidentRecordValue));

              // when
              final FlowNodeInstanceEntity flowNodeInstanceEntity =
                  new FlowNodeInstanceEntity().setId("666");
              underTest.updateEntity(incidentRecord, flowNodeInstanceEntity);

              // then
              assertThat(flowNodeInstanceEntity.getId()).isEqualTo("666");
              assertThat(flowNodeInstanceEntity.getIncidentKey())
                  .isEqualTo(incidentRecord.getKey());
            });
  }

  @Test
  public void shouldUpdateEntityForResolvedIncident() {
    // given
    final long elementInstanceKey = 123;
    final IncidentRecordValue incidentRecordValue =
        factory
            .generateObject(ImmutableIncidentRecordValue.Builder.class)
            .withElementInstanceKey(elementInstanceKey)
            .build();
    final Record<IncidentRecordValue> incidentRecord =
        factory.generateRecord(
            ValueType.INCIDENT,
            r -> r.withIntent(IncidentIntent.RESOLVED).withValue(incidentRecordValue));

    // when
    final FlowNodeInstanceEntity flowNodeInstanceEntity =
        new FlowNodeInstanceEntity().setId(String.valueOf(elementInstanceKey));
    underTest.updateEntity(incidentRecord, flowNodeInstanceEntity);

    // then
    assertThat(flowNodeInstanceEntity.getIncidentKey()).isNull();
  }
}
