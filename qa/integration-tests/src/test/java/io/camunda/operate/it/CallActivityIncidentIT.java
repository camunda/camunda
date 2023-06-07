/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.it;

import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllRunningQuery;
import static io.camunda.operate.webapp.rest.FlowNodeInstanceRestService.FLOW_NODE_INSTANCE_URL;
import static io.camunda.operate.webapp.rest.IncidentRestService.INCIDENT_URL;
import static io.camunda.operate.webapp.rest.ProcessInstanceRestService.PROCESS_INSTANCE_URL;
import static io.camunda.operate.webapp.rest.dto.listview.ProcessInstanceStateDto.ACTIVE;
import static io.camunda.operate.webapp.rest.dto.listview.ProcessInstanceStateDto.INCIDENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.qa.util.RestAPITestUtil;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.OperateZeebeIntegrationTest;
import io.camunda.operate.webapp.rest.ProcessInstanceRestService;
import io.camunda.operate.webapp.rest.dto.FlowNodeStatisticsDto;
import io.camunda.operate.webapp.rest.dto.ProcessInstanceCoreStatisticsDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceQueryDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceRequestDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceResponseDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeStateDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentsByProcessGroupStatisticsDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;

public class CallActivityIncidentIT extends OperateZeebeIntegrationTest {

  private static final String QUERY_PROCESS_STATISTICS_URL = "/api/process-instances/statistics";
  private static final String QUERY_INCIDENTS_BY_PROCESS_URL = INCIDENT_URL + "/byProcess";
  private static final String QUERY_PROCESS_CORE_STATISTICS_URL = "/api/process-instances/core-statistics";

  public static final String PARENT_PROCESS_ID = "parentProcess";
  public static final String CALLED_PROCESS_ID = "process";
  public static final String CALL_ACTIVITY_ID = "callActivity";
  public static final String TASK_ID = "task";
  public static final String TASK_ID_2 = "task2";
  private long calledProcessDefinitionKey;
  private long parentProcessDefinitionKey;
  private long incidentProcessInstanceKey;
  private long activeProcessInstanceKey;

  /*
   * parentProcess instance 1 -> process instance 1 has incident
   * parentProcess instance 2 -> process instance 2 without incident
   * parentProcess instance 3 -> process instance 3 without incident
   */
  @Before
  public void createData() {
    final BpmnModelInstance parentProcess =
        Bpmn.createExecutableProcess(PARENT_PROCESS_ID)
            .startEvent()
            .callActivity(CALL_ACTIVITY_ID)
            .zeebeProcessId(CALLED_PROCESS_ID)
            .done();
    final BpmnModelInstance childProcess =
        Bpmn.createExecutableProcess(CALLED_PROCESS_ID)
            .startEvent()
            .parallelGateway("parallel")
            .serviceTask(TASK_ID)
            .zeebeJobType(TASK_ID)
            .moveToNode("parallel")
            .serviceTask(TASK_ID_2)
            .zeebeJobType(TASK_ID_2)
            .done();
    calledProcessDefinitionKey = tester
        .deployProcess(childProcess, "calledProcess.bpmn")
        .getProcessDefinitionKey();
    parentProcessDefinitionKey = tester
        .deployProcess(parentProcess, "testProcess.bpmn")
        .getProcessDefinitionKey();

    incidentProcessInstanceKey = tester
        .startProcessInstance(PARENT_PROCESS_ID, null)
        .getProcessInstanceKey();
    tester.waitUntil()
        .conditionIsMet(processInstancesAreStartedByProcessId, calledProcessDefinitionKey, 1)
        .and()
        .failTask(TASK_ID, "Error in called process task")
        .failTask(TASK_ID_2, "Error in called process task 2")
        .waitUntil()
        .incidentsInAnyInstanceAreActive(2)
        .and()
        .resolveIncident()        //incident for TASK_ID_2 is resolved
        .waitUntil()
        .incidentsInAnyInstanceAreActive(1);

    activeProcessInstanceKey = tester
        .startProcessInstance(PARENT_PROCESS_ID, null)
        .getProcessInstanceKey();

    tester
        .startProcessInstance(PARENT_PROCESS_ID, null)
        .and().waitUntil()
        .conditionIsMet(processInstancesAreStartedByProcessId, calledProcessDefinitionKey, 3)
        .flowNodesInAnyInstanceAreActive(TASK_ID, 3)
        .flowNodesInAnyInstanceAreActive(TASK_ID_2, 3);
  }


