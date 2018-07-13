package org.camunda.operate.es;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.camunda.operate.entities.ActivityInstanceEntity;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.es.types.WorkflowInstanceType;
import org.camunda.operate.es.writer.ElasticsearchBulkProcessor;
import org.camunda.operate.es.writer.PersistenceException;
import org.camunda.operate.rest.dto.IncidentDto;
import org.camunda.operate.rest.dto.SortingDto;
import org.camunda.operate.rest.dto.WorkflowInstanceDto;
import org.camunda.operate.rest.dto.WorkflowInstanceQueryDto;
import org.camunda.operate.util.DateUtil;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.MockMvcTestRule;
import org.camunda.operate.util.OperateIntegrationTest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.fasterxml.jackson.core.type.TypeReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.rest.WorkflowInstanceRestService.WORKFLOW_INSTANCE_URL;
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
  private static final String COUNT_INSTANCES_URL = WORKFLOW_INSTANCE_URL + "/count";

  private Random random = new Random();

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Rule
  public MockMvcTestRule mockMvcTestRule = new MockMvcTestRule();

  @Autowired
  private ElasticsearchBulkProcessor elasticsearchBulkProcessor;

  private WorkflowInstanceEntity instanceWithoutIncident;

  private MockMvc mockMvc;

  @Before
  public void starting() {
    this.mockMvc = mockMvcTestRule.getMockMvc();
  }

  @Test
  public void testQueryAllRunningCount() throws Exception {
    createData();

    //query running instances
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setRunning(true);
    workflowInstanceQueryDto.setActive(true);
    workflowInstanceQueryDto.setIncidents(true);

    testCountQuery(workflowInstanceQueryDto, 3);

  }

  @Test
  public void testQueryAllRunning() throws Exception {
    createData();

    //query running instances
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setRunning(true);
    workflowInstanceQueryDto.setActive(true);
    workflowInstanceQueryDto.setIncidents(true);

    MockHttpServletRequestBuilder request = post(query(0, 100))
        .content(mockMvcTestRule.json(workflowInstanceQueryDto))
        .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentType(mockMvcTestRule.getContentType()))
        .andReturn();

     List<WorkflowInstanceDto> workflowInstanceDtos = mockMvcTestRule.listFromResponse(mvcResult, WorkflowInstanceDto.class);

     assertThat(workflowInstanceDtos.size()).isEqualTo(3);

     for (WorkflowInstanceDto workflowInstanceDto: workflowInstanceDtos) {
       assertThat(workflowInstanceDto.getEndDate()).isNull();
       assertThat(workflowInstanceDto.getState()).isEqualTo(WorkflowInstanceState.ACTIVE);
       assertThat(workflowInstanceDto.getActivities()).isEmpty();
     }
  }

  @Test
  public void testQueryByStartAndEndDate() throws Exception {
    //given
    final OffsetDateTime date1 = OffsetDateTime.of(2018, 1, 1, 15, 30, 30, 156, OffsetDateTime.now().getOffset());      //January 1, 2018
    final OffsetDateTime date2 = OffsetDateTime.of(2018, 2, 1, 12, 00, 30, 457, OffsetDateTime.now().getOffset());      //February 1, 2018
    final OffsetDateTime date3 = OffsetDateTime.of(2018, 3, 1, 17, 15, 14, 235, OffsetDateTime.now().getOffset());      //March 1, 2018
    final OffsetDateTime date4 = OffsetDateTime.of(2018, 4, 1, 2, 12, 0, 0, OffsetDateTime.now().getOffset());          //April 1, 2018
    final OffsetDateTime date5 = OffsetDateTime.of(2018, 5, 1, 23, 30, 15, 666, OffsetDateTime.now().getOffset());      //May 1, 2018
    final WorkflowInstanceEntity workflowInstance1 = createWorkflowInstance(date1, date5);
    final WorkflowInstanceEntity workflowInstance2 = createWorkflowInstance(date2, date4);
    final WorkflowInstanceEntity workflowInstance3 = createWorkflowInstance(date3, null);
    persist(workflowInstance1, workflowInstance2, workflowInstance3);

    //when
    WorkflowInstanceQueryDto query = createGetAllWorkflowInstancesQuery();
    query.setStartDateAfter(date1.minus(1, ChronoUnit.DAYS));
    query.setStartDateBefore(date3);
    //then
    requestAndAssertIds(query, "TEST CASE #1", workflowInstance1.getId(), workflowInstance2.getId());

    //test inclusion for startDateAfter and exclusion for startDateBefore
    //when
    query = createGetAllWorkflowInstancesQuery();
    query.setStartDateAfter(date1);
    query.setStartDateBefore(date3);
    //then
    requestAndAssertIds(query, "TEST CASE #2", workflowInstance1.getId(), workflowInstance2.getId());

    //when
    query = createGetAllWorkflowInstancesQuery();
    query.setStartDateAfter(date1.plus(1, ChronoUnit.MILLIS));
    query.setStartDateBefore(date3.plus(1, ChronoUnit.MILLIS));
    //then
    requestAndAssertIds(query, "TEST CASE #3", workflowInstance2.getId(), workflowInstance3.getId());

    //test combination of start date and end date
    //when
    query = createGetAllWorkflowInstancesQuery();
    query.setStartDateAfter(date2.minus(1, ChronoUnit.DAYS));
    query.setStartDateBefore(date3.plus(1, ChronoUnit.DAYS));
    query.setEndDateAfter(date4.minus(1, ChronoUnit.DAYS));
    query.setEndDateBefore(date4.plus(1, ChronoUnit.DAYS));
    //then
    requestAndAssertIds(query, "TEST CASE #4", workflowInstance2.getId());

    //test inclusion for endDateAfter and exclusion for endDateBefore
    //when
    query = createGetAllWorkflowInstancesQuery();
    query.setEndDateAfter(date4);
    query.setEndDateBefore(date5);
    //then
    requestAndAssertIds(query, "TEST CASE #5", workflowInstance2.getId());

    //when
    query = createGetAllWorkflowInstancesQuery();
    query.setEndDateAfter(date4);
    query.setEndDateBefore(date5.plus(1, ChronoUnit.MILLIS));
    //then
    requestAndAssertIds(query, "TEST CASE #6", workflowInstance1.getId(), workflowInstance2.getId());

  }

  private void requestAndAssertIds(WorkflowInstanceQueryDto query, String testCaseName, String... ids) throws Exception {
    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(query))
      .contentType(mockMvcTestRule.getContentType());
    //then
    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    List<WorkflowInstanceDto> workflowInstances = mockMvcTestRule.listFromResponse(mvcResult, WorkflowInstanceDto.class);

    assertThat(workflowInstances).as(testCaseName).extracting(WorkflowInstanceType.ID).containsExactlyInAnyOrder(ids);
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

    persist(workflowInstance1, workflowInstance2);

    //given
    WorkflowInstanceQueryDto query = createGetAllWorkflowInstancesQuery();
    query.setErrorMessage(errorMessage);

    MockHttpServletRequestBuilder request = post(query(0, 100))
        .content(mockMvcTestRule.json(query))
        .contentType(mockMvcTestRule.getContentType());
    //when
    MvcResult mvcResult = mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentType(mockMvcTestRule.getContentType()))
        .andReturn();

    List<WorkflowInstanceDto> workflowInstanceDtos = mockMvcTestRule.listFromResponse(mvcResult, WorkflowInstanceDto.class);

    //then
    assertThat(workflowInstanceDtos.size()).isEqualTo(1);

    assertThat(workflowInstanceDtos.get(0).getIncidents())
      .filteredOn(incident -> incident.getState().equals(IncidentState.ACTIVE))
      .extracting(WorkflowInstanceType.ERROR_MSG)
      .containsExactly(errorMessage);

  }

  @Test
  public void testQueryByActivityId() throws Exception {
    final String activityId = "taskA";

    //given we have 2 workflow instances: one with active activity with given id, another with completed activity with given id
    final WorkflowInstanceEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);

    final ActivityInstanceEntity activeWithIdActivityInstance = createActivityInstance(ActivityState.ACTIVE);
    activeWithIdActivityInstance.setActivityId(activityId);
    workflowInstance1.getActivities().add(activeWithIdActivityInstance);

    final ActivityInstanceEntity completedWithoutIdActivityInstance = createActivityInstance(ActivityState.COMPLETED);
    completedWithoutIdActivityInstance.setActivityId("otherActivityId");
    workflowInstance1.getActivities().add(completedWithoutIdActivityInstance);

    final WorkflowInstanceEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);

    final ActivityInstanceEntity activeWithoutIdActivityInstance = createActivityInstance(ActivityState.ACTIVE);
    activeWithoutIdActivityInstance.setActivityId("otherActivityId");
    workflowInstance2.getActivities().add(activeWithoutIdActivityInstance);

    final ActivityInstanceEntity completedWithIdActivityInstance = createActivityInstance(ActivityState.COMPLETED);
    completedWithIdActivityInstance.setActivityId(activityId);
    workflowInstance2.getActivities().add(completedWithIdActivityInstance);

    persist(workflowInstance1, workflowInstance2);

    //when
    WorkflowInstanceQueryDto query = createGetAllWorkflowInstancesQuery();
    query.setActivityId(activityId);

    MockHttpServletRequestBuilder request = post(query(0, 100))
        .content(mockMvcTestRule.json(query))
        .contentType(mockMvcTestRule.getContentType());
    MvcResult mvcResult = mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentType(mockMvcTestRule.getContentType()))
        .andReturn();

    List<WorkflowInstanceDto> workflowInstanceDtos = mockMvcTestRule.listFromResponse(mvcResult, WorkflowInstanceDto.class);

    //then
    assertThat(workflowInstanceDtos.size()).isEqualTo(1);

    assertThat(workflowInstanceDtos.get(0).getId())
      .isEqualTo(workflowInstance1.getId());

  }

  @Test
  public void testQueryByWorkflowInstanceIds() throws Exception {
    //given
    final WorkflowInstanceEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    final WorkflowInstanceEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.CANCELED);
    final WorkflowInstanceEntity workflowInstance3 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);
    persist(workflowInstance1, workflowInstance2, workflowInstance3);

    WorkflowInstanceQueryDto query = createGetAllWorkflowInstancesQuery();
    query.setIds(Arrays.asList(workflowInstance1.getId(), workflowInstance2.getId()));

    //when
    MockHttpServletRequestBuilder request = post(query(0, 100))
        .content(mockMvcTestRule.json(query))
        .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentType(mockMvcTestRule.getContentType()))
        .andReturn();

    //then
    List<WorkflowInstanceDto> workflowInstanceDtos = mockMvcTestRule.listFromResponse(mvcResult, WorkflowInstanceDto.class);

    assertThat(workflowInstanceDtos).hasSize(2);

    assertThat(workflowInstanceDtos).extracting(WorkflowInstanceType.ID).containsExactlyInAnyOrder(workflowInstance1.getId(), workflowInstance2.getId());
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

    persist(workflowInstance1, workflowInstance2, workflowInstance3, workflowInstance4);

    WorkflowInstanceQueryDto query = createGetAllWorkflowInstancesQuery();
    query.setWorkflowIds(Arrays.asList(wfId1, wfId3));

    //when
    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(query))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    //then
    List<WorkflowInstanceDto> workflowInstanceDtos = mockMvcTestRule.listFromResponse(mvcResult, WorkflowInstanceDto.class);

    assertThat(workflowInstanceDtos).hasSize(3);

    assertThat(workflowInstanceDtos).extracting(WorkflowInstanceType.ID)
      .containsExactlyInAnyOrder(workflowInstance1.getId(), workflowInstance3.getId(), workflowInstance4.getId());
  }

  private void persist(WorkflowInstanceEntity... entitiesToPersist) {
    try {
      elasticsearchBulkProcessor.persistOperateEntities(Arrays.asList(entitiesToPersist));
    } catch (PersistenceException e) {
      throw new RuntimeException(e);
    }
    elasticsearchTestRule.refreshIndexesInElasticsearch();
  }

  @Test
  public void testPagination() throws Exception {
    createData();

    //query running instances
    WorkflowInstanceQueryDto workflowInstanceQueryDto = createGetAllWorkflowInstancesQuery();

    //page 1
    MockHttpServletRequestBuilder request = post(query(0, 3))
        .content(mockMvcTestRule.json(workflowInstanceQueryDto))
        .contentType(mockMvcTestRule.getContentType());
    MvcResult mvcResult = mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentType(mockMvcTestRule.getContentType()))
        .andReturn();
     List<WorkflowInstanceDto> workflowInstanceDtos = mockMvcTestRule.listFromResponse(mvcResult, WorkflowInstanceDto.class);
     assertThat(workflowInstanceDtos.size()).isEqualTo(3);

    //page 2
    request = post(query(3, 3))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(mockMvcTestRule.getContentType());
    mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();
    workflowInstanceDtos = mockMvcTestRule.listFromResponse(mvcResult, WorkflowInstanceDto.class);
    assertThat(workflowInstanceDtos.size()).isEqualTo(2);
  }


  @Test
  public void testSortingByStartDateAsc() throws Exception {
    createData();

    //query running instances
    WorkflowInstanceQueryDto workflowInstanceQueryDto = createGetAllWorkflowInstancesQuery();
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("startDate");
    sorting.setSortOrder("asc");
    workflowInstanceQueryDto.setSorting(sorting);

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    List<WorkflowInstanceDto> workflowInstanceDtos = mockMvcTestRule.listFromResponse(mvcResult, WorkflowInstanceDto.class);

    assertThat(workflowInstanceDtos.size()).isEqualTo(5);

    assertThat(workflowInstanceDtos).isSortedAccordingTo(Comparator.comparing(WorkflowInstanceDto::getStartDate));
  }

  @Test
  public void testSortingByStartDateDesc() throws Exception {
    createData();

    //query running instances
    WorkflowInstanceQueryDto workflowInstanceQueryDto = createGetAllWorkflowInstancesQuery();
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("startDate");
    sorting.setSortOrder("desc");
    workflowInstanceQueryDto.setSorting(sorting);

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    List<WorkflowInstanceDto> workflowInstanceDtos = mockMvcTestRule.listFromResponse(mvcResult, WorkflowInstanceDto.class);

    assertThat(workflowInstanceDtos.size()).isEqualTo(5);

    assertThat(workflowInstanceDtos).isSortedAccordingTo((o1, o2) -> o2.getStartDate().compareTo(o1.getStartDate()));
  }

  @Test
  public void testSortingByIdAsc() throws Exception {
    createData();

    //query running instances
    WorkflowInstanceQueryDto workflowInstanceQueryDto = createGetAllWorkflowInstancesQuery();
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("id");
    sorting.setSortOrder("asc");
    workflowInstanceQueryDto.setSorting(sorting);

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    List<WorkflowInstanceDto> workflowInstanceDtos = mockMvcTestRule.listFromResponse(mvcResult, WorkflowInstanceDto.class);

    assertThat(workflowInstanceDtos.size()).isEqualTo(5);

    assertThat(workflowInstanceDtos).isSortedAccordingTo(Comparator.comparing(WorkflowInstanceDto::getId));
  }

  @Test
  public void testSortingByIdDesc() throws Exception {
    createData();

    //query running instances
    WorkflowInstanceQueryDto workflowInstanceQueryDto = createGetAllWorkflowInstancesQuery();
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("id");
    sorting.setSortOrder("desc");
    workflowInstanceQueryDto.setSorting(sorting);

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    List<WorkflowInstanceDto> workflowInstanceDtos = mockMvcTestRule.listFromResponse(mvcResult, WorkflowInstanceDto.class);

    assertThat(workflowInstanceDtos.size()).isEqualTo(5);

    assertThat(workflowInstanceDtos).isSortedAccordingTo((o1, o2) -> o2.getId().compareTo(o1.getId()));
  }

  @Test
  public void testSortingByEndDateAsc() throws Exception {
    createData();

    //query running instances
    WorkflowInstanceQueryDto workflowInstanceQueryDto = createGetAllWorkflowInstancesQuery();
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("endDate");
    sorting.setSortOrder("asc");
    workflowInstanceQueryDto.setSorting(sorting);

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    List<WorkflowInstanceDto> workflowInstanceDtos = mockMvcTestRule.listFromResponse(mvcResult, WorkflowInstanceDto.class);

    assertThat(workflowInstanceDtos.size()).isEqualTo(5);

    assertThat(workflowInstanceDtos).isSortedAccordingTo((o1, o2) -> {
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
    });
  }

  @Test
  public void testSortingByEndDateDesc() throws Exception {
    createData();

    //query running instances
    WorkflowInstanceQueryDto workflowInstanceQueryDto = createGetAllWorkflowInstancesQuery();
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("endDate");
    sorting.setSortOrder("desc");
    workflowInstanceQueryDto.setSorting(sorting);

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    List<WorkflowInstanceDto> workflowInstanceDtos = mockMvcTestRule.listFromResponse(mvcResult, WorkflowInstanceDto.class);

    assertThat(workflowInstanceDtos.size()).isEqualTo(5);

    assertThat(workflowInstanceDtos).isSortedAccordingTo((o1, o2) -> {
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
    });
  }

  @Test
  public void testQueryAllFinishedCount() throws Exception {
    createData();

    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setFinished(true);
    workflowInstanceQueryDto.setCompleted(true);
    workflowInstanceQueryDto.setCanceled(true);

    testCountQuery(workflowInstanceQueryDto, 2);
  }

  private WorkflowInstanceQueryDto createGetAllWorkflowInstancesQuery() {
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setRunning(true);
    workflowInstanceQueryDto.setActive(true);
    workflowInstanceQueryDto.setIncidents(true);
    workflowInstanceQueryDto.setFinished(true);
    workflowInstanceQueryDto.setCompleted(true);
    workflowInstanceQueryDto.setCanceled(true);
    return workflowInstanceQueryDto;
  }

  @Test
  public void testQueryAllFinished() throws Exception {
    createData();

    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setFinished(true);
    workflowInstanceQueryDto.setCompleted(true);
    workflowInstanceQueryDto.setCanceled(true);

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    List<WorkflowInstanceDto> workflowInstanceDtos = mockMvcTestRule.listFromResponse(mvcResult, WorkflowInstanceDto.class);

    assertThat(workflowInstanceDtos.size()).isEqualTo(2);
    for (WorkflowInstanceDto workflowInstanceDto : workflowInstanceDtos) {
      assertThat(workflowInstanceDto.getEndDate()).isNotNull();
      assertThat(workflowInstanceDto.getState()).isIn(WorkflowInstanceState.COMPLETED, WorkflowInstanceState.CANCELED);
      assertThat(workflowInstanceDto.getActivities()).isEmpty();
    }
  }

  @Test
  public void testQueryFinishedAndRunningCount() throws Exception {
    createData();

    WorkflowInstanceQueryDto workflowInstanceQueryDto = createGetAllWorkflowInstancesQuery();

    testCountQuery(workflowInstanceQueryDto, 5);
  }

  @Test
  public void testQueryFinishedAndRunning() throws Exception {
    createData();

    WorkflowInstanceQueryDto workflowInstanceQueryDto = createGetAllWorkflowInstancesQuery();

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    List<WorkflowInstanceDto> workflowInstanceDtos = mockMvcTestRule.listFromResponse(mvcResult, WorkflowInstanceDto.class);

    assertThat(workflowInstanceDtos.size()).isEqualTo(5);
  }

  @Test
  public void testQueryFinishedCompletedCount() throws Exception {
    createData();

    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setFinished(true);
    workflowInstanceQueryDto.setCompleted(true);

    testCountQuery(workflowInstanceQueryDto, 1);
  }

  @Test
  public void testQueryFinishedCompleted() throws Exception {
    createData();

    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setFinished(true);
    workflowInstanceQueryDto.setCompleted(true);

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    List<WorkflowInstanceDto> workflowInstanceDtos = mockMvcTestRule.listFromResponse(mvcResult, WorkflowInstanceDto.class);

    assertThat(workflowInstanceDtos.size()).isEqualTo(1);
    assertThat(workflowInstanceDtos.get(0).getEndDate()).isNotNull();
    assertThat(workflowInstanceDtos.get(0).getState()).isEqualTo(WorkflowInstanceState.COMPLETED);
    assertThat(workflowInstanceDtos.get(0).getActivities()).isEmpty();
  }
  @Test
  public void testQueryFinishedCanceledCount() throws Exception {
    createData();

    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setFinished(true);
    workflowInstanceQueryDto.setCanceled(true);

    testCountQuery(workflowInstanceQueryDto, 1);
  }

  @Test
  public void testQueryFinishedCanceled() throws Exception {
    createData();

    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setFinished(true);
    workflowInstanceQueryDto.setCanceled(true);

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    List<WorkflowInstanceDto> workflowInstanceDtos = mockMvcTestRule.listFromResponse(mvcResult, WorkflowInstanceDto.class);

    assertThat(workflowInstanceDtos.size()).isEqualTo(1);
    assertThat(workflowInstanceDtos.get(0).getEndDate()).isNotNull();
    assertThat(workflowInstanceDtos.get(0).getState()).isEqualTo(WorkflowInstanceState.CANCELED);
    assertThat(workflowInstanceDtos.get(0).getActivities()).isEmpty();
  }

  @Test
  public void testQueryRunningWithIncidentsCount() throws Exception {
    createData();

    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setRunning(true);
    workflowInstanceQueryDto.setIncidents(true);

    testCountQuery(workflowInstanceQueryDto, 1);
  }

  @Test
  public void testQueryRunningWithIncidents() throws Exception {
    createData();

    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setRunning(true);
    workflowInstanceQueryDto.setIncidents(true);

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc
      .perform(request)
      .andExpect(status().isOk())
      .andExpect(content()
        .contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    List<WorkflowInstanceDto> workflowInstanceDtos = mockMvcTestRule.listFromResponse(mvcResult, WorkflowInstanceDto.class);

    assertThat(workflowInstanceDtos.size()).isEqualTo(1);
    assertThat(workflowInstanceDtos.get(0).getActivities()).isEmpty();
    assertThat(workflowInstanceDtos.get(0).getIncidents().size()).isEqualTo(2);


    IncidentDto activeIncident = null;
    for (IncidentDto incident: workflowInstanceDtos.get(0).getIncidents()) {
      if (incident.getState().equals(IncidentState.ACTIVE)) {
        activeIncident = incident;
      }
    }
    assertThat(activeIncident).isNotNull();
  }

  @Test
  public void testQueryRunningWithoutIncidentsCount() throws Exception {
    createData();

    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setRunning(true);
    workflowInstanceQueryDto.setActive(true);

    testCountQuery(workflowInstanceQueryDto, 2);
  }

  @Test
  public void testQueryRunningWithoutIncidents() throws Exception {
    createData();

    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setRunning(true);
    workflowInstanceQueryDto.setActive(true);

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc
      .perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    List<WorkflowInstanceDto> workflowInstanceDtos = mockMvcTestRule.listFromResponse(mvcResult, WorkflowInstanceDto.class);

    assertThat(workflowInstanceDtos.size()).isEqualTo(2);

    for (WorkflowInstanceDto workflowInstanceDto: workflowInstanceDtos) {
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
    assertThat(workflowInstanceDto.getBusinessKey()).isEqualTo(instanceWithoutIncident.getBusinessKey());

    assertThat(workflowInstanceDto.getActivities().size()).isGreaterThan(0);
    assertThat(workflowInstanceDto.getActivities().size()).isEqualTo(instanceWithoutIncident.getActivities().size());

    assertThat(workflowInstanceDto.getIncidents().size()).isGreaterThan(0);
    assertThat(workflowInstanceDto.getIncidents().size()).isEqualTo(instanceWithoutIncident.getIncidents().size());

  }

  private void testCountQuery(WorkflowInstanceQueryDto workflowInstanceQueryDto, int count) throws Exception {
    MockHttpServletRequestBuilder request = post(COUNT_INSTANCES_URL)
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(mockMvcTestRule.getContentType());

    mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andExpect(content().json(String.format("{\"count\":%d}",count)));
  }

  private void createData() {
    //running instance with one activity and without incidents
    final WorkflowInstanceEntity runningInstance = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    runningInstance.getActivities().add(createActivityInstance(ActivityState.ACTIVE));

    //completed instance with one activity and without incidents
    final WorkflowInstanceEntity completedInstance = createWorkflowInstance(WorkflowInstanceState.COMPLETED);
    completedInstance.getActivities().add(createActivityInstance(ActivityState.COMPLETED));

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
    persist(runningInstance, completedInstance, instanceWithIncident, instanceWithoutIncident, canceledInstance);
  }

  private WorkflowInstanceEntity createWorkflowInstance(WorkflowInstanceState state) {
    WorkflowInstanceEntity workflowInstance = new WorkflowInstanceEntity();
    workflowInstance.setId(UUID.randomUUID().toString());
    workflowInstance.setBusinessKey("testProcess" + random.nextInt(10));
    workflowInstance.setStartDate(DateUtil.getRandomStartDate());
    if (state.equals(WorkflowInstanceState.COMPLETED) || state.equals(WorkflowInstanceState.CANCELED)) {
      final OffsetDateTime endDate = DateUtil.getRandomEndDate();
      workflowInstance.setEndDate(endDate);
    }
    workflowInstance.setState(state);
    return workflowInstance;
  }

  private WorkflowInstanceEntity createWorkflowInstance(OffsetDateTime startDate, OffsetDateTime endDate) {
    WorkflowInstanceEntity workflowInstance = new WorkflowInstanceEntity();
    workflowInstance.setId(UUID.randomUUID().toString());
    workflowInstance.setBusinessKey("testProcess" + random.nextInt(10));
    workflowInstance.setStartDate(startDate);
    workflowInstance.setState(WorkflowInstanceState.ACTIVE);
    if (endDate != null) {
      workflowInstance.setEndDate(endDate);
      workflowInstance.setState(WorkflowInstanceState.COMPLETED);
    }
    return workflowInstance;
  }

  private IncidentEntity createIncident(IncidentState state) {
    IncidentEntity incidentEntity = new IncidentEntity();
    incidentEntity.setId(UUID.randomUUID().toString());
    incidentEntity.setActivityId("start");
    incidentEntity.setActivityInstanceId(UUID.randomUUID().toString());
    incidentEntity.setErrorType("TASK_NO_RETRIES");
    incidentEntity.setErrorMessage("No more retries left.");
    incidentEntity.setState(state);
    return incidentEntity;
  }

  private ActivityInstanceEntity createActivityInstance(ActivityState state) {
    ActivityInstanceEntity activityInstanceEntity = new ActivityInstanceEntity();
    activityInstanceEntity.setId(UUID.randomUUID().toString());
    activityInstanceEntity.setActivityId("start");
    activityInstanceEntity.setStartDate(DateUtil.getRandomStartDate());
    activityInstanceEntity.setState(state);
    if (state.equals(ActivityState.COMPLETED) || state.equals(ActivityState.TERMINATED)) {
      activityInstanceEntity.setEndDate(DateUtil.getRandomEndDate());
    }
    return activityInstanceEntity;
  }

  private String query(int firstResult, int maxResults) {
    return String.format("%s?firstResult=%d&maxResults=%d", QUERY_INSTANCES_URL, firstResult, maxResults);
  }
}
