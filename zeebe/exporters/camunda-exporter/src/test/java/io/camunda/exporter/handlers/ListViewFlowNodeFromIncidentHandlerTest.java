/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.webapps.schema.descriptors.operate.template.JobTemplate.ERROR_MESSAGE;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.INCIDENT_POSITION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class ListViewFlowNodeFromIncidentHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-list-view";
  private final ListViewFlowNodeFromIncidentHandler underTest =
      new ListViewFlowNodeFromIncidentHandler(indexName);

  @Test
  public void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.INCIDENT);
  }

  @Test
  public void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(FlowNodeInstanceForListViewEntity.class);
  }

  @Test
  public void shouldHandlesRecord() {
    final Record<IncidentRecordValue> mockIncidentRecord = Mockito.mock(Record.class);
    assertThat(underTest.handlesRecord(mockIncidentRecord)).isTrue();
  }

  @Test
  public void shouldGenerateIds() {
    // given
    final Record<IncidentRecordValue> incidentRecord = factory.generateRecord(ValueType.INCIDENT);
    // when
    final var idList = underTest.generateIds(incidentRecord);
    // then
    assertThat(idList)
        .containsExactly(String.valueOf(incidentRecord.getValue().getElementInstanceKey()));
  }

  @Test
  public void shouldCreateNewEntity() {
    final var result = underTest.createNewEntity("id");
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  public void shouldUpsertEntityOnFlush() {
    // given
    final FlowNodeInstanceForListViewEntity inputEntity =
        new FlowNodeInstanceForListViewEntity()
            .setId("111")
            .setKey(111L)
            .setPartitionId(3)
            .setTenantId("tenantId")
            .setProcessInstanceKey(66L)
            .setErrorMessage("error")
            .setPositionIncident(123L);
    inputEntity.getJoinRelation().setParent(66L);

    final BatchRequest mockRequest = mock(BatchRequest.class);

    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put(ERROR_MESSAGE, inputEntity.getErrorMessage());
    expectedUpdateFields.put(INCIDENT_POSITION, inputEntity.getPositionIncident());
    // when
    underTest.flush(inputEntity, mockRequest);
    // then
    verify(mockRequest, times(1))
        .upsertWithRouting(
            indexName,
            inputEntity.getId(),
            inputEntity,
            expectedUpdateFields,
            String.valueOf(inputEntity.getProcessInstanceKey()));
  }

  @Test
  public void shouldUpdateEntityFromRecord() {
    // given
    final Record<IncidentRecordValue> incidentRecord =
        factory.generateRecord(ValueType.INCIDENT, r -> r.withIntent(IncidentIntent.CREATED));
    // when
    final FlowNodeInstanceForListViewEntity flowNodeInstanceForListViewEntity =
        new FlowNodeInstanceForListViewEntity();
    underTest.updateEntity(incidentRecord, flowNodeInstanceForListViewEntity);
    // then
    assertThat(flowNodeInstanceForListViewEntity.getId())
        .isEqualTo(String.valueOf(incidentRecord.getValue().getElementInstanceKey()));
    assertThat(flowNodeInstanceForListViewEntity.getKey())
        .isEqualTo(incidentRecord.getValue().getElementInstanceKey());
    assertThat(flowNodeInstanceForListViewEntity.getPartitionId())
        .isEqualTo(incidentRecord.getPartitionId());
    assertThat(flowNodeInstanceForListViewEntity.getActivityId())
        .isEqualTo(incidentRecord.getValue().getElementId());
    assertThat(flowNodeInstanceForListViewEntity.getProcessInstanceKey())
        .isEqualTo(incidentRecord.getValue().getProcessInstanceKey());
    assertThat(flowNodeInstanceForListViewEntity.getErrorMessage())
        .isEqualTo(incidentRecord.getValue().getErrorMessage());
    assertThat(flowNodeInstanceForListViewEntity.getTenantId())
        .isEqualTo(incidentRecord.getValue().getTenantId());
    assertThat(flowNodeInstanceForListViewEntity.getJoinRelation().getParent())
        .isEqualTo(incidentRecord.getValue().getProcessInstanceKey());
    assertThat(flowNodeInstanceForListViewEntity.getPositionIncident())
        .isEqualTo(incidentRecord.getPosition());
  }

  @Test
  public void shouldRemoveErrorMessageForResolvedIncident() {
    // given
    final Record<IncidentRecordValue> incidentRecord =
        factory.generateRecord(ValueType.INCIDENT, r -> r.withIntent(IncidentIntent.RESOLVED));
    // when
    final FlowNodeInstanceForListViewEntity flowNodeInstanceForListViewEntity =
        new FlowNodeInstanceForListViewEntity();
    underTest.updateEntity(incidentRecord, flowNodeInstanceForListViewEntity);
    // then
    assertThat(flowNodeInstanceForListViewEntity.getErrorMessage()).isNull();
  }
}
