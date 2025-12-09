/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.processors;

import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.VARIABLES_JOIN_RELATION;
import static io.camunda.operate.util.TestUtil.createProcessInstance;
import static io.camunda.operate.util.TestUtil.createVariableForListView;
import static io.camunda.operate.util.ZeebeRecordTestUtil.createZeebeRecordFromPi;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.VariableForListViewEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebeimport.ImportBatch;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableVariableRecordValue;
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
  private static final int EMPTY_PARENT_PROCESS_INSTANCE_ID = -1;
  private final String newBpmnProcessId = "newBpmnProcessId";
  private final String newProcessName = "New process name";
  @Autowired private ListViewTemplate listViewTemplate;
  @Autowired private ListViewZeebeRecordProcessor listViewZeebeRecordProcessor;
  @Autowired private BeanFactory beanFactory;
  @MockBean private PartitionHolder partitionHolder;
  @MockBean private ProcessCache processCache;

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
                    .withProcessDefinitionKey(definitionKey)
                    .withParentProcessInstanceKey(EMPTY_PARENT_PROCESS_INSTANCE_ID));

    importProcessInstanceZeebeRecord(zeebeRecord);
    final ProcessInstanceForListViewEntity actualPI = findProcessInstanceByKey(instanceKey);

    assertThat(actualPI.getProcessInstanceKey()).isEqualTo(instanceKey);
    assertThat(actualPI.getKey()).isEqualTo(pi.getKey());
    assertThat(actualPI.getProcessVersionTag()).isEqualTo(versionTag);
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

  @Test
  public void shouldGenerateTreePathBasedOnParentProcessInListView()
      throws PersistenceException, IOException {

    final long parentProcessInstanceKey = 111L;
    testSearchRepository.createOrUpdateDocument(
        listViewTemplate.getFullQualifiedName(),
        String.valueOf(parentProcessInstanceKey),
        Map.of(
            ListViewTemplate.KEY,
            parentProcessInstanceKey,
            ListViewTemplate.TREE_PATH,
            "/parent/tree/path"));

    final long parentElementInstanceKey = 222L;
    testSearchRepository.createOrUpdateDocument(
        listViewTemplate.getFullQualifiedName(),
        String.valueOf(parentElementInstanceKey),
        Map.of(
            ListViewTemplate.ID,
            parentElementInstanceKey,
            ListViewTemplate.JOIN_RELATION,
            Map.of(
                "parent",
                parentProcessInstanceKey,
                "name",
                ListViewTemplate.ACTIVITIES_JOIN_RELATION),
            ListViewTemplate.ACTIVITY_ID,
            "parentActivity"),
        String.valueOf(parentProcessInstanceKey));

    final long processInstanceKey = 333L;
    importProcessInstanceAndVerifyTreePath(
        processInstanceKey,
        parentProcessInstanceKey,
        parentElementInstanceKey,
        "/parent/tree/path/FN_parentActivity/FNI_222/PI_333");
  }

  @Test
  public void shouldGenerateFallbackTreePathIfParentProcessNotFound()
      throws PersistenceException, IOException {

    final long processInstanceKey = 334L;
    importProcessInstanceAndVerifyTreePath(processInstanceKey, 0xDEADL, 0xDEADL, "PI_334");
  }

  private void importProcessInstanceAndVerifyTreePath(
      final long processInstanceKey,
      final long parentProcessInstanceKey,
      final long parentElementInstanceKey,
      final String expectedTreePath)
      throws PersistenceException, IOException {
    final String versionTag = "tag-v1";
    final long definitionKey = 123L;
    when(processCache.getProcessNameOrDefaultValue(eq(definitionKey), anyString()))
        .thenReturn(newProcessName);
    when(processCache.getProcessVersionTag(eq(definitionKey))).thenReturn(versionTag);
    final ProcessInstanceForListViewEntity pi =
        createProcessInstance().setProcessInstanceKey(processInstanceKey);

    final Record<ProcessInstanceRecordValue> zeebeRecord =
        createZeebeRecordFromPi(
            pi,
            b -> b.withIntent(ELEMENT_COMPLETED),
            b ->
                b.withVersion(1)
                    .withBpmnProcessId(newBpmnProcessId)
                    .withProcessDefinitionKey(definitionKey)
                    .withParentProcessInstanceKey(parentProcessInstanceKey)
                    .withParentElementInstanceKey(parentElementInstanceKey));

    importProcessInstanceZeebeRecord(zeebeRecord);
    final ProcessInstanceForListViewEntity actualPI = findProcessInstanceByKey(processInstanceKey);

    assertThat(actualPI.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(actualPI.getKey()).isEqualTo(pi.getKey());
    assertThat(actualPI.getTreePath()).isEqualTo(expectedTreePath);
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

  private void importVariableZeebeRecord(final Record<VariableRecordValue> zeebeRecord)
      throws PersistenceException {
    final BatchRequest batchRequest = beanFactory.getBean(BatchRequest.class);
    listViewZeebeRecordProcessor.processVariableRecords(
        (Map) Map.of(zeebeRecord.getKey(), List.of(zeebeRecord)), batchRequest);
    batchRequest.execute();
    searchContainerManager.refreshIndices(listViewTemplate.getFullQualifiedName());
  }
}
