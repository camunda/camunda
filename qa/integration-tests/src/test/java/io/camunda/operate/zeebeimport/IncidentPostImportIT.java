/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport;

import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.util.ThreadUtil;
import io.camunda.operate.util.Tuple;
import io.camunda.operate.webapp.reader.IncidentReader;
import io.camunda.operate.webapp.reader.ListViewReader;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.rest.dto.listview.*;
import io.camunda.operate.webapp.zeebe.operation.CancelProcessInstanceHandler;
import io.camunda.operate.zeebe.ImportValueType;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllProcessInstancesRequest;
import static io.camunda.operate.util.ElasticsearchUtil.scroll;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public class IncidentPostImportIT extends OperateZeebeAbstractIT {

  @Autowired
  private ProcessInstanceReader processInstanceReader;

  @Autowired
  private IncidentReader incidentReader;

  @Autowired
  private ListViewReader listViewReader;

  @Autowired
  private CancelProcessInstanceHandler cancelProcessInstanceHandler;

  @Autowired
  @Qualifier("zeebeEsClient")
  protected RestHighLevelClient zeebeEsClient;

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
    final Long processDefinitionKey = tester.deployProcess("single-task.bpmn")
        .getProcessDefinitionKey();
    final Long processInstanceKey = tester
        .startProcessInstance(processId, null)
        .and()
        .failTask(taskId, errorMessage)
        .getProcessInstanceKey();
    processAllRecordsWithoutPostImporterAndWait(incidentsArePresentCheck, processInstanceKey, 1);
    Tuple<Long, Long> incidentData = findSingleZeebeIncidentData(processInstanceKey);
    tester.resolveIncident(incidentData.getRight(), incidentData.getLeft());
    //let the Zeebe process incident RESOLVE and export
    while (countZeebeIncidentRecords(processInstanceKey) < 2) {
      ThreadUtil.sleepFor(1000L);
    }
    processAllRecordsWithoutPostImporterAndWait(postImporterQueueCountCheck, 2);

    //when
    //run with post importer
    processImportTypeAndWait(ImportValueType.INCIDENT, incidentsAreResolved, processInstanceKey, 1);

    //then
    final ProcessInstanceForListViewEntity processInstanceEntity = processInstanceReader.getProcessInstanceByKey(processInstanceKey);
    assertThat(processInstanceEntity.isIncident()).isFalse();
    //and
    final List<IncidentEntity> allIncidents = incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey);
    assertThat(allIncidents).hasSize(0);

    //and
    final ListViewProcessInstanceDto pi = getSingleProcessInstanceForListView();
    assertThat(pi.getState()).isEqualTo(ProcessInstanceStateDto.ACTIVE);

    //and
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
    final Long processDefinitionKey = tester.deployProcess("single-task.bpmn")
        .getProcessDefinitionKey();
    final Long processInstanceKey = tester
        .startProcessInstance(processId, null)
        .getProcessInstanceKey();
    processRecordTypeWithoutPostImporterAndWait(ImportValueType.PROCESS_INSTANCE, processInstanceIsCreatedCheck, processInstanceKey);
    tester
        .processInstanceIsStarted()
        .and()
        .failTask(taskId, errorMessage);

    //cancel and delete process instance
    tester.cancelProcessInstanceOperation()
        .and()
        .executeOperations();
    processRecordTypeWithoutPostImporterAndWait(ImportValueType.PROCESS_INSTANCE, processInstanceIsCanceledCheck, processInstanceKey);
    tester
        .deleteProcessInstance()
        .and()
        .executeOperations();

    //create the 2nd instance with new incident
    final Long processInstanceKey2 = tester
        .startProcessInstance(processId, null)
        .getProcessInstanceKey();
    processRecordTypeWithoutPostImporterAndWait(ImportValueType.PROCESS_INSTANCE, processInstanceIsCreatedCheck, processInstanceKey2);
    tester
        .processInstanceIsStarted()
        .and()
        .failTask(taskId, errorMessage);

    //when
    //run with post importer
    processImportTypeAndWait(ImportValueType.INCIDENT, incidentIsActiveCheck, processInstanceKey2);

    //then
    final ProcessInstanceForListViewEntity processInstanceEntity = processInstanceReader.getProcessInstanceByKey(processInstanceKey2);
    assertThat(processInstanceEntity.isIncident()).isTrue();
    //and
    final List<IncidentEntity> allIncidents = incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey2);
    assertThat(allIncidents).hasSize(1);

    //and
    final ListViewProcessInstanceDto pi = getSingleProcessInstanceForListView();
    assertThat(pi.getState()).isEqualTo(ProcessInstanceStateDto.INCIDENT);

    //and
    final List<FlowNodeInstanceEntity> flowNodeInstances = getFlowNodeInstances(processInstanceKey2);
    assertThat(flowNodeInstances).hasSize(2);
    assertThat(flowNodeInstances.get(1).getState()).isEqualTo(FlowNodeState.ACTIVE);
    assertThat(flowNodeInstances.get(1).isIncident()).isTrue();
  }

  private Tuple<Long, Long> findSingleZeebeIncidentData(Long processInstanceKey) {
    SearchRequest request = new SearchRequest(zeebeRule.getPrefix() + "_incident*").source(
        new SearchSourceBuilder().query(termQuery("value.processInstanceKey", processInstanceKey)));
    List<Tuple<Long, Long>> incidentData = new ArrayList<>();
    try {
      scroll(request, sh -> {
        incidentData.addAll(Arrays.stream(sh.getHits()).map(
                hit -> new Tuple<>((Long)hit.getSourceAsMap().get("key"), (Long)(((Map)hit.getSourceAsMap().get("value")).get("jobKey"))))
            .collect(Collectors.toList()));
      }, zeebeEsClient);
    } catch (IOException e) {
      throw new OperateRuntimeException(e);
    }
    assertThat(incidentData).hasSize(1);
    return incidentData.get(0);
  }

  private long countZeebeIncidentRecords(Long processInstanceKey) {
    SearchRequest request = new SearchRequest(zeebeRule.getPrefix() + "_incident*").source(
        new SearchSourceBuilder().query(termQuery("value.processInstanceKey", processInstanceKey)));
    List<Tuple<Long, Long>> incidentData = new ArrayList<>();
    try {
      SearchResponse response = zeebeEsClient.search(request, RequestOptions.DEFAULT);
      return response.getHits().getTotalHits().value;
    } catch (IOException e) {
      throw new OperateRuntimeException(e);
    }
  }

  protected void processImportTypeAndWait(ImportValueType importValueType,
      Predicate<Object[]> waitTill, Object... arguments) {
    searchTestRule.processRecordsWithTypeAndWait(importValueType, waitTill, arguments);
  }

  private void processAllRecordsWithoutPostImporterAndWait(Predicate<Object[]> waitTill, Object... arguments) {
    searchTestRule.processAllRecordsAndWait(false, waitTill, null, arguments);
  }

  private void processRecordTypeWithoutPostImporterAndWait(ImportValueType valueType, Predicate<Object[]> waitTill, Object... arguments) {
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

  protected List<FlowNodeInstanceEntity> getFlowNodeInstances(Long processInstanceKey) {
    return tester.getAllFlowNodeInstances(processInstanceKey);
  }

}
