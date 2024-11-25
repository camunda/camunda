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

import io.camunda.exporter.cache.TestProcessCache;
import io.camunda.exporter.cache.process.CachedProcessEntity;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.operate.listview.ListViewJoinRelation;
import io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
  private final TestProcessCache processCache = new TestProcessCache();
  private final ListViewProcessInstanceFromProcessInstanceHandler underTest =
      new ListViewProcessInstanceFromProcessInstanceHandler(indexName, false, processCache);

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
  public void shouldNotHandleNotProcessRecord() {
    // given
    final ProcessInstanceRecordValue processInstanceRecordValue =
        ImmutableProcessInstanceRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceRecordValue.class))
            .withBpmnElementType(BpmnElementType.SERVICE_TASK)
            .build();
    final Record<ProcessInstanceRecordValue> processInstanceRecord =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r ->
                r.withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                    .withValue(processInstanceRecordValue));
    // when - then
    assertThat(underTest.handlesRecord(processInstanceRecord)).isFalse();
  }

  @Test
  public void shouldGenerateIds() {
    // given
    final long expectedId = 123;
    final ProcessInstanceRecordValue processInstanceRecordValue =
        ImmutableProcessInstanceRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceRecordValue.class))
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
    final var result = underTest.createNewEntity("id");

    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  void shouldUpsertEntityOnFlush() {
    // given
    final ProcessInstanceForListViewEntity inputEntity =
        new ProcessInstanceForListViewEntity()
            .setId("111")
            .setProcessName("process")
            .setProcessVersion(2)
            .setProcessDefinitionKey(444L)
            .setBpmnProcessId("bpmnProcessId")
            .setPosition(123L)
            .setStartDate(OffsetDateTime.now())
            .setEndDate(OffsetDateTime.now())
            .setState(ProcessInstanceState.ACTIVE);
    final BatchRequest mockRequest = mock(BatchRequest.class);

    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put(ListViewTemplate.PROCESS_NAME, "process");
    expectedUpdateFields.put(ListViewTemplate.PROCESS_VERSION, 2);
    expectedUpdateFields.put(ListViewTemplate.PROCESS_KEY, 444L);
    expectedUpdateFields.put(ListViewTemplate.BPMN_PROCESS_ID, "bpmnProcessId");
    expectedUpdateFields.put(ListViewTemplate.STATE, ProcessInstanceState.ACTIVE);
    expectedUpdateFields.put(ListViewTemplate.START_DATE, inputEntity.getStartDate());
    expectedUpdateFields.put(ListViewTemplate.END_DATE, inputEntity.getEndDate());
    expectedUpdateFields.put(POSITION, 123L);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).upsert(indexName, "111", inputEntity, expectedUpdateFields);
  }

  @Test
  void shouldUpsertEntityWithConcurrencyModeOnFlush() {
    // given
    final ListViewProcessInstanceFromProcessInstanceHandler underTest =
        new ListViewProcessInstanceFromProcessInstanceHandler(indexName, true, processCache);
    final ProcessInstanceForListViewEntity inputEntity =
        new ProcessInstanceForListViewEntity()
            .setId("111")
            .setProcessInstanceKey(111L)
            .setProcessName("process")
            .setProcessVersion(2)
            .setProcessDefinitionKey(444L)
            .setBpmnProcessId("bpmnProcessId")
            .setPosition(123L)
            .setStartDate(OffsetDateTime.now())
            .setEndDate(OffsetDateTime.now())
            .setState(ProcessInstanceState.ACTIVE);
    final BatchRequest mockRequest = mock(BatchRequest.class);

    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put(ListViewTemplate.PROCESS_NAME, "process");
    expectedUpdateFields.put(ListViewTemplate.PROCESS_VERSION, 2);
    expectedUpdateFields.put(ListViewTemplate.PROCESS_KEY, 444L);
    expectedUpdateFields.put(ListViewTemplate.BPMN_PROCESS_ID, "bpmnProcessId");
    expectedUpdateFields.put(ListViewTemplate.STATE, ProcessInstanceState.ACTIVE);
    expectedUpdateFields.put(ListViewTemplate.START_DATE, inputEntity.getStartDate());
    expectedUpdateFields.put(ListViewTemplate.END_DATE, inputEntity.getEndDate());
    expectedUpdateFields.put(POSITION, 123L);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1))
        .upsertWithScriptAndRouting(
            indexName,
            "111",
            inputEntity,
            underTest.getProcessInstanceScript(),
            expectedUpdateFields,
            "111");
  }

  @Test
  void shouldUpdateEntityFromRecord() {
    // given
    final long timestamp = new Date().getTime();
    final ProcessInstanceRecordValue processInstanceRecordValue =
        ImmutableProcessInstanceRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceRecordValue.class))
            .withBpmnElementType(BpmnElementType.PROCESS)
            .build();

    final Record<ProcessInstanceRecordValue> processInstanceRecord =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r ->
                r.withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                    .withTimestamp(timestamp)
                    .withPartitionId(3)
                    .withPosition(55L)
                    .withValue(processInstanceRecordValue));

    processCache.put(
        processInstanceRecordValue.getProcessDefinitionKey(),
        new CachedProcessEntity("test-process-name", "test-version-tag", new ArrayList<>()));

    // when
    final ProcessInstanceForListViewEntity processInstanceForListViewEntity =
        new ProcessInstanceForListViewEntity();
    underTest.updateEntity(processInstanceRecord, processInstanceForListViewEntity);

    // then
    assertThat(processInstanceForListViewEntity.getId())
        .isEqualTo(String.valueOf(processInstanceRecordValue.getProcessInstanceKey()));
    assertThat(processInstanceForListViewEntity.getProcessInstanceKey())
        .isEqualTo(processInstanceRecordValue.getProcessInstanceKey());
    assertThat(processInstanceForListViewEntity.getKey())
        .isEqualTo(processInstanceRecordValue.getProcessInstanceKey());
    assertThat(processInstanceForListViewEntity.getTenantId())
        .isEqualTo(processInstanceRecordValue.getTenantId());
    assertThat(processInstanceForListViewEntity.getPartitionId())
        .isEqualTo(processInstanceRecord.getPartitionId());
    assertThat(processInstanceForListViewEntity.getPosition())
        .isEqualTo(processInstanceRecord.getPosition());
    assertThat(processInstanceForListViewEntity.getProcessDefinitionKey())
        .isEqualTo(processInstanceRecordValue.getProcessDefinitionKey());
    assertThat(processInstanceForListViewEntity.getBpmnProcessId())
        .isEqualTo(processInstanceRecordValue.getBpmnProcessId());
    assertThat(processInstanceForListViewEntity.getProcessVersion())
        .isEqualTo(processInstanceRecordValue.getVersion());
    assertThat(processInstanceForListViewEntity.getState()).isEqualTo(ProcessInstanceState.ACTIVE);
    assertThat(processInstanceForListViewEntity.getStartDate())
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC));
    assertThat(processInstanceForListViewEntity.getEndDate()).isNull();
    assertThat(processInstanceForListViewEntity.getParentProcessInstanceKey())
        .isEqualTo(processInstanceRecordValue.getParentProcessInstanceKey());
    assertThat(processInstanceForListViewEntity.getParentFlowNodeInstanceKey())
        .isEqualTo(processInstanceRecordValue.getParentElementInstanceKey());
    assertThat(processInstanceForListViewEntity.getJoinRelation())
        .isEqualTo(new ListViewJoinRelation(ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION));
    assertThat(processInstanceForListViewEntity.getTreePath())
        .isEqualTo("PI_" + processInstanceRecordValue.getProcessInstanceKey());

    // process name and version tag is read from the cache
    assertThat(processInstanceForListViewEntity.getProcessName()).isEqualTo("test-process-name");
    assertThat(processInstanceForListViewEntity.getProcessVersionTag())
        .isEqualTo("test-version-tag");
  }

  @Test
  void shouldUpdateEndTimeForCompletedRecord() {
    // given
    final long timestamp = new Date().getTime();
    final ProcessInstanceRecordValue processInstanceRecordValue =
        ImmutableProcessInstanceRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceRecordValue.class))
            .withBpmnElementType(BpmnElementType.PROCESS)
            .build();
    final Record<ProcessInstanceRecordValue> processInstanceRecord =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r ->
                r.withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                    .withTimestamp(timestamp)
                    .withValue(processInstanceRecordValue));

    // when then
    final ProcessInstanceForListViewEntity processInstanceForListViewEntity =
        new ProcessInstanceForListViewEntity();
    underTest.updateEntity(processInstanceRecord, processInstanceForListViewEntity);

    assertThat(processInstanceForListViewEntity.getEndDate())
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC));
    assertThat(processInstanceForListViewEntity.getStartDate()).isNull();
    assertThat(processInstanceForListViewEntity.getState())
        .isEqualTo(ProcessInstanceState.COMPLETED);
  }

  @Test
  void shouldUpdateEndTimeForTerminatedRecord() {
    // given
    final long timestamp = new Date().getTime();
    final ProcessInstanceRecordValue processInstanceRecordValue =
        ImmutableProcessInstanceRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceRecordValue.class))
            .withBpmnElementType(BpmnElementType.PROCESS)
            .build();
    final Record<ProcessInstanceRecordValue> processInstanceRecord =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r ->
                r.withIntent(ProcessInstanceIntent.ELEMENT_TERMINATED)
                    .withTimestamp(timestamp)
                    .withValue(processInstanceRecordValue));

    // when then
    final ProcessInstanceForListViewEntity processInstanceForListViewEntity =
        new ProcessInstanceForListViewEntity();
    underTest.updateEntity(processInstanceRecord, processInstanceForListViewEntity);

    assertThat(processInstanceForListViewEntity.getEndDate())
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC));
    assertThat(processInstanceForListViewEntity.getStartDate()).isNull();
    assertThat(processInstanceForListViewEntity.getState())
        .isEqualTo(ProcessInstanceState.CANCELED);
  }

  @Test
  void shouldUpdateStateForMigrateRecord() {
    // given
    final long timestamp = new Date().getTime();
    final Record<ProcessInstanceRecordValue> processInstanceRecord =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r -> r.withIntent(ProcessInstanceIntent.ELEMENT_MIGRATED).withTimestamp(timestamp));

    // when then
    final ProcessInstanceForListViewEntity processInstanceForListViewEntity =
        new ProcessInstanceForListViewEntity();
    underTest.updateEntity(processInstanceRecord, processInstanceForListViewEntity);

    assertThat(processInstanceForListViewEntity.getEndDate()).isNull();
    assertThat(processInstanceForListViewEntity.getStartDate()).isNull();
    assertThat(processInstanceForListViewEntity.getState()).isEqualTo(ProcessInstanceState.ACTIVE);
  }
}
