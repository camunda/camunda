/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport;

import static io.camunda.operate.entities.ErrorType.JOB_NO_RETRIES;
import static io.camunda.operate.webapp.rest.ProcessInstanceRestService.PROCESS_INSTANCE_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.entities.ErrorType;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.*;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentResponseDto;
import io.camunda.operate.webapp.zeebe.operation.UpdateVariableHandler;
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.IfProfileValue;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.unit.DataSize;

@SpringBootTest(
    classes = {TestApplication.class},
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      // configure webhook to notify about the incidents
      OperateProperties.PREFIX + ".alert.webhook = http://somepath",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER"
    })
public class IncidentZeebeImportIT extends OperateZeebeAbstractIT {

  @Autowired protected OperateProperties operateProperties;
  @Autowired protected DatabaseInfo databaseInfo;
  @Autowired private UpdateVariableHandler updateVariableHandler;
  @MockBean private IncidentNotifier incidentNotifier;
  @Autowired private Predicate<Object[]> processInstanceExistsCheck;

  @Override
  @Before
  public void before() {
    super.before();
    updateVariableHandler.setZeebeClient(zeebeClient);
  }

  @Test
  public void testUnhandledErrorEventAsEndEvent() {
    // Given
    tester
        .deployProcess("error-end-event.bpmn")
        .waitUntil()
        .processIsDeployed()
        // when
        .startProcessInstance("error-end-process")
        .waitUntil()
        .incidentIsActive();
    // then
    final List<IncidentDto> incidents = tester.getIncidents();
    assertThat(incidents.size()).isEqualTo(1);
    assertIncident(incidents.get(0), ErrorType.UNHANDLED_ERROR_EVENT);
  }

  @Test
  @IfProfileValue(
      name = "spring.profiles.active",
      value = "test") // Do not execute on 'old-zeebe' profile
  public void testErrorMessageSizeExceeded() throws Exception {
    // given
    final int variableCount = 4;
    final String largeValue =
        "\"" + "x".repeat((int) (DataSize.ofMegabytes(4).toBytes() / variableCount)) + "\"";

    tester
        .deployProcess("single-task.bpmn")
        .waitUntil()
        .processIsDeployed()
        .then()
        .startProcessInstance("process", "{}")
        .waitUntil()
        .processInstanceIsStarted();

    for (int i = 0; i < variableCount; i++) {
      tester
          .updateVariableOperation(Integer.toString(i), largeValue)
          .waitUntil()
          .operationIsCompleted();
    }

    // when
    // ---
    // Activation of the job tries to accumulate all variables in the process
    // this triggers the incident, and the activate jobs command will not return a job
    tester.activateJob("task").waitUntil().incidentIsActive();
    // then
    final List<IncidentDto> incidents = tester.getIncidents();
    assertThat(incidents.size()).isEqualTo(1);
    assertIncident(incidents.get(0), ErrorType.MESSAGE_SIZE_EXCEEDED);
  }

  @Test
  public void testUnhandledErrorEvent() {
    // Given
    tester
        .deployProcess("errorProcess.bpmn")
        .waitUntil()
        .processIsDeployed()
        .startProcessInstance("errorProcess")
        // when
        .throwError("errorTask", "this-errorcode-does-not-exists", "Process error")
        .then()
        .waitUntil()
        .incidentIsActive();
    // then
    final List<IncidentDto> incidents = tester.getIncidents();
    assertThat(incidents.size()).isEqualTo(1);
    assertIncident(incidents.get(0), ErrorType.UNHANDLED_ERROR_EVENT);
  }

  @Ignore
  @Test
  public void testCatchAllErrorEvent() {
    // Given
    tester
        .deployProcess("errorProcessCatchAll.bpmn")
        .waitUntil()
        .processIsDeployed()
        .startProcessInstance("errorProcess")
        // when
        .throwError("errorTask", "unknown", "Process error")
        .then()
        .waitUntil()
        .processInstanceIsFinished();

    // then
    assertThat(tester.getIncidents()).isEmpty();
  }

  @Test
  public void testIncidentsAreReturned() throws Exception {
    // having
    final String processId = "complexProcess";
    deployProcess("complexProcess_v_3.bpmn");
    final long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"count\":3}");
    final String errorMsg = "some error";
    final String activityId = "alwaysFailingTask";
    ZeebeTestUtil.failTask(zeebeClient, activityId, getWorkerName(), 3, errorMsg);
    searchTestRule.processAllRecordsAndWait(incidentsInAnyInstanceAreActiveCheck, 4L);
    // elasticsearchTestRule.refreshIndexesInElasticsearch();

