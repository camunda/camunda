/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.webapp.rest.WorkflowInstanceRestService.WORKFLOW_INSTANCE_URL;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Optional;
import org.camunda.operate.entities.ErrorType;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.TestApplication;
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.webapp.rest.dto.incidents.IncidentDto;
import org.camunda.operate.webapp.rest.dto.incidents.IncidentResponseDto;
import org.camunda.operate.webapp.zeebe.operation.UpdateVariableHandler;
import org.camunda.operate.zeebeimport.v26.processors.IncidentZeebeRecordProcessor;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.IfProfileValue;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.unit.DataSize;

@SpringBootTest(
    classes = {TestApplication.class},
    properties = {OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
        OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
        OperateProperties.PREFIX + ".isNextFlowNodeInstances = true",
        //configure webhook to notify about the incidents
        OperateProperties.PREFIX + ".alert.webhook = http://somepath"})
public class IncidentIT extends OperateZeebeIntegrationTest {

  @Autowired
  private UpdateVariableHandler updateVariableHandler;

  @MockBean
  private IncidentNotifier incidentNotifier;

  @Autowired
  @InjectMocks
  private IncidentZeebeRecordProcessor incidentZeebeRecordProcessor100;

  @Autowired
  @InjectMocks
  private org.camunda.operate.zeebeimport.v26.processors.IncidentZeebeRecordProcessor
      incidentZeebeRecordProcessor26;

  @Before
  public void before() {
    super.before();
    updateVariableHandler.setZeebeClient(zeebeClient);
  }

  @Test
  public void testUnhandledErrorEventAsEndEvent() {
    // Given
    tester
    .deployWorkflow("error-end-event.bpmn").waitUntil().workflowIsDeployed()
    // when
    .startWorkflowInstance("error-end-process")
    .waitUntil()
    .incidentIsActive();
    // then
    List<IncidentEntity> incidents = tester.getIncidents();
    assertThat(incidents.size()).isEqualTo(1);
    assertIncidentEntity(incidents.get(0), ErrorType.UNHANDLED_ERROR_EVENT, "Unhandled error event");
  }

  @Test
  @IfProfileValue(name="spring.profiles.active", value="test") // Do not execute on 'old-zeebe' profile
  public void testErrorMessageSizeExceeded() throws Exception {
    // given
    int variableCount = 4;
    String largeValue = "\"" + "x".repeat((int) (DataSize.ofMegabytes(4).toBytes() / variableCount)) + "\"";

    tester.deployWorkflow("single-task.bpmn")
        .waitUntil().workflowIsDeployed()
        .then()
        .startWorkflowInstance("process", "{}")
        .waitUntil().workflowInstanceIsStarted();

    for(int i=0; i<variableCount; i++) {
      tester.updateVariableOperation(Integer.toString(i), largeValue).waitUntil().operationIsCompleted();
    }

    // when
    // ---
    // Activation of the job tries to accumulate all variables in the process
    // this triggers the incident, and the activate jobs command will not return a job
    tester.activateJob("task")
        .waitUntil().incidentIsActive();
    // then
    List<IncidentEntity> incidents = tester.getIncidents();
    assertThat(incidents.size()).isEqualTo(1);
    assertIncidentEntity(incidents.get(0), ErrorType.MESSAGE_SIZE_EXCEEDED, "Message size exceeded");
  }

  @Test
  public void testUnhandledErrorEvent() {
    // Given
    tester
      .deployWorkflow("errorProcess.bpmn").waitUntil().workflowIsDeployed()
      .startWorkflowInstance("errorProcess")
    // when
    .throwError("errorTask", "this-errorcode-does-not-exists", "Workflow error")
    .then().waitUntil()
    .incidentIsActive();

    // then
    List<IncidentEntity> incidents = tester.getIncidents();
    assertThat(incidents.size()).isEqualTo(1);
    assertIncidentEntity(incidents.get(0), ErrorType.UNHANDLED_ERROR_EVENT, "Unhandled error event");
  }

  @Test
  public void testIncidentsAreReturned() throws Exception {
    // having
    String processId = "complexProcess";
    deployWorkflow("complexProcess_v_3.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"count\":3}");
    final String errorMsg = "some error";
    final String activityId = "alwaysFailingTask";
    ZeebeTestUtil.failTask(zeebeClient, activityId, getWorkerName(), 3, errorMsg);
    elasticsearchTestRule.processAllRecordsAndWait(incidentsAreActiveCheck, workflowInstanceKey, 4);
    //elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when
    MvcResult mvcResult = getRequest(getIncidentsURL(workflowInstanceKey));
    final IncidentResponseDto incidentResponse = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {
    });

    //then
    assertThat(incidentResponse).isNotNull();
    assertThat(incidentResponse.getCount()).isEqualTo(4);
    assertThat(incidentResponse.getIncidents()).hasSize(4);
    assertThat(incidentResponse.getIncidents()).isSortedAccordingTo(IncidentDto.INCIDENT_DEFAULT_COMPARATOR);
    assertIncident(incidentResponse, errorMsg, activityId, ErrorType.JOB_NO_RETRIES);
    assertIncident(incidentResponse, "failed to evaluate expression '{taskOrderId:orderId}': no variable found for name 'orderId'", "upperTask", ErrorType.IO_MAPPING_ERROR);
    assertIncident(incidentResponse, "failed to evaluate expression 'clientId': no variable found for name 'clientId'", "messageCatchEvent", ErrorType.EXTRACT_VALUE_ERROR);
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

    //verify that incidents notification was called
    verify(incidentNotifier, atLeastOnce()).notifyOnIncidents(any());
  }

  protected void assertIncidentEntity(IncidentEntity anIncident,ErrorType anErrorType,String anErrorTypeTitle) {
    assertThat(anIncident.getErrorType()).isEqualTo(anErrorType);
    assertThat(anIncident.getErrorType().getTitle()).isEqualTo(anErrorTypeTitle);
    assertThat(anIncident.getWorkflowInstanceKey()).isEqualTo(tester.getWorkflowInstanceKey());
  }

  protected void assertErrorType(IncidentResponseDto incidentResponse, ErrorType errorType, int count) {
    assertThat(incidentResponse.getErrorTypes()).filteredOn(et -> et.getErrorType().equals(errorType.getTitle())).hasSize(1)
      .allMatch(et -> et.getCount() == count);
  }

  protected void assertIncidentFlowNode(IncidentResponseDto incidentResponse, String activityId, int count) {
    assertThat(incidentResponse.getFlowNodes()).filteredOn(fn -> fn.getFlowNodeId().equals(activityId)).hasSize(1).allMatch(fn -> fn.getCount() == count);
  }

  protected void assertIncident(IncidentResponseDto incidentResponse, String errorMsg, String activityId, ErrorType errorType) {
    final Optional<IncidentDto> incidentOpt = incidentResponse.getIncidents().stream().filter(inc -> inc.getErrorType().equals(errorType.getTitle())).findFirst();
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
