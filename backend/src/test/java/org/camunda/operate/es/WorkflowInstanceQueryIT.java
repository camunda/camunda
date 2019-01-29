package org.camunda.operate.es;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.ActivityType;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.entities.listview.ActivityInstanceForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.es.schema.templates.WorkflowInstanceTemplate;
import org.camunda.operate.rest.dto.SortingDto;
import org.camunda.operate.rest.dto.listview.ListViewQueryDto;
import org.camunda.operate.rest.dto.listview.ListViewRequestDto;
import org.camunda.operate.rest.dto.listview.ListViewResponseDto;
import org.camunda.operate.rest.dto.listview.ListViewWorkflowInstanceDto;
import org.camunda.operate.rest.dto.listview.WorkflowInstanceStateDto;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.MockMvcTestRule;
import org.camunda.operate.util.OperateIntegrationTest;
import org.camunda.operate.util.TestUtil;
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
import static org.camunda.operate.util.TestUtil.createActivityInstanceWithIncident;
import static org.camunda.operate.util.TestUtil.createIncident;
import static org.camunda.operate.util.TestUtil.createWorkflowInstance;
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

  private WorkflowInstanceForListViewEntity instanceWithoutIncident;

  private MockMvc mockMvc;

  @Before
  public void starting() {
    this.mockMvc = mockMvcTestRule.getMockMvc();
  }

  @Test
  public void testQueryAllRunning() throws Exception {
    createData();

    //query running instances
    ListViewRequestDto workflowInstanceQueryDto = TestUtil.createWorkflowInstanceQuery(q -> {
      q.setRunning(true);
      q.setActive(true);
      q.setIncidents(true);
    });

    MockHttpServletRequestBuilder request = post(query(0, 100)).content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request).andExpect(status().isOk()).andExpect(content().contentType(mockMvcTestRule.getContentType())).andReturn();

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {
    });

    assertThat(response.getWorkflowInstances().size()).isEqualTo(3);
    assertThat(response.getTotalCount()).isEqualTo(3);
    for (ListViewWorkflowInstanceDto workflowInstanceDto : response.getWorkflowInstances()) {
      assertThat(workflowInstanceDto.getEndDate()).isNull();
      assertThat(workflowInstanceDto.getState()).isIn(WorkflowInstanceStateDto.ACTIVE, WorkflowInstanceStateDto.INCIDENT);
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
    final WorkflowInstanceForListViewEntity workflowInstance1 = createWorkflowInstance(date1, date5);
    final WorkflowInstanceForListViewEntity workflowInstance2 = createWorkflowInstance(date2, date4);
    final WorkflowInstanceForListViewEntity workflowInstance3 = createWorkflowInstance(date3, null);
    elasticsearchTestRule.persistNew(workflowInstance1, workflowInstance2, workflowInstance3);

    //when
    ListViewRequestDto query = TestUtil.createGetAllWorkflowInstancesQuery(q -> {
      q.setStartDateAfter(date1.minus(1, ChronoUnit.DAYS));
      q.setStartDateBefore(date3);
    });
    //then
    requestAndAssertIds(query, "TEST CASE #1", workflowInstance1.getId(), workflowInstance2.getId());

    //test inclusion for startDateAfter and exclusion for startDateBefore
    //when
    query = TestUtil.createGetAllWorkflowInstancesQuery(q -> {
      q.setStartDateAfter(date1);
      q.setStartDateBefore(date3);
    });
    //then
    requestAndAssertIds(query, "TEST CASE #2", workflowInstance1.getId(), workflowInstance2.getId());

    //when
    query = TestUtil.createGetAllWorkflowInstancesQuery(q -> {
      q.setStartDateAfter(date1.plus(1, ChronoUnit.MILLIS));
      q.setStartDateBefore(date3.plus(1, ChronoUnit.MILLIS));
    });
    //then
    requestAndAssertIds(query, "TEST CASE #3", workflowInstance2.getId(), workflowInstance3.getId());

    //test combination of start date and end date
    //when
    query = TestUtil.createGetAllWorkflowInstancesQuery(q -> {
      q.setStartDateAfter(date2.minus(1, ChronoUnit.DAYS));
      q.setStartDateBefore(date3.plus(1, ChronoUnit.DAYS));
      q.setEndDateAfter(date4.minus(1, ChronoUnit.DAYS));
      q.setEndDateBefore(date4.plus(1, ChronoUnit.DAYS));
    });
    //then
    requestAndAssertIds(query, "TEST CASE #4", workflowInstance2.getId());

    //test inclusion for endDateAfter and exclusion for endDateBefore
    //when
    query = TestUtil.createGetAllWorkflowInstancesQuery(q -> {
      q.setEndDateAfter(date4);
      q.setEndDateBefore(date5);
    });
    //then
    requestAndAssertIds(query, "TEST CASE #5", workflowInstance2.getId());

    //when
    query = TestUtil.createGetAllWorkflowInstancesQuery(q -> {
      q.setEndDateAfter(date4);
      q.setEndDateBefore(date5.plus(1, ChronoUnit.MILLIS));
    });
    //then
    requestAndAssertIds(query, "TEST CASE #6", workflowInstance1.getId(), workflowInstance2.getId());

  }

  private void requestAndAssertIds(ListViewRequestDto query, String testCaseName, String... ids) throws Exception {
    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(query))
      .contentType(mockMvcTestRule.getContentType());
    //then
    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    assertThat(response.getWorkflowInstances()).as(testCaseName).extracting(WorkflowInstanceTemplate.ID).containsExactlyInAnyOrder(ids);
  }

  @Test
  public void testQueryByErrorMessage() throws Exception {
    final String errorMessage = "No more retries left.";

    //given we have 2 workflow instances: one with active activity with given error msg, another with active activity with another error message
    final WorkflowInstanceForListViewEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    final ActivityInstanceForListViewEntity activityInstance1 = createActivityInstanceWithIncident(workflowInstance1.getId(), ActivityState.ACTIVE,
      errorMessage, null);

    final WorkflowInstanceForListViewEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    final ActivityInstanceForListViewEntity activityInstance2 = createActivityInstanceWithIncident(workflowInstance2.getId(), ActivityState.ACTIVE,
      "other error message", null);

    elasticsearchTestRule.persistNew(workflowInstance1, activityInstance1, workflowInstance2, activityInstance2);

    //given
    ListViewRequestDto query = TestUtil.createGetAllWorkflowInstancesQuery(q -> q.setErrorMessage(errorMessage));

    MockHttpServletRequestBuilder request = post(query(0, 100))
        .content(mockMvcTestRule.json(query))
        .contentType(mockMvcTestRule.getContentType());
    //when
    MvcResult mvcResult = mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentType(mockMvcTestRule.getContentType()))
        .andReturn();

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    //then
    assertThat(response.getWorkflowInstances().size()).isEqualTo(1);

    final ListViewWorkflowInstanceDto workflowInstance = response.getWorkflowInstances().get(0);
    assertThat(workflowInstance.getState()).isEqualTo(WorkflowInstanceStateDto.INCIDENT);
    assertThat(workflowInstance.getId()).isEqualTo(workflowInstance1.getId());

  }

  @Test
  public void testQueryByActiveActivityId() throws Exception {

    final String activityId = "taskA";

    final OperateEntity[] data = createDataForActiveActivityIdQuery(activityId);
    elasticsearchTestRule.persistNew(data);

    //when
    ListViewRequestDto query = TestUtil.createWorkflowInstanceQuery(q -> {
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

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    //then
    assertThat(response.getWorkflowInstances().size()).isEqualTo(1);

    assertThat(response.getWorkflowInstances().get(0).getId())
      .isEqualTo(data[0].getId());

  }

  /**
   * 1st entity must be selected
   */
  private OperateEntity[] createDataForActiveActivityIdQuery(String activityId) {
    List<OperateEntity> entities = new ArrayList<>();

    //wi 1: active with active activity with given id
    final WorkflowInstanceForListViewEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);

    final ActivityInstanceForListViewEntity activeWithIdActivityInstance = createActivityInstance(workflowInstance1.getId(), ActivityState.ACTIVE, activityId);

    final ActivityInstanceForListViewEntity completedWithoutIdActivityInstance = createActivityInstance(workflowInstance1.getId(), ActivityState.COMPLETED, "otherActivityId");

    entities.addAll(Arrays.asList(workflowInstance1, activeWithIdActivityInstance, completedWithoutIdActivityInstance));

    //wi 2: active with active activity with another id
    final WorkflowInstanceForListViewEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);

    final ActivityInstanceForListViewEntity activeWithoutIdActivityInstance = createActivityInstance(workflowInstance2.getId(), ActivityState.ACTIVE, "otherActivityId");

    final ActivityInstanceForListViewEntity completedWithIdActivityInstance = createActivityInstanceWithIncident(workflowInstance2.getId(), ActivityState.ACTIVE, "error", null);
    completedWithIdActivityInstance.setActivityId(activityId);

    entities.addAll(Arrays.asList(workflowInstance2, activeWithoutIdActivityInstance, completedWithIdActivityInstance));
    return entities.toArray(new OperateEntity[entities.size()]);
  }

  @Test
  public void testQueryByIncidentActivityId() throws Exception {
    final String activityId = "taskA";

    final OperateEntity[] data = createDataForIncidentActivityIdQuery(activityId);
    elasticsearchTestRule.persistNew(data);

    //when
    ListViewRequestDto query = TestUtil.createWorkflowInstanceQuery(q -> {
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

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    //then
    assertThat(response.getWorkflowInstances().size()).isEqualTo(1);

    assertThat(response.getWorkflowInstances().get(0).getId())
      .isEqualTo(data[0].getId());

  }

  /**
   * 1st entity must be selected
   */
  private OperateEntity[] createDataForIncidentActivityIdQuery(String activityId) {
    List<OperateEntity> entities = new ArrayList<>();
    List<OperateEntity> activityInstances = new ArrayList<>();

    //wi1: active with activity in INCIDENT state with given id
    final WorkflowInstanceForListViewEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);

    final ActivityInstanceForListViewEntity incidentWithIdActivityInstance = createActivityInstanceWithIncident(workflowInstance1.getId(), ActivityState.ACTIVE, "error", null);
    incidentWithIdActivityInstance.setActivityId(activityId);

    final ActivityInstanceForListViewEntity completedWithoutIdActivityInstance = createActivityInstance(workflowInstance1.getId(), ActivityState.COMPLETED, "otherActivityId");

    entities.add(workflowInstance1);
    activityInstances.addAll(Arrays.asList(incidentWithIdActivityInstance, completedWithoutIdActivityInstance));

    //wi2: active with activity in INCIDENT state with another id
    final WorkflowInstanceForListViewEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);

    final ActivityInstanceForListViewEntity incidentWithoutIdActivityInstance = createActivityInstanceWithIncident(workflowInstance2.getId(), ActivityState.ACTIVE, "error", null);
    incidentWithoutIdActivityInstance.setActivityId("otherActivityId");

    final ActivityInstanceForListViewEntity completedWithIdActivityInstance = createActivityInstance(workflowInstance2.getId(), ActivityState.COMPLETED, activityId);

    entities.add(workflowInstance2);
    activityInstances.addAll(Arrays.asList(incidentWithoutIdActivityInstance, completedWithIdActivityInstance));

    //wi3: active with activity in ACTIVE state with given id
    final WorkflowInstanceForListViewEntity workflowInstance3 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);

    final ActivityInstanceForListViewEntity activeWithIdActivityInstance = createActivityInstance(workflowInstance3.getId(), ActivityState.ACTIVE, activityId);

    final ActivityInstanceForListViewEntity completedWithoutIdActivityInstance2 = createActivityInstance(workflowInstance3.getId(), ActivityState.COMPLETED, "otherActivityId");

    entities.add(workflowInstance3);
    activityInstances.addAll(Arrays.asList(activeWithIdActivityInstance, completedWithoutIdActivityInstance2));


    return entities.toArray(new OperateEntity[entities.size()]);
  }

  @Test
  public void testQueryByTerminatedActivityId() throws Exception {
    final String activityId = "taskA";

    final OperateEntity[] data = createDataForTerminatedActivityIdQuery(activityId);
    elasticsearchTestRule.persistNew(data);

    //when
    ListViewRequestDto query = TestUtil.createWorkflowInstanceQuery(q -> {
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

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    //then
    assertThat(response.getWorkflowInstances().size()).isEqualTo(1);

    assertThat(response.getWorkflowInstances().get(0).getId())
      .isEqualTo(data[0].getId());

  }

  /**
   * 1st entity must be selected
   */
  private OperateEntity[] createDataForTerminatedActivityIdQuery(String activityId) {
    List<OperateEntity> entities = new ArrayList<>();

    //wi1: canceled with TERMINATED activity with given id
    final WorkflowInstanceForListViewEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.CANCELED);

    final ActivityInstanceForListViewEntity terminatedWithIdActivityInstance = createActivityInstance(workflowInstance1.getId(), ActivityState.TERMINATED, activityId);

    final ActivityInstanceForListViewEntity completedWithoutIdActivityInstance = createActivityInstance(workflowInstance1.getId(), ActivityState.COMPLETED, "otherActivityId");

    entities.addAll(Arrays.asList(workflowInstance1, terminatedWithIdActivityInstance, completedWithoutIdActivityInstance));

    //wi2: canceled with TERMINATED activity with another id
    final WorkflowInstanceForListViewEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.CANCELED);

    final ActivityInstanceForListViewEntity terminatedWithoutIdActivityInstance = createActivityInstance(workflowInstance2.getId(), ActivityState.TERMINATED, "otherActivityId");

    final ActivityInstanceForListViewEntity completedWithIdActivityInstance = createActivityInstance(workflowInstance2.getId(), ActivityState.COMPLETED, activityId);

    entities.addAll(Arrays.asList(workflowInstance2, terminatedWithoutIdActivityInstance, completedWithIdActivityInstance));

    //wi3: active with ACTIVE activity with given id
    final WorkflowInstanceForListViewEntity workflowInstance3 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);

    final ActivityInstanceForListViewEntity activeWithIdActivityInstance = createActivityInstance(workflowInstance3.getId(), ActivityState.TERMINATED, activityId);

    final ActivityInstanceForListViewEntity completedWithoutIdActivityInstance2 = createActivityInstance(workflowInstance3.getId(), ActivityState.COMPLETED, "otherActivityId");

    entities.addAll(Arrays.asList(workflowInstance3, activeWithIdActivityInstance, completedWithoutIdActivityInstance2));

    return entities.toArray(new OperateEntity[entities.size()]);
  }

  @Test
  public void testQueryByCombinedStateActivityId() throws Exception {
    final String activityId = "taskA";

    List<String> selectedIds = new ArrayList<>();

    OperateEntity[] data = createDataForActiveActivityIdQuery(activityId);
    selectedIds.add(data[0].getId());
    elasticsearchTestRule.persistNew(data);

    data = createDataForIncidentActivityIdQuery(activityId);
    selectedIds.add(data[0].getId());
    selectedIds.add(data[2].getId());
    elasticsearchTestRule.persistNew(data);

    data = createDataForTerminatedActivityIdQuery(activityId);
    selectedIds.add(data[0].getId());
    elasticsearchTestRule.persistNew(data);

    //when
    ListViewRequestDto query = TestUtil.createWorkflowInstanceQuery(q -> {
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

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    //then
    assertThat(response.getWorkflowInstances().size()).isEqualTo(selectedIds.size());

    assertThat(response.getWorkflowInstances()).extracting("id").containsExactlyInAnyOrder(selectedIds.toArray());

  }

  @Test
  public void testQueryByCompletedActivityId() throws Exception {
    final String activityId = "endEvent";

    //wi 1: completed with completed end event
    final WorkflowInstanceForListViewEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);

    final ActivityInstanceForListViewEntity completedEndEventWithIdActivityInstance = createActivityInstance(workflowInstance1.getId(), ActivityState.COMPLETED, activityId, ActivityType.END_EVENT);

    final ActivityInstanceForListViewEntity completedWithoutIdActivityInstance = createActivityInstance(workflowInstance1.getId(), ActivityState.COMPLETED, "otherActivityId");

    //wi 2: completed without completed end event
    final WorkflowInstanceForListViewEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);

    final ActivityInstanceForListViewEntity activeEndEventWithIdActivityInstance = createActivityInstance(workflowInstance2.getId(), ActivityState.ACTIVE, activityId, ActivityType.END_EVENT);

    //wi 3: completed with completed end event (but not of type END_EVENT)
    final WorkflowInstanceForListViewEntity workflowInstance3 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);

    final ActivityInstanceForListViewEntity completedWithIdActivityInstance = createActivityInstance(workflowInstance3.getId(), ActivityState.COMPLETED, activityId);

    //wi 4: active with completed end event
    final WorkflowInstanceForListViewEntity workflowInstance4 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);

    final ActivityInstanceForListViewEntity completedEndEventWithIdActivityInstance2 = createActivityInstance(workflowInstance4.getId(), ActivityState.COMPLETED, activityId, ActivityType.END_EVENT);

    elasticsearchTestRule.persistNew(workflowInstance1, completedEndEventWithIdActivityInstance, completedWithoutIdActivityInstance,
      workflowInstance2, activeEndEventWithIdActivityInstance,
      workflowInstance3, completedWithIdActivityInstance,
      workflowInstance4, completedEndEventWithIdActivityInstance2);

    //when
    ListViewRequestDto query = TestUtil.createWorkflowInstanceQuery(q -> {
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

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    //then
    assertThat(response.getWorkflowInstances().size()).isEqualTo(1);

    assertThat(response.getWorkflowInstances().get(0).getId())
      .isEqualTo(workflowInstance1.getId());

  }


  @Test
  public void testQueryByWorkflowInstanceIds() throws Exception {
    //given
    final WorkflowInstanceForListViewEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    final WorkflowInstanceForListViewEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.CANCELED);
    final WorkflowInstanceForListViewEntity workflowInstance3 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);
    elasticsearchTestRule.persistNew(workflowInstance1, workflowInstance2, workflowInstance3);

    ListViewRequestDto query = TestUtil.createGetAllWorkflowInstancesQuery(q ->
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
    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    assertThat(response.getWorkflowInstances()).hasSize(2);

    assertThat(response.getWorkflowInstances()).extracting(WorkflowInstanceTemplate.ID).containsExactlyInAnyOrder(workflowInstance1.getId(), workflowInstance2.getId());
  }