  /**
   * Core statistics will count 2 as incidents and 4 as running.
   */
  @Test
  public void testIncidentPropagatedInCoreStatistics() throws Exception {
    //when
    ProcessInstanceCoreStatisticsDto coreStatistics = mockMvcTestRule
        .fromResponse(getRequest(QUERY_PROCESS_CORE_STATISTICS_URL),
            ProcessInstanceCoreStatisticsDto.class);
    // then return zero statistics
    assertEquals(6L, coreStatistics.getRunning().longValue());
    assertEquals(4L, coreStatistics.getActive().longValue());
    assertEquals(2L, coreStatistics.getWithIncidents().longValue());

  }

  @Test
  public void testIncidentPropagatedInIncidentsByProcess() throws Exception {
    List<IncidentsByProcessGroupStatisticsDto> processGroups = requestIncidentsByProcess();
    assertThat(processGroups).hasSize(2);
    for(IncidentsByProcessGroupStatisticsDto stat: processGroups) {
      assertThat(stat.getInstancesWithActiveIncidentsCount()).isEqualTo(1);
      assertThat(stat.getActiveInstancesCount()).isEqualTo(2);
    }
  }

  @Test
  public void testStatistics() throws Exception {
    //statistics for parent process
    ListViewQueryDto queryRequest = createGetAllProcessInstancesQuery(parentProcessDefinitionKey);

    List<FlowNodeStatisticsDto> activityStatisticsDtos = getActivityStatistics(queryRequest);
    assertThat(activityStatisticsDtos).hasSize(1);
    assertThat(activityStatisticsDtos).filteredOn(ai -> ai.getActivityId().equals(CALL_ACTIVITY_ID)).allMatch(ai->
      ai.getActive().equals(2L) && ai.getCanceled().equals(0L) && ai.getCompleted().equals(0L) && ai.getIncidents().equals(1L)
    );

    //statistics for called process
    queryRequest = createGetAllProcessInstancesQuery(calledProcessDefinitionKey);

    activityStatisticsDtos = getActivityStatistics(queryRequest);
    assertThat(activityStatisticsDtos).hasSize(2);
    assertThat(activityStatisticsDtos).filteredOn(ai -> ai.getActivityId().equals(TASK_ID)).allMatch(ai->
      ai.getActive().equals(2L) && ai.getCanceled().equals(0L) && ai.getCompleted().equals(0L) && ai.getIncidents().equals(1L)
    );
    assertThat(activityStatisticsDtos).filteredOn(ai -> ai.getActivityId().equals(TASK_ID_2)).allMatch(ai->
      ai.getActive().equals(3L) && ai.getCanceled().equals(0L) && ai.getCompleted().equals(0L) && ai.getIncidents().equals(0L)
    );
  }

  @Test
  public void testQueryByIncident() throws Exception {
    ListViewQueryDto queryRequest = createGetAllRunningQuery().setActive(false);
    ListViewResponseDto instanceList = getProcessInstanceList(new ListViewRequestDto(queryRequest));
    assertThat(instanceList.getTotalCount()).isEqualTo(2);
    assertThat(instanceList.getProcessInstances()).extracting("processName")
        .containsExactlyInAnyOrder(PARENT_PROCESS_ID, CALLED_PROCESS_ID);
    assertThat(instanceList.getProcessInstances()).extracting("state").containsOnly(INCIDENT);

    queryRequest = createGetAllRunningQuery().setIncidents(false);
    instanceList = getProcessInstanceList(new ListViewRequestDto(queryRequest));
    assertThat(instanceList.getTotalCount()).isEqualTo(4);
    assertThat(instanceList.getProcessInstances()).extracting("processName")
        .containsAll(List.of(PARENT_PROCESS_ID, CALLED_PROCESS_ID));
  }

  @Test
  public void testFlowNodeStates() throws Exception {
    Map<String, FlowNodeStateDto> flowNodeStates = getFlowNodeStateDtos(
        String.valueOf(incidentProcessInstanceKey));
    assertThat(flowNodeStates).hasSize(2);
    assertThat(flowNodeStates.get(CALL_ACTIVITY_ID)).isEqualTo(FlowNodeStateDto.INCIDENT);

    flowNodeStates = getFlowNodeStateDtos(String.valueOf(activeProcessInstanceKey));
    assertThat(flowNodeStates).hasSize(2);
    assertThat(flowNodeStates.get(CALL_ACTIVITY_ID)).isEqualTo(FlowNodeStateDto.ACTIVE);
  }

