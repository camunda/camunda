/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import lombok.SneakyThrows;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.UserTaskInstanceDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.VariableTestUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static javax.ws.rs.HttpMethod.GET;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.SUSPENDED_STATE;
import static org.camunda.optimize.service.util.configuration.EngineConstants.COMPLETED_USER_TASK_INSTANCE_ENDPOINT;
import static org.camunda.optimize.service.util.configuration.EngineConstants.DECISION_DEFINITION_ENDPOINT;
import static org.camunda.optimize.service.util.configuration.EngineConstants.IDENTITY_LINK_LOG_ENDPOINT;
import static org.camunda.optimize.service.util.configuration.EngineConstants.PROCESS_DEFINITION_ENDPOINT;
import static org.camunda.optimize.service.util.configuration.EngineConstants.RUNNING_USER_TASK_INSTANCE_ENDPOINT;
import static org.camunda.optimize.service.util.configuration.EngineConstants.TENANT_ENDPOINT;
import static org.camunda.optimize.service.util.configuration.EngineConstants.USER_OPERATION_LOG_ENDPOINT;
import static org.camunda.optimize.service.util.configuration.EngineConstants.VARIABLE_UPDATE_ENDPOINT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_INDEX_NAME;
import static org.mockserver.model.HttpRequest.request;

public class ImportIT extends AbstractImportIT {
  private static final String START_EVENT = "startEvent";
  private static final String END_EVENT = "endEvent";
  private static final String USER_TASK_1 = "userTask1";
  private static final String USER_TASK_2 = "userTask2";

  private static Stream<String> getEndpoints() {
    return Stream.of(
      DECISION_DEFINITION_ENDPOINT,
      PROCESS_DEFINITION_ENDPOINT,
      TENANT_ENDPOINT,
      RUNNING_USER_TASK_INSTANCE_ENDPOINT,
      COMPLETED_USER_TASK_INSTANCE_ENDPOINT,
      IDENTITY_LINK_LOG_ENDPOINT,
      VARIABLE_UPDATE_ENDPOINT,
      USER_OPERATION_LOG_ENDPOINT
    );
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("getEndpoints")
  public void importWorksDespiteTemporaryFetchingFailures(String endpoint) {
    // given "one of everything"
    engineIntegrationExtension.createTenant("someTenantId", "someTenantName");
    engineIntegrationExtension.deployDecisionDefinition();
    final Map<String, Object> variables = VariableTestUtil.createAllPrimitiveTypeVariables();
    final ProcessInstanceEngineDto processInstance = deployAndStartSimpleTwoUserTaskProcessWithVariables(variables);
    final String testCandidateGroup = "testCandidateGroup";
    engineIntegrationExtension.createGroup(testCandidateGroup);
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(testCandidateGroup);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.suspendProcessInstance(processInstance.getId());

    // when fetching endpoint temporarily fails
    final HttpRequest importFetcherEndpointMatcher = request()
      .withPath(".*" + endpoint)
      .withMethod(GET);
    final ClientAndServer esMockServer = useAndGetEngineMockServer();
    esMockServer
      .when(importFetcherEndpointMatcher)
      .respond(new HttpResponse().withStatusCode(500));

    // make sure fetching endpoint is called during import
    embeddedOptimizeExtension.startContinuousImportScheduling();
    Thread.sleep(500);
    esMockServer.verify(importFetcherEndpointMatcher);

    // endpoint no longer fails
    esMockServer.reset();

    // wait for import to finish
    embeddedOptimizeExtension.ensureImportSchedulerIsIdle(5000L);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then everything has been imported
    assertDocumentCountInES(DECISION_DEFINITION_INDEX_NAME, 1L);
    assertDocumentCountInES(PROCESS_DEFINITION_INDEX_NAME, 1L);
    assertDocumentCountInES(TENANT_INDEX_NAME, 1L);
    assertDocumentCountInES(PROCESS_INSTANCE_INDEX_NAME, 1L);

    final List<ProcessInstanceDto> storedProcessInstances =
      elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(storedProcessInstances)
      .allSatisfy(processInstanceDto -> {
        assertThat(processInstanceDto.getUserTasks()).hasSize(2);
        assertThat(processInstanceDto.getUserTasks())
          .extracting(UserTaskInstanceDto::getActivityId)
          .containsExactlyInAnyOrder(USER_TASK_1, USER_TASK_2);
        assertThat(processInstanceDto.getUserTasks())
          .flatExtracting(UserTaskInstanceDto::getCandidateGroupOperations)
          .allMatch(groupOperation -> groupOperation.getGroupId().equals(testCandidateGroup));
        assertThat(processInstanceDto.getVariables()).hasSize(variables.size());
        assertThat(processInstanceDto.getState()).isEqualTo(SUSPENDED_STATE);
      });
  }

  protected void assertDocumentCountInES(final String elasticsearchIndex,
                                         final long count) {
    final Integer docCount = elasticSearchIntegrationTestExtension.getDocumentCountOf(elasticsearchIndex);
    assertThat(docCount).isEqualTo(count);
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleTwoUserTaskProcessWithVariables(
    final Map<String, Object> variables) {
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
        .userTask(USER_TASK_1)
        .userTask(USER_TASK_2)
        .serviceTask()
          .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    // @formatter:on
    return engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
  }

}
