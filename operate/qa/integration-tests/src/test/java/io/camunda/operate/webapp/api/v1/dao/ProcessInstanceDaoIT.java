/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.webapp.api.v1.dao;

import static io.camunda.operate.webapp.api.v1.entities.ProcessInstance.BPMN_PROCESS_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.operate.util.j5templates.OperateZeebeSearchAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.ChangeStatus;
import io.camunda.operate.webapp.api.v1.entities.ProcessInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// The delete operation interacts with multiple systems and data objects so for simplicity, just
// utilize
// zeebe to setup all the data
public class ProcessInstanceDaoIT extends OperateZeebeSearchAbstractIT {
  @Autowired private ProcessInstanceDao dao;
  private Long callActivityProcessInstanceKey;
  private Long singleTaskProcessInstanceKey;

  @Override
  public void runAdditionalBeforeAllSetup() {
    operateTester.deployProcessAndWait("callActivityProcess.bpmn");
    operateTester.deployProcessAndWait("calledProcess.bpmn");
    operateTester.deployProcessAndWait("single-task.bpmn");

    // This starts two process instances: CallActivityProcess and CalledProcess since they are
    // linked
    // by parent-child relationship in the process definitions deployed
    callActivityProcessInstanceKey = operateTester.startProcessAndWait("CallActivityProcess");
    singleTaskProcessInstanceKey = operateTester.startProcessAndWait("process");
  }

  @Test
  public void shouldReturnProcessInstancesOnSearch() {
    final Results<ProcessInstance> processInstanceResults = dao.search(new Query<>());

    assertThat(processInstanceResults.getTotal()).isEqualTo(3);
    assertThat(processInstanceResults.getItems())
        .extracting(BPMN_PROCESS_ID)
        .containsExactlyInAnyOrder("CalledProcess", "CallActivityProcess", "process");
  }

  @Test
  public void searchShouldReturnParentKeyWhenExists() {
    // Find the child process
    final Results<ProcessInstance> processInstanceResults =
        dao.search(
            new Query<ProcessInstance>()
                .setFilter(new ProcessInstance().setBpmnProcessId("CalledProcess")));
    assertThat(processInstanceResults.getItems().size()).isEqualTo(1);
    final ProcessInstance instance = processInstanceResults.getItems().get(0);

    assertThat(instance.getParentKey()).isEqualTo(callActivityProcessInstanceKey);
    assertThat(instance.getParentFlowNodeInstanceKey()).isNotNull();
  }

  @Test
  public void shouldDeleteByKey() {
    // Complete the task so the process instance can be deleted
    operateTester.completeTaskAndWaitForProcessFinish(
        singleTaskProcessInstanceKey, "task", "task", null);

    // Delete the process instance
    final ChangeStatus changeStatus = dao.delete(singleTaskProcessInstanceKey);
    assertThat(changeStatus.getDeleted()).isEqualTo(1);

    // Ensure the indices are updated and then check that the deleted instance no longer appears in
    // search queries
    operateTester.refreshSearchIndices();
    final Results<ProcessInstance> results =
        dao.search(
            new Query<ProcessInstance>()
                .setFilter(new ProcessInstance().setBpmnProcessId("process")));
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
    final ProcessInstance result = dao.byKey(singleTaskProcessInstanceKey);
    assertThat(result.getKey()).isEqualTo(singleTaskProcessInstanceKey);
    assertThat(result.getBpmnProcessId()).isEqualTo("process");
    assertThat(result.getIncident()).isEqualTo(false);
  }

  @Test
  public void shouldThrowWhenKeyNotExists() {
    assertThrows(ResourceNotFoundException.class, () -> dao.byKey(1L));
  }

  @Test
  public void shouldSortInstancesAsc() {
    final Results<ProcessInstance> results =
        dao.search(
            new Query<ProcessInstance>()
                .setSort(Query.Sort.listOf(BPMN_PROCESS_ID, Query.Sort.Order.ASC)));

    assertThat(results.getItems()).hasSize(3);
    assertThat(results.getItems())
        .extracting(BPMN_PROCESS_ID)
        .containsExactly("CallActivityProcess", "CalledProcess", "process");
  }

