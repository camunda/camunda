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
package io.camunda.operate.zeebeimport;

import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllProcessInstancesRequest;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.util.ThreadUtil;
import io.camunda.operate.util.Tuple;
import io.camunda.operate.util.searchrepository.TestZeebeRepository;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.reader.IncidentReader;
import io.camunda.operate.webapp.reader.ListViewReader;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import io.camunda.operate.webapp.rest.dto.listview.ProcessInstanceStateDto;
import io.camunda.operate.webapp.zeebe.operation.CancelProcessInstanceHandler;
import io.camunda.operate.zeebe.ImportValueType;
import java.util.List;
import java.util.function.Predicate;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class IncidentPostImportZeebeImportIT extends OperateZeebeAbstractIT {

  @Autowired private ProcessInstanceReader processInstanceReader;

  @Autowired private IncidentReader incidentReader;

  @Autowired private ListViewReader listViewReader;

  @Autowired private CancelProcessInstanceHandler cancelProcessInstanceHandler;

  @Autowired private TestZeebeRepository testZeebeRepository;

  @Override
  @Before
  public void before() {
    super.before();
    cancelProcessInstanceHandler.setZeebeClient(super.getClient());
  }

  @Test
  public void testPostImporterRunsAfterIncidentResolved() {
    // having
    final String processId = "process";
    final String taskId = "task";
    final String errorMessage = "Some error";
    final Long processDefinitionKey =
        tester.deployProcess("single-task.bpmn").getProcessDefinitionKey();
    final Long processInstanceKey =
        tester
            .startProcessInstance(processId, null)
            .and()
            .failTask(taskId, errorMessage)
            .getProcessInstanceKey();
    processAllRecordsWithoutPostImporterAndWait(incidentsArePresentCheck, processInstanceKey, 1);
    final Tuple<Long, Long> incidentData = findSingleZeebeIncidentData(processInstanceKey);
    tester.resolveIncident(incidentData.getRight(), incidentData.getLeft());
    // let the Zeebe process incident RESOLVE and export
    while (countZeebeIncidentRecords(processInstanceKey) < 2) {
      ThreadUtil.sleepFor(1000L);
    }
    processAllRecordsWithoutPostImporterAndWait(postImporterQueueCountCheck, 2);

    // when
    // run with post importer
    processImportTypeAndWait(ImportValueType.INCIDENT, incidentsAreResolved, processInstanceKey, 1);

    // then
    final ProcessInstanceForListViewEntity processInstanceEntity =
        processInstanceReader.getProcessInstanceByKey(processInstanceKey);
    assertThat(processInstanceEntity.isIncident()).isFalse();
    // and
    final List<IncidentEntity> allIncidents =
        incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey);
    assertThat(allIncidents).hasSize(0);

    // and
    final ListViewProcessInstanceDto pi = getSingleProcessInstanceForListView();
    assertThat(pi.getState()).isEqualTo(ProcessInstanceStateDto.ACTIVE);

    // and
    final List<FlowNodeInstanceEntity> flowNodeInstances = getFlowNodeInstances(processInstanceKey);
    assertThat(flowNodeInstances).hasSize(2);
    assertThat(flowNodeInstances.get(1).getState()).isEqualTo(FlowNodeState.ACTIVE);
    assertThat(flowNodeInstances.get(1).isIncident()).isFalse();
  }

  @Test
  public void testPostImporterRunsAfterInstanceIsDeleted() throws Exception {
    // having
    final String processId = "process";
    final String taskId = "task";
    final String errorMessage = "Some error";
    final Long processDefinitionKey =
        tester.deployProcess("single-task.bpmn").getProcessDefinitionKey();
    final Long processInstanceKey =
        tester.startProcessInstance(processId, null).getProcessInstanceKey();
    processRecordTypeWithoutPostImporterAndWait(
        ImportValueType.PROCESS_INSTANCE, processInstanceIsCreatedCheck, processInstanceKey);
    tester.processInstanceIsStarted().and().failTask(taskId, errorMessage);

    // cancel and delete process instance
    tester.cancelProcessInstanceOperation().and().executeOperations();
    processRecordTypeWithoutPostImporterAndWait(
        ImportValueType.PROCESS_INSTANCE, processInstanceIsCanceledCheck, processInstanceKey);
    tester.deleteProcessInstance().and().executeOperations();

    // create the 2nd instance with new incident
    final Long processInstanceKey2 =
        tester.startProcessInstance(processId, null).getProcessInstanceKey();
    processRecordTypeWithoutPostImporterAndWait(
        ImportValueType.PROCESS_INSTANCE, processInstanceIsCreatedCheck, processInstanceKey2);
    tester.processInstanceIsStarted().and().failTask(taskId, errorMessage);

    // when
    // run with post importer
    processImportTypeAndWait(ImportValueType.INCIDENT, incidentIsActiveCheck, processInstanceKey2);

    // then
    final ProcessInstanceForListViewEntity processInstanceEntity =
        processInstanceReader.getProcessInstanceByKey(processInstanceKey2);
    assertThat(processInstanceEntity.isIncident()).isTrue();
    // and
    final List<IncidentEntity> allIncidents =
        incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey2);
    assertThat(allIncidents).hasSize(1);

    // and
    final ListViewProcessInstanceDto pi = getSingleProcessInstanceForListView();
    assertThat(pi.getState()).isEqualTo(ProcessInstanceStateDto.INCIDENT);

    // and
    final List<FlowNodeInstanceEntity> flowNodeInstances =
        getFlowNodeInstances(processInstanceKey2);
    assertThat(flowNodeInstances).hasSize(2);
    assertThat(flowNodeInstances.get(1).getState()).isEqualTo(FlowNodeState.ACTIVE);
    assertThat(flowNodeInstances.get(1).isIncident()).isTrue();
  }

  private Tuple<Long, Long> findSingleZeebeIncidentData(final Long processInstanceKey) {
    record Value(Long jobKey) {}
    record Result(Long key, Value value) {}

    final var index = zeebeRule.getPrefix() + "_incident*";
    final List<Tuple<Long, Long>> tuples =
        testZeebeRepository
            .scrollTerm(index, "value.processInstanceKey", processInstanceKey, Result.class)
            .stream()
            .map(result -> new Tuple<>(result.key, result.value.jobKey))
            .toList();

    assertThat(tuples).hasSize(1);
    return tuples.get(0);
  }

  private long countZeebeIncidentRecords(final Long processInstanceKey) {
    final var index = zeebeRule.getPrefix() + "_incident*";
    return testZeebeRepository
        .scrollTerm(index, "value.processInstanceKey", processInstanceKey, Object.class)
        .size();
  }

  protected void processImportTypeAndWait(
      final ImportValueType importValueType,
      final Predicate<Object[]> waitTill,
      final Object... arguments) {
    searchTestRule.processRecordsWithTypeAndWait(importValueType, waitTill, arguments);
  }

  private void processAllRecordsWithoutPostImporterAndWait(
      final Predicate<Object[]> waitTill, final Object... arguments) {
    searchTestRule.processAllRecordsAndWait(false, waitTill, null, arguments);
  }

  private void processRecordTypeWithoutPostImporterAndWait(
      final ImportValueType valueType,
      final Predicate<Object[]> waitTill,
      final Object... arguments) {
    searchTestRule.processRecordsWithTypeAndWait(valueType, false, waitTill, arguments);
  }

  protected ListViewProcessInstanceDto getSingleProcessInstanceForListView() {
    final ListViewRequestDto request = createGetAllProcessInstancesRequest();
    request.setPageSize(100);
    final ListViewResponseDto listViewResponse = listViewReader.queryProcessInstances(request);
    assertThat(listViewResponse.getTotalCount()).isEqualTo(1);
    assertThat(listViewResponse.getProcessInstances()).hasSize(1);
    return listViewResponse.getProcessInstances().get(0);
  }

  protected List<FlowNodeInstanceEntity> getFlowNodeInstances(final Long processInstanceKey) {
    return tester.getAllFlowNodeInstances(processInstanceKey);
  }
}
