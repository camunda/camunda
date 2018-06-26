package org.camunda.operate.es;

import java.io.IOException;
import java.nio.charset.Charset;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.camunda.operate.entities.ActivityInstanceEntity;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.es.writer.ElasticsearchBulkProcessor;
import org.camunda.operate.es.writer.PersistenceException;
import org.camunda.operate.rest.dto.IncidentDto;
import org.camunda.operate.rest.dto.WorkflowInstanceDto;
import org.camunda.operate.rest.dto.WorkflowInstanceQueryDto;
import org.camunda.operate.util.DateUtil;
import org.camunda.operate.util.ElasticsearchTestRule;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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

  @Autowired
  private ElasticsearchBulkProcessor elasticsearchBulkProcessor;

  private WorkflowInstanceEntity instanceWithoutIncident;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @Before
  public void starting() {
    this.mockMvc = elasticsearchTestRule.getMockMvc();
    this.objectMapper = elasticsearchTestRule.getObjectMapper();
    createData();
  }

  @Test
  public void testQueryRunningWorkflowInstancesCount() throws Exception {
    //query running instances
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setRunning(true);

    MockHttpServletRequestBuilder request = post(COUNT_INSTANCES_URL)
      .content(elasticsearchTestRule.json(workflowInstanceQueryDto))
      .contentType(contentType);

    mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(contentType))
      .andExpect(content().json("{\"count\":3}"));
  }

  @Test
  public void testQueryRunningWorkflowInstances() throws Exception {
    //query running instances
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setRunning(true);

    MockHttpServletRequestBuilder request = post(query(0, 3))
        .content(elasticsearchTestRule.json(workflowInstanceQueryDto))
        .contentType(contentType);

    MvcResult mvcResult = mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentType(contentType))
        .andReturn();

     List<WorkflowInstanceDto> workflowInstanceDtos = listFromResponse(mvcResult);

     assertThat(workflowInstanceDtos.size()).isEqualTo(3);

     for (WorkflowInstanceDto workflowInstanceDto: workflowInstanceDtos) {
       assertThat(workflowInstanceDto.getEndDate()).isNull();
       assertThat(workflowInstanceDto.getState()).isEqualTo(WorkflowInstanceState.ACTIVE);
       assertThat(workflowInstanceDto.getActivities()).isEmpty();
     }
  }

  @Test
  public void testPagination() throws Exception {
    //query running instances
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setRunning(true);

    MockHttpServletRequestBuilder request = post(query(1, 3))
        .content(elasticsearchTestRule.json(workflowInstanceQueryDto))
        .contentType(contentType);

    MvcResult mvcResult = mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentType(contentType))
        .andReturn();

     List<WorkflowInstanceDto> workflowInstanceDtos = listFromResponse(mvcResult);

     assertThat(workflowInstanceDtos.size()).isEqualTo(2);

     for (WorkflowInstanceDto workflowInstanceDto: workflowInstanceDtos) {
       assertThat(workflowInstanceDto.getEndDate()).isNull();
       assertThat(workflowInstanceDto.getState()).isEqualTo(WorkflowInstanceState.ACTIVE);
       assertThat(workflowInstanceDto.getActivities()).isEmpty();
     }
  }

  @Test
  public void testQueryCompletedWorkflowInstancesCount() throws Exception {
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setCompleted(true);

    MockHttpServletRequestBuilder request = post(COUNT_INSTANCES_URL)
      .content(elasticsearchTestRule.json(workflowInstanceQueryDto))
      .contentType(contentType);

    mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(contentType))
      .andExpect(content().json("{\"count\":1}"));
  }

  @Test
  public void testQueryCompletedWorkflowInstances() throws Exception {
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setCompleted(true);

    MockHttpServletRequestBuilder request = post(query(0, 3))
      .content(elasticsearchTestRule.json(workflowInstanceQueryDto))
      .contentType(contentType);

    MvcResult mvcResult = mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(contentType))
      .andReturn();

    List<WorkflowInstanceDto> workflowInstanceDtos = listFromResponse(mvcResult);

    assertThat(workflowInstanceDtos.size()).isEqualTo(1);
    assertThat(workflowInstanceDtos.get(0).getEndDate()).isNotNull();
    assertThat(workflowInstanceDtos.get(0).getState()).isEqualTo(WorkflowInstanceState.COMPLETED);
    assertThat(workflowInstanceDtos.get(0).getActivities()).isEmpty();
  }

  @Test
  public void testQueryWorkflowInstancesWithIncidentsCount() throws Exception {
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setWithIncidents(true);

    MockHttpServletRequestBuilder request = post(COUNT_INSTANCES_URL)
      .content(elasticsearchTestRule.json(workflowInstanceQueryDto))
      .contentType(contentType);

    mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(contentType))
      .andExpect(content().json("{\"count\":1}"));
  }

  @Test
  public void testQueryWorkflowInstancesWithIncidents() throws Exception {
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setWithIncidents(true);

    MockHttpServletRequestBuilder request = post(query(0, 3))
      .content(elasticsearchTestRule.json(workflowInstanceQueryDto))
      .contentType(contentType);

    MvcResult mvcResult = mockMvc
      .perform(request)
      .andExpect(status().isOk())
      .andExpect(content()
        .contentType(contentType))
      .andReturn();

    List<WorkflowInstanceDto> workflowInstanceDtos = listFromResponse(mvcResult);

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
  public void testQueryWorkflowInstancesWithoutIncidentsCount() throws Exception {
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setWithoutIncidents(true);

    MockHttpServletRequestBuilder request = post(COUNT_INSTANCES_URL)
      .content(elasticsearchTestRule.json(workflowInstanceQueryDto))
      .contentType(contentType);

    mockMvc.perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(contentType))
      .andExpect(content().json("{\"count\":3}"));
  }

  @Test
  public void testQueryWorkflowInstancesWithoutIncidents() throws Exception {
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setWithoutIncidents(true);

    MockHttpServletRequestBuilder request = post(query(0, 3))
      .content(elasticsearchTestRule.json(workflowInstanceQueryDto))
      .contentType(contentType);

    MvcResult mvcResult = mockMvc
      .perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(contentType))
      .andReturn();

    List<WorkflowInstanceDto> workflowInstanceDtos = listFromResponse(mvcResult);

    assertThat(workflowInstanceDtos.size()).isEqualTo(3);

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

    final WorkflowInstanceDto workflowInstanceDto = fromResponse(mvcResult, new TypeReference<WorkflowInstanceDto>() {});

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
    final WorkflowInstanceEntity runningInstance = createWorkflowInstance(false);
    runningInstance.getActivities().add(createActivityInstance(ActivityState.ACTIVE));

    //completed instances with one activity and without incidents
    final WorkflowInstanceEntity completedInstance = createWorkflowInstance(true);
    completedInstance.getActivities().add(createActivityInstance(ActivityState.COMPLETED));

    //instance with incidents (one resolved and one active) and one active activity
    final WorkflowInstanceEntity instanceWithIncident = createWorkflowInstance(false);
    instanceWithIncident.getIncidents().add(createIncident(IncidentState.ACTIVE));
    instanceWithIncident.getIncidents().add(createIncident(IncidentState.RESOLVED));
    instanceWithIncident.getActivities().add(createActivityInstance(ActivityState.ACTIVE));

    //instance with one resolved incident and one completed activity
    instanceWithoutIncident = createWorkflowInstance(false);
    instanceWithoutIncident.getIncidents().add(createIncident(IncidentState.RESOLVED));
    instanceWithoutIncident.getActivities().add(createActivityInstance(ActivityState.COMPLETED));

    List<WorkflowInstanceEntity> workflowInstances = new ArrayList<>();
    workflowInstances.addAll(Arrays.asList(runningInstance, completedInstance, instanceWithIncident, instanceWithoutIncident));

    //persist instances
    try {
      elasticsearchBulkProcessor.persistOperateEntities(workflowInstances);
    } catch (PersistenceException e) {
      throw new RuntimeException(e);
    }
    elasticsearchTestRule.refreshIndexesInElasticsearch();
  }

  private WorkflowInstanceEntity createWorkflowInstance(boolean completed) {
    WorkflowInstanceEntity workflowInstance = new WorkflowInstanceEntity();
    workflowInstance.setId(UUID.randomUUID().toString());
    workflowInstance.setBusinessKey("testProcess" + random.nextInt(10));
    workflowInstance.setStartDate(DateUtil.getRandomStartDate());
    if (completed) {
      final OffsetDateTime endDate = DateUtil.getRandomEndDate();
      workflowInstance.setEndDate(endDate);
    }
    workflowInstance.setState(workflowInstance.getEndDate() == null ? WorkflowInstanceState.ACTIVE : WorkflowInstanceState.COMPLETED);
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

  protected List<WorkflowInstanceDto> listFromResponse(MvcResult result) throws IOException {
    return fromResponse(result, new TypeReference<List<WorkflowInstanceDto>>() {
    });
  }

  protected <T> T fromResponse(MvcResult result, TypeReference<T> valueTypeRef) throws IOException {
    return objectMapper.readValue(result.getResponse().getContentAsString(), valueTypeRef);
  }

  protected String query(int firstResult, int maxResults) {
    return String.format("%s?firstResult=%d&maxResults=%d", QUERY_INSTANCES_URL, firstResult, maxResults);
  }
}