  @Test
  public void shouldSortInstancesDesc() {
    final Results<ProcessInstance> results =
        dao.search(
            new Query<ProcessInstance>()
                .setSort(Query.Sort.listOf(BPMN_PROCESS_ID, Query.Sort.Order.DESC)));

    assertThat(results.getItems()).hasSize(3);
    assertThat(results.getItems())
        .extracting(BPMN_PROCESS_ID)
        .containsExactly("process", "CalledProcess", "CallActivityProcess");
  }

  @Test
  public void shouldPageInstances() {
    // First page
    Results<ProcessInstance> results =
        dao.search(
            new Query<ProcessInstance>()
                .setSize(2)
                .setSort(Query.Sort.listOf(BPMN_PROCESS_ID, Query.Sort.Order.ASC)));

    assertThat(results.getTotal()).isEqualTo(3);
    assertThat(results.getItems().size()).isEqualTo(2);
    assertThat(results.getItems())
        .extracting(BPMN_PROCESS_ID)
        .containsExactly("CallActivityProcess", "CalledProcess");

    // Second page
    results =
        dao.search(
            new Query<ProcessInstance>()
                .setSize(2)
                .setSort(Query.Sort.listOf(BPMN_PROCESS_ID, Query.Sort.Order.ASC))
                .setSearchAfter(results.getSortValues()));

    assertThat(results.getTotal()).isEqualTo(3);
    assertThat(results.getItems().size()).isEqualTo(1);
    assertThat(results.getItems().get(0).getBpmnProcessId()).isEqualTo("process");
  }

  @Test
  public void shouldFilterByParentFlowNodeInstanceKey() {
    // Find the child process
    Results<ProcessInstance> results =
        dao.search(
            new Query<ProcessInstance>()
                .setFilter(new ProcessInstance().setBpmnProcessId("CalledProcess")));
    assertThat(results.getItems().size()).isEqualTo(1);
    final ProcessInstance childInstance = results.getItems().get(0);

    // Should return the same process when searching by the parent flow node instance key
    results =
        dao.search(
            new Query<ProcessInstance>()
                .setFilter(
                    new ProcessInstance()
                        .setParentFlowNodeInstanceKey(
                            childInstance.getParentFlowNodeInstanceKey())));

    assertThat(results.getItems().size()).isEqualTo(1);
    assertThat(results.getItems().get(0).getBpmnProcessId()).isEqualTo("CalledProcess");
  }

  @Test
  public void shouldFilterByDate() {
    // Get the start date of the child process
    Results<ProcessInstance> results =
        dao.search(
            new Query<ProcessInstance>()
                .setFilter(new ProcessInstance().setBpmnProcessId("CalledProcess")));
    assertThat(results.getItems().size()).isEqualTo(1);
    final String filterDateTime = results.getItems().get(0).getEndDate();

    // Parent and child process instances should have the same start time
    results =
        dao.search(
            new Query<ProcessInstance>()
                .setFilter(new ProcessInstance().setEndDate(filterDateTime)));

    assertThat(results.getItems().size()).isEqualTo(2);
    assertThat(results.getItems())
        .extracting(BPMN_PROCESS_ID)
        .containsExactlyInAnyOrder("CallActivityProcess", "CalledProcess");
  }

  @Test
  public void shouldFilterByDateWithDateMath() {

    // Get the start date of the child process
    Results<ProcessInstance> results =
        dao.search(
            new Query<ProcessInstance>()
                .setFilter(new ProcessInstance().setBpmnProcessId("CalledProcess")));
    assertThat(results.getItems().size()).isEqualTo(1);
    final String filterDateTime = results.getItems().get(0).getEndDate() + "||/d";

    // Parent and child process instances should have the same start time
    results =
        dao.search(
            new Query<ProcessInstance>()
                .setFilter(new ProcessInstance().setEndDate(filterDateTime)));

    assertThat(results.getItems().size()).isEqualTo(2);
    assertThat(results.getItems())
        .extracting(BPMN_PROCESS_ID)
        .containsExactlyInAnyOrder("CallActivityProcess", "CalledProcess");
  }

  @Test
  public void shouldFilterByIncident() {

    final Results<ProcessInstance> resultsWithIncident =
        dao.search(new Query<ProcessInstance>().setFilter(new ProcessInstance().setIncident(true)));

    final Results<ProcessInstance> resultsWithoutIncident =
        dao.search(
            new Query<ProcessInstance>().setFilter(new ProcessInstance().setIncident(false)));

    assertTrue(resultsWithIncident.getItems().isEmpty());
    assertThat(resultsWithoutIncident.getItems().size()).isEqualTo(3);
  }
}
