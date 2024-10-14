/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor.POSITION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

public class ListViewProcessInstanceFromProcessInstanceHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-list-view";
  private final ListViewProcessInstanceFromProcessInstanceHandler underTest =
      new ListViewProcessInstanceFromProcessInstanceHandler(indexName, false);

  @Test
  public void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.PROCESS_INSTANCE);
  }

  @Test
  public void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(ProcessInstanceForListViewEntity.class);
  }

  @Test
  public void shouldHandleRecord() {
    final Set<ProcessInstanceIntent> intents2Handle =
        Set.of(
            ProcessInstanceIntent.ELEMENT_ACTIVATING,
            ProcessInstanceIntent.ELEMENT_COMPLETED,
            ProcessInstanceIntent.ELEMENT_TERMINATED,
            ProcessInstanceIntent.ELEMENT_MIGRATED);

    intents2Handle.stream()
        .forEach(
            intent -> {
              final Record<ProcessInstanceRecordValue> processInstanceRecord = createRecord(intent);
              // when - then
              assertThat(underTest.handlesRecord(processInstanceRecord))
                  .as("Handles intent %s", intent)
                  .isTrue();
            });
  }

  private Record<ProcessInstanceRecordValue> createRecord(final ProcessInstanceIntent intent) {
    final ProcessInstanceRecordValue processInstanceRecordValue =
        ImmutableProcessInstanceRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceRecordValue.class))
            .withBpmnElementType(BpmnElementType.PROCESS)
            .build();
    final Record<ProcessInstanceRecordValue> processInstanceRecord =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r -> r.withIntent(intent).withValue(processInstanceRecordValue));
    return processInstanceRecord;
  }

  @Test
  public void shouldNotHandleRecord() {
    final Set<ProcessInstanceIntent> intents2Handle =
        Set.of(
            ProcessInstanceIntent.ELEMENT_ACTIVATING,
            ProcessInstanceIntent.ELEMENT_COMPLETED,
            ProcessInstanceIntent.ELEMENT_TERMINATED,
            ProcessInstanceIntent.ELEMENT_MIGRATED);
    final Set<ProcessInstanceIntent> intents2Ignore =
        Arrays.stream(ProcessInstanceIntent.values())
            .filter(intent -> !intents2Handle.contains(intent))
            .collect(Collectors.toSet());

    intents2Ignore.stream()
        .forEach(
            intent -> {
              final Record<ProcessInstanceRecordValue> processInstanceRecord = createRecord(intent);
              // when - then
              assertThat(underTest.handlesRecord(processInstanceRecord))
                  .as("Does not handle intent %s", intent)
                  .isFalse();
            });
  }

  @Test
  public void shouldGenerateIds() {
    // given
    final long expectedId = 123;
    final ProcessInstanceRecordValue processInstanceRecordValue =
        ImmutableProcessInstanceRecordValue.builder()
            .from(factory.generateObject(DecisionRecordValue.class))
            .withBpmnElementType(BpmnElementType.PROCESS)
            .withProcessInstanceKey(expectedId)
            .build();

    final Record<ProcessInstanceRecordValue> processInstanceRecord =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r ->
                r.withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                    .withValue(processInstanceRecordValue));

    // when
    final var idList = underTest.generateIds(processInstanceRecord);

    // then
    assertThat(idList).containsExactly(String.valueOf(expectedId));
  }

  @Test
  public void shouldCreateNewEntity() {
    // when
    final var result = (underTest.createNewEntity("id"));

    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  void shouldAddEntityOnFlush() {
    // given
    final ProcessInstanceForListViewEntity inputEntity =
        new ProcessInstanceForListViewEntity()
            .setId("111")
            .setProcessName("process")
            .setProcessVersion(2)
            .setProcessDefinitionKey(444L)
            .setBpmnProcessId("bpmnProcessId")
            .setPosition(123L);
    final BatchRequest mockRequest = mock(BatchRequest.class);

    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put(ListViewTemplate.PROCESS_NAME, "process");
    expectedUpdateFields.put(ListViewTemplate.PROCESS_VERSION, 2);
    expectedUpdateFields.put(ListViewTemplate.PROCESS_KEY, 444L);
    expectedUpdateFields.put(ListViewTemplate.BPMN_PROCESS_ID, "bpmnProcessId");
    expectedUpdateFields.put(POSITION, 123L);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).upsert(indexName, "111", inputEntity, expectedUpdateFields);
  }

  @Test
  void shouldUpdateEntityFromRecord() {
    // given
    final ProcessInstanceRecordValue processInstanceRecordValue =
        ImmutableProcessInstanceRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceRecordValue.class))
            .withProcessInstanceKey(66L)
            .withProcessDefinitionKey(222L)
            .withBpmnProcessId("bpmnProcessId")
            .withVersion(7)
            .withTenantId("tenantId")
            .withParentProcessInstanceKey(777L)
            .withParentElementInstanceKey(111L)
            .build();

    final long timestamp = new Date().getTime();
    final Record<ProcessInstanceRecordValue> processInstanceRecord =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r ->
                r.withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                    .withTimestamp(timestamp)
                    .withPartitionId(3)
                    .withPosition(55L)
                    .withValue(processInstanceRecordValue));

    // when
    final ProcessInstanceForListViewEntity processInstanceForListViewEntity =
        new ProcessInstanceForListViewEntity();
    underTest.updateEntity(processInstanceRecord, processInstanceForListViewEntity);

    // then
    assertThat(processInstanceForListViewEntity.getId()).isEqualTo("66");
    assertThat(processInstanceForListViewEntity.getProcessInstanceKey()).isEqualTo(66L);
    assertThat(processInstanceForListViewEntity.getKey()).isEqualTo(66L);
    assertThat(processInstanceForListViewEntity.getTenantId()).isEqualTo("tenantId");
    assertThat(processInstanceForListViewEntity.getPartitionId()).isEqualTo(3);
    assertThat(processInstanceForListViewEntity.getPosition()).isEqualTo(55L);
    assertThat(processInstanceForListViewEntity.getProcessDefinitionKey()).isEqualTo(222L);
    assertThat(processInstanceForListViewEntity.getBpmnProcessId()).isEqualTo("bpmnProcessId");
    assertThat(processInstanceForListViewEntity.getProcessVersion()).isEqualTo(7);
    assertThat(processInstanceForListViewEntity.getProcessName()).isEqualTo("bpmnProcessId");
    assertThat(processInstanceForListViewEntity.getState()).isEqualTo(ProcessInstanceState.ACTIVE);
    assertThat(processInstanceForListViewEntity.getStartDate())
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC));
    assertThat(processInstanceForListViewEntity.getParentProcessInstanceKey()).isEqualTo(777L);
    assertThat(processInstanceForListViewEntity.getParentFlowNodeInstanceKey()).isEqualTo(111L);
    assertThat(processInstanceForListViewEntity.getTreePath()).isEqualTo("PI_66");
  }
}
