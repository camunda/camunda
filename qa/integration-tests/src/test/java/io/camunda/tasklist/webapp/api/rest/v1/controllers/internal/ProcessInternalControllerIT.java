/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers.internal;

import static io.camunda.tasklist.util.assertions.CustomAssertions.assertThat;
import static org.apache.commons.lang3.RandomStringUtils.randomNumeric;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.ElasticsearchChecks;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.util.ZeebeTestUtil;
import io.camunda.tasklist.webapp.api.rest.v1.entities.ProcessPublicEndpointsResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.ProcessResponse;
import io.camunda.tasklist.webapp.graphql.entity.ProcessInstanceDTO;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

public class ProcessInternalControllerIT extends TasklistZeebeIntegrationTest {

  @Autowired
  @Qualifier("processIsDeployedCheck")
  private ElasticsearchChecks.TestCheck processIsDeployedCheck;

  @Autowired private WebApplicationContext context;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TasklistProperties tasklistProperties;

  private MockMvcHelper mockMvcHelper;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("camunda.tasklist.cloud.clusterId", () -> "449ac2ad-d3c6-4c73-9c68-7752e39ae616");
    registry.add("camunda.tasklist.client.clusterId", () -> "449ac2ad-d3c6-4c73-9c68-7752e39ae616");
    registry.add("camunda.tasklist.featureFlag.processPublicEndpoints", () -> true);
  }

  @Before
  public void setUp() {
    mockMvcHelper =
        new MockMvcHelper(MockMvcBuilders.webAppContextSetup(context).build(), objectMapper);
  }

  @Test
  public void searchProcessesByProcessId() {
    tasklistProperties.setVersion(TasklistProperties.ALPHA_RELEASES_SUFIX);
    // given
    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process.bpmn");
    final String processId2 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process_2.bpmn");
    final String processId3 = ZeebeTestUtil.deployProcess(zeebeClient, "userTaskForm.bpmn");

    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId1);
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId2);
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId3);

    // when
    final var result =
        mockMvcHelper.doRequest(get(TasklistURIs.PROCESSES_URL_V1).param("query", processId2));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingListContent(objectMapper, ProcessResponse.class)
        .singleElement()
        .satisfies(
            process -> {
              assertThat(process.getId()).isEqualTo(processId2);
              assertThat(process.getProcessDefinitionKey()).isEqualTo("testProcess2");
              assertThat(process.getVersion()).isEqualTo(1);
            });
  }

  @Test
  public void searchProcessesWhenEmptyQueryProvidedThenAllProcessesReturned() {
    tasklistProperties.setVersion(tasklistProperties.ALPHA_RELEASES_SUFIX);
    // given
    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process.bpmn");
    final String processId2 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process_2.bpmn");
    final String processId3 = ZeebeTestUtil.deployProcess(zeebeClient, "userTaskForm.bpmn");

    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId1);
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId2);
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId3);

    // when
    final var result = mockMvcHelper.doRequest(get(TasklistURIs.PROCESSES_URL_V1));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingListContent(objectMapper, ProcessResponse.class)
        .extracting("id", "processDefinitionKey", "version")
        .containsExactlyInAnyOrder(
            tuple(processId1, "Process_1g4wt4m", 1),
            tuple(processId2, "testProcess2", 1),
            tuple(processId3, "userTaskFormProcess", 1));
  }

  @Test
  public void searchProcessesWhenWrongQueryProvidedThenEmptyResultReturned() {
    // given
    final String query = "WRONG QUERY";
    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process.bpmn");
    final String processId2 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process_2.bpmn");
    final String processId3 = ZeebeTestUtil.deployProcess(zeebeClient, "userTaskForm.bpmn");

    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId1);
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId2);
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId3);

    // when
    final var result =
        mockMvcHelper.doRequest(get(TasklistURIs.PROCESSES_URL_V1).param("query", query));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingListContent(objectMapper, ProcessResponse.class)
        .isEmpty();
  }

  @Test
  public void startProcessInstance() {
    // given
    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process.bpmn");
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId1);

    // when
    final var result =
        mockMvcHelper.doRequest(
            patch(
                TasklistURIs.PROCESSES_URL_V1.concat("/{processDefinitionKey}/start"),
                "Process_1g4wt4m"));
    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingContent(objectMapper, ProcessInstanceDTO.class)
        .extracting("id")
        .isNotNull();
  }

  @Test
  public void startProcessInstanceWhenProcessNotFoundByProcessDefinitionKeyThen404ErrorExpected() {
    // given
    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process.bpmn");
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId1);

    // when
    final var result =
        mockMvcHelper.doRequest(
            patch(
                TasklistURIs.PROCESSES_URL_V1.concat("/{processDefinitionKey}/start"),
                "UNKNOWN_KEY"));

    // then
    assertThat(result)
        .hasHttpStatus(HttpStatus.NOT_FOUND)
        .hasApplicationProblemJsonContentType()
        .extractingErrorContent(objectMapper)
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasInstanceId()
        .hasMessage("No process definition found with processDefinitionKey: 'UNKNOWN_KEY'");
  }

  @Test
  public void deleteProcessInstance() {
    final String bpmnProcessId = "testProcess";
    final String flowNodeBpmnId = "taskA";
    final String processInstanceId =
        tester
            .createAndDeploySimpleProcess(bpmnProcessId, flowNodeBpmnId)
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstance(bpmnProcessId)
            .waitUntil()
            .taskIsCreated(flowNodeBpmnId)
            .claimAndCompleteHumanTask(
                flowNodeBpmnId,
                "delete",
                "\"me\"",
                "by",
                "\"REST API\"",
                "when",
                "\"processInstance is completed\"")
            .then()
            .waitUntil()
            .processInstanceIsCompleted()
            .getProcessInstanceId();

    // when
    final var result =
        mockMvcHelper.doRequest(
            delete(
                TasklistURIs.PROCESSES_URL_V1.concat("/{processInstanceId}"), processInstanceId));

    // then
    assertThat(result).hasHttpStatus(HttpStatus.NO_CONTENT).hasNoContent();
  }

  @Test
  public void deleteProcessInstanceWhenProcessNotFoundByProcessInstanceIdThen404ErrorExpected() {
    // given
    final var randomProcessInstanceId = randomNumeric(16);

    // when
    final var result =
        mockMvcHelper.doRequest(
            delete(
                TasklistURIs.PROCESSES_URL_V1.concat("/{processInstanceId}"),
                randomProcessInstanceId));

    // then
    assertThat(result)
        .hasHttpStatus(HttpStatus.NOT_FOUND)
        .hasApplicationProblemJsonContentType()
        .extractingErrorContent(objectMapper)
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasInstanceId()
        .hasMessage(
            "The process with processInstanceId: '%s' is not found", randomProcessInstanceId);
  }

  @Test
  public void shouldReturnPublicEndpointJustForLatestVersions() {
    tasklistProperties.getFeatureFlag().setProcessPublicEndpoints(true);
    // given
    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "subscribeFormProcess.bpmn");
    final String processId2 = ZeebeTestUtil.deployProcess(zeebeClient, "travelSearchProcess.bpmn");
    final String processId3 =
        ZeebeTestUtil.deployProcess(zeebeClient, "travelSearchProcess_v2.bpmn");

    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId1);
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId2);
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId3);

    // when
    final var result =
        mockMvcHelper.doRequest(get(TasklistURIs.PROCESSES_URL_V1.concat("/publicEndpoints")));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingListContent(objectMapper, ProcessPublicEndpointsResponse.class)
        .singleElement()
        .satisfies(
            process -> {
              assertThat(process.getProcessDefinitionKey()).isEqualTo(processId1);
              assertThat(process.getEndpoint())
                  .isEqualTo(TasklistURIs.START_PUBLIC_PROCESS.concat("subscribeFormProcess"));
            });
  }

  @Test
  public void shouldNotReturnPublicEndpoints() {
    // given
    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process.bpmn");
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId1);

    // when
    final var result =
        mockMvcHelper.doRequest(get(TasklistURIs.PROCESSES_URL_V1.concat("/publicEndpoints")));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingListContent(objectMapper, ProcessPublicEndpointsResponse.class)
        .isEmpty();
  }

  @Test
  public void shouldNotReturnPublicEndPointsAsFeatureFlagIsFalse() {
    tasklistProperties.getFeatureFlag().setProcessPublicEndpoints(false);
    // given
    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "subscribeFormProcess.bpmn");
    final String processId2 = ZeebeTestUtil.deployProcess(zeebeClient, "travelSearchProcess.bpmn");
    final String processId3 =
        ZeebeTestUtil.deployProcess(zeebeClient, "travelSearchProcess_v2.bpmn");

    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId1);
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId2);
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId3);

    // when
    final var result =
        mockMvcHelper.doRequest(get(TasklistURIs.PROCESSES_URL_V1.concat("/publicEndpoints")));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingListContent(objectMapper, ProcessPublicEndpointsResponse.class)
        .isEmpty();
  }

  @Test
  public void shouldReturnPublicEndpointByBpmnProcessId() {
    tasklistProperties.getFeatureFlag().setProcessPublicEndpoints(true);

    final String bpmnProcessId = "subscribeFormProcess";

    // given
    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "subscribeFormProcess.bpmn");
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId1);

    // when
    final var result =
        mockMvcHelper.doRequest(
            get(
                TasklistURIs.PROCESSES_URL_V1.concat("/{bpmnProcessId}/publicEndpoint"),
                bpmnProcessId));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingContent(objectMapper, ProcessPublicEndpointsResponse.class)
        .satisfies(
            process -> {
              assertThat(process.getProcessDefinitionKey()).isEqualTo(processId1);
              assertThat(process.getEndpoint())
                  .isEqualTo(TasklistURIs.START_PUBLIC_PROCESS.concat("subscribeFormProcess"));
            });
  }

  @Test
  public void shouldNotReturnPublicEndpointByBpmnProcessId() {
    tasklistProperties.getFeatureFlag().setProcessPublicEndpoints(true);

    final String bpmnProcessId = "travelSearchProcess";

    // given
    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "travelSearchProcess.bpmn");
    final String processId2 =
        ZeebeTestUtil.deployProcess(zeebeClient, "travelSearchProcess_v2.bpmn");
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId1);
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId2);

    // when
    final var result =
        mockMvcHelper.doRequest(
            get(
                TasklistURIs.PROCESSES_URL_V1.concat("/{bpmnProcessId}/publicEndpoint"),
                bpmnProcessId));

    // then
    assertThat(result)
        .hasHttpStatus(HttpStatus.NOT_FOUND)
        .hasApplicationProblemJsonContentType()
        .extractingErrorContent(objectMapper)
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasInstanceId()
        .hasMessage("The public endpoint for bpmnProcessId: '%s' is not found", bpmnProcessId);
  }
}
