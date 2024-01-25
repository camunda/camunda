/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import io.camunda.operate.util.j5templates.OperateZeebeSearchAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.ChangeStatus;
import io.camunda.operate.webapp.api.v1.entities.ProcessInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static io.camunda.operate.webapp.api.v1.entities.ProcessInstance.BPMN_PROCESS_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

// The delete operation interacts with multiple systems and data objects so for simplicity, just utilize
// zeebe to setup all the data
public class ProcessInstanceDaoIT extends OperateZeebeSearchAbstractIT {
  @Autowired
  private ProcessInstanceDao dao;
  private Long callActivityProcessInstanceKey;
  private Long singleTaskProcessInstanceKey;

  @Override
  public void runAdditionalBeforeAllSetup() {
    operateTester.deployProcessAndWait("callActivityProcess.bpmn");
    operateTester.deployProcessAndWait("calledProcess.bpmn");
    operateTester.deployProcessAndWait("single-task.bpmn");

    // This starts two process instances: CallActivityProcess and CalledProcess since they are linked
    // by parent-child relationship in the process definitions deployed
    callActivityProcessInstanceKey = operateTester.startProcessAndWait("CallActivityProcess");
    singleTaskProcessInstanceKey = operateTester.startProcessAndWait("process");

  }

  @Test
  public void shouldReturnProcessInstancesOnSearch() {
    Results<ProcessInstance> processInstanceResults = dao.search(new Query<>());

    assertThat(processInstanceResults.getTotal()).isEqualTo(3);
    assertThat(processInstanceResults.getItems()).extracting(BPMN_PROCESS_ID)
        .containsExactlyInAnyOrder("CalledProcess", "CallActivityProcess", "process");
  }

  @Test
  public void searchShouldReturnParentKeyWhenExists() {
    // Find the child process
    Results<ProcessInstance> processInstanceResults = dao.search(new Query<ProcessInstance>()
        .setFilter(new ProcessInstance().setBpmnProcessId("CalledProcess")));
    assertThat(processInstanceResults.getItems().size()).isEqualTo(1);
    ProcessInstance instance = processInstanceResults.getItems().get(0);

    assertThat(instance.getParentKey()).isEqualTo(callActivityProcessInstanceKey);
    assertThat(instance.getParentFlowNodeInstanceKey()).isNotNull();
  }

  @Test
  public void shouldDeleteByKey() {
    // Complete the task so the process instance can be deleted
    operateTester.completeTaskAndWaitForProcessFinish(singleTaskProcessInstanceKey, "task",
        "task", null);

    // Delete the process instance
    ChangeStatus changeStatus = dao.delete(singleTaskProcessInstanceKey);
    assertThat(changeStatus.getDeleted()).isEqualTo(1);

    // Ensure the indices are updated and then check that the deleted instance no longer appears in search queries
    operateTester.refreshSearchIndices();
    Results<ProcessInstance> results = dao.search(new Query<ProcessInstance>().setFilter(
        new ProcessInstance().setBpmnProcessId("process")));
    assertThat(results.getTotal()).isEqualTo(0);

    // Restart the instance for future tests
    singleTaskProcessInstanceKey = operateTester.startProcessAndWait("process");
  }

  @Test
  public void shouldThrowForDeleteWhenKeyNotExists() {
    assertThrows(ResourceNotFoundException.class, () -> dao.delete(1L));
  }

  @Test
  public void shouldReturnProcessInstanceByKey() {
    ProcessInstance result = dao.byKey(singleTaskProcessInstanceKey);
    assertThat(result.getKey()).isEqualTo(singleTaskProcessInstanceKey);
    assertThat(result.getBpmnProcessId()).isEqualTo("process");
  }

  @Test
  public void shouldThrowWhenKeyNotExists() {
    assertThrows(ResourceNotFoundException.class, () -> dao.byKey(1L));
  }

  @Test
  public void shouldSortInstancesAsc() {
    Results<ProcessInstance> results = dao.search(new Query<ProcessInstance>()
        .setSort(Query.Sort.listOf(BPMN_PROCESS_ID, Query.Sort.Order.ASC)));

    assertThat(results.getItems()).hasSize(3);
    assertThat(results.getItems()).extracting(BPMN_PROCESS_ID)
        .containsExactly("CallActivityProcess", "CalledProcess", "process");
  }

  @Test
  public void shouldSortInstancesDesc() {
    Results<ProcessInstance> results = dao.search(new Query<ProcessInstance>()
        .setSort(Query.Sort.listOf(BPMN_PROCESS_ID, Query.Sort.Order.DESC)));

    assertThat(results.getItems()).hasSize(3);
    assertThat(results.getItems()).extracting(BPMN_PROCESS_ID)
        .containsExactly("process", "CalledProcess", "CallActivityProcess");
  }

  @Test
  public void shouldPageInstances() {
    // First page
    Results<ProcessInstance> results = dao.search(new Query<ProcessInstance>().setSize(2)
        .setSort(Query.Sort.listOf(BPMN_PROCESS_ID, Query.Sort.Order.ASC)));

    assertThat(results.getTotal()).isEqualTo(3);
    assertThat(results.getItems().size()).isEqualTo(2);
    assertThat(results.getItems()).extracting(BPMN_PROCESS_ID)
        .containsExactly("CallActivityProcess", "CalledProcess");

    // Second page
    results = dao.search(new Query<ProcessInstance>().setSize(2)
        .setSort(Query.Sort.listOf(BPMN_PROCESS_ID, Query.Sort.Order.ASC))
        .setSearchAfter(results.getSortValues()));

    assertThat(results.getTotal()).isEqualTo(3);
    assertThat(results.getItems().size()).isEqualTo(1);
    assertThat(results.getItems().get(0).getBpmnProcessId()).isEqualTo("process");
  }

  @Test
  public void shouldFilterByParentFlowNodeInstanceKey() {
    // Find the child process
    Results<ProcessInstance> results = dao.search(new Query<ProcessInstance>()
        .setFilter(new ProcessInstance().setBpmnProcessId("CalledProcess")));
    assertThat(results.getItems().size()).isEqualTo(1);
    ProcessInstance childInstance = results.getItems().get(0);

    // Should return the same process when searching by the parent flow node instance key
    results = dao.search(new Query<ProcessInstance>()
        .setFilter(new ProcessInstance().setParentFlowNodeInstanceKey(
            childInstance.getParentFlowNodeInstanceKey())));

    assertThat(results.getItems().size()).isEqualTo(1);
    assertThat(results.getItems().get(0).getBpmnProcessId()).isEqualTo("CalledProcess");
  }

  @Test
  public void shouldFilterByDate() {
    // Get the start date of the child process
    Results<ProcessInstance> results = dao.search(new Query<ProcessInstance>()
        .setFilter(new ProcessInstance().setBpmnProcessId("CalledProcess")));
    assertThat(results.getItems().size()).isEqualTo(1);
    String filterDateTime = results.getItems().get(0).getStartDate();

    // Parent and child process instances should have the same start time
    results = dao.search(new Query<ProcessInstance>()
        .setFilter(new ProcessInstance().setStartDate(filterDateTime)));

    assertThat(results.getItems().size()).isEqualTo(2);
    assertThat(results.getItems()).extracting(BPMN_PROCESS_ID)
        .containsExactlyInAnyOrder("CallActivityProcess", "CalledProcess");
  }
}
