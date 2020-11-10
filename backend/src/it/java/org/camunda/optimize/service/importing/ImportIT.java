/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import io.github.netmikey.logunit.api.LogCapturer;
import lombok.SneakyThrows;
import org.awaitility.Awaitility;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.UserTaskInstanceDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.job.importing.VariableUpdateElasticsearchImportJob;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.test.it.extension.ErrorResponseMock;
import org.camunda.optimize.test.util.VariableTestUtil;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.client.RequestOptions;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.slf4j.event.LoggingEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static javax.ws.rs.HttpMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.SUSPENDED_STATE;
import static org.camunda.optimize.service.es.schema.IndexSettingsBuilder.buildDynamicSettings;
import static org.camunda.optimize.service.util.importing.EngineConstants.COMPLETED_USER_TASK_INSTANCE_ENDPOINT;
import static org.camunda.optimize.service.util.importing.EngineConstants.DECISION_DEFINITION_ENDPOINT;
import static org.camunda.optimize.service.util.importing.EngineConstants.IDENTITY_LINK_LOG_ENDPOINT;
import static org.camunda.optimize.service.util.importing.EngineConstants.PROCESS_DEFINITION_ENDPOINT;
import static org.camunda.optimize.service.util.importing.EngineConstants.RUNNING_USER_TASK_INSTANCE_ENDPOINT;
import static org.camunda.optimize.service.util.importing.EngineConstants.TENANT_ENDPOINT;
import static org.camunda.optimize.service.util.importing.EngineConstants.USER_OPERATION_LOG_ENDPOINT;
import static org.camunda.optimize.service.util.importing.EngineConstants.VARIABLE_UPDATE_ENDPOINT;
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

  @RegisterExtension
  @Order(5)
  protected final LogCapturer logCapturer =
    LogCapturer.create().captureForType(VariableUpdateElasticsearchImportJob.class);

  private static Stream<Arguments> getEndpointsAndErrorResponses() {
    return Stream.of(
      DECISION_DEFINITION_ENDPOINT,
      PROCESS_DEFINITION_ENDPOINT,
      TENANT_ENDPOINT,
      RUNNING_USER_TASK_INSTANCE_ENDPOINT,
      COMPLETED_USER_TASK_INSTANCE_ENDPOINT,
      IDENTITY_LINK_LOG_ENDPOINT,
      VARIABLE_UPDATE_ENDPOINT,
      USER_OPERATION_LOG_ENDPOINT
    ).flatMap(endpoint -> engineErrors()
      .map(mockResp -> Arguments.of(endpoint, mockResp)));
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("getEndpointsAndErrorResponses")
  public void importWorksDespiteTemporaryFetchingFailures(String endpoint,
                                                          ErrorResponseMock mockResp) {
    // given "one of everything"
    engineIntegrationExtension.createTenant("someTenantId", "someTenantName");
    engineIntegrationExtension.deployDecisionDefinition();
    final Map<String, Object> variables = VariableTestUtil.createAllPrimitiveTypeVariables();
    final ProcessInstanceEngineDto processInstance = deployAndStartSimpleTwoUserTaskProcessWithVariables(variables);
    final String testCandidateGroup = "testCandidateGroup";
    engineIntegrationExtension.createGroup(testCandidateGroup);
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(testCandidateGroup);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.suspendProcessInstanceByInstanceId(processInstance.getId());

    // when fetching endpoint temporarily fails
    final HttpRequest importFetcherEndpointMatcher = request()
      .withPath(".*" + endpoint)
      .withMethod(GET);
    final ClientAndServer engineMockServer = useAndGetEngineMockServer();

    mockResp.mock(importFetcherEndpointMatcher, Times.unlimited(), engineMockServer);

    // make sure fetching endpoint is called during import
    embeddedOptimizeExtension.startContinuousImportScheduling();
    Awaitility.catchUncaughtExceptions()
      .timeout(10, TimeUnit.SECONDS)
      .untilAsserted(() -> engineMockServer.verify(importFetcherEndpointMatcher));

    // endpoint no longer fails
    engineMockServer.reset();

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
      .isNotEmpty()
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

  @SneakyThrows
  @Test
  public void nestedDocsLimitExceptionLogIncludesConfigHint() {
    // given a process instance with more nested docs than the limit
    final int originalNestedDocLimit = embeddedOptimizeExtension.getConfigurationService().getEsNestedDocumentsLimit();
    updateProcessInstanceNestedDocLimit(1);
    final Map<String, Object> variables = new HashMap<>();
    variables.put("var1", 1);
    variables.put("var2", 2);
    deployAndStartSimpleTwoUserTaskProcessWithVariables(variables);

    // when
    try {
      embeddedOptimizeExtension.startContinuousImportScheduling();
      Awaitility.dontCatchUncaughtExceptions()
        .timeout(10, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(logCapturer.getEvents())
          .extracting(LoggingEvent::getThrowable)
          .extracting(Throwable::getMessage)
          .isNotEmpty()
          .anyMatch(msg -> msg.contains("If you are experiencing failures due to too many nested documents, " +
                                          "try carefully increasing the configured nested object limit (es.settings" +
                                          ".index.nested_documents_limit). " +
                                          "See Optimize documentation for details.")));
    } finally {
      updateProcessInstanceNestedDocLimit(originalNestedDocLimit);
    }
  }

  @SneakyThrows
  private void updateProcessInstanceNestedDocLimit(final int nestedDocLimit) {
    embeddedOptimizeExtension.getConfigurationService().setEsNestedDocumentsLimit(nestedDocLimit);
    final OptimizeElasticsearchClient esClient = elasticSearchIntegrationTestExtension.getOptimizeElasticClient();
    final String indexName =
      esClient.getIndexNameService().getOptimizeIndexNameWithVersionForAllIndicesOf(new ProcessInstanceIndex());

    esClient.getHighLevelClient().indices().putSettings(
      new UpdateSettingsRequest(
        buildDynamicSettings(embeddedOptimizeExtension.getConfigurationService()),
        indexName
      ),
      RequestOptions.DEFAULT
    );
  }

  private void assertDocumentCountInES(final String elasticsearchIndex,
                                       final long count) {
    final Integer docCount = elasticSearchIntegrationTestExtension.getDocumentCountOf(elasticsearchIndex);
    assertThat(docCount.longValue()).isEqualTo(count);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleTwoUserTaskProcessWithVariables(
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
