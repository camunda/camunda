/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor.POSITION;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.cache.TestProcessCache;
import io.camunda.exporter.cache.process.CachedProcessEntity;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.operate.TreePath;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.listview.ListViewJoinRelation;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue.Builder;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

public class ListViewProcessInstanceFromProcessInstanceHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-list-view";
  private final TestProcessCache processCache = new TestProcessCache();
  private final ListViewProcessInstanceFromProcessInstanceHandler underTest =
      new ListViewProcessInstanceFromProcessInstanceHandler(indexName, processCache);

  @Test
  public void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.PROCESS_INSTANCE);
  }

  @Test
  public void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(ProcessInstanceForListViewEntity.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = ProcessInstanceIntent.class,
      names = {
        "ELEMENT_ACTIVATING",
        "ELEMENT_COMPLETED",
        "ELEMENT_TERMINATED",
        "ELEMENT_MIGRATED",
        "ANCESTOR_MIGRATED"
      },
      mode = Mode.INCLUDE)
  public void shouldHandleRecord(final ProcessInstanceIntent intent) {
    final Record<ProcessInstanceRecordValue> processInstanceRecord = createRecord(intent);
    // when - then
    assertThat(underTest.handlesRecord(processInstanceRecord))
        .as("Handles intent %s", intent)
        .isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = ProcessInstanceIntent.class,
      names = {
        "ELEMENT_ACTIVATING",
        "ELEMENT_COMPLETED",
        "ELEMENT_TERMINATED",
        "ELEMENT_MIGRATED",
        "ANCESTOR_MIGRATED"
      },
      mode = Mode.EXCLUDE)
  public void shouldNotHandleRecord(final ProcessInstanceIntent intent) {

    final Record<ProcessInstanceRecordValue> processInstanceRecord = createRecord(intent);
    // when - then
    assertThat(underTest.handlesRecord(processInstanceRecord))
        .as("Does not handle intent %s", intent)
        .isFalse();
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
            r -> r.withIntent(ELEMENT_ACTIVATING).withValue(processInstanceRecordValue));
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
            r -> r.withIntent(ELEMENT_ACTIVATING).withValue(processInstanceRecordValue));

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
            .setProcessVersionTag("versionTag")
            .setProcessDefinitionKey(444L)
            .setBpmnProcessId("bpmnProcessId")
            .setPosition(123L)
            .setStartDate(OffsetDateTime.now())
            .setEndDate(OffsetDateTime.now())
            .setState(ProcessInstanceState.ACTIVE)
            .setTreePath("PI_111");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put(ListViewTemplate.PROCESS_NAME, "process");
    expectedUpdateFields.put(ListViewTemplate.PROCESS_VERSION, 2);
    expectedUpdateFields.put(ListViewTemplate.PROCESS_VERSION_TAG, "versionTag");
    expectedUpdateFields.put(ListViewTemplate.PROCESS_KEY, 444L);
    expectedUpdateFields.put(ListViewTemplate.TREE_PATH, "PI_111");
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
  void shouldNotUpsertNullFields() {
    // given
    final ProcessInstanceForListViewEntity inputEntity =
        new ProcessInstanceForListViewEntity()
            .setId("111")
            .setProcessName("process")
            .setProcessVersion(2)
            .setProcessDefinitionKey(444L)
            .setBpmnProcessId("bpmnProcessId")
            .setPosition(123L)
            .setState(ProcessInstanceState.ACTIVE);
    final BatchRequest mockRequest = mock(BatchRequest.class);

    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put(ListViewTemplate.PROCESS_NAME, "process");
    expectedUpdateFields.put(ListViewTemplate.PROCESS_VERSION, 2);
    expectedUpdateFields.put(ListViewTemplate.PROCESS_KEY, 444L);
    expectedUpdateFields.put(ListViewTemplate.BPMN_PROCESS_ID, "bpmnProcessId");
    expectedUpdateFields.put(ListViewTemplate.STATE, ProcessInstanceState.ACTIVE);
    expectedUpdateFields.put(POSITION, 123L);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).upsert(indexName, "111", inputEntity, expectedUpdateFields);
  }

  @Test
  void shouldUpdateEntityFromRecord() {
    // given
    final long timestamp = new Date().getTime();
    final ProcessInstanceRecordValue processInstanceRecordValue =
        ImmutableProcessInstanceRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceRecordValue.class))
            .withProcessInstanceKey(111)
            .withProcessDefinitionKey(222)
            .withBpmnElementType(BpmnElementType.PROCESS)
            .withElementInstancePath(List.of(List.of(111L)))
            .withProcessDefinitionPath(List.of(222L))
            .build();
    final Record<ProcessInstanceRecordValue> processInstanceRecord =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r ->
                r.withKey(111)
                    .withIntent(ELEMENT_ACTIVATING)
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
    assertThat(processInstanceForListViewEntity.getProcessVersionTag())
        .isEqualTo("test-version-tag");
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
    assertThat(processInstanceForListViewEntity.getTreePath()).isEqualTo("PI_111");

    // process name and version tag is read from the cache
    assertThat(processInstanceForListViewEntity.getProcessName()).isEqualTo("test-process-name");
    assertThat(processInstanceForListViewEntity.getProcessVersionTag())
        .isEqualTo("test-version-tag");
  }

  @Test
  public void shouldUpdateTreePathFromRecordWithCallActivity() {
    // given
    final long processDefinitionKey1 = 999L;
    final Long pi1Key = 111L;
    final Integer callActivityIndex1 = 3;
    final String callActivityId1 = "callActivity1";
    final long subprocess1Key = 987L;
    final long callActivity1Key = 123L;
    final long processDefinitionKey2 = 888L;
    final Long pi2Key = 222L;
    final Integer callActivityIndex2 = 1;
    final String callActivityId2 = "callActivity2";
    final long subprocess2Key = 876L;
    final long callActivity2Key = 234L;
    final Long pi3Key = 333L;
    final Record<ProcessInstanceRecordValue> processInstanceRecord =
        createProcessInstanceRecord(
            List.of(
                List.of(pi1Key, subprocess1Key, callActivity1Key),
                List.of(pi2Key, subprocess2Key, callActivity2Key),
                List.of(pi3Key)),
            List.of(callActivityIndex1, callActivityIndex2),
            List.of(processDefinitionKey1, processDefinitionKey2, 777L),
            pi3Key);
    processCache.put(
        processDefinitionKey1,
        new CachedProcessEntity(null, null, List.of("0", "1", "2", callActivityId1)));

    processCache.put(
        processDefinitionKey2, new CachedProcessEntity(null, null, List.of("0", callActivityId2)));

    // when called process 3rd level
    final ProcessInstanceForListViewEntity processInstanceForListViewEntity3 =
        new ProcessInstanceForListViewEntity().setId(String.valueOf(pi3Key));
    underTest.updateEntity(processInstanceRecord, processInstanceForListViewEntity3);
    // then
    assertThat(processInstanceForListViewEntity3.getTreePath())
        .isEqualTo("PI_111/FN_callActivity1/FNI_123/PI_222/FN_callActivity2/FNI_234/PI_333");
  }

  @Test
  public void shouldUpdateTreePathFromRecordWithoutCallActivity() {
    // given
    final Long pi1Key = 111L;
    final long subprocess1Key = 987L;
    final Record<ProcessInstanceRecordValue> processInstanceRecord =
        createProcessInstanceRecord(
            List.of(List.of(pi1Key, subprocess1Key, 345L)), List.of(), List.of(777L), pi1Key);

    // when called process 3rd level
    final ProcessInstanceForListViewEntity processInstanceForListViewEntity3 =
        new ProcessInstanceForListViewEntity().setId(String.valueOf(pi1Key));
    underTest.updateEntity(processInstanceRecord, processInstanceForListViewEntity3);
    // then
    assertThat(processInstanceForListViewEntity3.getTreePath()).isEqualTo("PI_111");
  }

  private Record<ProcessInstanceRecordValue> createProcessInstanceRecord(
      final List<List<Long>> elementInstancePath,
      final List<Integer> callingElementPath,
      final List<Long> processDefinitionPath,
      final Long processInstanceKey) {
    final Builder builder =
        ImmutableProcessInstanceRecordValue.builder()
            .withElementInstancePath(elementInstancePath)
            .withProcessInstanceKey(processInstanceKey);
    if (callingElementPath != null) {
      builder.withCallingElementPath(callingElementPath);
    }
    if (processDefinitionPath != null) {
      builder.withProcessDefinitionPath(processDefinitionPath);
    }
    final ProcessInstanceRecordValue processInstanceValue = builder.build();
    final Record<ProcessInstanceRecordValue> processInstanceRecord =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r ->
                r.withIntent(ELEMENT_ACTIVATING)
                    .withKey(processInstanceKey)
                    .withValue(processInstanceValue));
    return processInstanceRecord;
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

  @ParameterizedTest
  @EnumSource(
      value = ProcessInstanceIntent.class,
      names = {"ELEMENT_MIGRATED", "ANCESTOR_MIGRATED"},
      mode = Mode.INCLUDE)
  void shouldUpdateStateAfterMigration(final ProcessInstanceIntent intent) {
    // given
    final long timestamp = new Date().getTime();
    final Record<ProcessInstanceRecordValue> processInstanceRecord =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE, r -> r.withIntent(intent).withTimestamp(timestamp));

    final String treePath =
        new TreePath()
            .startTreePath(processInstanceRecord.getValue().getProcessInstanceKey())
            .toString();

    // when
    final ProcessInstanceForListViewEntity processInstanceForListViewEntity =
        new ProcessInstanceForListViewEntity();
    underTest.updateEntity(processInstanceRecord, processInstanceForListViewEntity);

    // then
    assertThat(processInstanceForListViewEntity.getEndDate()).isNull();
    assertThat(processInstanceForListViewEntity.getStartDate()).isNull();
    assertThat(processInstanceForListViewEntity.getState()).isEqualTo(ProcessInstanceState.ACTIVE);
    assertThat(processInstanceForListViewEntity.getTreePath()).isEqualTo(treePath);
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
}
