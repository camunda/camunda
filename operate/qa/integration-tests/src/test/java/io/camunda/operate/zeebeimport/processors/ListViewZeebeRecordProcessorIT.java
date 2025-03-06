/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.processors;

import static io.camunda.operate.entities.listview.ProcessInstanceState.ACTIVE;
import static io.camunda.operate.entities.listview.ProcessInstanceState.COMPLETED;
import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.VARIABLES_JOIN_RELATION;
import static io.camunda.operate.util.TestUtil.createFlowNodeInstance;
import static io.camunda.operate.util.TestUtil.createProcessInstance;
import static io.camunda.operate.util.TestUtil.createVariableForListView;
import static io.camunda.operate.util.ZeebeRecordTestUtil.createZeebeRecordFromFni;
import static io.camunda.operate.util.ZeebeRecordTestUtil.createZeebeRecordFromPi;
import static io.camunda.zeebe.protocol.record.intent.IncidentIntent.CREATED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.VariableForListViewEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebeimport.ImportBatch;
import io.camunda.operate.zeebeimport.ImportPositionHolder;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableIncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class ListViewZeebeRecordProcessorIT extends OperateSearchAbstractIT {

  private final int newVersion = 111;
  private final String newBpmnProcessId = "newBpmnProcessId";
  private final long newProcessDefinitionKey = 111;
  private final String newProcessName = "New process name";
  private final String errorMessage = "Error message";
  @Autowired private ListViewTemplate listViewTemplate;
  @Autowired private ListViewZeebeRecordProcessor listViewZeebeRecordProcessor;
  @Autowired private BeanFactory beanFactory;
  @MockBean private PartitionHolder partitionHolder;
  @MockBean private ProcessCache processCache;
  @Autowired private ImportPositionHolder importPositionHolder;
  @Autowired private OperateProperties operateProperties;

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    when(partitionHolder.getPartitionIds()).thenReturn(List.of(1));
  }

  @Override
  @AfterAll
  public void afterAllTeardown() {
    super.afterAllTeardown();
  }

  @Test
  public void shouldOverrideProcessInstanceFields() throws IOException, PersistenceException {
    // having
    // process instance entity with position = 1
    final ProcessInstanceForListViewEntity pi = createProcessInstance().setPosition(1L);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(), pi.getId(), pi);

    // when
    // importing Zeebe record with bigger position
    when(processCache.getProcessNameOrDefaultValue(eq(newProcessDefinitionKey), anyString()))
        .thenReturn(newProcessName);
    final long newPosition = 2L;
    final Record<ProcessInstanceRecordValue> zeebeRecord =
        createZeebeRecordFromPi(
            pi,
            b -> b.withPosition(newPosition).withIntent(ELEMENT_COMPLETED),
            b ->
                b.withVersion(newVersion)
                    .withBpmnProcessId(newBpmnProcessId)
                    .withProcessDefinitionKey(newProcessDefinitionKey));
    importProcessInstanceZeebeRecord(zeebeRecord);

    // then
    // process instance fields are updated
    final ProcessInstanceForListViewEntity updatedPI = findProcessInstanceByKey(pi.getKey());
    // old values
    assertThat(updatedPI.getProcessInstanceKey()).isEqualTo(pi.getProcessInstanceKey());
    assertThat(updatedPI.getTenantId()).isEqualTo(pi.getTenantId());
    assertThat(updatedPI.getKey()).isEqualTo(pi.getKey());
    assertThat(updatedPI.getTenantId()).isEqualTo(pi.getTenantId());
    assertThat(updatedPI.getStartDate()).isNotNull();
    // new values
    assertThat(updatedPI.getProcessName()).isEqualTo(newProcessName);
    assertThat(updatedPI.getProcessDefinitionKey()).isEqualTo(newProcessDefinitionKey);
    assertThat(updatedPI.getProcessVersion()).isEqualTo(newVersion);
    assertThat(updatedPI.getState()).isEqualTo(COMPLETED);
    assertThat(updatedPI.getEndDate()).isNotNull();
    assertThat(updatedPI.getPosition()).isEqualTo(newPosition);
  }

  @Test
  public void shouldOverrideProcessInstanceFieldsForNullPosition()
      throws IOException, PersistenceException {
    // having
    // process instance entity with null position
    final ProcessInstanceForListViewEntity pi = createProcessInstance(); // null positions field
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(), pi.getId(), pi);

    // when
    // importing Zeebe record with bigger position
    when(processCache.getProcessNameOrDefaultValue(eq(newProcessDefinitionKey), anyString()))
        .thenReturn(newProcessName);
    final long newPosition = 2L;
    final Record<ProcessInstanceRecordValue> zeebeRecord =
        createZeebeRecordFromPi(
            pi,
            b -> b.withPosition(newPosition).withIntent(ELEMENT_COMPLETED),
            b ->
                b.withVersion(newVersion)
                    .withBpmnProcessId(newBpmnProcessId)
                    .withProcessDefinitionKey(newProcessDefinitionKey));
    importProcessInstanceZeebeRecord(zeebeRecord);

    // then
    // process instance fields are updated
    final ProcessInstanceForListViewEntity updatedPI = findProcessInstanceByKey(pi.getKey());
    // old values
    assertThat(updatedPI.getProcessInstanceKey()).isEqualTo(pi.getProcessInstanceKey());
    assertThat(updatedPI.getTenantId()).isEqualTo(pi.getTenantId());
    assertThat(updatedPI.getKey()).isEqualTo(pi.getKey());
    assertThat(updatedPI.getTenantId()).isEqualTo(pi.getTenantId());
    assertThat(updatedPI.getStartDate()).isNotNull();
    // new values
    assertThat(updatedPI.getProcessName()).isEqualTo(newProcessName);
    assertThat(updatedPI.getProcessDefinitionKey()).isEqualTo(newProcessDefinitionKey);
    assertThat(updatedPI.getProcessVersion()).isEqualTo(newVersion);
    assertThat(updatedPI.getState()).isEqualTo(COMPLETED);
    assertThat(updatedPI.getEndDate()).isNotNull();
    assertThat(updatedPI.getPosition()).isEqualTo(newPosition);
  }

  @Test
  public void shouldNotOverrideProcessInstanceFields() throws IOException, PersistenceException {
    // having
    // process instance entity with position = 2
    final long oldPosition = 2L;
    final ProcessInstanceForListViewEntity pi = createProcessInstance().setPosition(oldPosition);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(), pi.getId(), pi);

    // when
    // importing Zeebe record with smaller position
    when(processCache.getProcessNameOrDefaultValue(eq(newProcessDefinitionKey), anyString()))
        .thenReturn(newProcessName);
    final long newPosition = 1L;
    final Record<ProcessInstanceRecordValue> zeebeRecord =
        createZeebeRecordFromPi(
            pi,
            b -> b.withPosition(newPosition).withIntent(ELEMENT_COMPLETED),
            b ->
                b.withVersion(newVersion)
                    .withBpmnProcessId(newBpmnProcessId)
                    .withProcessDefinitionKey(newProcessDefinitionKey));
    importProcessInstanceZeebeRecord(zeebeRecord);

    // then
    // process instance fields are updated
    final ProcessInstanceForListViewEntity updatedPI = findProcessInstanceByKey(pi.getKey());
    // old values
    assertThat(updatedPI.getProcessInstanceKey()).isEqualTo(pi.getProcessInstanceKey());
    assertThat(updatedPI.getTenantId()).isEqualTo(pi.getTenantId());
    assertThat(updatedPI.getKey()).isEqualTo(pi.getKey());
    assertThat(updatedPI.getTenantId()).isEqualTo(pi.getTenantId());
    assertThat(updatedPI.getStartDate()).isNotNull();
    // old values
    assertThat(updatedPI.getProcessName()).isEqualTo(pi.getProcessName());
    assertThat(updatedPI.getProcessDefinitionKey()).isEqualTo(pi.getProcessDefinitionKey());
    assertThat(updatedPI.getProcessVersion()).isEqualTo(pi.getProcessVersion());
    assertThat(updatedPI.getState()).isEqualTo(ACTIVE);
    assertThat(updatedPI.getEndDate()).isNull();
    assertThat(updatedPI.getPosition()).isEqualTo(oldPosition);
  }

  @Test
  public void shouldHaveVersionTagOfProcessInListView() throws PersistenceException, IOException {
    final String versionTag = "tag-v1";
    final long instanceKey = 333L;
    final long definitionKey = 123L;
    when(processCache.getProcessNameOrDefaultValue(eq(definitionKey), anyString()))
        .thenReturn(newProcessName);
    when(processCache.getProcessVersionTag(eq(definitionKey))).thenReturn(versionTag);
    final ProcessInstanceForListViewEntity pi =
        createProcessInstance().setProcessInstanceKey(instanceKey);
    final Record<ProcessInstanceRecordValue> zeebeRecord =
        createZeebeRecordFromPi(
            pi,
            b -> b.withIntent(ELEMENT_COMPLETED),
            b ->
                b.withVersion(1)
                    .withBpmnProcessId(newBpmnProcessId)
                    .withProcessDefinitionKey(definitionKey));

    importProcessInstanceZeebeRecord(zeebeRecord);
    final ProcessInstanceForListViewEntity actualPI = findProcessInstanceByKey(instanceKey);

    assertThat(actualPI.getProcessInstanceKey()).isEqualTo(instanceKey);
    assertThat(actualPI.getKey()).isEqualTo(pi.getKey());
    assertThat(actualPI.getProcessVersionTag()).isEqualTo(versionTag);
  }

  @Test
  public void shouldOverrideIncidentErrorMsg() throws IOException, PersistenceException {
    // having
    // flow node instance entity with position = 1
    final long processInstanceKey = 111L;
    final FlowNodeInstanceForListViewEntity fni =
        createFlowNodeInstance(processInstanceKey, FlowNodeState.ACTIVE).setPositionIncident(1L);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        fni.getId(),
        fni,
        String.valueOf(processInstanceKey));

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final Record<IncidentRecordValue> zeebeRecord =
        (Record)
            ImmutableRecord.builder()
                .withKey(112L)
                .withPosition(newPosition)
                .withIntent(CREATED)
                .withValue(
                    ImmutableIncidentRecordValue.builder()
                        .withElementInstanceKey(fni.getKey())
                        .withErrorMessage(errorMessage)
                        .build())
                .build();
    importIncidentZeebeRecord(zeebeRecord);

    // then
    // incident fields are updated
    final FlowNodeInstanceForListViewEntity updatedFni = findFlowNodeInstanceByKey(fni.getKey());
    // old values
    assertThat(updatedFni.getKey()).isEqualTo(fni.getKey());
    // new values
    assertThat(updatedFni.getErrorMessage()).isEqualTo(errorMessage);
    assertThat(updatedFni.getPositionIncident()).isEqualTo(newPosition);
  }

  @Test
  public void shouldOverrideIncidentErrorMsgForNullPosition()
      throws IOException, PersistenceException {
    // having
    // flow node instance entity with null position
    final long processInstanceKey = 111L;
    final FlowNodeInstanceForListViewEntity fni =
        createFlowNodeInstance(processInstanceKey, FlowNodeState.ACTIVE); // null positions field
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        fni.getId(),
        fni,
        String.valueOf(processInstanceKey));

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final Record<IncidentRecordValue> zeebeRecord =
        (Record)
            ImmutableRecord.builder()
                .withKey(112L)
                .withPosition(newPosition)
                .withIntent(CREATED)
                .withValue(
                    ImmutableIncidentRecordValue.builder()
                        .withElementInstanceKey(fni.getKey())
                        .withErrorMessage(errorMessage)
                        .build())
                .build();
    importIncidentZeebeRecord(zeebeRecord);

    // then
    // incident fields are updated
    final FlowNodeInstanceForListViewEntity updatedFni = findFlowNodeInstanceByKey(fni.getKey());
    // old values
    assertThat(updatedFni.getKey()).isEqualTo(fni.getKey());
    // new values
    assertThat(updatedFni.getErrorMessage()).isEqualTo(errorMessage);
    assertThat(updatedFni.getPositionIncident()).isEqualTo(newPosition);
  }

  @Test
  public void shouldNotOverrideIncidentErrorMsg() throws IOException, PersistenceException {
    // having
    // flow node instance entity with position = 2
    final long processInstanceKey = 111L;
    final FlowNodeInstanceForListViewEntity fni =
        createFlowNodeInstance(processInstanceKey, FlowNodeState.ACTIVE).setPositionIncident(2L);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        fni.getId(),
        fni,
        String.valueOf(processInstanceKey));

    // when
    // importing Zeebe record with smaller position
    final long newPosition = 1L;
    final Record<IncidentRecordValue> zeebeRecord =
        (Record)
            ImmutableRecord.builder()
                .withKey(112L)
                .withPosition(newPosition)
                .withIntent(CREATED)
                .withValue(
                    ImmutableIncidentRecordValue.builder()
                        .withElementInstanceKey(fni.getKey())
                        .withErrorMessage(errorMessage)
                        .build())
                .build();
    importIncidentZeebeRecord(zeebeRecord);

    // then
    // incident fields are not updated
    final FlowNodeInstanceForListViewEntity updatedFni = findFlowNodeInstanceByKey(fni.getKey());
    // old values
    assertThat(updatedFni.getKey()).isEqualTo(fni.getKey());
    assertThat(updatedFni.getErrorMessage()).isNull();
    assertThat(updatedFni.getPositionIncident()).isEqualTo(fni.getPositionIncident());
  }

  @Test
  public void shouldOverrideVariableFields() throws IOException, PersistenceException {
    // having
    // variable entity with position = 1
    final long processInstanceKey = 111L;
    final VariableForListViewEntity var =
        createVariableForListView(processInstanceKey).setPosition(1L);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        var.getId(),
        var,
        String.valueOf(processInstanceKey));

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final String newValue = "newValue";
    final Record<VariableRecordValue> zeebeRecord =
        (Record)
            ImmutableRecord.builder()
                .withKey(113L)
                .withPosition(newPosition)
                .withIntent(VariableIntent.UPDATED)
                .withValue(
                    ImmutableVariableRecordValue.builder()
                        .withName(var.getVarName())
                        .withValue(newValue)
                        .withScopeKey(var.getScopeKey())
                        .withProcessInstanceKey(processInstanceKey)
                        .build())
                .build();
    importVariableZeebeRecord(zeebeRecord);

    // then
    // variable fields are updated
    final VariableForListViewEntity updatedVar = variableById(var.getId());
    // old values
    assertThat(updatedVar.getId()).isEqualTo(var.getId());
    assertThat(updatedVar.getVarName()).isEqualTo(var.getVarName());
    // new values
    assertThat(updatedVar.getVarValue()).isEqualTo(newValue);
  }

  @Test
  public void shouldOverrideVariableFieldsForNullPosition()
      throws IOException, PersistenceException {
    // having
    // variable entity with null position
    final long processInstanceKey = 111L;
    final VariableForListViewEntity var = createVariableForListView(processInstanceKey);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        var.getId(),
        var,
        String.valueOf(processInstanceKey));

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final String newValue = "newValue";
    final Record<VariableRecordValue> zeebeRecord =
        (Record)
            ImmutableRecord.builder()
                .withKey(113L)
                .withPosition(newPosition)
                .withIntent(VariableIntent.UPDATED)
                .withValue(
                    ImmutableVariableRecordValue.builder()
                        .withName(var.getVarName())
                        .withValue(newValue)
                        .withScopeKey(var.getScopeKey())
                        .withProcessInstanceKey(processInstanceKey)
                        .build())
                .build();
    importVariableZeebeRecord(zeebeRecord);

    // then
    // variable fields are updated
    final VariableForListViewEntity updatedVar = variableById(var.getId());
    // old values
    assertThat(updatedVar.getId()).isEqualTo(var.getId());
    assertThat(updatedVar.getVarName()).isEqualTo(var.getVarName());
    // new values
    assertThat(updatedVar.getVarValue()).isEqualTo(newValue);
  }

  @Test
  public void shouldNotOverrideVariableFields() throws IOException, PersistenceException {
    // having
    // process instance entity with position = 2
    final long processInstanceKey = 111L;
    final VariableForListViewEntity var =
        createVariableForListView(processInstanceKey).setPosition(2L);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        var.getId(),
        var,
        String.valueOf(processInstanceKey));

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 1L;
    final String newValue = "newValue";
    final Record<VariableRecordValue> zeebeRecord =
        (Record)
            ImmutableRecord.builder()
                .withKey(113L)
                .withPosition(newPosition)
                .withIntent(VariableIntent.UPDATED)
                .withValue(
                    ImmutableVariableRecordValue.builder()
                        .withName(var.getVarName())
                        .withValue(newValue)
                        .withScopeKey(var.getScopeKey())
                        .withProcessInstanceKey(processInstanceKey)
                        .build())
                .build();
    importVariableZeebeRecord(zeebeRecord);

    // then
    // variable fields are not updated
    final VariableForListViewEntity updatedVar = variableById(var.getId());
    // old values
    assertThat(updatedVar.getId()).isEqualTo(var.getId());
    assertThat(updatedVar.getVarName()).isEqualTo(var.getVarName());
    assertThat(updatedVar.getVarValue()).isEqualTo(var.getVarValue());
  }

  @Test
  public void shouldOverrideFlowNodeInstanceFields() throws IOException, PersistenceException {
    // having
    // flow node instance entity with position = 1
    final long processInstanceKey = 222L;
    final FlowNodeInstanceForListViewEntity fni =
        createFlowNodeInstance(processInstanceKey, FlowNodeState.ACTIVE).setPosition(1L);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        fni.getId(),
        fni,
        String.valueOf(processInstanceKey));

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final Record<ProcessInstanceRecordValue> zeebeRecord =
        createZeebeRecordFromFni(
            fni, b -> b.withPosition(newPosition).withIntent(ELEMENT_COMPLETED), null);
    importProcessInstanceZeebeRecord(zeebeRecord);

    // then
    // incident fields are updated
    final FlowNodeInstanceForListViewEntity updatedFni = findFlowNodeInstanceByKey(fni.getKey());
    // old values
    assertThat(updatedFni.getKey()).isEqualTo(fni.getKey());
    // new values
    assertThat(updatedFni.getActivityState()).isEqualTo(FlowNodeState.COMPLETED);
    assertThat(updatedFni.getPosition()).isEqualTo(newPosition);
  }

  @Test
  public void shouldOverrideFlowNodeInstanceFieldsForNullPosition()
      throws IOException, PersistenceException {
    // having
    // flow node instance entity with null position
    final long processInstanceKey = 222L;
    final FlowNodeInstanceForListViewEntity fni =
        createFlowNodeInstance(processInstanceKey, FlowNodeState.ACTIVE);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        fni.getId(),
        fni,
        String.valueOf(processInstanceKey));

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final Record<ProcessInstanceRecordValue> zeebeRecord =
        createZeebeRecordFromFni(
            fni, b -> b.withPosition(newPosition).withIntent(ELEMENT_COMPLETED), null);
    importProcessInstanceZeebeRecord(zeebeRecord);

    // then
    // incident fields are updated
    final FlowNodeInstanceForListViewEntity updatedFni = findFlowNodeInstanceByKey(fni.getKey());
    // old values
    assertThat(updatedFni.getKey()).isEqualTo(fni.getKey());
    // new values
    assertThat(updatedFni.getActivityState()).isEqualTo(FlowNodeState.COMPLETED);
    assertThat(updatedFni.getPosition()).isEqualTo(newPosition);
  }

  @Test
  public void shouldNotOverrideFlowNodeInstanceFields() throws IOException, PersistenceException {
    // having
    // flow node instance entity with position = 2
    final long processInstanceKey = 222L;
    final FlowNodeInstanceForListViewEntity fni =
        createFlowNodeInstance(processInstanceKey, FlowNodeState.ACTIVE).setPosition(2L);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        fni.getId(),
        fni,
        String.valueOf(processInstanceKey));

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 1L;
    final Record<ProcessInstanceRecordValue> zeebeRecord =
        createZeebeRecordFromFni(
            fni, b -> b.withPosition(newPosition).withIntent(ELEMENT_COMPLETED), null);
    importProcessInstanceZeebeRecord(zeebeRecord);

    // then
    // incident fields are updated
    final FlowNodeInstanceForListViewEntity updatedFni = findFlowNodeInstanceByKey(fni.getKey());
    // old values
    assertThat(updatedFni.getKey()).isEqualTo(fni.getKey());
    assertThat(updatedFni.getActivityState()).isEqualTo(FlowNodeState.ACTIVE);
    assertThat(updatedFni.getPosition()).isEqualTo(2L);
  }

  @Test
  public void shouldOverrideJobFailedWithRetriesField() throws IOException, PersistenceException {
    // having
    // flow node instance entity with position = 1
    final long processInstanceKey = 333L;
    final FlowNodeInstanceForListViewEntity fni =
        createFlowNodeInstance(processInstanceKey, FlowNodeState.ACTIVE).setPositionJob(1L);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        fni.getId(),
        fni,
        String.valueOf(processInstanceKey));

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final Record<JobRecordValue> zeebeRecord =
        (Record)
            ImmutableRecord.builder()
                .withKey(114L)
                .withPosition(newPosition)
                .withIntent(JobIntent.FAILED)
                .withValue(
                    ImmutableJobRecordValue.builder()
                        .withElementInstanceKey(fni.getKey())
                        .withProcessInstanceKey(processInstanceKey)
                        .withRetries(1)
                        .build())
                .build();
    importJobZeebeRecord(zeebeRecord);

    // then
    // incident fields are updated
    final FlowNodeInstanceForListViewEntity updatedFni = findFlowNodeInstanceByKey(fni.getKey());
    // old values
    assertThat(updatedFni.getKey()).isEqualTo(fni.getKey());
    // new values
    assertThat(updatedFni.isJobFailedWithRetriesLeft()).isEqualTo(true);
    assertThat(updatedFni.getPositionJob()).isEqualTo(newPosition);
  }

  @Test
  public void shouldOverrideJobFailedWithRetriesForNullPosition()
      throws IOException, PersistenceException {
    // having
    // flow node instance entity with null position
    final long processInstanceKey = 333L;
    final FlowNodeInstanceForListViewEntity fni =
        createFlowNodeInstance(processInstanceKey, FlowNodeState.ACTIVE);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        fni.getId(),
        fni,
        String.valueOf(processInstanceKey));

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final Record<JobRecordValue> zeebeRecord =
        (Record)
            ImmutableRecord.builder()
                .withKey(115L)
                .withPosition(newPosition)
                .withIntent(JobIntent.FAILED)
                .withValue(
                    ImmutableJobRecordValue.builder()
                        .withElementInstanceKey(fni.getKey())
                        .withProcessInstanceKey(processInstanceKey)
                        .withRetries(1)
                        .build())
                .build();
    importJobZeebeRecord(zeebeRecord);

    // then
    // incident fields are updated
    final FlowNodeInstanceForListViewEntity updatedFni = findFlowNodeInstanceByKey(fni.getKey());
    // old values
    assertThat(updatedFni.getKey()).isEqualTo(fni.getKey());
    // new values
    assertThat(updatedFni.isJobFailedWithRetriesLeft()).isEqualTo(true);
    assertThat(updatedFni.getPositionJob()).isEqualTo(newPosition);
  }

  @Test
  public void shouldNotOverrideJobFailedWithRetriesField()
      throws IOException, PersistenceException {
    // having
    // flow node instance entity with position = 2
    final long processInstanceKey = 333L;
    final FlowNodeInstanceForListViewEntity fni =
        createFlowNodeInstance(processInstanceKey, FlowNodeState.ACTIVE).setPositionJob(2L);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        fni.getId(),
        fni,
        String.valueOf(processInstanceKey));

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 1L;
    final Record<JobRecordValue> zeebeRecord =
        (Record)
            ImmutableRecord.builder()
                .withKey(115L)
                .withPosition(newPosition)
                .withIntent(JobIntent.FAILED)
                .withValue(
                    ImmutableJobRecordValue.builder()
                        .withElementInstanceKey(fni.getKey())
                        .withProcessInstanceKey(processInstanceKey)
                        .withRetries(1)
                        .build())
                .build();
    importJobZeebeRecord(zeebeRecord);

    // then
    // incident fields are updated
    final FlowNodeInstanceForListViewEntity updatedFni = findFlowNodeInstanceByKey(fni.getKey());
    // old values
    assertThat(updatedFni.getKey()).isEqualTo(fni.getKey());
    assertThat(updatedFni.isJobFailedWithRetriesLeft()).isEqualTo(false);
    assertThat(updatedFni.getPositionJob()).isEqualTo(2L);
  }

  @Test
  public void shouldNotClearVariableValueDuringMigration()
      throws PersistenceException, IOException {
    // having
    final long processInstanceKey = 333L;
    final VariableForListViewEntity variableEntity = createVariableForListView(processInstanceKey);

    final String variableValue = "varValue";
    variableEntity.setVarValue(variableValue);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        variableEntity.getId(),
        variableEntity,
        String.valueOf(processInstanceKey));

    final Record<VariableRecordValue> zeebeRecord =
        (Record)
            ImmutableRecord.builder()
                .withKey(variableEntity.getKey())
                .withPosition(1L)
                .withIntent(VariableIntent.MIGRATED)
                .withValue(
                    ImmutableVariableRecordValue.builder()
                        .withBpmnProcessId("bpmnId")
                        .withName(variableEntity.getVarName())
                        .withProcessDefinitionKey(123L)
                        .withProcessInstanceKey(processInstanceKey)
                        .withScopeKey(processInstanceKey)
                        .withValue(null) // migrated Zeebe variable records have a null value
                        .build())
                .build();

    // when
    importVariableZeebeRecord(zeebeRecord);

    // then
    // the variable value has not been set to null but is still the old value
    final VariableForListViewEntity persistedVariable = variableById(variableEntity.getId());
    // old values
    assertThat(persistedVariable.getVarValue()).isEqualTo(variableValue);
  }

  @NotNull
  private ProcessInstanceForListViewEntity findProcessInstanceByKey(final long key)
      throws IOException {
    final List<ProcessInstanceForListViewEntity> entities =
        testSearchRepository.searchJoinRelation(
            listViewTemplate.getFullQualifiedName(),
            PROCESS_INSTANCE_JOIN_RELATION,
            ProcessInstanceForListViewEntity.class,
            10);
    final Optional<ProcessInstanceForListViewEntity> first =
        entities.stream().filter(p -> p.getKey() == key).findFirst();
    assertThat(first.isPresent()).isTrue();
    return first.get();
  }

  @NotNull
  private FlowNodeInstanceForListViewEntity findFlowNodeInstanceByKey(final long key)
      throws IOException {
    final List<FlowNodeInstanceForListViewEntity> entities =
        testSearchRepository.searchJoinRelation(
            listViewTemplate.getFullQualifiedName(),
            ACTIVITIES_JOIN_RELATION,
            FlowNodeInstanceForListViewEntity.class,
            10);
    final Optional<FlowNodeInstanceForListViewEntity> first =
        entities.stream().filter(p -> p.getKey() == key).findFirst();
    assertThat(first.isPresent()).isTrue();
    return first.get();
  }

  @NotNull
  private VariableForListViewEntity variableById(final String id) throws IOException {
    final List<VariableForListViewEntity> entities =
        testSearchRepository.searchJoinRelation(
            listViewTemplate.getFullQualifiedName(),
            VARIABLES_JOIN_RELATION,
            VariableForListViewEntity.class,
            10);
    final Optional<VariableForListViewEntity> first =
        entities.stream().filter(p -> p.getId().equals(id)).findFirst();
    assertThat(first.isPresent()).isTrue();
    return first.get();
  }

  private void importProcessInstanceZeebeRecord(
      final Record<ProcessInstanceRecordValue> zeebeRecord) throws PersistenceException {
    final BatchRequest batchRequest = beanFactory.getBean(BatchRequest.class);
    listViewZeebeRecordProcessor.processProcessInstanceRecord(
        (Map) Map.of(zeebeRecord.getKey(), List.of(zeebeRecord)),
        batchRequest,
        mock(ImportBatch.class));
    batchRequest.execute();
    searchContainerManager.refreshIndices(listViewTemplate.getFullQualifiedName());
  }

  private void importIncidentZeebeRecord(final Record<IncidentRecordValue> zeebeRecord)
      throws PersistenceException {
    final BatchRequest batchRequest = beanFactory.getBean(BatchRequest.class);
    listViewZeebeRecordProcessor.processIncidentRecord(zeebeRecord, batchRequest);
    batchRequest.execute();
    searchContainerManager.refreshIndices(listViewTemplate.getFullQualifiedName());
  }

  private void importVariableZeebeRecord(final Record<VariableRecordValue> zeebeRecord)
      throws PersistenceException {
    final BatchRequest batchRequest = beanFactory.getBean(BatchRequest.class);
    listViewZeebeRecordProcessor.processVariableRecords(
        (Map) Map.of(zeebeRecord.getKey(), List.of(zeebeRecord)), batchRequest);
    batchRequest.execute();
    searchContainerManager.refreshIndices(listViewTemplate.getFullQualifiedName());
  }

  private void importJobZeebeRecord(final Record<JobRecordValue> zeebeRecord)
      throws PersistenceException {
    final BatchRequest batchRequest = beanFactory.getBean(BatchRequest.class);
    listViewZeebeRecordProcessor.processJobRecords(
        (Map) Map.of(zeebeRecord.getKey(), List.of(zeebeRecord)), batchRequest);
    batchRequest.execute();
    searchContainerManager.refreshIndices(listViewTemplate.getFullQualifiedName());
  }
}
