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
package org.camunda.operate.es;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.camunda.operate.entities.ActivityInstanceEntity;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.ActivityType;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.rest.dto.ActivityStatisticsDto;
import org.camunda.operate.rest.dto.WorkflowInstanceQueryDto;
import org.camunda.operate.rest.dto.WorkflowInstanceRequestDto;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.MockMvcTestRule;
import org.camunda.operate.util.OperateIntegrationTest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.util.TestUtil.createActivityInstance;
import static org.camunda.operate.util.TestUtil.createIncident;
import static org.camunda.operate.util.TestUtil.createWorkflowInstance;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests Elasticsearch query for workflow statistics.
 */
public class WorkflowStatisticsIT extends OperateIntegrationTest {

  private static final String QUERY_WORKFLOW_STATISTICS_URL = "/api/workflow-instances/statistics";

  private Random random = new Random();

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Rule
  public MockMvcTestRule mockMvcTestRule = new MockMvcTestRule();

  private MockMvc mockMvc;

  @Before
  public void starting() {
    this.mockMvc = mockMvcTestRule.getMockMvc();
  }

  @Test
  public void testOneWorkflowStatistics() throws Exception {
    String workflowId = "demoProcess";

    createData(workflowId);

    getStatisticsAndAssert(createGetAllWorkflowInstancesQuery(workflowId));
  }

  @Test
  public void testStatisticsWithQuery() throws Exception {
    String workflowId = "demoProcess";

    createData(workflowId);

    final WorkflowInstanceRequestDto query = createGetAllWorkflowInstancesQuery(workflowId);
    query.getQueries().get(0).setActivityId("taskA");

    final List<ActivityStatisticsDto> activityStatisticsDtos = getActivityStatistics(query);
    assertThat(activityStatisticsDtos).hasSize(1);
    assertThat(activityStatisticsDtos).filteredOn(ai -> ai.getActivityId().equals("taskA")).allMatch(ai->
      ai.getActive().equals(2L) && ai.getCanceled().equals(0L) && ai.getCompleted().equals(0L) && ai.getIncidents().equals(0L)
    );
  }

  @Test
  public void testFailStatisticsWithNoWorkflowId() throws Exception {
    String workflowId = "demoProcess";

    createData(workflowId);

    final WorkflowInstanceRequestDto query = createGetAllWorkflowInstancesQuery(workflowId);
    query.getQueries().get(0).setWorkflowIds(new ArrayList<>());

    MockHttpServletRequestBuilder request = post(QUERY_WORKFLOW_STATISTICS_URL)
      .content(mockMvcTestRule.json(query))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isBadRequest())
      .andReturn();

