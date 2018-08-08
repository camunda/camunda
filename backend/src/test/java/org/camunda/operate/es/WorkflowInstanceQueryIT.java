package org.camunda.operate.es;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;
import java.util.UUID;
import org.camunda.operate.entities.ActivityInstanceEntity;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.es.types.WorkflowInstanceType;
import org.camunda.operate.rest.dto.IncidentDto;
import org.camunda.operate.rest.dto.SortingDto;
import org.camunda.operate.rest.dto.WorkflowInstanceDto;
import org.camunda.operate.rest.dto.WorkflowInstanceQueryDto;
import org.camunda.operate.rest.dto.WorkflowInstanceRequestDto;
import org.camunda.operate.rest.dto.WorkflowInstanceResponseDto;
import org.camunda.operate.util.DateUtil;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests Elasticsearch queries for workflow instances.
 */
public class WorkflowInstanceQueryIT extends OperateIntegrationTest {

  @FunctionalInterface
  interface FiltersSupplier {
    void applyFilters(WorkflowInstanceQueryDto query);
  }

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

    elasticsearchTestRule.persist(workflowInstance1, workflowInstance2);

    //when
    WorkflowInstanceRequestDto query = createGetAllWorkflowInstancesQuery(q ->
      q.setActivityId(activityId));

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


  @Test
  public void testSortingByStartDateAsc() throws Exception {
    createData();

    //query running instances
    WorkflowInstanceRequestDto workflowInstanceQueryDto = createGetAllWorkflowInstancesQuery();
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

    WorkflowInstanceResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() { });

    assertThat(response.getWorkflowInstances().size()).isEqualTo(5);

    assertThat(response.getWorkflowInstances()).isSortedAccordingTo(Comparator.comparing(WorkflowInstanceDto::getStartDate));
  }

  @Test
  public void testSortingByStartDateDesc() throws Exception {
    createData();

    //query running instances
    WorkflowInstanceRequestDto workflowInstanceQueryDto = createGetAllWorkflowInstancesQuery();
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

    WorkflowInstanceResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() { });

    assertThat(response.getWorkflowInstances().size()).isEqualTo(5);

    assertThat(response.getWorkflowInstances()).isSortedAccordingTo((o1, o2) -> o2.getStartDate().compareTo(o1.getStartDate()));
  }

  @Test
  public void testSortingByIdAsc() throws Exception {
    createData();

    //query running instances
    WorkflowInstanceRequestDto workflowInstanceQueryDto = createGetAllWorkflowInstancesQuery();
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

    WorkflowInstanceResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() { });

    assertThat(response.getWorkflowInstances().size()).isEqualTo(5);

    assertThat(response.getWorkflowInstances()).isSortedAccordingTo(Comparator.comparing(WorkflowInstanceDto::getId));
  }

  @Test
  public void testSortingByIdDesc() throws Exception {
    createData();

    //query running instances
    WorkflowInstanceRequestDto workflowInstanceQueryDto = createGetAllWorkflowInstancesQuery();
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

    WorkflowInstanceResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() { });

    assertThat(response.getWorkflowInstances().size()).isEqualTo(5);

    assertThat(response.getWorkflowInstances()).isSortedAccordingTo((o1, o2) -> o2.getId().compareTo(o1.getId()));
  }

  @Test
  public void testSortingByEndDateAsc() throws Exception {
    createData();

    //query running instances
    WorkflowInstanceRequestDto workflowInstanceQueryDto = createGetAllWorkflowInstancesQuery();
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

    WorkflowInstanceResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() { });

    assertThat(response.getWorkflowInstances().size()).isEqualTo(5);

    assertThat(response.getWorkflowInstances()).isSortedAccordingTo((o1, o2) -> {
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
    WorkflowInstanceRequestDto workflowInstanceQueryDto = createGetAllWorkflowInstancesQuery();
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

    WorkflowInstanceResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() { });

    assertThat(response.getWorkflowInstances().size()).isEqualTo(5);

    assertThat(response.getWorkflowInstances()).isSortedAccordingTo((o1, o2) -> {
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

  private WorkflowInstanceRequestDto createGetAllWorkflowInstancesQuery(FiltersSupplier filtersSupplier) {
    final WorkflowInstanceRequestDto workflowInstanceQuery = createGetAllWorkflowInstancesQuery();
    filtersSupplier.applyFilters(workflowInstanceQuery.getQueries().get(0));

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
    assertThat(workflowInstanceDto.getBusinessKey()).isEqualTo(instanceWithoutIncident.getBusinessKey());

    assertThat(workflowInstanceDto.getActivities().size()).isGreaterThan(0);
    assertThat(workflowInstanceDto.getActivities().size()).isEqualTo(instanceWithoutIncident.getActivities().size());

    assertThat(workflowInstanceDto.getIncidents().size()).isGreaterThan(0);
    assertThat(workflowInstanceDto.getIncidents().size()).isEqualTo(instanceWithoutIncident.getIncidents().size());

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
    elasticsearchTestRule.persist(runningInstance, completedInstance, instanceWithIncident, instanceWithoutIncident, canceledInstance);
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

  private WorkflowInstanceRequestDto createWorkflowInstanceQuery(FiltersSupplier filtersSupplier) {
    WorkflowInstanceRequestDto request = new WorkflowInstanceRequestDto();
    WorkflowInstanceQueryDto query = new WorkflowInstanceQueryDto();
    filtersSupplier.applyFilters(query);
    request.getQueries().add(query);
    return request;
  }


}


