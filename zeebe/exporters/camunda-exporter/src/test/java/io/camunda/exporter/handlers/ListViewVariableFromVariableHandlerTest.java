/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor.POSITION;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.VAR_NAME;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.VAR_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.listview.VariableForListViewEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

public class ListViewVariableFromVariableHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-list-view";
  private final ListViewVariableFromVariableHandler underTest =
      new ListViewVariableFromVariableHandler(indexName);

  @Test
  public void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.VARIABLE);
  }

  @Test
  public void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(VariableForListViewEntity.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = VariableIntent.class,
      names = {"CREATED", "UPDATED"},
      mode = Mode.INCLUDE)
  void shouldHandleRecord(final VariableIntent intent) {
    // given
    final Record<VariableRecordValue> variableRecord =
        factory.generateRecord(ValueType.VARIABLE, r -> r.withIntent(intent));

    // when - then
    assertThat(underTest.handlesRecord(variableRecord)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = VariableIntent.class,
      names = {"MIGRATED"},
      mode = Mode.INCLUDE)
  void shouldNotHandleRecord(final VariableIntent intent) {
    // given
    final Record<VariableRecordValue> variableRecord =
        factory.generateRecord(ValueType.VARIABLE, r -> r.withIntent(intent));

    // when - then
    assertThat(underTest.handlesRecord(variableRecord)).isFalse();
  }

  @Test
  public void shouldGenerateIds() {
    // given
    final Record<VariableRecordValue> variableRecord =
        factory.generateRecord(ValueType.VARIABLE, r -> r.withIntent(VariableIntent.CREATED));
    final String expectedId =
        VariableForListViewEntity.getIdBy(
            variableRecord.getValue().getScopeKey(), variableRecord.getValue().getName());

    // when
    final var idList = underTest.generateIds(variableRecord);

    // then
    assertThat(idList).containsExactly(expectedId);
  }

  @Test
  public void shouldCreateNewEntity() {
    final var result = (underTest.createNewEntity("id"));
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  public void shouldUpsertEntityOnFlush() {
    // given
    final VariableForListViewEntity inputEntity =
        new VariableForListViewEntity()
            .setId("66-A")
            .setProcessInstanceKey(66L)
            .setPosition(123L)
            .setVarName("A")
            .setVarValue("B");

    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put(POSITION, inputEntity.getPosition());
    expectedUpdateFields.put(VAR_NAME, inputEntity.getVarName());
    expectedUpdateFields.put(VAR_VALUE, inputEntity.getVarValue());

    final BatchRequest mockRequest = mock(BatchRequest.class);

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
    // having
    final Record<VariableRecordValue> variableRecord =
        factory.generateRecord(ValueType.VARIABLE, r -> r.withIntent(VariableIntent.CREATED));

    // when
    final VariableForListViewEntity entity = new VariableForListViewEntity();
    underTest.updateEntity(variableRecord, entity);

    // then
    assertThat(entity.getId())
        .isEqualTo(
            VariableForListViewEntity.getIdBy(
                variableRecord.getValue().getScopeKey(), variableRecord.getValue().getName()));
    assertThat(entity.getKey()).isEqualTo(variableRecord.getKey());
    assertThat(entity.getPartitionId()).isEqualTo(variableRecord.getPartitionId());
    assertThat(entity.getScopeKey()).isEqualTo(variableRecord.getValue().getScopeKey());
    assertThat(entity.getProcessInstanceKey())
        .isEqualTo(variableRecord.getValue().getProcessInstanceKey());
    assertThat(entity.getPosition()).isEqualTo(variableRecord.getPosition());
    assertThat(entity.getVarName()).isEqualTo(variableRecord.getValue().getName());
    assertThat(entity.getVarValue()).isEqualTo(variableRecord.getValue().getValue());
    assertThat(entity.getTenantId()).isEqualTo(variableRecord.getValue().getTenantId());
  }
}
