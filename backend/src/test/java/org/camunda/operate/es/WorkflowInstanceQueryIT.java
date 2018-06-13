package org.camunda.operate.es;

import java.nio.charset.Charset;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.camunda.operate.es.writer.WorkflowInstanceWriter;
import org.camunda.operate.po.IncidentEntity;
import org.camunda.operate.po.IncidentState;
import org.camunda.operate.po.WorkflowInstanceEntity;
import org.camunda.operate.po.WorkflowInstanceState;
import org.camunda.operate.rest.dto.IncidentDto;
import org.camunda.operate.rest.dto.WorkflowInstanceDto;
import org.camunda.operate.rest.dto.WorkflowInstanceQueryDto;
import org.camunda.operate.util.DateUtil;
import org.camunda.operate.util.ElasticsearchIntegrationTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.core.type.TypeReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Svetlana Dorokhova.
 */
public class WorkflowInstanceQueryIT extends ElasticsearchIntegrationTest {

  @Autowired
  private WorkflowInstanceWriter workflowInstanceWriter;

  private MediaType contentType = new MediaType(MediaType.APPLICATION_JSON.getType(),
    MediaType.APPLICATION_JSON.getSubtype(),
    Charset.forName("utf8"));

  private Random random = new Random();

  @Before
  public void starting() {
    super.starting();
    createData();
  }

  @After
  public void finished() {
    super.cleanUpElasticSearch();
    super.finished();
  }

  @Test
  public void testQueryRunningWorkflowInstancesCount() throws Exception {
    //query running instances
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setRunning(true);
    mockMvc.perform(post("/api/workflow-instances/count/")
      .content(this.json(workflowInstanceQueryDto))
      .contentType(contentType))
      .andExpect(status().isOk())
      .andExpect(content().contentType(contentType))
      .andExpect(content().json("{\"count\":3}"));
  }

  @Test
  public void testQueryRunningWorkflowInstances() throws Exception {
    //query running instances
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setRunning(true);
    final MvcResult mvcResult =
      mockMvc.perform(post("/api/workflow-instances/")
        .content(this.json(workflowInstanceQueryDto))
        .contentType(contentType))
        .andExpect(status().isOk())
        .andExpect(content()
          .contentType(contentType))
        .andReturn();
     List<WorkflowInstanceDto> workflowInstanceDtos = objectMapper.readValue(mvcResult.getResponse().getContentAsByteArray(), new TypeReference<List<WorkflowInstanceDto>>(){});
     assertThat(workflowInstanceDtos.size()).isEqualTo(3);
     for (WorkflowInstanceDto workflowInstanceDto: workflowInstanceDtos) {
       assertThat(workflowInstanceDto.getEndDate()).isNull();
       assertThat(workflowInstanceDto.getState()).isEqualTo(WorkflowInstanceState.ACTIVE);
     }
  }

  @Test
  public void testQueryCompletedWorkflowInstancesCount() throws Exception {
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setCompleted(true);
    mockMvc.perform(post("/api/workflow-instances/count/")
      .content(this.json(workflowInstanceQueryDto))
      .contentType(contentType))
      .andExpect(status().isOk())
      .andExpect(content().contentType(contentType))
      .andExpect(content().json("{\"count\":1}"));
  }

  @Test
  public void testQueryCompletedWorkflowInstances() throws Exception {
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setCompleted(true);
    final MvcResult mvcResult = mockMvc
      .perform(post("/api/workflow-instances/")
        .content(this.json(workflowInstanceQueryDto))
        .contentType(contentType))
      .andExpect(status().isOk())
      .andExpect(content()
        .contentType(contentType))
      .andReturn();
    List<WorkflowInstanceDto> workflowInstanceDtos = objectMapper.readValue(mvcResult.getResponse().getContentAsByteArray(), new TypeReference<List<WorkflowInstanceDto>>(){});
    assertThat(workflowInstanceDtos.size()).isEqualTo(1);
    assertThat(workflowInstanceDtos.get(0).getEndDate()).isNotNull();
    assertThat(workflowInstanceDtos.get(0).getState()).isEqualTo(WorkflowInstanceState.COMPLETED);
  }

  @Test
  public void testQueryWorkflowInstancesWithIncidentsCount() throws Exception {
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setWithIncidents(true);
    mockMvc.perform(post("/api/workflow-instances/count/")
      .content(this.json(workflowInstanceQueryDto))
      .contentType(contentType))
      .andExpect(status().isOk())
      .andExpect(content().contentType(contentType))
      .andExpect(content().json("{\"count\":1}"));
  }

  @Test
  public void testQueryWorkflowInstancesWithIncidents() throws Exception {
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setWithIncidents(true);
    final MvcResult mvcResult = mockMvc
      .perform(post("/api/workflow-instances/")
        .content(this.json(workflowInstanceQueryDto))
        .contentType(contentType))
      .andExpect(status().isOk())
      .andExpect(content()
        .contentType(contentType))
      .andReturn();
    List<WorkflowInstanceDto> workflowInstanceDtos = objectMapper.readValue(mvcResult.getResponse().getContentAsByteArray(), new TypeReference<List<WorkflowInstanceDto>>(){});
    assertThat(workflowInstanceDtos.size()).isEqualTo(1);
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
    mockMvc.perform(post("/api/workflow-instances/count/")
      .content(this.json(workflowInstanceQueryDto))
      .contentType(contentType))
      .andExpect(status().isOk())
      .andExpect(content().contentType(contentType))
      .andExpect(content().json("{\"count\":3}"));
  }

  @Test
  public void testQueryWorkflowInstancesWithoutIncidents() throws Exception {
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setWithoutIncidents(true);
    final MvcResult mvcResult = mockMvc
      .perform(post("/api/workflow-instances/")
        .content(this.json(workflowInstanceQueryDto))
        .contentType(contentType))
      .andExpect(status().isOk())
      .andExpect(content()
        .contentType(contentType))
      .andReturn();
    List<WorkflowInstanceDto> workflowInstanceDtos = objectMapper.readValue(mvcResult.getResponse().getContentAsByteArray(), new TypeReference<List<WorkflowInstanceDto>>(){});
    assertThat(workflowInstanceDtos.size()).isEqualTo(3);
    for (WorkflowInstanceDto workflowInstanceDto: workflowInstanceDtos) {
      IncidentDto activeIncident = null;
      for (IncidentDto incident : workflowInstanceDto.getIncidents()) {
        if (incident.getState().equals(IncidentState.ACTIVE)) {
          activeIncident = incident;
        }
      }
      assertThat(activeIncident).isNull();
    }
  }

  private void createData() {
    final WorkflowInstanceEntity runningInstance = createWorkflowInstance(false);
    final WorkflowInstanceEntity completedInstance = createWorkflowInstance(true);
    final WorkflowInstanceEntity instanceWithIncident = createWorkflowInstance(false);
    instanceWithIncident.getIncidents().add(createIncident(IncidentState.ACTIVE));
    instanceWithIncident.getIncidents().add(createIncident(IncidentState.RESOLVED));
    final WorkflowInstanceEntity instanceWithoutIncident = createWorkflowInstance(false);
    instanceWithoutIncident.getIncidents().add(createIncident(IncidentState.RESOLVED));

    List<WorkflowInstanceEntity> workflowInstances = new ArrayList<>();
    workflowInstances.addAll(Arrays.asList(runningInstance, completedInstance, instanceWithIncident, instanceWithoutIncident));

    //persist instances
    workflowInstanceWriter.persistWorkflowInstances(workflowInstances);
    super.refreshIndexesInElasticsearch();
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

}