    assertThat(mvcResult.getResolvedException().getMessage()).isEqualTo("Exactly one workflowId must be specified in the request.");
  }

  @Test
  public void testFailStatisticsWithMoreThanOneWorkflowId() throws Exception {
    String workflowId = "demoProcess";

    createData(workflowId);

    final WorkflowInstanceRequestDto query = createGetAllWorkflowInstancesQuery(workflowId);
    query.getQueries().get(0).setWorkflowIds(Arrays.asList(workflowId, "otherWorkflowId"));

    MockHttpServletRequestBuilder request = post(QUERY_WORKFLOW_STATISTICS_URL)
      .content(mockMvcTestRule.json(query))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isBadRequest())
      .andReturn();

    assertThat(mvcResult.getResolvedException().getMessage()).isEqualTo("Exactly one workflowId must be specified in the request.");
  }

  @Test
  public void testFailStatisticsWithNoQuery() throws Exception {
    String workflowId = "demoProcess";

    createData(workflowId);

    final WorkflowInstanceRequestDto query = new WorkflowInstanceRequestDto();

    MockHttpServletRequestBuilder request = post(QUERY_WORKFLOW_STATISTICS_URL)
      .content(mockMvcTestRule.json(query))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isBadRequest())
      .andReturn();

    assertThat(mvcResult.getResolvedException().getMessage()).isEqualTo("Exactly one query must be specified in the request.");
  }

  @Test
  public void testFailStatisticsWithMoreThanOneQuery() throws Exception {
    String workflowId = "demoProcess";

    createData(workflowId);

    final WorkflowInstanceRequestDto query = createGetAllWorkflowInstancesQuery(workflowId);
    query.getQueries().add(new WorkflowInstanceQueryDto());


    MockHttpServletRequestBuilder request = post(QUERY_WORKFLOW_STATISTICS_URL)
      .content(mockMvcTestRule.json(query))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isBadRequest())
      .andReturn();

    assertThat(mvcResult.getResolvedException().getMessage()).isEqualTo("Exactly one query must be specified in the request.");
  }

  private WorkflowInstanceRequestDto createGetAllWorkflowInstancesQuery(String workflowId) {
    WorkflowInstanceQueryDto q = new WorkflowInstanceQueryDto();
    q.setRunning(true);
    q.setActive(true);
    q.setIncidents(true);
    q.setFinished(true);
    q.setCompleted(true);
    q.setCanceled(true);
    q.setWorkflowIds(Arrays.asList(workflowId));
    WorkflowInstanceRequestDto request = new WorkflowInstanceRequestDto();
    request.getQueries().add(q);
    return request;
  }

  private void getStatisticsAndAssert(WorkflowInstanceRequestDto query) throws Exception {
    final List<ActivityStatisticsDto> activityStatisticsDtos = getActivityStatistics(query);

    assertThat(activityStatisticsDtos).hasSize(5);
    assertThat(activityStatisticsDtos).filteredOn(ai -> ai.getActivityId().equals("taskA")).allMatch(ai->
      ai.getActive().equals(2L) && ai.getCanceled().equals(0L) && ai.getCompleted().equals(0L) && ai.getIncidents().equals(0L)
    );
    assertThat(activityStatisticsDtos).filteredOn(ai -> ai.getActivityId().equals("taskC")).allMatch(ai->
      ai.getActive().equals(0L) && ai.getCanceled().equals(2L) && ai.getCompleted().equals(0L) && ai.getIncidents().equals(2L)
    );
    assertThat(activityStatisticsDtos).filteredOn(ai -> ai.getActivityId().equals("taskD")).allMatch(ai->
      ai.getActive().equals(0L) && ai.getCanceled().equals(1L) && ai.getCompleted().equals(0L) && ai.getIncidents().equals(0L)
    );
    assertThat(activityStatisticsDtos).filteredOn(ai -> ai.getActivityId().equals("taskE")).allMatch(ai->
      ai.getActive().equals(1L) && ai.getCanceled().equals(0L) && ai.getCompleted().equals(0L) && ai.getIncidents().equals(1L)
    );
    assertThat(activityStatisticsDtos).filteredOn(ai -> ai.getActivityId().equals("end")).allMatch(ai->
      ai.getActive().equals(0L) && ai.getCanceled().equals(0L) && ai.getCompleted().equals(2L) && ai.getIncidents().equals(0L)
    );
  }

  private List<ActivityStatisticsDto> getActivityStatistics(WorkflowInstanceRequestDto query) throws Exception {
    MockHttpServletRequestBuilder request = post(QUERY_WORKFLOW_STATISTICS_URL)
      .content(mockMvcTestRule.json(query))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    return mockMvcTestRule.listFromResponse(mvcResult, ActivityStatisticsDto.class);
  }

  @Test
  public void testTwoWorkflowsStatistics() throws Exception {
    String workflowId1 = "demoProcess";
    String workflowId2 = "sampleProcess";

    createData(workflowId1);
    createData(workflowId2);

    getStatisticsAndAssert(createGetAllWorkflowInstancesQuery(workflowId1));
    getStatisticsAndAssert(createGetAllWorkflowInstancesQuery(workflowId2));
  }

  /**
   * start
   * taskA  - 2 active
   * taskB
   * taskC  -           - 2 canceled  - 2 with incident
   * taskD  -           - 1 canceled
   * taskE  - 1 active  -             - 1 with incident
   * end    -           -             -                   - 2 finished
   */
  private void createData(String workflowId) {

    List<WorkflowInstanceEntity> instances = new ArrayList<>();

    WorkflowInstanceEntity inst = createWorkflowInstance(WorkflowInstanceState.ACTIVE, workflowId);
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "start", ActivityType.START_EVENT));
    inst.getActivities().add(createActivityInstance(ActivityState.ACTIVE, "taskA", null));
    inst.getActivities().add(createActivityInstance(ActivityState.ACTIVE, "taskA", null));    //duplicated on purpose, to be sure, that we sount workflow instances, but not activity inctanses
    instances.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.ACTIVE, workflowId);
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "start", ActivityType.START_EVENT));
    inst.getActivities().add(createActivityInstance(ActivityState.ACTIVE, "taskA", null));
    instances.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.CANCELED, workflowId);
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "start", ActivityType.START_EVENT));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "taskA", null));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "taskB", null));
    inst.getActivities().add(createActivityInstance(ActivityState.TERMINATED, "taskC", null));
    instances.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.CANCELED, workflowId);
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "start", ActivityType.START_EVENT));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "taskA", null));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "taskB", null));
    inst.getActivities().add(createActivityInstance(ActivityState.TERMINATED, "taskC", null));
    instances.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.ACTIVE, workflowId);
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "start", ActivityType.START_EVENT));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "taskA", null));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "taskB", null));
    ActivityInstanceEntity task = createActivityInstance(ActivityState.INCIDENT, "taskC", null);
    inst.getActivities().add(task);
    inst.getIncidents().add(createIncident(IncidentState.ACTIVE, task.getActivityId(), task.getId()));
    inst.getIncidents().add(createIncident(IncidentState.RESOLVED, task.getActivityId(), task.getId()));
    instances.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.ACTIVE, workflowId);
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "start", ActivityType.START_EVENT));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "taskA", null));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "taskB", null));
    task = createActivityInstance(ActivityState.INCIDENT, "taskC", null);
    inst.getActivities().add(task);
    inst.getIncidents().add(createIncident(IncidentState.ACTIVE, task.getActivityId(), task.getId()));
    inst.getIncidents().add(createIncident(IncidentState.RESOLVED, task.getActivityId(), task.getId()));
    instances.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.CANCELED, workflowId);
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "start", ActivityType.START_EVENT));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "taskA", null));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "taskB", null));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "taskC", null));
    inst.getActivities().add(createActivityInstance(ActivityState.TERMINATED, "taskD", null));
    instances.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.ACTIVE, workflowId);
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "start", ActivityType.START_EVENT));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "taskA", null));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "taskB", null));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "taskC", null));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "taskD", null));
    inst.getActivities().add(createActivityInstance(ActivityState.ACTIVE, "taskE", null));
    instances.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.ACTIVE, workflowId);
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "start", ActivityType.START_EVENT));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "taskA", null));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "taskB", null));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "taskC", null));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "taskD", null));
    task = createActivityInstance(ActivityState.INCIDENT, "taskE", null);
    inst.getActivities().add(task);
    inst.getIncidents().add(createIncident(IncidentState.ACTIVE, task.getActivityId(), task.getId()));
    instances.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.COMPLETED, workflowId);
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "start", ActivityType.START_EVENT));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "taskA", null));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "taskB", null));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "taskC", null));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "taskD", null));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "taskE", null));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "end", ActivityType.END_EVENT));
    instances.add(inst);

    inst = createWorkflowInstance(WorkflowInstanceState.COMPLETED, workflowId);
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "start", ActivityType.START_EVENT));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "taskA", null));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "taskB", null));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "taskC", null));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "taskD", null));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "taskE", null));
    inst.getActivities().add(createActivityInstance(ActivityState.COMPLETED, "end", ActivityType.END_EVENT));
    instances.add(inst);

    elasticsearchTestRule.persist(instances.toArray(new WorkflowInstanceEntity[instances.size()]));

  }

}
