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

import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBusinessIdIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceBusinessIdRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceBusinessIdRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

public class ListViewProcessInstanceBusinessIdFromProcessInstanceBusinessIdHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-list-view";
  private final ListViewProcessInstanceBusinessIdFromProcessInstanceBusinessIdHandler underTest =
      new ListViewProcessInstanceBusinessIdFromProcessInstanceBusinessIdHandler(indexName);

  @Test
  public void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.PROCESS_INSTANCE_BUSINESS_ID);
  }

  @Test
  public void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(ProcessInstanceForListViewEntity.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = ProcessInstanceBusinessIdIntent.class,
      names = {"ASSIGNED"},
      mode = Mode.INCLUDE)
  public void shouldHandleRecord(final ProcessInstanceBusinessIdIntent intent) {
    final Record<ProcessInstanceBusinessIdRecordValue> record = createRecord(intent);
    // when - then
    assertThat(underTest.handlesRecord(record)).as("Handles intent %s", intent).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = ProcessInstanceBusinessIdIntent.class,
      names = {"ASSIGNED"},
      mode = Mode.EXCLUDE)
  public void shouldNotHandleRecord(final ProcessInstanceBusinessIdIntent intent) {
    final Record<ProcessInstanceBusinessIdRecordValue> record = createRecord(intent);
    // when - then
    assertThat(underTest.handlesRecord(record)).as("Does not handle intent %s", intent).isFalse();
  }

  @Test
  public void shouldGenerateIds() {
    // given
    final long expectedId = 123;
    final Record<ProcessInstanceBusinessIdRecordValue> record =
        createRecord(
            ProcessInstanceBusinessIdIntent.ASSIGNED, b -> b.withProcessInstanceKey(expectedId));

    // when
    final var idList = underTest.generateIds(record);

    // then
    assertThat(idList).containsExactly(String.valueOf(expectedId));
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
  void shouldUpdateOnlyBusinessIdOnFlush() {
    // given
    final ProcessInstanceForListViewEntity inputEntity =
        new ProcessInstanceForListViewEntity().setId("111").setBusinessId("my-business-id");
    final TargetIndex index = mock(TargetIndex.class);
    final BatchRequest mockRequest = mock(BatchRequest.class);

    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put(ListViewTemplate.BUSINESS_ID, "my-business-id");

    // when
    underTest.flush(index, inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).update(index, "111", expectedUpdateFields);
  }

  @Test
  void shouldNotCreateDocumentAndNormalizeEmptyBusinessIdToNullOnFlush() {
    // given
    final ProcessInstanceForListViewEntity inputEntity =
        new ProcessInstanceForListViewEntity().setId("111").setBusinessId("");
    final TargetIndex index = mock(TargetIndex.class);
    final BatchRequest mockRequest = mock(BatchRequest.class);

    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put(ListViewTemplate.BUSINESS_ID, null);

    // when
    underTest.flush(index, inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).update(index, "111", expectedUpdateFields);
  }

  @Test
  void shouldPopulateBusinessIdOnUpdateEntity() {
    // given
    final Record<ProcessInstanceBusinessIdRecordValue> record =
        createRecord(
            ProcessInstanceBusinessIdIntent.ASSIGNED,
            b -> b.withProcessInstanceKey(111L).withBusinessId("my-business-id"));
    final ProcessInstanceForListViewEntity entity = underTest.createNewEntity(String.valueOf(111L));

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getId()).isEqualTo("111");
    assertThat(entity.getProcessInstanceKey()).isEqualTo(111L);
    assertThat(entity.getKey()).isEqualTo(111L);
    assertThat(entity.getBusinessId()).isEqualTo("my-business-id");
  }

  @Test
  void shouldNormalizeEmptyBusinessIdToNullOnUpdateEntity() {
    // given
    final Record<ProcessInstanceBusinessIdRecordValue> record =
        createRecord(
            ProcessInstanceBusinessIdIntent.ASSIGNED,
            b -> b.withProcessInstanceKey(111L).withBusinessId(""));
    final ProcessInstanceForListViewEntity entity = underTest.createNewEntity(String.valueOf(111L));

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getBusinessId()).isNull();
  }

  private Record<ProcessInstanceBusinessIdRecordValue> createRecord(
      final ProcessInstanceBusinessIdIntent intent) {
    return createRecord(intent, b -> b);
  }

  private Record<ProcessInstanceBusinessIdRecordValue> createRecord(
      final ProcessInstanceBusinessIdIntent intent,
      final java.util.function.UnaryOperator<ImmutableProcessInstanceBusinessIdRecordValue.Builder>
          customizer) {
    final ProcessInstanceBusinessIdRecordValue value =
        customizer
            .apply(
                ImmutableProcessInstanceBusinessIdRecordValue.builder()
                    .from(factory.generateObject(ProcessInstanceBusinessIdRecordValue.class)))
            .build();
    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE_BUSINESS_ID, r -> r.withIntent(intent).withValue(value));
  }
}
