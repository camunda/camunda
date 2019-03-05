/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.it;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.ActivityType;
import org.camunda.operate.entities.ErrorType;
import org.camunda.operate.entities.VariableEntity;
import org.camunda.operate.entities.detailview.ActivityInstanceForDetailViewEntity;
import org.camunda.operate.es.reader.DetailViewReader;
import org.camunda.operate.rest.dto.detailview.ActivityInstanceTreeDto;
import org.camunda.operate.rest.dto.detailview.ActivityInstanceTreeRequestDto;
import org.camunda.operate.rest.dto.detailview.DetailViewActivityInstanceDto;
import org.camunda.operate.rest.dto.incidents.IncidentDto;
import org.camunda.operate.rest.dto.incidents.IncidentResponseDto;
import org.camunda.operate.util.IdTestUtil;
import org.camunda.operate.util.MockMvcTestRule;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.zeebeimport.ZeebeESImporter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.fasterxml.jackson.core.type.TypeReference;
import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.rest.ActivityInstanceRestService.ACTIVITY_INSTANCE_URL;
import static org.camunda.operate.rest.WorkflowInstanceRestService.WORKFLOW_INSTANCE_URL;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class DetailViewIT extends OperateZeebeIntegrationTest {

  @Autowired
  @Qualifier("activityIsCompletedCheck")
  private Predicate<Object[]> activityIsCompletedCheck;

  @Autowired
  @Qualifier("activityIsActiveCheck")
  private Predicate<Object[]> activityIsActiveCheck;

  @Autowired
  @Qualifier("incidentIsActiveCheck")
  private Predicate<Object[]> incidentIsActiveCheck;

  @Autowired
  @Qualifier("incidentsAreActiveCheck")
  private Predicate<Object[]> incidentsAreActiveCheck;

  @Autowired
  private DetailViewReader detailViewReader;

  @Rule
  public MockMvcTestRule mockMvcTestRule = new MockMvcTestRule();

  private ZeebeClient zeebeClient;

  private MockMvc mockMvc;

  @Before
  public void init() {
    super.before();
    zeebeClient = super.getClient();
    this.mockMvc = mockMvcTestRule.getMockMvc();
  }

  @Test
  public void testIncidentsAreReturned() throws Exception {
    // having
    String processId = "complexProcess";
    deployWorkflow("complexProcess_v_3.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, null);
    final String errorMsg = "some error";
    final String activityId = "alwaysFailingTask";
    ZeebeTestUtil.failTask(zeebeClient, activityId, getWorkerName(), 3, errorMsg);
    elasticsearchTestRule.processAllEventsAndWait(incidentsAreActiveCheck, workflowInstanceKey, 4);
    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when
    MockHttpServletRequestBuilder request = get(getIncidentsURL(workflowInstanceKey));
    MvcResult mvcResult = mockMvc
      .perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();
    final IncidentResponseDto incidentResponse = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<IncidentResponseDto>() {
    });

    //then
    assertThat(incidentResponse).isNotNull();
    assertThat(incidentResponse.getCount()).isEqualTo(4);
    assertThat(incidentResponse.getIncidents()).hasSize(4);
    assertThat(incidentResponse.getIncidents()).isSortedAccordingTo(IncidentDto.INCIDENT_DEFAULT_COMPARATOR);
    assertIncident(incidentResponse, errorMsg, activityId, ErrorType.JOB_NO_RETRIES);
    assertIncident(incidentResponse, "No data found for query $.orderId.", "upperTask", ErrorType.IO_MAPPING_ERROR);
    assertIncident(incidentResponse, "Failed to extract the correlation-key by '$.clientId': no value found", "messageCatchEvent", ErrorType.EXTRACT_VALUE_ERROR);
    assertIncident(incidentResponse, "Expected at least one condition to evaluate to true, or to have a default flow", "exclusiveGateway", ErrorType.CONDITION_ERROR);

    assertThat(incidentResponse.getFlowNodes()).hasSize(4);
    assertIncidentFlowNode(incidentResponse, activityId, 1);
    assertIncidentFlowNode(incidentResponse, "upperTask", 1);
    assertIncidentFlowNode(incidentResponse, "messageCatchEvent", 1);
    assertIncidentFlowNode(incidentResponse, "exclusiveGateway", 1);

    assertThat(incidentResponse.getErrorTypes()).hasSize(4);
    assertErrorType(incidentResponse, ErrorType.JOB_NO_RETRIES, 1);
    assertErrorType(incidentResponse, ErrorType.IO_MAPPING_ERROR, 1);
    assertErrorType(incidentResponse, ErrorType.EXTRACT_VALUE_ERROR, 1);
    assertErrorType(incidentResponse, ErrorType.CONDITION_ERROR, 1);
  }

  protected void assertErrorType(IncidentResponseDto incidentResponse, ErrorType errorType, int count) {
    assertThat(incidentResponse.getErrorTypes()).filteredOn(et -> et.getErrorType().equals(errorType.name())).hasSize(1)
      .allMatch(et -> et.getCount() == count && et.getErrorTypeTitle().equals(errorType.getTitle()));
  }

  protected void assertIncidentFlowNode(IncidentResponseDto incidentResponse, String activityId, int count) {
    assertThat(incidentResponse.getFlowNodes()).filteredOn(fn -> fn.getFlowNodeId().equals(activityId)).hasSize(1).allMatch(fn -> fn.getCount() == count);
  }

  protected void assertIncident(IncidentResponseDto incidentResponse, String errorMsg, String activityId, ErrorType errorType) {
    final Optional<IncidentDto> incidentOpt = incidentResponse.getIncidents().stream().filter(inc -> inc.getErrorType().equals(errorType.getTitle())).findFirst();
    assertThat(incidentOpt).isPresent();
    final IncidentDto inc = incidentOpt.get();
    assertThat(inc.getId()).as(activityId + ".id").isNotNull();
    assertThat(inc.getCreationTime()).as(activityId + ".creationTime").isNotNull();
    assertThat(inc.getErrorMessage()).as(activityId + ".errorMessage").isEqualTo(errorMsg);
    assertThat(inc.getFlowNodeId()).as(activityId + ".flowNodeId").isEqualTo(activityId);
    assertThat(inc.getFlowNodeInstanceId()).as(activityId + ".flowNodeInstanceId").isNotNull();
    if (errorType.equals(ErrorType.JOB_NO_RETRIES)) {
      assertThat(inc.getJobId()).as(activityId + ".jobKey").isNotNull();
    } else {
      assertThat(inc.getJobId()).as(activityId + ".jobKey").isNull();
    }
  }

  protected String getIncidentsURL(long workflowInstanceKey) {
    return String.format(WORKFLOW_INSTANCE_URL + "/%s/incidents", workflowInstanceKey);
  }

  protected String getVariablesURL(String workflowInstanceId) {
    return String.format(WORKFLOW_INSTANCE_URL + "/%s/variables", workflowInstanceId);
  }

  protected String getVariablesURL(String workflowInstanceId, String scopeId) {
    return String.format(WORKFLOW_INSTANCE_URL + "/%s/variables?scopeId=%s", workflowInstanceId, scopeId);
  }

  @Test
  public void testActivityInstanceTreeForNonInterruptingBoundaryEvent() throws Exception {
    // having
    String processId = "nonInterruptingBoundaryEvent";
    deployWorkflow("nonInterruptingBoundaryEvent_v_2.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, null);
    //let the boundary event happen
    Thread.sleep(1500L);
    elasticsearchTestRule.processAllEventsAndWait(activityIsActiveCheck, workflowInstanceKey, "task2");

    //when
    ActivityInstanceTreeDto response = getActivityInstanceTreeFromRest(workflowInstanceKey);

    //then
    assertThat(response.getChildren()).hasSize(4);
    String workflowInstanceId = IdTestUtil.getId(workflowInstanceKey);
    assertThat(response.getChildren()).hasSize(4);
    assertChild(response.getChildren(), 0, "startEvent", ActivityState.COMPLETED, workflowInstanceId, ActivityType.START_EVENT, 0);
    assertChild(response.getChildren(), 1, "task1", ActivityState.ACTIVE, workflowInstanceId, ActivityType.SERVICE_TASK, 0);
    assertChild(response.getChildren(), 2, "boundaryEvent", ActivityState.COMPLETED, workflowInstanceId, ActivityType.BOUNDARY_EVENT, 0);
    assertChild(response.getChildren(), 3, "task2", ActivityState.ACTIVE, workflowInstanceId, ActivityType.SERVICE_TASK, 0);
  }

  @Test
  public void testActivityInstanceTreeIsBuild() throws Exception {
    // having
    String processId = "prWithSubprocess";
    deployWorkflow("subProcess.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, null);
    ZeebeTestUtil.completeTask(zeebeClient, "taskA", getWorkerName(), null, 3);
    elasticsearchTestRule.processAllEventsAndWait(activityIsActiveCheck, workflowInstanceKey, "taskB");
    ZeebeTestUtil.completeTask(zeebeClient, "taskB", getWorkerName(), null, 3);
    elasticsearchTestRule.processAllEventsAndWait(activityIsActiveCheck, workflowInstanceKey, "taskC");
    ZeebeTestUtil.failTask(zeebeClient, "taskC", getWorkerName(), 3, "some error");
    elasticsearchTestRule.processAllEventsAndWait(incidentIsActiveCheck, workflowInstanceKey);

    //when
    ActivityInstanceTreeDto response = getActivityInstanceTreeFromRest(workflowInstanceKey);

    //then
    assertThat(response.getChildren()).hasSize(3);

    String workflowInstanceId = IdTestUtil.getId(workflowInstanceKey);
    assertThat(response.getChildren()).hasSize(3);
    assertChild(response.getChildren(), 0, "startEvent", ActivityState.COMPLETED, workflowInstanceId, ActivityType.START_EVENT, 0);
    assertChild(response.getChildren(), 1, "taskA", ActivityState.COMPLETED, workflowInstanceId, ActivityType.SERVICE_TASK, 0);
    DetailViewActivityInstanceDto subprocess = assertChild(response.getChildren(), 2, "subprocess", ActivityState.INCIDENT, workflowInstanceId, ActivityType.SUB_PROCESS, 3);

    assertThat(subprocess.getChildren()).hasSize(3);
    assertChild(subprocess.getChildren(), 0, "startEventSubprocess", ActivityState.COMPLETED, subprocess.getId(), ActivityType.START_EVENT, 0);
    final DetailViewActivityInstanceDto innerSubprocess = assertChild(subprocess.getChildren(), 1, "innerSubprocess", ActivityState.COMPLETED,
      subprocess.getId(), ActivityType.SUB_PROCESS, 3);
    assertChild(subprocess.getChildren(), 2, "taskC", ActivityState.INCIDENT, subprocess.getId(), ActivityType.SERVICE_TASK, 0);

    assertThat(innerSubprocess.getChildren()).hasSize(3);
    assertChild(innerSubprocess.getChildren(), 0, "startEventInnerSubprocess", ActivityState.COMPLETED, innerSubprocess.getId(), ActivityType.START_EVENT, 0);
    assertChild(innerSubprocess.getChildren(), 1, "taskB", ActivityState.COMPLETED, innerSubprocess.getId(), ActivityType.SERVICE_TASK, 0);
    assertChild(innerSubprocess.getChildren(), 2, "endEventInnerSubprocess", ActivityState.COMPLETED, innerSubprocess.getId(), ActivityType.END_EVENT, 0);

  }

  protected DetailViewActivityInstanceDto assertChild(List<DetailViewActivityInstanceDto> children, int childPosition, String activityId, ActivityState state, String parentId, ActivityType type, int numberOfChildren) {
    final DetailViewActivityInstanceDto ai = children.get(childPosition);
    assertThat(ai.getActivityId()).isEqualTo(activityId);
    assertThat(ai.getId()).isNotNull();
    assertThat(ai.getState()).isEqualTo(state);
    assertThat(ai.getParentId()).isEqualTo(parentId);
    assertThat(ai.getStartDate()).isNotNull();
    if (state.equals(ActivityState.COMPLETED) || state.equals(ActivityState.TERMINATED)) {
      assertThat(ai.getEndDate()).isNotNull();
      assertThat(ai.getStartDate()).isBeforeOrEqualTo(ai.getEndDate());
    } else {
      assertThat(ai.getEndDate()).isNull();
    }
    assertThat(ai.getType()).isEqualTo(type);
    assertThat(ai.getChildren()).hasSize(numberOfChildren);
    return ai;
  }

  @Test
  public void testActivityInstanceTreeIncidentStatePropagated() throws Exception {

    // having
    String processId = "prWithSubprocess";
    deployWorkflow("subProcess.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, null);
    ZeebeTestUtil.completeTask(zeebeClient, "taskA", getWorkerName(), null, 3);
    elasticsearchTestRule.processAllEventsAndWait(activityIsActiveCheck, workflowInstanceKey, "taskB");
    ZeebeTestUtil.failTask(zeebeClient, "taskB", getWorkerName(), 3, "some error");
    elasticsearchTestRule.processAllEventsAndWait(incidentIsActiveCheck, workflowInstanceKey);

    //when
    ActivityInstanceTreeDto response = getActivityInstanceTreeFromRest(workflowInstanceKey);

    //then
    assertThat(response.getChildren()).filteredOn("activityId", "subprocess").hasSize(1);
    final DetailViewActivityInstanceDto subprocess = response.getChildren().stream().filter(ai -> ai.getActivityId().equals("subprocess"))
      .findFirst().get();
    assertThat(subprocess.getState()).isEqualTo(ActivityState.INCIDENT);
    assertThat(response.getChildren()).filteredOn(ai -> !ai.getActivityId().equals("subprocess")).allMatch(ai -> !ai.getState().equals(ActivityState.INCIDENT));

    assertThat(subprocess.getChildren()).filteredOn("activityId", "innerSubprocess").hasSize(1);
    final DetailViewActivityInstanceDto innerSuprocess = subprocess.getChildren().stream().filter(ai -> ai.getActivityId().equals("innerSubprocess"))
      .findFirst().get();
    assertThat(innerSuprocess.getState()).isEqualTo(ActivityState.INCIDENT);
    assertThat(subprocess.getChildren()).filteredOn(ai -> !ai.getActivityId().equals("innerSubprocess")).allMatch(ai -> !ai.getState().equals(ActivityState.INCIDENT));

    assertThat(innerSuprocess.getChildren()).filteredOn("activityId", "taskB").allMatch(ai -> ai.getState().equals(ActivityState.INCIDENT));
    assertThat(innerSuprocess.getChildren()).filteredOn(ai -> !ai.getActivityId().equals("taskB")).allMatch(ai -> !ai.getState().equals(ActivityState.INCIDENT));

  }

  @Test
  public void testActivityInstanceTreeFails() throws Exception {
    ActivityInstanceTreeRequestDto treeRequest = new ActivityInstanceTreeRequestDto();
    MockHttpServletRequestBuilder request = post(ACTIVITY_INSTANCE_URL)
      .content(mockMvcTestRule.json(treeRequest))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isBadRequest())
      .andReturn();

    assertThat(mvcResult.getResolvedException().getMessage()).isEqualTo("Workflow instance id must be provided when requesting for activity instance tree.");
  }

  @Test
  public void testVariablesLoaded() throws Exception {
    // having
    String processId = "demoProcess";
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(processId)
      .startEvent("start")
        .serviceTask("task1").zeebeTaskType("task1")
        .subProcess("subProcess")
          .zeebeInput("$.var1", "$.subprocessVarIn")
        .embeddedSubProcess()
        .startEvent()
          .serviceTask("task2").zeebeTaskType("task2")
          .zeebeInput("$.subprocessVarIn", "$.taskVarIn")
          .zeebeOutput("$.taskVarOut", "$.varOut")
        .endEvent()
        .subProcessDone()
        .serviceTask("task3").zeebeTaskType("task3")
      .endEvent()
      .done();
    deployWorkflow(workflow, processId + ".bpmn");

    //TC 1 - when workflow instance is started
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"var1\": \"initialValue\", \"otherVar\": 123}");
    elasticsearchTestRule.processAllEventsAndWait(activityIsActiveCheck, workflowInstanceKey, "task1");
    elasticsearchTestRule.processOneBatchOfEvents(ZeebeESImporter.ImportValueType.VARIABLE);

    //then
    final String workflowInstanceId = IdTestUtil.getId(workflowInstanceKey);

    List<VariableEntity> variables = getVariables(workflowInstanceId);
    assertThat(variables).hasSize(2);
    assertVariable(variables, "var1","\"initialValue\"");
    assertVariable(variables, "otherVar","123");

    //TC2 - when subprocess and task with input mapping are activated
    completeTask(workflowInstanceKey, "task1", null);
    elasticsearchTestRule.processAllEventsAndWait(activityIsActiveCheck, workflowInstanceKey, "task2");
    elasticsearchTestRule.processOneBatchOfEvents(ZeebeESImporter.ImportValueType.VARIABLE);

    //then
    variables = getVariables(workflowInstanceId,"subProcess");
    assertThat(variables).hasSize(1);
    assertVariable(variables, "subprocessVarIn","\"initialValue\"");

    variables = getVariables(workflowInstanceId,"task2");
    assertThat(variables).hasSize(1);
    assertVariable(variables, "taskVarIn","\"initialValue\"");

    //TC3 - when activity with output mapping is completed
    completeTask(workflowInstanceKey, "task2", "{\"taskVarOut\": \"someResult\", \"otherTaskVar\": 456}");
    elasticsearchTestRule.processAllEventsAndWait(activityIsActiveCheck, workflowInstanceKey, "task3");
    elasticsearchTestRule.processOneBatchOfEvents(ZeebeESImporter.ImportValueType.VARIABLE);

    //then
    variables = getVariables(workflowInstanceId,"task2");
    assertThat(variables).hasSize(3);
    assertVariable(variables, "taskVarIn","\"initialValue\"");
    assertVariable(variables, "taskVarOut","\"someResult\"");
    assertVariable(variables, "otherTaskVar","456");
    variables = getVariables(workflowInstanceId,"subProcess");
    assertThat(variables).hasSize(1);
    assertVariable(variables, "subprocessVarIn","\"initialValue\"");
    variables = getVariables(workflowInstanceId);
    assertThat(variables).hasSize(3);
    assertVariable(variables, "var1","\"initialValue\"");
    assertVariable(variables, "varOut","\"someResult\"");
    assertVariable(variables, "otherVar","123");

    //TC4 - when payload is explicitly updated
    ZeebeTestUtil.updatePayload(zeebeClient, workflowInstanceId, "{\"var1\": \"updatedValue\" , \"newVar\": 555 }");
    elasticsearchTestRule.processAllEvents(2, ZeebeESImporter.ImportValueType.VARIABLE);
    variables = getVariables(workflowInstanceId);
    assertThat(variables).hasSize(4);
    assertVariable(variables, "var1","\"updatedValue\"");
    assertVariable(variables, "otherVar","123");
    assertVariable(variables, "varOut","\"someResult\"");
    assertVariable(variables, "newVar","555");

    //TC5 - when task is completed with new payload and workflow instance is finished
    completeTask(workflowInstanceKey, "task3", "{\"task3Completed\": true}");

    //then
    variables = getVariables(workflowInstanceId);
    assertThat(variables).hasSize(5);
    assertVariable(variables, "var1","\"updatedValue\"");
    assertVariable(variables, "otherVar","123");
    assertVariable(variables, "varOut","\"someResult\"");
    assertVariable(variables, "newVar","555");
    assertVariable(variables, "task3Completed","true");

  }

  @Test
  public void testVariablesRequestFailOnEmptyScopeId() throws Exception {
    MvcResult mvcResult = mockMvc.perform(get(getVariablesURL("id")))
      .andExpect(status().isBadRequest())
      .andReturn();

    assertThat(mvcResult.getResolvedException().getMessage()).isEqualTo("Required String parameter 'scopeId' is not present");
  }

  private void assertVariable(List<VariableEntity> variables, String name, String value) {
    assertThat(variables).filteredOn(v -> v.getName().equals(name)).hasSize(1)
      .allMatch(v -> v.getValue().equals(value));
  }

  protected List<VariableEntity> getVariables(String workflowInstanceId) throws Exception {
    MvcResult mvcResult = mockMvc
      .perform(get(getVariablesURL(workflowInstanceId, workflowInstanceId)))
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();
    return mockMvcTestRule.listFromResponse(mvcResult, VariableEntity.class);
  }

  protected List<VariableEntity> getVariables(String workflowInstanceId, String activityId) throws Exception {
    final List<ActivityInstanceForDetailViewEntity> allActivityInstances = detailViewReader.getAllActivityInstances(workflowInstanceId);
    final String task1Id = findActivityInstanceId(allActivityInstances, activityId);
    MvcResult mvcResult = mockMvc
      .perform(get(getVariablesURL(workflowInstanceId, task1Id)))
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();
    return mockMvcTestRule.listFromResponse(mvcResult, VariableEntity.class);
  }

  protected String findActivityInstanceId(List<ActivityInstanceForDetailViewEntity> allActivityInstances, String activityId) {
    assertThat(allActivityInstances).filteredOn(ai -> ai.getActivityId().equals(activityId)).hasSize(1);
    return allActivityInstances.stream().filter(ai -> ai.getActivityId().equals(activityId)).findFirst().get().getId();
  }

  protected ActivityInstanceTreeDto getActivityInstanceTreeFromRest(long workflowInstanceKey) throws Exception {
    ActivityInstanceTreeRequestDto treeRequest = new ActivityInstanceTreeRequestDto(IdTestUtil.getId(workflowInstanceKey));
    MockHttpServletRequestBuilder request = post(ACTIVITY_INSTANCE_URL)
      .content(mockMvcTestRule.json(treeRequest))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc
      .perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    return mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ActivityInstanceTreeDto>() { });
  }

}
