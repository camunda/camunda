package org.camunda.operate.es;

import java.nio.charset.Charset;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

  protected static final String QUERY_INSTANCES_URL = WORKFLOW_INSTANCE_URL;
  protected static final String GET_INSTANCE_URL = WORKFLOW_INSTANCE_URL + "/%s";
  protected static final String COUNT_INSTANCES_URL = WORKFLOW_INSTANCE_URL + "/count";

  private MediaType contentType = new MediaType(MediaType.APPLICATION_JSON.getType(),
    MediaType.APPLICATION_JSON.getSubtype(),
    Charset.forName("utf8"));

  private Random random = new Random();

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Rule
  public MockMvcTestRule mockMvcTestRule = new MockMvcTestRule();

  @Autowired
  private ElasticsearchBulkProcessor elasticsearchBulkProcessor;

  private WorkflowInstanceEntity instanceWithoutIncident;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  private String errorMessage = "No more retries left.";

  @Before
  public void starting() {
    this.mockMvc = mockMvcTestRule.getMockMvc();
    this.objectMapper = mockMvcTestRule.getObjectMapper();
    createData();
  }

  @Test
  public void testQueryAllRunningCount() throws Exception {
    //query running instances
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setRunning(true);
    workflowInstanceQueryDto.setActive(true);
    workflowInstanceQueryDto.setIncidents(true);

    testCountQuery(workflowInstanceQueryDto, 3);

  }

  @Test
  public void testQueryAllRunning() throws Exception {
    //query running instances
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setRunning(true);
    workflowInstanceQueryDto.setActive(true);
    workflowInstanceQueryDto.setIncidents(true);

    MockHttpServletRequestBuilder request = post(query(0, 100))
        .content(mockMvcTestRule.json(workflowInstanceQueryDto))
        .contentType(contentType);

    MvcResult mvcResult = mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentType(contentType))
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
  public void testQueryByErrorMessage() throws Exception {
    //given
    WorkflowInstanceQueryDto query = createGetAllWorkflowInstancesQuery();
    query.setErrorMessage(errorMessage);

    MockHttpServletRequestBuilder request = post(query(0, 100))
        .content(mockMvcTestRule.json(query))
        .contentType(contentType);
    //when
    MvcResult mvcResult = mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentType(contentType))
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
  public void testQueryByWorkflowInstanceIds() throws Exception {
    //given
    final WorkflowInstanceEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    final WorkflowInstanceEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.CANCELED);
    final WorkflowInstanceEntity workflowInstance3 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);
    try {
      elasticsearchBulkProcessor.persistOperateEntities(Arrays.asList(workflowInstance1, workflowInstance2, workflowInstance3));
    } catch (PersistenceException e) {
      throw new RuntimeException(e);
    }
    elasticsearchTestRule.refreshIndexesInElasticsearch();


    WorkflowInstanceQueryDto query = createGetAllWorkflowInstancesQuery();
    query.setIds(Arrays.asList(workflowInstance1.getId(), workflowInstance2.getId()));

    //when
    MockHttpServletRequestBuilder request = post(query(0, 100))
        .content(mockMvcTestRule.json(query))
        .contentType(contentType);

    MvcResult mvcResult = mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentType(contentType))
        .andReturn();

    //then
    List<WorkflowInstanceDto> workflowInstanceDtos = mockMvcTestRule.listFromResponse(mvcResult, WorkflowInstanceDto.class);

    assertThat(workflowInstanceDtos.size()).isEqualTo(2);

    assertThat(workflowInstanceDtos).extracting(WorkflowInstanceType.ID).containsExactlyInAnyOrder(workflowInstance1.getId(), workflowInstance2.getId());
  }

  @Test
  public void testPagination() throws Exception {
    //query running instances
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setRunning(true);
    workflowInstanceQueryDto.setActive(true);
    workflowInstanceQueryDto.setIncidents(true);

    MockHttpServletRequestBuilder request = post(query(1, 3))
        .content(mockMvcTestRule.json(workflowInstanceQueryDto))
        .contentType(contentType);

    MvcResult mvcResult = mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentType(contentType))
        .andReturn();

     List<WorkflowInstanceDto> workflowInstanceDtos = mockMvcTestRule.listFromResponse(mvcResult, WorkflowInstanceDto.class);

     assertThat(workflowInstanceDtos.size()).isEqualTo(2);

     for (WorkflowInstanceDto workflowInstanceDto: workflowInstanceDtos) {
       assertThat(workflowInstanceDto.getEndDate()).isNull();
       assertThat(workflowInstanceDto.getState()).isEqualTo(WorkflowInstanceState.ACTIVE);
       assertThat(workflowInstanceDto.getActivities()).isEmpty();
     }
  }


  @Test
  public void testSortingByStartDateAsc() throws Exception {
    //query running instances
    WorkflowInstanceQueryDto workflowInstanceQueryDto = createGetAllWorkflowInstancesQuery();
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("startDate");
    sorting.setSortOrder("asc");
    workflowInstanceQueryDto.setSorting(sorting);

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(contentType);

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(contentType))
      .andReturn();

    List<WorkflowInstanceDto> workflowInstanceDtos = mockMvcTestRule.listFromResponse(mvcResult, WorkflowInstanceDto.class);

    assertThat(workflowInstanceDtos.size()).isEqualTo(5);

    assertThat(workflowInstanceDtos).isSortedAccordingTo(Comparator.comparing(WorkflowInstanceDto::getStartDate));
  }

  @Test
  public void testSortingByStartDateDesc() throws Exception {
    //query running instances
    WorkflowInstanceQueryDto workflowInstanceQueryDto = createGetAllWorkflowInstancesQuery();
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("startDate");
    sorting.setSortOrder("desc");
    workflowInstanceQueryDto.setSorting(sorting);

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(contentType);

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(contentType))
      .andReturn();

    List<WorkflowInstanceDto> workflowInstanceDtos = mockMvcTestRule.listFromResponse(mvcResult, WorkflowInstanceDto.class);

    assertThat(workflowInstanceDtos.size()).isEqualTo(5);

    assertThat(workflowInstanceDtos).isSortedAccordingTo((o1, o2) -> o2.getStartDate().compareTo(o1.getStartDate()));
  }

  @Test
  public void testSortingByIdAsc() throws Exception {
    //query running instances
    WorkflowInstanceQueryDto workflowInstanceQueryDto = createGetAllWorkflowInstancesQuery();
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("id");
    sorting.setSortOrder("asc");
    workflowInstanceQueryDto.setSorting(sorting);

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(contentType);

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(contentType))
      .andReturn();

    List<WorkflowInstanceDto> workflowInstanceDtos = mockMvcTestRule.listFromResponse(mvcResult, WorkflowInstanceDto.class);

    assertThat(workflowInstanceDtos.size()).isEqualTo(5);

    assertThat(workflowInstanceDtos).isSortedAccordingTo(Comparator.comparing(WorkflowInstanceDto::getId));
  }

  @Test
  public void testSortingByIdDesc() throws Exception {
    //query running instances
    WorkflowInstanceQueryDto workflowInstanceQueryDto = createGetAllWorkflowInstancesQuery();
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("id");
    sorting.setSortOrder("desc");
    workflowInstanceQueryDto.setSorting(sorting);

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(contentType);

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(contentType))
      .andReturn();

    List<WorkflowInstanceDto> workflowInstanceDtos = mockMvcTestRule.listFromResponse(mvcResult, WorkflowInstanceDto.class);

    assertThat(workflowInstanceDtos.size()).isEqualTo(5);

    assertThat(workflowInstanceDtos).isSortedAccordingTo((o1, o2) -> o2.getId().compareTo(o1.getId()));
  }

  @Test
  public void testSortingByEndDateAsc() throws Exception {
    //query running instances
    WorkflowInstanceQueryDto workflowInstanceQueryDto = createGetAllWorkflowInstancesQuery();
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("endDate");
    sorting.setSortOrder("asc");
    workflowInstanceQueryDto.setSorting(sorting);

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(contentType);

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(contentType))
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
    //query running instances
    WorkflowInstanceQueryDto workflowInstanceQueryDto = createGetAllWorkflowInstancesQuery();
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("endDate");
    sorting.setSortOrder("desc");
    workflowInstanceQueryDto.setSorting(sorting);

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(contentType);

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(contentType))
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
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setFinished(true);
    workflowInstanceQueryDto.setCompleted(true);
    workflowInstanceQueryDto.setCanceled(true);

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(contentType);

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(contentType))
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
    WorkflowInstanceQueryDto workflowInstanceQueryDto = createGetAllWorkflowInstancesQuery();

    testCountQuery(workflowInstanceQueryDto, 5);
  }

  @Test
  public void testQueryFinishedAndRunning() throws Exception {
    WorkflowInstanceQueryDto workflowInstanceQueryDto = createGetAllWorkflowInstancesQuery();

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(contentType);

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(contentType))
      .andReturn();

    List<WorkflowInstanceDto> workflowInstanceDtos = mockMvcTestRule.listFromResponse(mvcResult, WorkflowInstanceDto.class);

    assertThat(workflowInstanceDtos.size()).isEqualTo(5);
  }

  @Test
  public void testQueryFinishedCompletedCount() throws Exception {
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setFinished(true);
    workflowInstanceQueryDto.setCompleted(true);

    testCountQuery(workflowInstanceQueryDto, 1);
  }

  @Test
  public void testQueryFinishedCompleted() throws Exception {
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setFinished(true);
    workflowInstanceQueryDto.setCompleted(true);

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(contentType);

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(contentType))
      .andReturn();

    List<WorkflowInstanceDto> workflowInstanceDtos = mockMvcTestRule.listFromResponse(mvcResult, WorkflowInstanceDto.class);

    assertThat(workflowInstanceDtos.size()).isEqualTo(1);
    assertThat(workflowInstanceDtos.get(0).getEndDate()).isNotNull();
    assertThat(workflowInstanceDtos.get(0).getState()).isEqualTo(WorkflowInstanceState.COMPLETED);
    assertThat(workflowInstanceDtos.get(0).getActivities()).isEmpty();
  }
  @Test
  public void testQueryFinishedCanceledCount() throws Exception {
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setFinished(true);
    workflowInstanceQueryDto.setCanceled(true);

    testCountQuery(workflowInstanceQueryDto, 1);
  }

  @Test
  public void testQueryFinishedCanceled() throws Exception {
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setFinished(true);
    workflowInstanceQueryDto.setCanceled(true);

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(contentType);

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(contentType))
      .andReturn();

    List<WorkflowInstanceDto> workflowInstanceDtos = mockMvcTestRule.listFromResponse(mvcResult, WorkflowInstanceDto.class);

    assertThat(workflowInstanceDtos.size()).isEqualTo(1);
    assertThat(workflowInstanceDtos.get(0).getEndDate()).isNotNull();
    assertThat(workflowInstanceDtos.get(0).getState()).isEqualTo(WorkflowInstanceState.CANCELED);
    assertThat(workflowInstanceDtos.get(0).getActivities()).isEmpty();
  }

  @Test
  public void testQueryRunningWithIncidentsCount() throws Exception {
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setRunning(true);
    workflowInstanceQueryDto.setIncidents(true);

    testCountQuery(workflowInstanceQueryDto, 1);
  }

  @Test
  public void testQueryRunningWithIncidents() throws Exception {
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setRunning(true);
    workflowInstanceQueryDto.setIncidents(true);

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(contentType);

    MvcResult mvcResult = mockMvc
      .perform(request)
      .andExpect(status().isOk())
      .andExpect(content()
        .contentType(contentType))
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
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setRunning(true);
    workflowInstanceQueryDto.setActive(true);

    testCountQuery(workflowInstanceQueryDto, 2);
  }

  @Test
  public void testQueryRunningWithoutIncidents() throws Exception {
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setRunning(true);
    workflowInstanceQueryDto.setActive(true);

    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(workflowInstanceQueryDto))
      .contentType(contentType);

    MvcResult mvcResult = mockMvc
      .perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(contentType))
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
    MockHttpServletRequestBuilder request = get(String.format(GET_INSTANCE_URL, instanceWithoutIncident.getId()));

    MvcResult mvcResult = mockMvc
      .perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(contentType))
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
      .contentType(contentType);

    mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(contentType))
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

    List<WorkflowInstanceEntity> workflowInstances = new ArrayList<>();
    workflowInstances.addAll(Arrays.asList(runningInstance, completedInstance, instanceWithIncident, instanceWithoutIncident, canceledInstance));

    //persist instances
    try {
      elasticsearchBulkProcessor.persistOperateEntities(workflowInstances);
    } catch (PersistenceException e) {
      throw new RuntimeException(e);
    }
    elasticsearchTestRule.refreshIndexesInElasticsearch();
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

  private IncidentEntity createIncident(IncidentState state) {
    IncidentEntity incidentEntity = new IncidentEntity();
    incidentEntity.setId(UUID.randomUUID().toString());
    incidentEntity.setActivityId("start");
    incidentEntity.setActivityInstanceId(UUID.randomUUID().toString());
    incidentEntity.setErrorType("TASK_NO_RETRIES");
    incidentEntity.setErrorMessage(errorMessage);
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

  protected String query(int firstResult, int maxResults) {
    return String.format("%s?firstResult=%d&maxResults=%d", QUERY_INSTANCES_URL, firstResult, maxResults);
  }
}
