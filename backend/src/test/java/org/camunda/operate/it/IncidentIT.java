/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.it;

import java.util.Optional;
import java.util.function.Predicate;

import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.rest.dto.incidents.IncidentDto;
import org.camunda.operate.rest.dto.incidents.IncidentResponseDto;
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
import io.zeebe.protocol.ErrorType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.rest.WorkflowInstanceRestService.WORKFLOW_INSTANCE_URL;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class IncidentIT extends OperateZeebeIntegrationTest {

  @Autowired
  @Qualifier("incidentsAreActiveCheck")
  private Predicate<Object[]> incidentsAreActiveCheck;

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
  public void testIncidentsAreReturned() throws Exception {
    // having
    String processId = "complexProcess";
    deployWorkflow("complexProcess_v_3.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, null);
    final String errorMsg = "some error";
    final String activityId = "alwaysFailingTask";
    ZeebeTestUtil.failTask(zeebeClient, activityId, getWorkerName(), 3, errorMsg);
    elasticsearchTestRule.processAllRecordsAndWait(incidentsAreActiveCheck, workflowInstanceKey, 4);
    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when
    MockHttpServletRequestBuilder request = get(getIncidentsURL(workflowInstanceKey));
    MvcResult mvcResult = mockMvc
      .perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();
    final IncidentResponseDto incidentResponse = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<IncidentResponseDto>() {
    });

    //then
    assertThat(incidentResponse).isNotNull();
    assertThat(incidentResponse.getCount()).isEqualTo(4);
    assertThat(incidentResponse.getIncidents()).hasSize(4);
    assertThat(incidentResponse.getIncidents()).isSortedAccordingTo(IncidentDto.INCIDENT_DEFAULT_COMPARATOR);
    assertIncident(incidentResponse, errorMsg, activityId, ErrorType.JOB_NO_RETRIES);
    assertIncident(incidentResponse, "No data found for query orderId.", "upperTask", ErrorType.IO_MAPPING_ERROR);
    assertIncident(incidentResponse, "Failed to extract the correlation-key by 'clientId': no value found", "messageCatchEvent", ErrorType.EXTRACT_VALUE_ERROR);
    assertIncident(incidentResponse, "Expected at least one condition to evaluate to true, or to have a default flow", "exclusiveGateway", ErrorType.CONDITION_ERROR);

    assertThat(incidentResponse.getFlowNodes()).hasSize(4);
    assertIncidentFlowNode(incidentResponse, activityId, 1);
    assertIncidentFlowNode(incidentResponse, "upperTask", 1);
    assertIncidentFlowNode(incidentResponse, "messageCatchEvent", 1);
    assertIncidentFlowNode(incidentResponse, "exclusiveGateway", 1);

    assertThat(incidentResponse.getErrorTypes()).hasSize(4);
    assertErrorType(incidentResponse, ErrorType.JOB_NO_RETRIES, 1);
    assertErrorType(incidentResponse, ErrorType.IO_MAPPING_ERROR, 1);
    assertErrorType(incidentResponse, ErrorType.EXTRACT_VALUE_ERROR, 1);
    assertErrorType(incidentResponse, ErrorType.CONDITION_ERROR, 1);
  }

  protected void assertErrorType(IncidentResponseDto incidentResponse, ErrorType errorType, int count) {
    assertThat(incidentResponse.getErrorTypes()).filteredOn(et -> et.getErrorType().equals(IncidentEntity.getErrorTypeTitle(errorType))).hasSize(1)
      .allMatch(et -> et.getCount() == count);
  }

  protected void assertIncidentFlowNode(IncidentResponseDto incidentResponse, String activityId, int count) {
    assertThat(incidentResponse.getFlowNodes()).filteredOn(fn -> fn.getFlowNodeId().equals(activityId)).hasSize(1).allMatch(fn -> fn.getCount() == count);
  }

  protected void assertIncident(IncidentResponseDto incidentResponse, String errorMsg, String activityId, ErrorType errorType) {
    final Optional<IncidentDto> incidentOpt = incidentResponse.getIncidents().stream().filter(inc -> inc.getErrorType().equals(IncidentEntity.getErrorTypeTitle(errorType))).findFirst();
    assertThat(incidentOpt).isPresent();
    final IncidentDto inc = incidentOpt.get();
    assertThat(inc.getId()).as(activityId + ".id").isNotNull();
    assertThat(inc.getCreationTime()).as(activityId + ".creationTime").isNotNull();
    assertThat(inc.getErrorMessage()).as(activityId + ".errorMessage").isEqualTo(errorMsg);
    assertThat(inc.getFlowNodeId()).as(activityId + ".flowNodeId").isEqualTo(activityId);
    assertThat(inc.getFlowNodeInstanceId()).as(activityId + ".flowNodeInstanceId").isNotNull();
    if (errorType.equals(ErrorType.JOB_NO_RETRIES)) {
      assertThat(inc.getJobId()).as(activityId + ".jobKey").isNotNull();
    } else {
      assertThat(inc.getJobId()).as(activityId + ".jobKey").isNull();
    }
  }

  protected String getIncidentsURL(long workflowInstanceKey) {
    return String.format(WORKFLOW_INSTANCE_URL + "/%s/incidents", workflowInstanceKey);
  }

}