//
//  @Test
//  public void testQueryByVariableValues() throws Exception {
//    //given
//    final String strName = "str1";
//    final String stringValue = "strValue1";
//    final String nullName = "null1";
//    final String intName = "int1";
//    final long intValue = 111L;
//    final String longName = "long1";
//    final long longValue = Long.valueOf(Integer.MAX_VALUE) + 1L;
//    final String boolName = "bool1";
//    final boolean boolValue = true;
//    final String floatName = "float1";
//    final float floatValue = .5f;
//    final String doubleName = "double1";
//    final double doubleValue = Double.valueOf(Float.MAX_VALUE) + 1;
//
//    final WorkflowInstanceEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
//    addVariableEntity(workflowInstance1, strName, stringValue);
//    addVariableEntity(workflowInstance1, nullName, (String)null);
//    addVariableEntity(workflowInstance1, intName, intValue);
//    addVariableEntity(workflowInstance1, longName, longValue);
//    addVariableEntity(workflowInstance1, boolName, boolValue);
//    addVariableEntity(workflowInstance1, floatName, (double).1f);
//    addVariableEntity(workflowInstance1, doubleName, doubleValue);
//
//    final WorkflowInstanceEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.CANCELED);
//    addVariableEntity(workflowInstance2, strName, "strValue2");
//    addVariableEntity(workflowInstance2, intName, 222L);
//    addVariableEntity(workflowInstance2, longName, longValue);
//    addVariableEntity(workflowInstance2, boolName, false);
//    addVariableEntity(workflowInstance2, floatName, (double)floatValue);
//    addVariableEntity(workflowInstance2, doubleName, .555);
//
//    final WorkflowInstanceEntity workflowInstance3 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);
//    addVariableEntity(workflowInstance3, strName, stringValue);
//    addVariableEntity(workflowInstance3, intName, intValue);
//    addVariableEntity(workflowInstance3, longName, Long.valueOf(Integer.MAX_VALUE) + 2L);
//    addVariableEntity(workflowInstance3, boolName, boolValue);
//    addVariableEntity(workflowInstance3, floatName, (double)floatValue);
//    addVariableEntity(workflowInstance3, doubleName, doubleValue);
//
//    elasticsearchTestRule.persistNew(workflowInstance1, workflowInstance2, workflowInstance3);
//
//    //when
//    ListViewRequestDto query = TestUtil.createGetAllWorkflowInstancesQuery(q ->
//      q.setVariablesQuery(new VariablesQueryDto(strName, stringValue))
//    );
//    //then
//    requestAndAssertIds(query, "TEST CASE #1", workflowInstance1.getId(), workflowInstance3.getId());
//
//    //when
//    query = TestUtil.createGetAllWorkflowInstancesQuery(q ->
//      q.setVariablesQuery(new VariablesQueryDto(intName, intValue))
//    );
//    //then
//    requestAndAssertIds(query, "TEST CASE #2", workflowInstance1.getId(), workflowInstance3.getId());
//
//    //when
//    query = TestUtil.createGetAllWorkflowInstancesQuery(q ->
//      q.setVariablesQuery(new VariablesQueryDto(longName, longValue))
//    );
//    //then
//    requestAndAssertIds(query, "TEST CASE #3", workflowInstance1.getId(), workflowInstance2.getId());
//
//    //when
//    query = TestUtil.createGetAllWorkflowInstancesQuery(q ->
//      q.setVariablesQuery(new VariablesQueryDto(boolName, boolValue))
//    );
//    //then
//    requestAndAssertIds(query, "TEST CASE #4", workflowInstance1.getId(), workflowInstance3.getId());
//
//    //when
//    query = TestUtil.createGetAllWorkflowInstancesQuery(q ->
//      q.setVariablesQuery(new VariablesQueryDto(floatName, floatValue))
//    );
//    //then
//    requestAndAssertIds(query, "TEST CASE #5", workflowInstance2.getId(), workflowInstance3.getId());
//
//    //when
//    query = TestUtil.createGetAllWorkflowInstancesQuery(q ->
//      q.setVariablesQuery(new VariablesQueryDto(doubleName, doubleValue))
//    );
//    //then
//    requestAndAssertIds(query, "TEST CASE #6", workflowInstance1.getId(), workflowInstance3.getId());
//
//    //when
//    query = TestUtil.createGetAllWorkflowInstancesQuery(q ->
//      q.setVariablesQuery(new VariablesQueryDto(nullName, null))
//    );
//    //then
//    requestAndAssertIds(query, "TEST CASE #7", workflowInstance1.getId());
//  }
//
//  @Test
//  public void testQueryByVariableValuesFailOnNullVariableName() throws Exception {
//    //when
//    ListViewRequestDto query = TestUtil.createGetAllWorkflowInstancesQuery(q ->
//      q.setVariablesQuery(new VariablesQueryDto(null, "someValue"))
//    );
//    MockHttpServletRequestBuilder request = post(query(0, 100))
//      .content(mockMvcTestRule.json(query))
//      .contentType(mockMvcTestRule.getContentType());
//
//    //then
//    MvcResult mvcResult = mockMvc.perform(request)
//      .andExpect(status().isBadRequest())
//      .andReturn();
//
//    assertThat(mvcResult.getResolvedException().getMessage()).contains("Variables query must provide not-null variable name.");
//
//  }

  @Test
  public void testQueryByExcludeIds() throws Exception {
    //given
    final WorkflowInstanceForListViewEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    final WorkflowInstanceForListViewEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.CANCELED);
    final WorkflowInstanceForListViewEntity workflowInstance3 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);
    final WorkflowInstanceForListViewEntity workflowInstance4 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);
    elasticsearchTestRule.persistNew(workflowInstance1, workflowInstance2, workflowInstance3, workflowInstance4);

    ListViewRequestDto query = TestUtil.createGetAllWorkflowInstancesQuery(q ->
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
    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    assertThat(response.getWorkflowInstances()).hasSize(2);

    assertThat(response.getWorkflowInstances()).extracting(WorkflowInstanceTemplate.ID).containsExactlyInAnyOrder(workflowInstance2.getId(), workflowInstance4.getId());
  }

  @Test
  public void testQueryByWorkflowIds() throws Exception {
    //given
    String wfId1 = "1";
    String wfId2 = "2";
    String wfId3 = "3";
    final WorkflowInstanceForListViewEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    workflowInstance1.setWorkflowId(wfId1);
    final WorkflowInstanceForListViewEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.CANCELED);
    workflowInstance2.setWorkflowId(wfId2);
    final WorkflowInstanceForListViewEntity workflowInstance3 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);
    final WorkflowInstanceForListViewEntity workflowInstance4 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);
    workflowInstance3.setWorkflowId(wfId3);
    workflowInstance4.setWorkflowId(wfId3);

    elasticsearchTestRule.persistNew(workflowInstance1, workflowInstance2, workflowInstance3, workflowInstance4);

    ListViewRequestDto query = TestUtil.createGetAllWorkflowInstancesQuery(q -> q.setWorkflowIds(Arrays.asList(wfId1, wfId3)));

    //when
    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(query))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    //then
    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    assertThat(response.getWorkflowInstances()).hasSize(3);

    assertThat(response.getWorkflowInstances()).extracting(WorkflowInstanceTemplate.ID)
      .containsExactlyInAnyOrder(workflowInstance1.getId(), workflowInstance3.getId(), workflowInstance4.getId());
  }

  @Test
  public void testQueryByBpmnProcessIdAndVersion() throws Exception {
    //given
    String bpmnProcessId1 = "pr1";
    int version1 = 1;
    String bpmnProcessId2 = "pr2";
    int version2 = 2;
    final WorkflowInstanceForListViewEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    workflowInstance1.setBpmnProcessId(bpmnProcessId1);
    workflowInstance1.setWorkflowVersion(version1);
    final WorkflowInstanceForListViewEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.CANCELED);
    workflowInstance2.setBpmnProcessId(bpmnProcessId1);
    workflowInstance2.setWorkflowVersion(version2);
    final WorkflowInstanceForListViewEntity workflowInstance3 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);
    workflowInstance3.setBpmnProcessId(bpmnProcessId2);
    workflowInstance3.setWorkflowVersion(version1);

    elasticsearchTestRule.persistNew(workflowInstance1, workflowInstance2, workflowInstance3);

    ListViewRequestDto query = TestUtil.createGetAllWorkflowInstancesQuery(q -> {
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
    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    assertThat(response.getWorkflowInstances()).hasSize(1);

    assertThat(response.getWorkflowInstances()).extracting(WorkflowInstanceTemplate.ID)
      .containsExactly(workflowInstance1.getId());
  }

  @Test
  public void testQueryByWorkflowVersionFail() throws Exception {
    //when
    ListViewRequestDto query = TestUtil.createGetAllWorkflowInstancesQuery(q -> {
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
    final WorkflowInstanceForListViewEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    workflowInstance1.setBpmnProcessId(bpmnProcessId1);
    workflowInstance1.setWorkflowVersion(version1);
    final WorkflowInstanceForListViewEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.CANCELED);
    workflowInstance2.setBpmnProcessId(bpmnProcessId1);
    workflowInstance2.setWorkflowVersion(version2);
    final WorkflowInstanceForListViewEntity workflowInstance3 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);
    workflowInstance3.setBpmnProcessId(bpmnProcessId2);
    workflowInstance3.setWorkflowVersion(version1);

    elasticsearchTestRule.persistNew(workflowInstance1, workflowInstance2, workflowInstance3);

    ListViewRequestDto query = TestUtil.createGetAllWorkflowInstancesQuery(q -> q.setBpmnProcessId(bpmnProcessId1));

    //when
    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(query))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    //then
    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    assertThat(response.getWorkflowInstances()).hasSize(2);

    assertThat(response.getWorkflowInstances()).extracting(WorkflowInstanceTemplate.ID)
      .containsExactlyInAnyOrder(workflowInstance1.getId(), workflowInstance2.getId());
  }

  @Test
  public void testPagination() throws Exception {
    createData();

    //query running instances
    ListViewRequestDto workflowInstanceQueryDto = TestUtil.createGetAllWorkflowInstancesQuery();

    //page 1
    MockHttpServletRequestBuilder request = post(query(0, 3))
        .content(mockMvcTestRule.json(workflowInstanceQueryDto))
        .contentType(mockMvcTestRule.getContentType());
    MvcResult mvcResult = mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentType(mockMvcTestRule.getContentType()))
        .andReturn();
     ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });
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
    response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });
    assertThat(response.getWorkflowInstances().size()).isEqualTo(2);
    assertThat(response.getTotalCount()).isEqualTo(5);
  }

  private void testSorting(SortingDto sorting, Comparator<ListViewWorkflowInstanceDto> comparator) throws Exception {
    createData();

    //query running instances
    ListViewRequestDto workflowInstanceQueryDto = TestUtil.createGetAllWorkflowInstancesQuery();
    workflowInstanceQueryDto.setSorting(sorting);

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    assertThat(response.getWorkflowInstances().size()).isEqualTo(5);

    assertThat(response.getWorkflowInstances()).isSortedAccordingTo(comparator);
  }

  @Test
  public void testSortingByStartDateAsc() throws Exception {
    final Comparator<ListViewWorkflowInstanceDto> comparator = Comparator.comparing(ListViewWorkflowInstanceDto::getStartDate);
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("startDate");
    sorting.setSortOrder("asc");

    testSorting(sorting, comparator);
  }

  @Test
  public void testSortingByStartDateDesc() throws Exception {
    final Comparator<ListViewWorkflowInstanceDto> comparator = (o1, o2) -> o2.getStartDate().compareTo(o1.getStartDate());
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("startDate");
    sorting.setSortOrder("desc");

    testSorting(sorting, comparator);
  }

  @Test
  public void testSortingByIdAsc() throws Exception {
    final Comparator<ListViewWorkflowInstanceDto> comparator = Comparator.comparing(ListViewWorkflowInstanceDto::getId);
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("id");
    sorting.setSortOrder("asc");

    testSorting(sorting, comparator);
  }

  @Test
  public void testSortingByIdDesc() throws Exception {
    final Comparator<ListViewWorkflowInstanceDto> comparator = (o1, o2) -> o2.getId().compareTo(o1.getId());
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("id");
    sorting.setSortOrder("desc");

    testSorting(sorting, comparator);
  }
  @Test
  public void testSortingByWorkflowNameAsc() throws Exception {
    final Comparator<ListViewWorkflowInstanceDto> comparator = Comparator.comparing(ListViewWorkflowInstanceDto::getWorkflowName);
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("workflowName");
    sorting.setSortOrder("asc");

    testSorting(sorting, comparator);
  }

  @Test
  public void testSortingByWorkflowNameDesc() throws Exception {
    final Comparator<ListViewWorkflowInstanceDto> comparator = (o1, o2) -> o2.getWorkflowName().compareTo(o1.getWorkflowName());
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("workflowName");
    sorting.setSortOrder("desc");

    testSorting(sorting, comparator);
  }
  @Test
  public void testSortingByWorkflowVersionAsc() throws Exception {
    final Comparator<ListViewWorkflowInstanceDto> comparator = Comparator.comparing(ListViewWorkflowInstanceDto::getWorkflowVersion);
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("workflowVersion");
    sorting.setSortOrder("asc");

    testSorting(sorting, comparator);
  }

  @Test
  public void testSortingByWorkflowVersionDesc() throws Exception {
    final Comparator<ListViewWorkflowInstanceDto> comparator = (o1, o2) -> o2.getWorkflowVersion().compareTo(o1.getWorkflowVersion());
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("workflowVersion");
    sorting.setSortOrder("desc");

    testSorting(sorting, comparator);
  }

  @Test
  public void testSortingByEndDateAsc() throws Exception {
    final Comparator<ListViewWorkflowInstanceDto> comparator = (o1, o2) -> {
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
    final Comparator<ListViewWorkflowInstanceDto> comparator = (o1, o2) -> {
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

  @Test
  public void testQueryAllFinished() throws Exception {
    createData();

    ListViewRequestDto workflowInstanceQueryDto = TestUtil.createGetAllFinishedQuery();

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    assertThat(response.getTotalCount()).isEqualTo(2);
    assertThat(response.getWorkflowInstances().size()).isEqualTo(2);
    for (ListViewWorkflowInstanceDto workflowInstanceDto : response.getWorkflowInstances()) {
      assertThat(workflowInstanceDto.getEndDate()).isNotNull();
      assertThat(workflowInstanceDto.getState()).isIn(WorkflowInstanceStateDto.COMPLETED, WorkflowInstanceStateDto.CANCELED);
    }
  }

  @Test
  public void testQueryFinishedAndRunning() throws Exception {
    createData();

    ListViewRequestDto workflowInstanceQueryDto = TestUtil.createGetAllWorkflowInstancesQuery();

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    assertThat(response.getTotalCount()).isEqualTo(5);
    assertThat(response.getWorkflowInstances().size()).isEqualTo(5);
  }

  @Test
  public void testQueryWithTwoFragments() throws Exception {
    createData();

    ListViewRequestDto workflowInstanceQueryDto = TestUtil.createWorkflowInstanceQuery(q -> {
      q.setRunning(true);    //1st fragment
      q.setActive(true);
      q.setIncidents(true);
    });
    //2nd fragment
    ListViewQueryDto query2 = new ListViewQueryDto();
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

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    assertThat(response.getTotalCount()).isEqualTo(5);
    assertThat(response.getWorkflowInstances().size()).isEqualTo(5);
  }

  @Test
  public void testQueryFinishedCompleted() throws Exception {
    createData();

    ListViewRequestDto workflowInstanceQueryDto = TestUtil.createWorkflowInstanceQuery(q -> {
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

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getWorkflowInstances().size()).isEqualTo(1);
    assertThat(response.getWorkflowInstances().get(0).getEndDate()).isNotNull();
    assertThat(response.getWorkflowInstances().get(0).getState()).isEqualTo(WorkflowInstanceStateDto.COMPLETED);
  }

  @Test
  public void testQueryFinishedCanceled() throws Exception {
    createData();

    ListViewRequestDto workflowInstanceQueryDto = TestUtil.createWorkflowInstanceQuery(q -> {
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

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getWorkflowInstances().size()).isEqualTo(1);
    assertThat(response.getWorkflowInstances().get(0).getEndDate()).isNotNull();
    assertThat(response.getWorkflowInstances().get(0).getState()).isEqualTo(WorkflowInstanceStateDto.CANCELED);
  }

  @Test
  public void testQueryRunningWithIncidents() throws Exception {
    createData();

    ListViewRequestDto workflowInstanceQueryDto = TestUtil.createWorkflowInstanceQuery(q -> {
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

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getWorkflowInstances().size()).isEqualTo(1);
    assertThat(response.getWorkflowInstances().get(0).getState()).isEqualTo(WorkflowInstanceStateDto.INCIDENT);

  }

  @Test
  public void testQueryRunningWithoutIncidents() throws Exception {
    createData();

    ListViewRequestDto workflowInstanceQueryDto = TestUtil.createWorkflowInstanceQuery(q -> {
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

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    assertThat(response.getTotalCount()).isEqualTo(2);
    assertThat(response.getWorkflowInstances().size()).isEqualTo(2);
    assertThat(response.getWorkflowInstances()).allMatch((wi) -> !wi.getState().equals(WorkflowInstanceStateDto.INCIDENT));

  }
//
//  @Test
//  public void testGetWorkflowInstanceById() throws Exception {
//    createData();
//
//    MockHttpServletRequestBuilder request = get(String.format(GET_INSTANCE_URL, instanceWithoutIncident.getId()));
//
//    MvcResult mvcResult = mockMvc
//      .perform(request)
//      .andExpect(status().isOk())
//      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
//      .andReturn();
//
//    final WorkflowInstanceDto workflowInstanceDto = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceDto>() {});
//
//    assertThat(workflowInstanceDto.getId()).isEqualTo(instanceWithoutIncident.getId());
//    assertThat(workflowInstanceDto.getWorkflowId()).isEqualTo(instanceWithoutIncident.getWorkflowId());
//    assertThat(workflowInstanceDto.getState()).isEqualTo(instanceWithoutIncident.getState());
//    assertThat(workflowInstanceDto.getStartDate()).isEqualTo(instanceWithoutIncident.getStartDate());
//    assertThat(workflowInstanceDto.getEndDate()).isEqualTo(instanceWithoutIncident.getEndDate());
//    assertThat(workflowInstanceDto.getBpmnProcessId()).isEqualTo(instanceWithoutIncident.getBpmnProcessId());
//
//    assertThat(workflowInstanceDto.getActivities().size()).isGreaterThan(0);
//    assertThat(workflowInstanceDto.getActivities().size()).isEqualTo(instanceWithoutIncident.getActivities().size());
//
//    assertThat(workflowInstanceDto.getIncidents().size()).isGreaterThan(0);
//    assertThat(workflowInstanceDto.getIncidents().size()).isEqualTo(instanceWithoutIncident.getIncidents().size());
//
//  }

  private void createData() {
    //running instance with one activity and without incidents
    final WorkflowInstanceForListViewEntity runningInstance = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    final ActivityInstanceForListViewEntity activityInstance1 = createActivityInstance(runningInstance.getId(), ActivityState.ACTIVE);

    //completed instance with one activity and without incidents
    final WorkflowInstanceForListViewEntity completedInstance = createWorkflowInstance(WorkflowInstanceState.COMPLETED);
    final ActivityInstanceForListViewEntity activityInstance2 = createActivityInstance(completedInstance.getId(), ActivityState.COMPLETED);

    //canceled instance with two activities and without incidents
    final WorkflowInstanceForListViewEntity canceledInstance = createWorkflowInstance(WorkflowInstanceState.CANCELED);
    final ActivityInstanceForListViewEntity activityInstance3 = createActivityInstance(canceledInstance.getId(), ActivityState.COMPLETED);
    final ActivityInstanceForListViewEntity activityInstance4 = createActivityInstance(canceledInstance.getId(), ActivityState.TERMINATED);

    //instance with incidents (one resolved and one active) and one active activity
    final WorkflowInstanceForListViewEntity instanceWithIncident = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    final ActivityInstanceForListViewEntity activityInstance5 = createActivityInstance(instanceWithIncident.getId(), ActivityState.ACTIVE);
    createIncident(activityInstance5, null, null);

    //instance with one resolved incident and one completed activity
    instanceWithoutIncident = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    final ActivityInstanceForListViewEntity activityInstance6 = createActivityInstance(instanceWithoutIncident.getId(), ActivityState.COMPLETED);

    //persist instances
    elasticsearchTestRule.persistNew(runningInstance, completedInstance, instanceWithIncident, instanceWithoutIncident, canceledInstance,
      activityInstance1, activityInstance2, activityInstance3, activityInstance4, activityInstance5, activityInstance6);
  }

//  private void addVariableEntity(WorkflowInstanceEntity workflowInstance, String name, String value) {
//    workflowInstance.getStringVariables().add(new StringVariableEntity(name, value));
//  }
//  private void addVariableEntity(WorkflowInstanceEntity workflowInstance, String name, Long value) {
//    workflowInstance.getLongVariables().add(new LongVariableEntity(name, value));
//  }
//  private void addVariableEntity(WorkflowInstanceEntity workflowInstance, String name, Double value) {
//    workflowInstance.getDoubleVariables().add(new DoubleVariableEntity(name, value));
//  }
//  private void addVariableEntity(WorkflowInstanceEntity workflowInstance, String name, Boolean value) {
//    workflowInstance.getBooleanVariables().add(new BooleanVariableEntity(name, value));
//  }

  private String query(int firstResult, int maxResults) {
    return String.format("%s?firstResult=%d&maxResults=%d", QUERY_INSTANCES_URL, firstResult, maxResults);
  }

}


