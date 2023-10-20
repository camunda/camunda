/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.it.store;

import io.camunda.operate.elasticsearch.TestSearchRepository;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.ProcessStore;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static io.camunda.operate.util.ElasticsearchUtil.QueryType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

public class ProcessStoreIT extends OperateZeebeAbstractIT {

  private static final String DEMO_PROCESS_RESOURCE = "demoProcess_v_3.bpmn";
  private static final String DEMO_PROCESS_BPMN_ID = "demoProcess";
  private static final String PARENT_PROCESS_RESOURCE = "callActivityProcess.bpmn";
  private static final String PARENT_PROCESS_BPMN_ID = "CallActivityProcess";
  private static final String CHILD_PROCESS_RESOURCE = "calledProcess.bpmn";
  private static final String CHILD_PROCESS_BPMN_ID = "CalledProcess";

  @Autowired
  private ProcessStore processStore;

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private ProcessIndex processIndex;

  @Autowired
  private TestSearchRepository searchRepository;

  @Before
  public void before() {
    super.before();
  }

  @Test
  public void shouldReturnNoProcessInstancesByProcessAndStatesWhenNoDefinition() {
    // given

    // when
    List<ProcessInstanceForListViewEntity> processInstances = processStore.getProcessInstancesByProcessAndStates(123L, Set.of(ProcessInstanceState.COMPLETED), 10, null);

    // then
    assertThat(processInstances.size()).isZero();
  }

  @Test
  public void shouldReturnNoProcessInstancesByProcessAndStatesWhenDifferentState() {
    // given
    long processDefinitionKey = deployProcess(DEMO_PROCESS_RESOURCE);
    String payload = "{\"manualTasks\":true}";
    for (int i = 0; i < 2; i++) {
      tester.startProcessInstance(DEMO_PROCESS_BPMN_ID, payload).waitUntil().processInstanceIsCompleted();
    }

    // when
    List<ProcessInstanceForListViewEntity> processInstances = processStore.getProcessInstancesByProcessAndStates(processDefinitionKey, Set.of(ProcessInstanceState.ACTIVE), 10, null);

    // then
    assertThat(processInstances.size()).isZero();
  }

  @Test
  public void shouldReturnProcessInstancesByProcessAndStatesWithSizeLimit() {
    // given
    long processDefinitionKey = deployProcess(DEMO_PROCESS_RESOURCE);
    String payload = "{\"manualTasks\":true}";
    for (int i = 0; i < 2; i++) {
      tester.startProcessInstance(DEMO_PROCESS_BPMN_ID, payload).waitUntil().processInstanceIsCompleted();
    }

    // when
    List<ProcessInstanceForListViewEntity> processInstances = processStore.getProcessInstancesByProcessAndStates(processDefinitionKey, Set.of(ProcessInstanceState.COMPLETED), 1, null);

    // then
    assertThat(processInstances.size()).isEqualTo(1);
  }

  @Test
  public void shouldReturnProcessInstancesByProcessAndStates() {
    // given
    long processDefinitionKey = deployProcess(DEMO_PROCESS_RESOURCE);
    String payload = "{\"manualTasks\":true}";
    for (int i = 0; i < 2; i++) {
      tester.startProcessInstance(DEMO_PROCESS_BPMN_ID, payload).waitUntil().processInstanceIsCompleted();
    }

    // when
    List<ProcessInstanceForListViewEntity> processInstances = processStore.getProcessInstancesByProcessAndStates(processDefinitionKey, Set.of(ProcessInstanceState.COMPLETED), 10, null);

    // then
    assertThat(processInstances.size()).isEqualTo(2);
  }

  @Test
  public void shouldReturnProcessInstancesByParentKeys() {
    // given
    long processDefinitionKeyParent = deployProcess(PARENT_PROCESS_RESOURCE);
    long processDefinitionKeyChild = deployProcess(CHILD_PROCESS_RESOURCE);
    Set<Long> parentKeys = new HashSet<>();
    for (int i = 0; i < 2; i++) {
      parentKeys.add(tester.startProcessInstance(PARENT_PROCESS_BPMN_ID).waitUntil().processInstanceIsCompleted().getProcessInstanceKey());
    }

    // when
    List<ProcessInstanceForListViewEntity> processInstances = processStore.getProcessInstancesByParentKeys(parentKeys, 10, null);

    // then
    assertThat(processInstances.size()).isEqualTo(2);
    assertThat(processInstances).extracting(ListViewTemplate.BPMN_PROCESS_ID).containsExactly(CHILD_PROCESS_BPMN_ID, CHILD_PROCESS_BPMN_ID);
  }

