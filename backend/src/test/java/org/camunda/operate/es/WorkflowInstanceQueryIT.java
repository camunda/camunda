package org.camunda.operate.es;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import org.camunda.operate.entities.ActivityInstanceEntity;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.ActivityType;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.entities.variables.BooleanVariableEntity;
import org.camunda.operate.entities.variables.DoubleVariableEntity;
import org.camunda.operate.entities.variables.LongVariableEntity;
import org.camunda.operate.entities.variables.StringVariableEntity;
import org.camunda.operate.es.types.WorkflowInstanceType;
import org.camunda.operate.rest.dto.IncidentDto;
import org.camunda.operate.rest.dto.SortingDto;
import org.camunda.operate.rest.dto.VariablesQueryDto;
import org.camunda.operate.rest.dto.WorkflowInstanceDto;
import org.camunda.operate.rest.dto.WorkflowInstanceQueryDto;
import org.camunda.operate.rest.dto.WorkflowInstanceRequestDto;
import org.camunda.operate.rest.dto.WorkflowInstanceResponseDto;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.MockMvcTestRule;
import org.camunda.operate.util.OperateIntegrationTest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.fasterxml.jackson.core.type.TypeReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.rest.WorkflowInstanceRestService.WORKFLOW_INSTANCE_URL;
import static org.camunda.operate.util.TestUtil.createActivityInstance;
import static org.camunda.operate.util.TestUtil.createIncident;
import static org.camunda.operate.util.TestUtil.createSequenceFlow;
import static org.camunda.operate.util.TestUtil.createWorkflowInstance;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests Elasticsearch queries for workflow instances.
 */
public class WorkflowInstanceQueryIT extends OperateIntegrationTest {

  private static final String QUERY_INSTANCES_URL = WORKFLOW_INSTANCE_URL;
  private static final String GET_INSTANCE_URL = WORKFLOW_INSTANCE_URL + "/%s";

  private Random random = new Random();

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Rule
  public MockMvcTestRule mockMvcTestRule = new MockMvcTestRule();

  private WorkflowInstanceEntity instanceWithoutIncident;

  private MockMvc mockMvc;

  @Before
  public void starting() {
    this.mockMvc = mockMvcTestRule.getMockMvc();
  }

  @Test
  public void testQueryAllRunning() throws Exception {
    createData();

    //query running instances
    WorkflowInstanceRequestDto workflowInstanceQueryDto = createWorkflowInstanceQuery(q -> {
      q.setRunning(true);
      q.setActive(true);
      q.setIncidents(true);
    });

    MockHttpServletRequestBuilder request = post(query(0, 100)).content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request).andExpect(status().isOk()).andExpect(content().contentType(mockMvcTestRule.getContentType())).andReturn();

    WorkflowInstanceResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() {
    });

    assertThat(response.getWorkflowInstances().size()).isEqualTo(3);
    assertThat(response.getTotalCount()).isEqualTo(3);
    for (WorkflowInstanceDto workflowInstanceDto : response.getWorkflowInstances()) {
      assertThat(workflowInstanceDto.getEndDate()).isNull();
      assertThat(workflowInstanceDto.getState()).isEqualTo(WorkflowInstanceState.ACTIVE);
      assertThat(workflowInstanceDto.getActivities()).isEmpty();
      assertThat(workflowInstanceDto.getSequenceFlows()).isEmpty();
    }
  }

  @Test
  public void testQueryByStartAndEndDate() throws Exception {
    //given
    final OffsetDateTime date1 = OffsetDateTime.of(2018, 1, 1, 15, 30, 30, 156, OffsetDateTime.now().getOffset());      //January 1, 2018
    final OffsetDateTime date2 = OffsetDateTime.of(2018, 2, 1, 12, 0, 30, 457, OffsetDateTime.now().getOffset());      //February 1, 2018
    final OffsetDateTime date3 = OffsetDateTime.of(2018, 3, 1, 17, 15, 14, 235, OffsetDateTime.now().getOffset());      //March 1, 2018
    final OffsetDateTime date4 = OffsetDateTime.of(2018, 4, 1, 2, 12, 0, 0, OffsetDateTime.now().getOffset());          //April 1, 2018
    final OffsetDateTime date5 = OffsetDateTime.of(2018, 5, 1, 23, 30, 15, 666, OffsetDateTime.now().getOffset());      //May 1, 2018
    final WorkflowInstanceEntity workflowInstance1 = createWorkflowInstance(date1, date5);
    final WorkflowInstanceEntity workflowInstance2 = createWorkflowInstance(date2, date4);
    final WorkflowInstanceEntity workflowInstance3 = createWorkflowInstance(date3, null);
    elasticsearchTestRule.persist(workflowInstance1, workflowInstance2, workflowInstance3);

    //when
    WorkflowInstanceRequestDto query = createGetAllWorkflowInstancesQuery(q -> {
      q.setStartDateAfter(date1.minus(1, ChronoUnit.DAYS));
      q.setStartDateBefore(date3);
    });
    //then
    requestAndAssertIds(query, "TEST CASE #1", workflowInstance1.getId(), workflowInstance2.getId());

    //test inclusion for startDateAfter and exclusion for startDateBefore
    //when
    query = createGetAllWorkflowInstancesQuery(q -> {
      q.setStartDateAfter(date1);
      q.setStartDateBefore(date3);
    });
    //then
    requestAndAssertIds(query, "TEST CASE #2", workflowInstance1.getId(), workflowInstance2.getId());

    //when
    query = createGetAllWorkflowInstancesQuery(q -> {
      q.setStartDateAfter(date1.plus(1, ChronoUnit.MILLIS));
      q.setStartDateBefore(date3.plus(1, ChronoUnit.MILLIS));
    });
    //then
    requestAndAssertIds(query, "TEST CASE #3", workflowInstance2.getId(), workflowInstance3.getId());

    //test combination of start date and end date
    //when
    query = createGetAllWorkflowInstancesQuery(q -> {
      q.setStartDateAfter(date2.minus(1, ChronoUnit.DAYS));
      q.setStartDateBefore(date3.plus(1, ChronoUnit.DAYS));
      q.setEndDateAfter(date4.minus(1, ChronoUnit.DAYS));
      q.setEndDateBefore(date4.plus(1, ChronoUnit.DAYS));
    });
    //then
    requestAndAssertIds(query, "TEST CASE #4", workflowInstance2.getId());

    //test inclusion for endDateAfter and exclusion for endDateBefore
    //when
    query = createGetAllWorkflowInstancesQuery(q -> {
      q.setEndDateAfter(date4);
      q.setEndDateBefore(date5);
    });
    //then
    requestAndAssertIds(query, "TEST CASE #5", workflowInstance2.getId());

    //when
    query = createGetAllWorkflowInstancesQuery(q -> {
      q.setEndDateAfter(date4);
      q.setEndDateBefore(date5.plus(1, ChronoUnit.MILLIS));
    });
    //then
    requestAndAssertIds(query, "TEST CASE #6", workflowInstance1.getId(), workflowInstance2.getId());

  }

  private void requestAndAssertIds(WorkflowInstanceRequestDto query, String testCaseName, String... ids) throws Exception {
    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(query))
      .contentType(mockMvcTestRule.getContentType());
    //then
    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    WorkflowInstanceResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() { });

    assertThat(response.getWorkflowInstances()).as(testCaseName).extracting(WorkflowInstanceType.ID).containsExactlyInAnyOrder(ids);
  }

  @Test
  public void testQueryByErrorMessage() throws Exception {
    final String errorMessage = "No more retries left.";

    //given we have 2 workflow instances: one with active activity with given id, another with completed activity with given id
    final WorkflowInstanceEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);

    final IncidentEntity activeIncidentWithMsg = createIncident(IncidentState.ACTIVE);
    activeIncidentWithMsg.setErrorMessage(errorMessage);
    workflowInstance1.getIncidents().add(activeIncidentWithMsg);

    final IncidentEntity resolvedIncidentWithoutMsg = createIncident(IncidentState.RESOLVED);
    resolvedIncidentWithoutMsg.setErrorMessage("other error message");
    workflowInstance1.getIncidents().add(resolvedIncidentWithoutMsg);

    final WorkflowInstanceEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);

    final IncidentEntity activeIncidentWithoutMsg = createIncident(IncidentState.ACTIVE);
    activeIncidentWithoutMsg.setErrorMessage("other error message");
    workflowInstance2.getIncidents().add(activeIncidentWithoutMsg);

    final IncidentEntity resolvedIncidentWithMsg = createIncident(IncidentState.RESOLVED);
    resolvedIncidentWithMsg.setErrorMessage(errorMessage);
    workflowInstance2.getIncidents().add(resolvedIncidentWithMsg);

    elasticsearchTestRule.persist(workflowInstance1, workflowInstance2);

    //given
    WorkflowInstanceRequestDto query = createGetAllWorkflowInstancesQuery(q -> q.setErrorMessage(errorMessage));

    MockHttpServletRequestBuilder request = post(query(0, 100))
        .content(mockMvcTestRule.json(query))
        .contentType(mockMvcTestRule.getContentType());
    //when
    MvcResult mvcResult = mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentType(mockMvcTestRule.getContentType()))
        .andReturn();

    WorkflowInstanceResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() { });

    //then
    assertThat(response.getWorkflowInstances().size()).isEqualTo(1);

    assertThat(response.getWorkflowInstances().get(0).getIncidents())
      .filteredOn(incident -> incident.getState().equals(IncidentState.ACTIVE))
      .extracting(WorkflowInstanceType.ERROR_MSG)
      .containsExactly(errorMessage);

  }

  @Test
  public void testQueryByActiveActivityId() throws Exception {

    final String activityId = "taskA";

    final WorkflowInstanceEntity[] data = createDataForActiveActivityIdQuery(activityId);
    elasticsearchTestRule.persist(data);

    //when
    WorkflowInstanceRequestDto query = createWorkflowInstanceQuery(q -> {
      q.setRunning(true);
      q.setActive(true);
      q.setActivityId(activityId);
    });

    MockHttpServletRequestBuilder request = post(query(0, 100))
        .content(mockMvcTestRule.json(query))
        .contentType(mockMvcTestRule.getContentType());
    MvcResult mvcResult = mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentType(mockMvcTestRule.getContentType()))
        .andReturn();

    WorkflowInstanceResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() { });

    //then
    assertThat(response.getWorkflowInstances().size()).isEqualTo(1);

    assertThat(response.getWorkflowInstances().get(0).getId())
      .isEqualTo(data[0].getId());

  }

  /**
   * 1st entity must be selected
   */
  private WorkflowInstanceEntity[] createDataForActiveActivityIdQuery(String activityId) {
    List<WorkflowInstanceEntity> entities = new ArrayList<>();

    //wi 1: active with active activity with given id
    final WorkflowInstanceEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);

    final ActivityInstanceEntity activeWithIdActivityInstance = createActivityInstance(ActivityState.ACTIVE);
    activeWithIdActivityInstance.setActivityId(activityId);
    workflowInstance1.getActivities().add(activeWithIdActivityInstance);

    final ActivityInstanceEntity completedWithoutIdActivityInstance = createActivityInstance(ActivityState.COMPLETED);
    completedWithoutIdActivityInstance.setActivityId("otherActivityId");
    workflowInstance1.getActivities().add(completedWithoutIdActivityInstance);

    entities.add(workflowInstance1);

    //wi 2: active with active activity with another id
    final WorkflowInstanceEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);

    final ActivityInstanceEntity activeWithoutIdActivityInstance = createActivityInstance(ActivityState.ACTIVE);
    activeWithoutIdActivityInstance.setActivityId("otherActivityId");
    workflowInstance2.getActivities().add(activeWithoutIdActivityInstance);

    final ActivityInstanceEntity completedWithIdActivityInstance = createActivityInstance(ActivityState.INCIDENT);
    completedWithIdActivityInstance.setActivityId(activityId);
    workflowInstance2.getActivities().add(completedWithIdActivityInstance);

    entities.add(workflowInstance2);
    return entities.toArray(new WorkflowInstanceEntity[entities.size()]);
  }

  @Test
  public void testQueryByIncidentActivityId() throws Exception {
    final String activityId = "taskA";

    final WorkflowInstanceEntity[] data = createDataForIncidentActivityIdQuery(activityId);
    elasticsearchTestRule.persist(data);

    //when
    WorkflowInstanceRequestDto query = createWorkflowInstanceQuery(q -> {
      q.setRunning(true);
      q.setIncidents(true);
      q.setActivityId(activityId);
    });

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(query))
      .contentType(mockMvcTestRule.getContentType());
    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    WorkflowInstanceResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() { });

    //then
    assertThat(response.getWorkflowInstances().size()).isEqualTo(1);

    assertThat(response.getWorkflowInstances().get(0).getId())
      .isEqualTo(data[0].getId());

  }

  /**
   * 1st entity must be selected
   */
  private WorkflowInstanceEntity[] createDataForIncidentActivityIdQuery(String activityId) {
    List<WorkflowInstanceEntity> entities = new ArrayList<>();

    //wi1: active with activity in INCIDENT state with given id
    final WorkflowInstanceEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);

    final ActivityInstanceEntity incidentWithIdActivityInstance = createActivityInstance(ActivityState.INCIDENT);
    incidentWithIdActivityInstance.setActivityId(activityId);
    workflowInstance1.getActivities().add(incidentWithIdActivityInstance);
    workflowInstance1.getIncidents().add(createIncident(IncidentState.ACTIVE));

    final ActivityInstanceEntity completedWithoutIdActivityInstance = createActivityInstance(ActivityState.COMPLETED);
    completedWithoutIdActivityInstance.setActivityId("otherActivityId");
    workflowInstance1.getActivities().add(completedWithoutIdActivityInstance);

    entities.add(workflowInstance1);

    //wi2: active with activity in INCIDENT state with another id
    final WorkflowInstanceEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);

    final ActivityInstanceEntity incidentWithoutIdActivityInstance = createActivityInstance(ActivityState.INCIDENT);
    incidentWithoutIdActivityInstance.setActivityId("otherActivityId");
    workflowInstance2.getActivities().add(incidentWithoutIdActivityInstance);
    workflowInstance2.getIncidents().add(createIncident(IncidentState.ACTIVE));

    final ActivityInstanceEntity completedWithIdActivityInstance = createActivityInstance(ActivityState.COMPLETED);
    completedWithIdActivityInstance.setActivityId(activityId);
    workflowInstance2.getActivities().add(completedWithIdActivityInstance);

    entities.add(workflowInstance2);

    //wi3: active with activity in ACTIVE state with given id
    final WorkflowInstanceEntity workflowInstance3 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);

    final ActivityInstanceEntity activeWithIdActivityInstance = createActivityInstance(ActivityState.ACTIVE);
    activeWithIdActivityInstance.setActivityId(activityId);
    workflowInstance3.getActivities().add(activeWithIdActivityInstance);

    final ActivityInstanceEntity completedWithoutIdActivityInstance2 = createActivityInstance(ActivityState.COMPLETED);
    completedWithoutIdActivityInstance2.setActivityId("otherActivityId");
    workflowInstance3.getActivities().add(completedWithoutIdActivityInstance2);

    entities.add(workflowInstance3);

    return entities.toArray(new WorkflowInstanceEntity[entities.size()]);
  }

  @Test
  public void testQueryByTerminatedActivityId() throws Exception {
    final String activityId = "taskA";

    final WorkflowInstanceEntity[] data = createDataForTerminatedActivityIdQuery(activityId);
    elasticsearchTestRule.persist(data);

    //when
    WorkflowInstanceRequestDto query = createWorkflowInstanceQuery(q -> {
      q.setFinished(true);
      q.setCanceled(true);
      q.setActivityId(activityId);
    });

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(query))
      .contentType(mockMvcTestRule.getContentType());
    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    WorkflowInstanceResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() { });

    //then
    assertThat(response.getWorkflowInstances().size()).isEqualTo(1);

    assertThat(response.getWorkflowInstances().get(0).getId())
      .isEqualTo(data[0].getId());

  }

  /**
   * 1st entity must be selected
   */
  private WorkflowInstanceEntity[] createDataForTerminatedActivityIdQuery(String activityId) {
    List<WorkflowInstanceEntity> entities = new ArrayList<>();

    //wi1: canceled with TERMINATED activity with given id
    final WorkflowInstanceEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.CANCELED);

    final ActivityInstanceEntity terminatedWithIdActivityInstance = createActivityInstance(ActivityState.TERMINATED);
    terminatedWithIdActivityInstance.setActivityId(activityId);
    workflowInstance1.getActivities().add(terminatedWithIdActivityInstance);

    final ActivityInstanceEntity completedWithoutIdActivityInstance = createActivityInstance(ActivityState.COMPLETED);
    completedWithoutIdActivityInstance.setActivityId("otherActivityId");
    workflowInstance1.getActivities().add(completedWithoutIdActivityInstance);

    entities.add(workflowInstance1);

    //wi2: canceled with TERMINATED activity with another id
    final WorkflowInstanceEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.CANCELED);

    final ActivityInstanceEntity terminatedWithoutIdActivityInstance = createActivityInstance(ActivityState.TERMINATED);
    terminatedWithoutIdActivityInstance.setActivityId("otherActivityId");
    workflowInstance2.getActivities().add(terminatedWithoutIdActivityInstance);

    final ActivityInstanceEntity completedWithIdActivityInstance = createActivityInstance(ActivityState.COMPLETED);
    completedWithIdActivityInstance.setActivityId(activityId);
    workflowInstance2.getActivities().add(completedWithIdActivityInstance);

    entities.add(workflowInstance2);

    //wi3: active with ACTIVE activity with given id
    final WorkflowInstanceEntity workflowInstance3 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);

    final ActivityInstanceEntity activeWithIdActivityInstance = createActivityInstance(ActivityState.TERMINATED);
    activeWithIdActivityInstance.setActivityId(activityId);
    workflowInstance3.getActivities().add(activeWithIdActivityInstance);

    final ActivityInstanceEntity completedWithoutIdActivityInstance2 = createActivityInstance(ActivityState.COMPLETED);
    completedWithoutIdActivityInstance2.setActivityId("otherActivityId");
    workflowInstance3.getActivities().add(completedWithoutIdActivityInstance2);

    entities.add(workflowInstance3);

    return entities.toArray(new WorkflowInstanceEntity[entities.size()]);
  }

  @Test
  public void testQueryByCombinedStateActivityId() throws Exception {
    final String activityId = "taskA";

    List<String> selectedIds = new ArrayList<>();

    WorkflowInstanceEntity[] data = createDataForActiveActivityIdQuery(activityId);
    selectedIds.add(data[0].getId());
    elasticsearchTestRule.persist(data);

    data = createDataForIncidentActivityIdQuery(activityId);
    selectedIds.add(data[0].getId());
    selectedIds.add(data[2].getId());
    elasticsearchTestRule.persist(data);

    data = createDataForTerminatedActivityIdQuery(activityId);
    selectedIds.add(data[0].getId());
    elasticsearchTestRule.persist(data);

    //when
    WorkflowInstanceRequestDto query = createWorkflowInstanceQuery(q -> {
      q.setRunning(true);
      q.setIncidents(true);
      q.setActive(true);
      q.setFinished(true);
      q.setCanceled(true);
      q.setActivityId(activityId);
    });

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(query))
      .contentType(mockMvcTestRule.getContentType());
    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    WorkflowInstanceResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() { });

    //then
    assertThat(response.getWorkflowInstances().size()).isEqualTo(selectedIds.size());

    assertThat(response.getWorkflowInstances()).extracting("id").containsExactlyInAnyOrder(selectedIds.toArray());

  }

  @Test
  public void testQueryByCompletedActivityId() throws Exception {
    final String activityId = "endEvent";

    //wi 1: completed with completed end event
    final WorkflowInstanceEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);

    final ActivityInstanceEntity completedEndEventWithIdActivityInstance = createActivityInstance(ActivityState.COMPLETED);
    completedEndEventWithIdActivityInstance.setActivityId(activityId);
    completedEndEventWithIdActivityInstance.setType(ActivityType.END_EVENT);
    workflowInstance1.getActivities().add(completedEndEventWithIdActivityInstance);

    final ActivityInstanceEntity completedWithoutIdActivityInstance = createActivityInstance(ActivityState.COMPLETED);
    completedWithoutIdActivityInstance.setActivityId("otherActivityId");
    workflowInstance1.getActivities().add(completedWithoutIdActivityInstance);

    //wi 2: completed without completed end event
    final WorkflowInstanceEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);

    final ActivityInstanceEntity activeEndEventWithIdActivityInstance = createActivityInstance(ActivityState.ACTIVE);
    activeEndEventWithIdActivityInstance.setActivityId(activityId);
    activeEndEventWithIdActivityInstance.setType(ActivityType.END_EVENT);
    workflowInstance2.getActivities().add(activeEndEventWithIdActivityInstance);

    //wi 3: completed with completed end event (but not of type END_EVENT)
    final WorkflowInstanceEntity workflowInstance3 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);

    final ActivityInstanceEntity completedWithIdActivityInstance = createActivityInstance(ActivityState.COMPLETED);
    completedWithIdActivityInstance.setActivityId(activityId);
    workflowInstance3.getActivities().add(completedWithIdActivityInstance);

    //wi 4: active with completed end event
    final WorkflowInstanceEntity workflowInstance4 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);

    final ActivityInstanceEntity completedEndEventWithIdActivityInstance2 = createActivityInstance(ActivityState.COMPLETED);
    completedEndEventWithIdActivityInstance2.setActivityId(activityId);
    completedEndEventWithIdActivityInstance2.setType(ActivityType.END_EVENT);
    workflowInstance4.getActivities().add(completedEndEventWithIdActivityInstance2);

    elasticsearchTestRule.persist(workflowInstance1, workflowInstance2, workflowInstance3, workflowInstance4);

    //when
    WorkflowInstanceRequestDto query = createWorkflowInstanceQuery(q -> {
      q.setFinished(true);
      q.setCompleted(true);
      q.setActivityId(activityId);
    });

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(query))
      .contentType(mockMvcTestRule.getContentType());
    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    WorkflowInstanceResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() { });

    //then
    assertThat(response.getWorkflowInstances().size()).isEqualTo(1);

    assertThat(response.getWorkflowInstances().get(0).getId())
      .isEqualTo(workflowInstance1.getId());

  }


  @Test
  public void testQueryByWorkflowInstanceIds() throws Exception {
    //given
    final WorkflowInstanceEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    final WorkflowInstanceEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.CANCELED);
    final WorkflowInstanceEntity workflowInstance3 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);
    elasticsearchTestRule.persist(workflowInstance1, workflowInstance2, workflowInstance3);

    WorkflowInstanceRequestDto query = createGetAllWorkflowInstancesQuery(q ->
      q.setIds(Arrays.asList(workflowInstance1.getId(), workflowInstance2.getId()))
    );

    //when
    MockHttpServletRequestBuilder request = post(query(0, 100))
        .content(mockMvcTestRule.json(query))
        .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentType(mockMvcTestRule.getContentType()))
        .andReturn();

    //then
    WorkflowInstanceResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() { });

    assertThat(response.getWorkflowInstances()).hasSize(2);

    assertThat(response.getWorkflowInstances()).extracting(WorkflowInstanceType.ID).containsExactlyInAnyOrder(workflowInstance1.getId(), workflowInstance2.getId());
  }

  @Test
  public void testQueryByVariableValues() throws Exception {
    //given
    final String strName = "str1";
    final String stringValue = "strValue1";
    final String nullName = "null1";
    final String intName = "int1";
    final long intValue = 111L;
    final String longName = "long1";
    final long longValue = Long.valueOf(Integer.MAX_VALUE) + 1L;
    final String boolName = "bool1";
    final boolean boolValue = true;
    final String floatName = "float1";
    final float floatValue = .5f;
    final String doubleName = "double1";
    final double doubleValue = Double.valueOf(Float.MAX_VALUE) + 1;

    final WorkflowInstanceEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    addVariableEntity(workflowInstance1, strName, stringValue);
    addVariableEntity(workflowInstance1, nullName, (String)null);
    addVariableEntity(workflowInstance1, intName, intValue);
    addVariableEntity(workflowInstance1, longName, longValue);
    addVariableEntity(workflowInstance1, boolName, boolValue);
    addVariableEntity(workflowInstance1, floatName, (double).1f);
    addVariableEntity(workflowInstance1, doubleName, doubleValue);

    final WorkflowInstanceEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.CANCELED);
    addVariableEntity(workflowInstance2, strName, "strValue2");
    addVariableEntity(workflowInstance2, intName, 222L);
    addVariableEntity(workflowInstance2, longName, longValue);
    addVariableEntity(workflowInstance2, boolName, false);
    addVariableEntity(workflowInstance2, floatName, (double)floatValue);
    addVariableEntity(workflowInstance2, doubleName, .555);

    final WorkflowInstanceEntity workflowInstance3 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);
    addVariableEntity(workflowInstance3, strName, stringValue);
    addVariableEntity(workflowInstance3, intName, intValue);
    addVariableEntity(workflowInstance3, longName, Long.valueOf(Integer.MAX_VALUE) + 2L);
    addVariableEntity(workflowInstance3, boolName, boolValue);
    addVariableEntity(workflowInstance3, floatName, (double)floatValue);
    addVariableEntity(workflowInstance3, doubleName, doubleValue);

    elasticsearchTestRule.persist(workflowInstance1, workflowInstance2, workflowInstance3);

    //when
    WorkflowInstanceRequestDto query = createGetAllWorkflowInstancesQuery(q ->
      q.setVariablesQuery(new VariablesQueryDto(strName, stringValue))
    );
    //then
    requestAndAssertIds(query, "TEST CASE #1", workflowInstance1.getId(), workflowInstance3.getId());

    //when
    query = createGetAllWorkflowInstancesQuery(q ->
      q.setVariablesQuery(new VariablesQueryDto(intName, intValue))
    );
    //then
    requestAndAssertIds(query, "TEST CASE #2", workflowInstance1.getId(), workflowInstance3.getId());

    //when
    query = createGetAllWorkflowInstancesQuery(q ->
      q.setVariablesQuery(new VariablesQueryDto(longName, longValue))
    );
    //then
    requestAndAssertIds(query, "TEST CASE #3", workflowInstance1.getId(), workflowInstance2.getId());

    //when
    query = createGetAllWorkflowInstancesQuery(q ->
      q.setVariablesQuery(new VariablesQueryDto(boolName, boolValue))
    );
    //then
    requestAndAssertIds(query, "TEST CASE #4", workflowInstance1.getId(), workflowInstance3.getId());

    //when
    query = createGetAllWorkflowInstancesQuery(q ->
      q.setVariablesQuery(new VariablesQueryDto(floatName, floatValue))
    );
    //then
    requestAndAssertIds(query, "TEST CASE #5", workflowInstance2.getId(), workflowInstance3.getId());

    //when
    query = createGetAllWorkflowInstancesQuery(q ->
      q.setVariablesQuery(new VariablesQueryDto(doubleName, doubleValue))
    );
    //then
    requestAndAssertIds(query, "TEST CASE #6", workflowInstance1.getId(), workflowInstance3.getId());

    //when
    query = createGetAllWorkflowInstancesQuery(q ->
      q.setVariablesQuery(new VariablesQueryDto(nullName, null))
    );
    //then
    requestAndAssertIds(query, "TEST CASE #7", workflowInstance1.getId());
  }

  @Test
  public void testQueryByVariableValuesFailOnNullVariableName() throws Exception {
    //when
    WorkflowInstanceRequestDto query = createGetAllWorkflowInstancesQuery(q ->
      q.setVariablesQuery(new VariablesQueryDto(null, "someValue"))
    );
    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(query))
      .contentType(mockMvcTestRule.getContentType());

    //then
    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isBadRequest())
      .andReturn();

    assertThat(mvcResult.getResolvedException().getMessage()).contains("Variables query must provide not-null variable name.");

  }

  @Test
  public void testQueryByExcludeIds() throws Exception {
    //given
    final WorkflowInstanceEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    final WorkflowInstanceEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.CANCELED);
    final WorkflowInstanceEntity workflowInstance3 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);
    final WorkflowInstanceEntity workflowInstance4 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);
    elasticsearchTestRule.persist(workflowInstance1, workflowInstance2, workflowInstance3, workflowInstance4);

    WorkflowInstanceRequestDto query = createGetAllWorkflowInstancesQuery(q ->
      q.setExcludeIds(Arrays.asList(workflowInstance1.getId(), workflowInstance3.getId()))
    );

    //when
    MockHttpServletRequestBuilder request = post(query(0, 100))
        .content(mockMvcTestRule.json(query))
        .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentType(mockMvcTestRule.getContentType()))
        .andReturn();

    //then
    WorkflowInstanceResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() { });

    assertThat(response.getWorkflowInstances()).hasSize(2);

    assertThat(response.getWorkflowInstances()).extracting(WorkflowInstanceType.ID).containsExactlyInAnyOrder(workflowInstance2.getId(), workflowInstance4.getId());
  }

  @Test
  public void testQueryByWorkflowIds() throws Exception {
    //given
    String wfId1 = "1";
    String wfId2 = "2";
    String wfId3 = "3";
    final WorkflowInstanceEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    workflowInstance1.setWorkflowId(wfId1);
    final WorkflowInstanceEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.CANCELED);
    workflowInstance2.setWorkflowId(wfId2);
    final WorkflowInstanceEntity workflowInstance3 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);
    final WorkflowInstanceEntity workflowInstance4 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);
    workflowInstance3.setWorkflowId(wfId3);
    workflowInstance4.setWorkflowId(wfId3);

    elasticsearchTestRule.persist(workflowInstance1, workflowInstance2, workflowInstance3, workflowInstance4);

    WorkflowInstanceRequestDto query = createGetAllWorkflowInstancesQuery(q -> q.setWorkflowIds(Arrays.asList(wfId1, wfId3)));

    //when
    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(query))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    //then
    WorkflowInstanceResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() { });

    assertThat(response.getWorkflowInstances()).hasSize(3);

    assertThat(response.getWorkflowInstances()).extracting(WorkflowInstanceType.ID)
      .containsExactlyInAnyOrder(workflowInstance1.getId(), workflowInstance3.getId(), workflowInstance4.getId());
  }

  @Test
  public void testQueryByBpmnProcessIdAndVersion() throws Exception {
    //given
    String bpmnProcessId1 = "pr1";
    int version1 = 1;
    String bpmnProcessId2 = "pr2";
    int version2 = 2;
    final WorkflowInstanceEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    workflowInstance1.setBpmnProcessId(bpmnProcessId1);
    workflowInstance1.setWorkflowVersion(version1);
    final WorkflowInstanceEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.CANCELED);
    workflowInstance2.setBpmnProcessId(bpmnProcessId1);
    workflowInstance2.setWorkflowVersion(version2);
    final WorkflowInstanceEntity workflowInstance3 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);
    workflowInstance3.setBpmnProcessId(bpmnProcessId2);
    workflowInstance3.setWorkflowVersion(version1);

    elasticsearchTestRule.persist(workflowInstance1, workflowInstance2, workflowInstance3);

    WorkflowInstanceRequestDto query = createGetAllWorkflowInstancesQuery(q -> {
      q.setBpmnProcessId(bpmnProcessId1);
      q.setWorkflowVersion(version1);
    });

    //when
    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(query))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    //then
    WorkflowInstanceResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() { });

    assertThat(response.getWorkflowInstances()).hasSize(1);

    assertThat(response.getWorkflowInstances()).extracting(WorkflowInstanceType.ID)
      .containsExactly(workflowInstance1.getId());
  }

  @Test
  public void testQueryByWorkflowVersionFail() throws Exception {
    //when
    WorkflowInstanceRequestDto query = createGetAllWorkflowInstancesQuery(q -> {
      q.setWorkflowVersion(1);
    });
    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(query))
      .contentType(mockMvcTestRule.getContentType());

    //then
    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isBadRequest())
      .andReturn();

    assertThat(mvcResult.getResolvedException().getMessage()).contains("BpmnProcessId must be provided in request, when workflow version is not null");

  }

  @Test
  public void testQueryByBpmnProcessIdAllVersions() throws Exception {
    //given
    String bpmnProcessId1 = "pr1";
    int version1 = 1;
    String bpmnProcessId2 = "pr2";
    int version2 = 2;
    final WorkflowInstanceEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    workflowInstance1.setBpmnProcessId(bpmnProcessId1);
    workflowInstance1.setWorkflowVersion(version1);
    final WorkflowInstanceEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.CANCELED);
    workflowInstance2.setBpmnProcessId(bpmnProcessId1);
    workflowInstance2.setWorkflowVersion(version2);
    final WorkflowInstanceEntity workflowInstance3 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);
    workflowInstance3.setBpmnProcessId(bpmnProcessId2);
    workflowInstance3.setWorkflowVersion(version1);

    elasticsearchTestRule.persist(workflowInstance1, workflowInstance2, workflowInstance3);

    WorkflowInstanceRequestDto query = createGetAllWorkflowInstancesQuery(q -> q.setBpmnProcessId(bpmnProcessId1));

    //when
    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(query))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    //then
    WorkflowInstanceResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() { });

    assertThat(response.getWorkflowInstances()).hasSize(2);

    assertThat(response.getWorkflowInstances()).extracting(WorkflowInstanceType.ID)
      .containsExactlyInAnyOrder(workflowInstance1.getId(), workflowInstance2.getId());
  }

  @Test
  public void testPagination() throws Exception {
    createData();

    //query running instances
    WorkflowInstanceRequestDto workflowInstanceQueryDto = createGetAllWorkflowInstancesQuery();

    //page 1
    MockHttpServletRequestBuilder request = post(query(0, 3))
        .content(mockMvcTestRule.json(workflowInstanceQueryDto))
        .contentType(mockMvcTestRule.getContentType());
    MvcResult mvcResult = mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentType(mockMvcTestRule.getContentType()))
        .andReturn();
     WorkflowInstanceResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() { });
     assertThat(response.getWorkflowInstances().size()).isEqualTo(3);
     assertThat(response.getTotalCount()).isEqualTo(5);

    //page 2
    request = post(query(3, 3))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(mockMvcTestRule.getContentType());
    mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();
    response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() { });
    assertThat(response.getWorkflowInstances().size()).isEqualTo(2);
    assertThat(response.getTotalCount()).isEqualTo(5);
  }

  private void testSorting(SortingDto sorting, Comparator<WorkflowInstanceDto> comparator) throws Exception {
    createData();

    //query running instances
    WorkflowInstanceRequestDto workflowInstanceQueryDto = createGetAllWorkflowInstancesQuery();
    workflowInstanceQueryDto.setSorting(sorting);

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    WorkflowInstanceResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() { });

    assertThat(response.getWorkflowInstances().size()).isEqualTo(5);

    assertThat(response.getWorkflowInstances()).isSortedAccordingTo(comparator);
  }

  @Test
  public void testSortingByStartDateAsc() throws Exception {
    final Comparator<WorkflowInstanceDto> comparator = Comparator.comparing(WorkflowInstanceDto::getStartDate);
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("startDate");
    sorting.setSortOrder("asc");

    testSorting(sorting, comparator);
  }

  @Test
  public void testSortingByStartDateDesc() throws Exception {
    final Comparator<WorkflowInstanceDto> comparator = (o1, o2) -> o2.getStartDate().compareTo(o1.getStartDate());
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("startDate");
    sorting.setSortOrder("desc");

    testSorting(sorting, comparator);
  }

  @Test
  public void testSortingByIdAsc() throws Exception {
    final Comparator<WorkflowInstanceDto> comparator = Comparator.comparing(WorkflowInstanceDto::getId);
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("id");
    sorting.setSortOrder("asc");

    testSorting(sorting, comparator);
  }

  @Test
  public void testSortingByIdDesc() throws Exception {
    final Comparator<WorkflowInstanceDto> comparator = (o1, o2) -> o2.getId().compareTo(o1.getId());
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("id");
    sorting.setSortOrder("desc");

    testSorting(sorting, comparator);
  }
  @Test
  public void testSortingByWorkflowNameAsc() throws Exception {
    final Comparator<WorkflowInstanceDto> comparator = Comparator.comparing(WorkflowInstanceDto::getWorkflowName);
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("workflowName");
    sorting.setSortOrder("asc");

    testSorting(sorting, comparator);
  }

  @Test
  public void testSortingByWorkflowNameDesc() throws Exception {
    final Comparator<WorkflowInstanceDto> comparator = (o1, o2) -> o2.getWorkflowName().compareTo(o1.getWorkflowName());
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("workflowName");
    sorting.setSortOrder("desc");

    testSorting(sorting, comparator);
  }
  @Test
  public void testSortingByWorkflowVersionAsc() throws Exception {
    final Comparator<WorkflowInstanceDto> comparator = Comparator.comparing(WorkflowInstanceDto::getWorkflowVersion);
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("workflowVersion");
    sorting.setSortOrder("asc");

    testSorting(sorting, comparator);
  }

  @Test
  public void testSortingByWorkflowVersionDesc() throws Exception {
    final Comparator<WorkflowInstanceDto> comparator = (o1, o2) -> o2.getWorkflowVersion().compareTo(o1.getWorkflowVersion());
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("workflowVersion");
    sorting.setSortOrder("desc");

    testSorting(sorting, comparator);
  }

  @Test
  public void testSortingByEndDateAsc() throws Exception {
    final Comparator<WorkflowInstanceDto> comparator = (o1, o2) -> {
      //nulls are always at the end
      if (o1.getEndDate() == null && o2.getEndDate() == null) {
        return 0;
      } else if (o1.getEndDate() == null) {
        return 1;
      } else if (o2.getEndDate() == null) {
        return -1;
      } else {
        return o1.getEndDate().compareTo(o2.getEndDate());
      }
    };
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("endDate");
    sorting.setSortOrder("asc");

    testSorting(sorting, comparator);
  }

  @Test
  public void testSortingByEndDateDesc() throws Exception {
    final Comparator<WorkflowInstanceDto> comparator = (o1, o2) -> {
      //nulls are always at the end
      if (o1.getEndDate() == null && o2.getEndDate() == null) {
        return 0;
      } else if (o1.getEndDate() == null) {
        return 1;
      } else if (o2.getEndDate() == null) {
        return -1;
      } else {
        return o2.getEndDate().compareTo(o1.getEndDate());
      }
    };
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("endDate");
    sorting.setSortOrder("desc");

    testSorting(sorting, comparator);
  }

  private WorkflowInstanceRequestDto createGetAllWorkflowInstancesQuery() {
    return
      createWorkflowInstanceQuery(q -> {
      q.setRunning(true);
      q.setActive(true);
      q.setIncidents(true);
      q.setFinished(true);
      q.setCompleted(true);
      q.setCanceled(true);
    });
  }

  private WorkflowInstanceRequestDto createGetAllWorkflowInstancesQuery(Consumer<WorkflowInstanceQueryDto> filtersSupplier) {
    final WorkflowInstanceRequestDto workflowInstanceQuery = createGetAllWorkflowInstancesQuery();
    filtersSupplier.accept(workflowInstanceQuery.getQueries().get(0));

    return workflowInstanceQuery;
  }

  private WorkflowInstanceRequestDto createGetAllFinishedQuery() {
    return
      createWorkflowInstanceQuery(q -> {
        q.setFinished(true);
        q.setCompleted(true);
        q.setCanceled(true);
      });
  }

  @Test
  public void testQueryAllFinished() throws Exception {
    createData();

    WorkflowInstanceRequestDto workflowInstanceQueryDto = createGetAllFinishedQuery();

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    WorkflowInstanceResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() { });

    assertThat(response.getTotalCount()).isEqualTo(2);
    assertThat(response.getWorkflowInstances().size()).isEqualTo(2);
    for (WorkflowInstanceDto workflowInstanceDto : response.getWorkflowInstances()) {
      assertThat(workflowInstanceDto.getEndDate()).isNotNull();
      assertThat(workflowInstanceDto.getState()).isIn(WorkflowInstanceState.COMPLETED, WorkflowInstanceState.CANCELED);
      assertThat(workflowInstanceDto.getActivities()).isEmpty();
      assertThat(workflowInstanceDto.getSequenceFlows()).isEmpty();
    }
  }

  @Test
  public void testQueryFinishedAndRunning() throws Exception {
    createData();

    WorkflowInstanceRequestDto workflowInstanceQueryDto = createGetAllWorkflowInstancesQuery();

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    WorkflowInstanceResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() { });

    assertThat(response.getTotalCount()).isEqualTo(5);
    assertThat(response.getWorkflowInstances().size()).isEqualTo(5);
  }

  @Test
  public void testQueryWithTwoFragments() throws Exception {
    createData();

    WorkflowInstanceRequestDto workflowInstanceQueryDto = createWorkflowInstanceQuery(q -> {
      q.setRunning(true);    //1st fragment
      q.setActive(true);
      q.setIncidents(true);
    });
    //2nd fragment
    WorkflowInstanceQueryDto query2 = new WorkflowInstanceQueryDto();
    query2.setFinished(true);
    query2.setCompleted(true);
    query2.setCanceled(true);
    workflowInstanceQueryDto.getQueries().add(query2);

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    WorkflowInstanceResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() { });

    assertThat(response.getTotalCount()).isEqualTo(5);
    assertThat(response.getWorkflowInstances().size()).isEqualTo(5);
  }

  @Test
  public void testQueryFinishedCompleted() throws Exception {
    createData();

    WorkflowInstanceRequestDto workflowInstanceQueryDto = createWorkflowInstanceQuery(q -> {
      q.setFinished(true);
      q.setCompleted(true);
    });

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    WorkflowInstanceResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() { });

    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getWorkflowInstances().size()).isEqualTo(1);
    assertThat(response.getWorkflowInstances().get(0).getEndDate()).isNotNull();
    assertThat(response.getWorkflowInstances().get(0).getState()).isEqualTo(WorkflowInstanceState.COMPLETED);
    assertThat(response.getWorkflowInstances().get(0).getActivities()).isEmpty();
  }

  @Test
  public void testQueryFinishedCanceled() throws Exception {
    createData();

    WorkflowInstanceRequestDto workflowInstanceQueryDto = createWorkflowInstanceQuery(q -> {
      q.setFinished(true);
      q.setCanceled(true);
    });

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    WorkflowInstanceResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() { });

    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getWorkflowInstances().size()).isEqualTo(1);
    assertThat(response.getWorkflowInstances().get(0).getEndDate()).isNotNull();
    assertThat(response.getWorkflowInstances().get(0).getState()).isEqualTo(WorkflowInstanceState.CANCELED);
    assertThat(response.getWorkflowInstances().get(0).getActivities()).isEmpty();
  }

  @Test
  public void testQueryRunningWithIncidents() throws Exception {
    createData();

    WorkflowInstanceRequestDto workflowInstanceQueryDto = createWorkflowInstanceQuery(q -> {
      q.setRunning(true);
      q.setIncidents(true);
    });

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc
      .perform(request)
      .andExpect(status().isOk())
      .andExpect(content()
        .contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    WorkflowInstanceResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() { });

    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getWorkflowInstances().size()).isEqualTo(1);
    assertThat(response.getWorkflowInstances().get(0).getActivities()).isEmpty();
    assertThat(response.getWorkflowInstances().get(0).getIncidents().size()).isEqualTo(2);


    IncidentDto activeIncident = null;
    for (IncidentDto incident: response.getWorkflowInstances().get(0).getIncidents()) {
      if (incident.getState().equals(IncidentState.ACTIVE)) {
        activeIncident = incident;
      }
    }
    assertThat(activeIncident).isNotNull();
  }

  @Test
  public void testQueryRunningWithoutIncidents() throws Exception {
    createData();

    WorkflowInstanceRequestDto workflowInstanceQueryDto = createWorkflowInstanceQuery(q -> {
      q.setRunning(true);
      q.setActive(true);
    });

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc
      .perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    WorkflowInstanceResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() { });

    assertThat(response.getTotalCount()).isEqualTo(2);
    assertThat(response.getWorkflowInstances().size()).isEqualTo(2);

    for (WorkflowInstanceDto workflowInstanceDto: response.getWorkflowInstances()) {
      assertThat(workflowInstanceDto.getActivities()).isEmpty();
      IncidentDto activeIncident = null;
      for (IncidentDto incident : workflowInstanceDto.getIncidents()) {
        if (incident.getState().equals(IncidentState.ACTIVE)) {
          activeIncident = incident;
        }
      }
      assertThat(activeIncident).isNull();
    }
  }

  @Test
  public void testGetWorkflowInstanceById() throws Exception {
    createData();

    MockHttpServletRequestBuilder request = get(String.format(GET_INSTANCE_URL, instanceWithoutIncident.getId()));

    MvcResult mvcResult = mockMvc
      .perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    final WorkflowInstanceDto workflowInstanceDto = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceDto>() {});

    assertThat(workflowInstanceDto.getId()).isEqualTo(instanceWithoutIncident.getId());
    assertThat(workflowInstanceDto.getWorkflowId()).isEqualTo(instanceWithoutIncident.getWorkflowId());
    assertThat(workflowInstanceDto.getState()).isEqualTo(instanceWithoutIncident.getState());
    assertThat(workflowInstanceDto.getStartDate()).isEqualTo(instanceWithoutIncident.getStartDate());
    assertThat(workflowInstanceDto.getEndDate()).isEqualTo(instanceWithoutIncident.getEndDate());
    assertThat(workflowInstanceDto.getBpmnProcessId()).isEqualTo(instanceWithoutIncident.getBpmnProcessId());

    assertThat(workflowInstanceDto.getActivities().size()).isGreaterThan(0);
    assertThat(workflowInstanceDto.getActivities().size()).isEqualTo(instanceWithoutIncident.getActivities().size());

    assertThat(workflowInstanceDto.getIncidents().size()).isGreaterThan(0);
    assertThat(workflowInstanceDto.getIncidents().size()).isEqualTo(instanceWithoutIncident.getIncidents().size());

  }

  private void createData() {
    //running instance with one activity and without incidents
    final WorkflowInstanceEntity runningInstance = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    runningInstance.getActivities().add(createActivityInstance(ActivityState.ACTIVE));
    runningInstance.getSequenceFlows().add(createSequenceFlow());

    //completed instance with one activity and without incidents
    final WorkflowInstanceEntity completedInstance = createWorkflowInstance(WorkflowInstanceState.COMPLETED);
    completedInstance.getActivities().add(createActivityInstance(ActivityState.COMPLETED));
    completedInstance.getSequenceFlows().add(createSequenceFlow());
    completedInstance.getSequenceFlows().add(createSequenceFlow());

    //canceled instance with two activities and without incidents
    final WorkflowInstanceEntity canceledInstance = createWorkflowInstance(WorkflowInstanceState.CANCELED);
    canceledInstance.getActivities().add(createActivityInstance(ActivityState.COMPLETED));
    canceledInstance.getActivities().add(createActivityInstance(ActivityState.TERMINATED));

    //instance with incidents (one resolved and one active) and one active activity
    final WorkflowInstanceEntity instanceWithIncident = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    instanceWithIncident.getIncidents().add(createIncident(IncidentState.ACTIVE));
    instanceWithIncident.getIncidents().add(createIncident(IncidentState.RESOLVED));
    instanceWithIncident.getActivities().add(createActivityInstance(ActivityState.ACTIVE));

    //instance with one resolved incident and one completed activity
    instanceWithoutIncident = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    instanceWithoutIncident.getIncidents().add(createIncident(IncidentState.RESOLVED));
    instanceWithoutIncident.getActivities().add(createActivityInstance(ActivityState.COMPLETED));

    //persist instances
    elasticsearchTestRule.persist(runningInstance, completedInstance, instanceWithIncident, instanceWithoutIncident, canceledInstance);
  }

  private void addVariableEntity(WorkflowInstanceEntity workflowInstance, String name, String value) {
    workflowInstance.getStringVariables().add(new StringVariableEntity(name, value));
  }
  private void addVariableEntity(WorkflowInstanceEntity workflowInstance, String name, Long value) {
    workflowInstance.getLongVariables().add(new LongVariableEntity(name, value));
  }
  private void addVariableEntity(WorkflowInstanceEntity workflowInstance, String name, Double value) {
    workflowInstance.getDoubleVariables().add(new DoubleVariableEntity(name, value));
  }
  private void addVariableEntity(WorkflowInstanceEntity workflowInstance, String name, Boolean value) {
    workflowInstance.getBooleanVariables().add(new BooleanVariableEntity(name, value));
  }

  private String query(int firstResult, int maxResults) {
    return String.format("%s?firstResult=%d&maxResults=%d", QUERY_INSTANCES_URL, firstResult, maxResults);
  }

  private WorkflowInstanceRequestDto createWorkflowInstanceQuery(Consumer<WorkflowInstanceQueryDto> filtersSupplier) {
    WorkflowInstanceRequestDto request = new WorkflowInstanceRequestDto();
    WorkflowInstanceQueryDto query = new WorkflowInstanceQueryDto();
    filtersSupplier.accept(query);
    request.getQueries().add(query);
    return request;
  }


}


