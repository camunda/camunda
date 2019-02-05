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
import org.camunda.operate.rest.dto.detailview.ActivityInstanceTreeDto;
import org.camunda.operate.rest.dto.detailview.ActivityInstanceTreeRequestDto;
import org.camunda.operate.rest.dto.detailview.DetailViewActivityInstanceDto;
import org.camunda.operate.util.IdTestUtil;
import org.camunda.operate.util.MockMvcTestRule;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.ZeebeTestUtil;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.rest.ActivityInstanceRestService.ACTIVITY_INSTANCE_URL;
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