    // when
    final MvcResult mvcResult = getRequest(getIncidentsURL(processInstanceKey));
    final IncidentResponseDto incidentResponse =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    // then
    assertThat(incidentResponse).isNotNull();
    assertThat(incidentResponse.getCount()).isEqualTo(4);
    assertThat(incidentResponse.getIncidents()).hasSize(4);
    assertThat(incidentResponse.getIncidents())
        .isSortedAccordingTo(IncidentDto.INCIDENT_DEFAULT_COMPARATOR);
    assertIncident(incidentResponse, errorMsg, activityId, JOB_NO_RETRIES);
    assertIncident(
        incidentResponse,
        "Assertion failure on evaluate the expression '{taskOrderId:assert(orderId, orderId!=null, \"no variable found for name 'orderId'\")}': no variable found for name 'orderId'",
        "upperTask",
        ErrorType.IO_MAPPING_ERROR);
    assertIncident(
        incidentResponse,
        "Failed to extract the correlation key for 'clientId': The value must be either a string or a number, but was 'NULL'.",
        "messageCatchEvent",
        ErrorType.EXTRACT_VALUE_ERROR);
    assertIncident(
        incidentResponse,
        "Expected at least one condition to evaluate to true, or to have a default flow",
        "exclusiveGateway",
        ErrorType.CONDITION_ERROR);

    assertThat(incidentResponse.getFlowNodes()).hasSize(4);
    assertIncidentFlowNode(incidentResponse, activityId, 1);
    assertIncidentFlowNode(incidentResponse, "upperTask", 1);
    assertIncidentFlowNode(incidentResponse, "messageCatchEvent", 1);
    assertIncidentFlowNode(incidentResponse, "exclusiveGateway", 1);

    assertThat(incidentResponse.getErrorTypes()).hasSize(4);
    assertErrorType(incidentResponse, JOB_NO_RETRIES, 1);
    assertErrorType(incidentResponse, ErrorType.IO_MAPPING_ERROR, 1);
    assertErrorType(incidentResponse, ErrorType.EXTRACT_VALUE_ERROR, 1);
    assertErrorType(incidentResponse, ErrorType.CONDITION_ERROR, 1);

