/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.ERROR_MSG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableIncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class ListViewProcessInstanceFromIncidentHandlerTest {

  private static final String TENANT_ID = "test-tenant";

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-list-view";
  private final ListViewProcessInstanceFromIncidentHandler underTest =
      new ListViewProcessInstanceFromIncidentHandler(indexName);

  @Test
  public void shouldHandleIncidentValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.INCIDENT);
  }

  @Test
  public void shouldHandleProcessInstanceForListViewEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(ProcessInstanceForListViewEntity.class);
  }

  @Test
  void shouldGenerateIdFromProcessInstanceKey() {
    // given
    final Record<IncidentRecordValue> incidentRecord =
        createProcessLevelIncidentRecord(IncidentIntent.CREATED, 123L);

    // when - then
    assertThat(underTest.generateIds(incidentRecord)).containsExactly("123");
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
    final ProcessInstanceForListViewEntity inputEntity =
        new ProcessInstanceForListViewEntity()
            .setId("111")
            .setKey(111L)
            .setPartitionId(3)
            .setTenantId("tenantId")
            .setErrorMessage("error");

    final BatchRequest mockRequest = mock(BatchRequest.class);

    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put(ERROR_MSG, "error");

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1))
        .upsert(indexName, inputEntity.getId(), inputEntity, expectedUpdateFields);
  }

  @Test
  public void shouldUpdateEntityFromRecord() {
    // given
    final var processInstanceKey = 123L;
    final Record<IncidentRecordValue> incidentRecord =
        createProcessLevelIncidentRecord(IncidentIntent.CREATED, processInstanceKey);

    // when
    final var entity = new ProcessInstanceForListViewEntity();
    underTest.updateEntity(incidentRecord, entity);

    // then
    assertThat(entity.getId()).isEqualTo(String.valueOf(processInstanceKey));
    assertThat(entity.getKey()).isEqualTo(processInstanceKey);
    assertThat(entity.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(entity.getPartitionId()).isEqualTo(incidentRecord.getPartitionId());
    assertThat(entity.getTenantId()).isEqualTo(TENANT_ID);
    assertThat(entity.getErrorMessage()).isEqualTo(incidentRecord.getValue().getErrorMessage());
  }

  @Test
  public void shouldDefaultTenantIdWhenRecordHasNone() {
    // given
    final var recordValue =
        ImmutableIncidentRecordValue.builder()
            .from(factory.generateObject(IncidentRecordValue.class))
            .withProcessInstanceKey(123L)
            .withElementInstanceKey(123L)
            .withTenantId("")
            .build();
    final Record<IncidentRecordValue> incidentRecord =
        factory.generateRecord(
            ValueType.INCIDENT, r -> r.withIntent(IncidentIntent.CREATED).withValue(recordValue));

    // when
    final var entity = new ProcessInstanceForListViewEntity();
    underTest.updateEntity(incidentRecord, entity);

    // then
    assertThat(entity.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  public void shouldTrimErrorMessage() {
    // given
    final var processInstanceKey = 123L;
    final Record<IncidentRecordValue> incidentRecord =
        createProcessLevelIncidentRecord(
            IncidentIntent.CREATED, processInstanceKey, "  padded error\n");

    // when
    final var entity = new ProcessInstanceForListViewEntity();
    underTest.updateEntity(incidentRecord, entity);

    // then
    assertThat(entity.getErrorMessage()).isEqualTo("padded error");
  }

  @Test
  public void shouldRemoveErrorMessageForResolvedIncident() {
    // given
    final Record<IncidentRecordValue> incidentRecord =
        createProcessLevelIncidentRecord(IncidentIntent.RESOLVED, 123L);

    // when
    final var entity = new ProcessInstanceForListViewEntity().setErrorMessage("error");
    underTest.updateEntity(incidentRecord, entity);

    // then
    assertThat(entity.getErrorMessage()).isNull();
  }

  @ParameterizedTest
  @EnumSource(
      value = IncidentIntent.class,
      names = {"CREATED", "RESOLVED"})
  void shouldHandleProcessLevelIncidentWithSupportedIntent(final IncidentIntent intent) {
    // given
    final Record<IncidentRecordValue> incidentRecord =
        createProcessLevelIncidentRecord(intent, 123L);

    // when - then
    assertThat(underTest.handlesRecord(incidentRecord)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = IncidentIntent.class,
      names = {"RESOLVE", "MIGRATED"})
  void shouldNotHandleRecordWithUnsupportedIntent(final IncidentIntent intent) {
    // given
    final Record<IncidentRecordValue> incidentRecord =
        createProcessLevelIncidentRecord(intent, 123L);

    // when - then
    assertThat(underTest.handlesRecord(incidentRecord)).isFalse();
  }

  @Test
  void shouldNotHandleFlowNodeLevelIncidentRecords() {
    // given
    final var recordValue =
        ImmutableIncidentRecordValue.builder()
            .from(factory.generateObject(IncidentRecordValue.class))
            .withProcessInstanceKey(123L)
            .withElementInstanceKey(456L)
            .build();
    final Record<IncidentRecordValue> incidentRecord =
        factory.generateRecord(
            ValueType.INCIDENT, r -> r.withIntent(IncidentIntent.CREATED).withValue(recordValue));

    // when - then
    assertThat(underTest.handlesRecord(incidentRecord)).isFalse();
  }

  private Record<IncidentRecordValue> createProcessLevelIncidentRecord(
      final IncidentIntent intent, final long processInstanceKey) {
    return createProcessLevelIncidentRecord(intent, processInstanceKey, "error");
  }

  private Record<IncidentRecordValue> createProcessLevelIncidentRecord(
      final IncidentIntent intent, final long processInstanceKey, final String errorMessage) {
    final var recordValue =
        ImmutableIncidentRecordValue.builder()
            .from(factory.generateObject(IncidentRecordValue.class))
            .withProcessInstanceKey(processInstanceKey)
            .withElementInstanceKey(processInstanceKey)
            .withErrorMessage(errorMessage)
            .withTenantId(TENANT_ID)
            .build();
    return factory.generateRecord(
        ValueType.INCIDENT, r -> r.withIntent(intent).withValue(recordValue));
  }
}