  @Test
  public void testSingleProcessInstance() throws Exception {
    ListViewProcessInstanceDto instance = getProcessInstanceById(String.valueOf(
        incidentProcessInstanceKey));
    assertThat(instance.getBpmnProcessId()).isEqualTo(PARENT_PROCESS_ID);
    assertThat(instance.getState()).isEqualTo(INCIDENT);

    instance = getProcessInstanceById(String.valueOf(activeProcessInstanceKey));
    assertThat(instance.getBpmnProcessId()).isEqualTo(PARENT_PROCESS_ID);
    assertThat(instance.getState()).isEqualTo(ACTIVE);
  }

  @Test
  public void testFlowNodeInstances() throws Exception {
    String processInstanceId = String.valueOf(incidentProcessInstanceKey);
    FlowNodeInstanceQueryDto request = new FlowNodeInstanceQueryDto(
        processInstanceId, processInstanceId);
    List<FlowNodeInstanceDto> instances = getFlowNodeInstanceOneListFromRest(request);
    assertThat(instances).filteredOn(i -> i.getFlowNodeId().equals(CALL_ACTIVITY_ID))
        .extracting("state").containsOnly(FlowNodeStateDto.INCIDENT);

    processInstanceId = String.valueOf(activeProcessInstanceKey);
    request = new FlowNodeInstanceQueryDto(
        processInstanceId, processInstanceId);
    instances = getFlowNodeInstanceOneListFromRest(request);
    assertThat(instances).filteredOn(i -> i.getFlowNodeId().equals(CALL_ACTIVITY_ID))
        .extracting("state").containsOnly(FlowNodeStateDto.ACTIVE);
  }

  private ListViewQueryDto createGetAllProcessInstancesQuery(Long... processDefinitionKeys) {
    ListViewQueryDto q = RestAPITestUtil.createGetAllProcessInstancesQuery();
    if (processDefinitionKeys != null && processDefinitionKeys.length > 0) {
      q.setProcessIds(CollectionUtil.toSafeListOfStrings(processDefinitionKeys));
    }
    return q;
  }

  private List<FlowNodeStatisticsDto> getActivityStatistics(ListViewQueryDto query) throws Exception {
    return mockMvcTestRule.listFromResponse(postRequest(QUERY_PROCESS_STATISTICS_URL, query), FlowNodeStatisticsDto.class);
  }

  private ListViewResponseDto getProcessInstanceList(ListViewRequestDto request) throws Exception {
    return mockMvcTestRule
        .fromResponse(postRequest(PROCESS_INSTANCE_URL, request), new TypeReference<>() {
        });
  }

  private List<IncidentsByProcessGroupStatisticsDto> requestIncidentsByProcess() throws Exception {
    return mockMvcTestRule.listFromResponse(getRequest(QUERY_INCIDENTS_BY_PROCESS_URL), IncidentsByProcessGroupStatisticsDto.class);
  }

  private Map<String, FlowNodeStateDto> getFlowNodeStateDtos(String processInstanceId) throws Exception {
    MvcResult mvcResult = getRequest(String.format(ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/%s/flow-node-states", processInstanceId));
    return mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() { });
  }

  private ListViewProcessInstanceDto getProcessInstanceById(final String processInstanceId)
      throws Exception {
    String url = String.format("%s/%s", PROCESS_INSTANCE_URL, processInstanceId);
    final MvcResult result = getRequest(url);
    return mockMvcTestRule.fromResponse(result, new TypeReference<>() {
    });
  }

  private List<FlowNodeInstanceDto> getFlowNodeInstanceOneListFromRest(
      FlowNodeInstanceQueryDto query) throws Exception {
    FlowNodeInstanceRequestDto request = new FlowNodeInstanceRequestDto(query);
    MvcResult mvcResult = postRequest(FLOW_NODE_INSTANCE_URL, request);
    final Map<String, FlowNodeInstanceResponseDto> response = mockMvcTestRule
        .fromResponse(mvcResult, new TypeReference<>() {
        });
    assertThat(response).hasSize(1);
    return response.values().iterator().next().getChildren();
  }

}
