/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.processors;

import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.operate.util.TestUtil.createProcessInstance;
import static io.camunda.operate.util.ZeebeRecordTestUtil.createZeebeRecordFromPi;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebeimport.ImportBatch;
import io.camunda.operate.zeebeimport.ImportPositionHolder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
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
}