    // verify that incidents notification was called
    verify(incidentNotifier, atLeastOnce()).notifyOnIncidents(any());
  }

  /**
   * parentProcess -> calledProcess (has incident) -> process (has incident) Getting the incidents
   * from parentProcess should return two incidents with corresponding rootCause.
   *
   * @throws Exception
   */
  @Test
  public void testTwoIncidentsForCallActivity() throws Exception {
    // having process with call activity
    final String parentProcessId = "parentProcess";
    final String callActivity1Id = "callActivity1";
    final String calledProcess1Id = "calledProcess";
    final String callActivity2Id = "callActivity2";
    final String calledProcess2Id = "process";
    final String lastCalledTaskId = "task";
    final String serviceTaskId = "serviceTask";
    final String errorMsg1 = "Error in called process task";
    final String errorMsg2 = "Error in last called process task";
    final BpmnModelInstance testProcess =
        Bpmn.createExecutableProcess(parentProcessId)
            .startEvent()
            .callActivity(callActivity1Id)
            .zeebeProcessId(calledProcess1Id)
            .done();
    final BpmnModelInstance testProcess2 =
        Bpmn.createExecutableProcess(calledProcess1Id)
            .startEvent()
            .parallelGateway("parallel")
            .serviceTask(serviceTaskId)
            .zeebeJobType(serviceTaskId)
            .moveToNode("parallel")
            .callActivity(callActivity2Id)
            .zeebeProcessId(calledProcess2Id)
            .done();
    final long calledProcessDefinitionKey2 =
        tester.deployProcess("single-task.bpmn").getProcessDefinitionKey();

    final String calledProcess1DefId =
        tester
            .deployProcess(testProcess, "testProcess.bpmn")
            .deployProcess(testProcess2, "testProcess2.bpmn")
            .getProcessDefinitionKey()
            .toString();
    final long parentProcessInstanceKey =
        tester
            .startProcessInstance(parentProcessId, null)
            .and()
            .waitUntil()
            .conditionIsMet(processInstancesAreStartedByProcessId, calledProcessDefinitionKey2, 1)
            .and()
            .failTask(lastCalledTaskId, errorMsg2)
            .and()
            .failTask(serviceTaskId, errorMsg1)
            .waitUntil()
            .incidentsInAnyInstanceAreActive(2)
            .getProcessInstanceKey();

    // when
    // get incidents for parent process instance
    final MvcResult mvcResult = getRequest(getIncidentsURL(parentProcessInstanceKey));
    final IncidentResponseDto incidentResponse =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    // then two incidents are returned
    final List<IncidentDto> incidents = incidentResponse.getIncidents();
    assertThat(incidents.size()).isEqualTo(2);

    // -------------assert incident 1-------------------
    final Optional<IncidentDto> incident1Optional =
        incidents.stream().filter(inc -> inc.getErrorMessage().equals(errorMsg1)).findFirst();
    assertThat(incident1Optional).isNotEmpty();
    final IncidentDto incident1 = incident1Optional.get();
    assertThat(incident1.getFlowNodeId()).isEqualTo(callActivity1Id);

    // assert flow node instance id
    final Optional<FlowNodeInstanceDto> fniOptional =
        tester.getFlowNodeInstanceOneListFromRest(String.valueOf(parentProcessInstanceKey)).stream()
            .filter(fni -> fni.getType().equals(FlowNodeType.CALL_ACTIVITY))
            .findFirst();
    assertThat(fniOptional).isNotEmpty();
    assertThat(incident1.getFlowNodeInstanceId()).isEqualTo(fniOptional.get().getId());

    assertThat(incident1.getRootCauseInstance()).isNotNull();
    assertThat(incident1.getRootCauseInstance().getProcessDefinitionName())
        .isEqualTo(calledProcess1Id);
    assertThat(incident1.getRootCauseInstance().getProcessDefinitionId())
        .isEqualTo(calledProcess1DefId);

    // assert root cause process instance id
    final String calledProcessInstanceId1 =
        tester.getSingleProcessInstanceByBpmnProcessId(calledProcess1DefId).getId();
    assertThat(incident1.getRootCauseInstance().getInstanceId())
        .isEqualTo(calledProcessInstanceId1);

    // -------------assert incident 2-------------------
    final Optional<IncidentDto> incident2Optional =
        incidents.stream().filter(inc -> inc.getErrorMessage().equals(errorMsg2)).findFirst();
    assertThat(incident1Optional).isNotEmpty();
    final IncidentDto incident2 = incident2Optional.get();
    assertThat(incident2.getFlowNodeId()).isEqualTo(callActivity1Id);

    // assert flow node instance id
    assertThat(incident2.getFlowNodeInstanceId()).isEqualTo(fniOptional.get().getId());

    assertThat(incident2.getRootCauseInstance()).isNotNull();
    assertThat(incident2.getRootCauseInstance().getProcessDefinitionName())
        .isEqualTo(calledProcess2Id);
    assertThat(incident2.getRootCauseInstance().getProcessDefinitionId())
        .isEqualTo(String.valueOf(calledProcessDefinitionKey2));

    // assert root cause process instance id
    final String calledProcessInstanceId2 =
        tester
            .getSingleProcessInstanceByBpmnProcessId(String.valueOf(calledProcessDefinitionKey2))
            .getId();
    assertThat(incident2.getRootCauseInstance().getInstanceId())
        .isEqualTo(calledProcessInstanceId2);

    assertThat(incidentResponse.getErrorTypes()).hasSize(1);
    assertThat(incidentResponse.getErrorTypes().get(0).getId()).isEqualTo(JOB_NO_RETRIES.name());
    assertThat(incidentResponse.getErrorTypes().get(0).getName())
        .isEqualTo(JOB_NO_RETRIES.getTitle());
    assertThat(incidentResponse.getErrorTypes().get(0).getCount()).isEqualTo(2);

    assertThat(incidentResponse.getFlowNodes()).hasSize(1);
    assertThat(incidentResponse.getFlowNodes().get(0).getId()).isEqualTo(callActivity1Id);
    assertThat(incidentResponse.getFlowNodes().get(0).getCount()).isEqualTo(2);
  }

  // Regression https://github.com/camunda/camunda/issues/42059
  @Test
  public void testProcessInstanceIncidentImporting() throws IOException {

    final var prefix =
        databaseInfo.isElasticsearchDb()
            ? operateProperties.getElasticsearch().getIndexPrefix()
            : operateProperties.getOpensearch().getIndexPrefix();

    final var listViewIndex = prefix + "-" + ListViewTemplate.INDEX_NAME + "-8.3.0_";

    final var zeebeProcessInstanceIndex = zeebeRule.getPrefix() + "_process-instance_*";

    // start a process instance only to have zeebe record index generated
    tester
        .deployProcess("single-task.bpmn")
        .waitUntil()
        .processIsDeployed()
        .then()
        .startProcessInstance("process", "{}")
        .waitUntil()
        .processInstanceIsStarted();

    // block reads on zeebe process instance index to simulate import delays
    searchTestRule.blockIndexRead(zeebeProcessInstanceIndex, true);

    // given - a process instance with a start execution listener that fails
    final var pi =
        tester
            .deployProcess("process-listener.bpmn")
            .waitUntil()
            .processIsDeployed()
            .startProcessInstance("process-listener")
            .and()
            .failTask("startListener", "start listener failed")
            .and()
            .getProcessInstanceKey();

    // then - wait for incident to be imported
    searchTestRule.processRecordsWithTypeAndWait(
        ImportValueType.INCIDENT, false, incidentsArePresentCheck, pi, 1);

    // unblock reads on zeebe process instance index to allow process instance to be parsed
    searchTestRule.blockIndexRead(zeebeProcessInstanceIndex, false);

    searchTestRule.processRecordsWithTypeAndWait(
        ImportValueType.PROCESS_INSTANCE, true, processInstanceExistsCheck, List.of(pi));

    Awaitility.await("process instance with incident is imported")
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              final var doc =
                  searchTestRule.getDocumentById(
                      listViewIndex, String.valueOf(pi), ProcessInstanceForListViewEntity.class);

              assertThat(doc).isNotNull();
              assertThat(doc.getProcessDefinitionKey()).isGreaterThan(0L);
              assertThat(doc.getProcessName()).isNotEmpty();
              assertThat(doc.getProcessVersion()).isGreaterThan(0);
              assertThat(doc.getBpmnProcessId()).isNotEmpty();
              assertThat(doc.getTreePath()).isNotEmpty();
            });
  }

  protected void assertIncident(final IncidentDto anIncident, final ErrorType anErrorType) {
    assertThat(anIncident.getErrorType().getId()).isEqualTo(anErrorType.name());
    assertThat(anIncident.getErrorType().getName()).isEqualTo(anErrorType.getTitle());
    assertThat(anIncident.getRootCauseInstance().getInstanceId())
        .isEqualTo(String.valueOf(tester.getProcessInstanceKey()));
  }

  protected void assertErrorType(
      final IncidentResponseDto incidentResponse, final ErrorType errorType, final int count) {
    assertThat(incidentResponse.getErrorTypes())
        .filteredOn(et -> et.getName().equals(errorType.getTitle()))
        .hasSize(1)
        .allMatch(et -> et.getCount() == count);
  }

  protected void assertIncidentFlowNode(
      final IncidentResponseDto incidentResponse, final String activityId, final int count) {
    assertThat(incidentResponse.getFlowNodes())
        .filteredOn(fn -> fn.getId().equals(activityId))
        .hasSize(1)
        .allMatch(fn -> fn.getCount() == count);
  }

  protected void assertIncident(
      final IncidentResponseDto incidentResponse,
      final String errorMsg,
      final String activityId,
      final ErrorType errorType) {
    final Optional<IncidentDto> incidentOpt =
        incidentResponse.getIncidents().stream()
            .filter(inc -> inc.getErrorType().getName().equals(errorType.getTitle()))
            .findFirst();
    assertThat(incidentOpt).isPresent();
    final IncidentDto inc = incidentOpt.get();
    assertThat(inc.getId()).as(activityId + ".id").isNotNull();
    assertThat(inc.getCreationTime()).as(activityId + ".creationTime").isNotNull();
    assertThat(inc.getErrorMessage()).as(activityId + ".errorMessage").startsWith(errorMsg);
    assertThat(inc.getFlowNodeId()).as(activityId + ".flowNodeId").isEqualTo(activityId);
    assertThat(inc.getFlowNodeInstanceId()).as(activityId + ".flowNodeInstanceId").isNotNull();
    if (errorType.equals(JOB_NO_RETRIES)) {
      assertThat(inc.getJobId()).as(activityId + ".jobKey").isNotNull();
    } else {
      assertThat(inc.getJobId()).as(activityId + ".jobKey").isNull();
    }
  }

  protected String getIncidentsURL(final long processInstanceKey) {
    return String.format(PROCESS_INSTANCE_URL + "/%s/incidents", processInstanceKey);
  }
}
