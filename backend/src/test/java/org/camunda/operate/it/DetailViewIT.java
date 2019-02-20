/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.operate.it;

import java.util.List;
import java.util.function.Predicate;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.ActivityType;
import org.camunda.operate.entities.VariableEntity;
import org.camunda.operate.entities.detailview.ActivityInstanceForDetailViewEntity;
import org.camunda.operate.es.reader.DetailViewReader;
import org.camunda.operate.rest.dto.detailview.ActivityInstanceTreeDto;
import org.camunda.operate.rest.dto.detailview.ActivityInstanceTreeRequestDto;
import org.camunda.operate.rest.dto.detailview.DetailViewActivityInstanceDto;
import org.camunda.operate.rest.dto.detailview.VariablesRequestDto;
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
import static org.camunda.operate.rest.VariableRestService.VARIABLE_URL;
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
  public void testVariablesRequestFailOnTwoNullParameters() throws Exception {
    VariablesRequestDto variablesRequest = new VariablesRequestDto(null, null);
    MockHttpServletRequestBuilder request = post(VARIABLE_URL)
      .content(mockMvcTestRule.json(variablesRequest))
      .contentType(mockMvcTestRule.getContentType());
    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isBadRequest())
      .andReturn();

    assertThat(mvcResult.getResolvedException().getMessage()).isEqualTo("WorkflowInstanceId and ActivityInstanceId must be provided in the request.");
  }

  @Test
  public void testVariablesRequestFailOnEmptyWorkflowInstanceId() throws Exception {
    VariablesRequestDto variablesRequest = new VariablesRequestDto(null, "id");
    MockHttpServletRequestBuilder request = post(VARIABLE_URL)
      .content(mockMvcTestRule.json(variablesRequest))
      .contentType(mockMvcTestRule.getContentType());
    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isBadRequest())
      .andReturn();

    assertThat(mvcResult.getResolvedException().getMessage()).isEqualTo("WorkflowInstanceId and ActivityInstanceId must be provided in the request.");
  }

  @Test
  public void testVariablesRequestFailOnEmptyScopeId() throws Exception {
    VariablesRequestDto variablesRequest = new VariablesRequestDto("id", null);
    MockHttpServletRequestBuilder request = post(VARIABLE_URL)
      .content(mockMvcTestRule.json(variablesRequest))
      .contentType(mockMvcTestRule.getContentType());
    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isBadRequest())
      .andReturn();

    assertThat(mvcResult.getResolvedException().getMessage()).isEqualTo("WorkflowInstanceId and ActivityInstanceId must be provided in the request.");
  }

  private void assertVariable(List<VariableEntity> variables, String name, String value) {
    assertThat(variables).filteredOn(v -> v.getName().equals(name)).hasSize(1)
      .allMatch(v -> v.getValue().equals(value));
  }

  protected List<VariableEntity> getVariables(String workflowInstanceId) throws Exception {
    VariablesRequestDto variablesRequest = new VariablesRequestDto(workflowInstanceId, workflowInstanceId);
    MockHttpServletRequestBuilder request = post(VARIABLE_URL)
      .content(mockMvcTestRule.json(variablesRequest))
      .contentType(mockMvcTestRule.getContentType());
    MvcResult mvcResult = mockMvc
      .perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();
    return mockMvcTestRule.listFromResponse(mvcResult, VariableEntity.class);
  }
  protected List<VariableEntity> getVariables(String workflowInstanceId, String activityId) throws Exception {
    final List<ActivityInstanceForDetailViewEntity> allActivityInstances = detailViewReader.getAllActivityInstances(workflowInstanceId);
    final String task1Id = findActivityInstanceId(allActivityInstances, activityId);
    VariablesRequestDto variablesRequest = new VariablesRequestDto(workflowInstanceId, task1Id);
    MockHttpServletRequestBuilder request = post(VARIABLE_URL)
      .content(mockMvcTestRule.json(variablesRequest))
      .contentType(mockMvcTestRule.getContentType());
    MvcResult mvcResult = mockMvc
      .perform(request)
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