  @Test
  public void shouldDeleteProcessDefinitionsByKeys() throws IOException {
    // given
    long processDefinitionKeyParent = deployProcess(PARENT_PROCESS_RESOURCE);
    long processDefinitionKeyChild = deployProcess(CHILD_PROCESS_RESOURCE);

    // when
    long deleted = processStore.deleteProcessDefinitionsByKeys(processDefinitionKeyParent);
    processStore.refreshIndices(processIndex.getAlias());
    record Result(Long key){}
    List<Result> documents = searchRepository.searchAll(processIndex.getAlias(), Result.class);

    // then
    assertThat(deleted).isEqualTo(1);
    assertThat(documents.size()).isEqualTo(1);
    assertThat(documents.get(0).key()).isEqualTo(processDefinitionKeyChild);
  }

  @Test
  public void shouldDeleteAllProcessDefinitionsByKeys() throws IOException {
    // given
    long processDefinitionKeyParent = deployProcess(PARENT_PROCESS_RESOURCE);
    long processDefinitionKeyChild = deployProcess(CHILD_PROCESS_RESOURCE);

    // when
    long deleted = processStore.deleteProcessDefinitionsByKeys(processDefinitionKeyParent, processDefinitionKeyChild);
    processStore.refreshIndices(processIndex.getAlias());
    List<Void> documents = searchRepository.searchAll(processIndex.getAlias(), Void.class);

    // then
    assertThat(deleted).isEqualTo(2);
    assertThat(documents.size()).isZero();
  }

  @Test
  public void shouldDeleteProcessInstancesAndDependants() throws IOException {
    // given
    long processDefinitionKey = deployProcess(DEMO_PROCESS_RESOURCE);
    String payload = "{\"manualTasks\":true}";
    List<Long> processInstances = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      processInstances.add(tester.startProcessInstance(DEMO_PROCESS_BPMN_ID, payload).waitUntil().processInstanceIsCompleted().getProcessInstanceKey());
    }

    // when
    long deleted = processStore.deleteProcessInstancesAndDependants(Set.of(processInstances.get(0)));
    processStore.refreshIndices(listViewTemplate.getAlias());
    record Result(Long processInstanceKey){}
    List<Result> documents = searchRepository.searchAll(listViewTemplate.getAlias(), Result.class);
    Set<Long> processInstanceKeysLeft = documents.stream().map(Result::processInstanceKey).collect(Collectors.toSet());

    // then
    assertThat(deleted).isEqualTo(22);
    assertThat(processInstanceKeysLeft.size()).isEqualTo(1);
    assertTrue(processInstanceKeysLeft.contains(processInstances.get(1)));
  }

  @Test
  public void shouldDeleteAllProcessInstancesAndDependants() throws IOException {
    // given
    long processDefinitionKey = deployProcess(DEMO_PROCESS_RESOURCE);
    String payload = "{\"manualTasks\":true}";
    List<Long> processInstances = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      processInstances.add(tester.startProcessInstance(DEMO_PROCESS_BPMN_ID, payload).waitUntil().processInstanceIsCompleted().getProcessInstanceKey());
    }

    // when
    long deleted = processStore.deleteProcessInstancesAndDependants(new HashSet<>(processInstances));
    processStore.refreshIndices(listViewTemplate.getAlias());
    List<Void> documents = searchRepository.searchAll(listViewTemplate.getAlias(), Void.class);

    // then
    assertThat(deleted).isEqualTo(44);
    assertThat(documents.size()).isZero();
  }

  protected Long deployProcess(String resourceName) {
    Long processDefinitionKey = tester.deployProcess(resourceName).waitUntil().processIsDeployed().getProcessDefinitionKey();
    return processDefinitionKey;
  }
}
