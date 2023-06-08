/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import io.github.netmikey.logunit.api.LogCapturer;
import lombok.SneakyThrows;
import org.awaitility.Awaitility;
import org.camunda.optimize.dto.optimize.ProcessInstanceConstants;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.job.importing.VariableUpdateElasticsearchImportJob;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.util.BpmnModels;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.LoggingEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.es.schema.IndexSettingsBuilder.buildDynamicSettings;

public class ImportIT extends AbstractImportIT {

  private static final String START_EVENT = "startEvent";
  private static final String USER_TASK_1 = "userTask1";
  private static final String PROC_DEF_KEY = "aProcess";

  private int originalNestedDocLimit;

  @RegisterExtension
  @Order(5)
  protected final LogCapturer logCapturer =
    LogCapturer.create().captureForType(VariableUpdateElasticsearchImportJob.class);

  @BeforeEach
  public void setup() {
    originalNestedDocLimit = embeddedOptimizeExtension.getConfigurationService().getEsNestedDocumentsLimit();
  }

  @AfterEach
  public void tearDown() {
    updateProcessInstanceNestedDocLimit(PROC_DEF_KEY, originalNestedDocLimit);
  }

  @Test
  public void nestedDocsLimitExceptionLogIncludesConfigHint() {
    // given a process instance with more nested docs than the limit
    embeddedOptimizeExtension.getConfigurationService().setSkipDataAfterNestedDocLimitReached(false);
    final Map<String, Object> variables = new HashMap<>();
    variables.put("var1", 1);
    final ProcessInstanceEngineDto instance = deployAndStartSimpleTwoUserTaskProcessWithVariables(variables);
    // import first instance to create the index
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();

    // update index setting and create second instance with more nested docs than the limit
    updateProcessInstanceNestedDocLimit(instance.getProcessDefinitionKey(), 1);
    variables.put("var2", 2);
    engineIntegrationExtension.startProcessInstance(instance.getDefinitionId(), variables);

    // when
    embeddedOptimizeExtension.startContinuousImportScheduling();
    Awaitility.dontCatchUncaughtExceptions()
      .timeout(10, TimeUnit.SECONDS)
      .untilAsserted(() -> assertThat(logCapturer.getEvents())
        .extracting(LoggingEvent::getThrowable)
        .extracting(Throwable::getMessage)
        .isNotEmpty()
        .anyMatch(msg -> msg.contains(
          "If you are experiencing failures due to too many nested documents, try carefully increasing the configured" +
            " nested object limit (es.settings.index.nested_documents_limit) or enabling the skipping of documents that" +
            " have reached this limit during import (import.skipDataAfterNestedDocLimitReached). See Optimize" +
            " documentation for details.")));
  }

  @Test
  public void documentsHittingNestedDocLimitAreSkippedOnImportIfConfigurationEnabled() {
    // given
    embeddedOptimizeExtension.getConfigurationService().setSkipDataAfterNestedDocLimitReached(true);
    final ProcessInstanceEngineDto firstInstance = deployAndStartSimpleTwoUserTaskProcess();
    // import instance to create the index
    importAllEngineEntitiesFromScratch();
    // get the current nested document count for first instance
    final ProcessInstanceDto firstInstanceOnFirstRoundImport = getProcessInstanceForId(firstInstance.getId());
    final int currentNestedDocCount = getNestedDocumentCountForProcessInstance(firstInstanceOnFirstRoundImport);
    // the instance is incomplete so is initially active
    assertThat(firstInstanceOnFirstRoundImport.getState()).isEqualTo(ProcessInstanceConstants.ACTIVE_STATE);

    // update index setting so no more nested documents can be stored
    updateProcessInstanceNestedDocLimit(firstInstance.getProcessDefinitionKey(), currentNestedDocCount);
    // finished both user tasks so we would expect a second user task and end event flow node instances on next import
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    // and start a second instance, which should still be imported
    final ProcessInstanceEngineDto secondInstance =
      engineIntegrationExtension.startProcessInstance(firstInstance.getDefinitionId());

    // when
    importAllEngineEntitiesFromLastIndex();

    // then the first instance does not get updated with new nested data
    final ProcessInstanceDto firstInstanceAfterSecondRoundImport = getProcessInstanceForId(firstInstance.getId());
    assertThat(firstInstanceAfterSecondRoundImport.getFlowNodeInstances())
      .isEqualTo(firstInstanceOnFirstRoundImport.getFlowNodeInstances());
    // but the parent document state can still be updated
    assertThat(firstInstanceAfterSecondRoundImport.getState()).isEqualTo(ProcessInstanceConstants.COMPLETED_STATE);
    // and the second instance can be imported included its nested document
    assertThat(getProcessInstanceForId(secondInstance.getId()).getFlowNodeInstances())
      .extracting(FlowNodeInstanceDto::getFlowNodeId)
      .containsExactlyInAnyOrder(START_EVENT, USER_TASK_1);
  }

  private int getNestedDocumentCountForProcessInstance(final ProcessInstanceDto instance) {
    return instance.getFlowNodeInstances().size() + instance.getVariables().size()
      + instance.getIncidents().size();
  }

  private ProcessInstanceDto getProcessInstanceForId(final String processInstanceId) {
    final List<ProcessInstanceDto> instances = elasticSearchIntegrationTestExtension.getAllProcessInstances()
      .stream()
      .filter(instance -> instance.getProcessInstanceId().equals(processInstanceId))
      .collect(Collectors.toList());
    assertThat(instances).hasSize(1);
    return instances.get(0);
  }

  @SneakyThrows
  private void updateProcessInstanceNestedDocLimit(final String processDefinitionKey, final int nestedDocLimit) {
    embeddedOptimizeExtension.getConfigurationService().setEsNestedDocumentsLimit(nestedDocLimit);
    final OptimizeElasticsearchClient esClient = elasticSearchIntegrationTestExtension.getOptimizeElasticClient();
    final String indexName = esClient.getIndexNameService()
      .getOptimizeIndexNameWithVersionForAllIndicesOf(new ProcessInstanceIndex(processDefinitionKey));

    esClient.getHighLevelClient().indices().putSettings(
      new UpdateSettingsRequest(
        buildDynamicSettings(embeddedOptimizeExtension.getConfigurationService()),
        indexName
      ),
      esClient.requestOptions()
    );
  }

  private ProcessInstanceEngineDto deployAndStartSimpleTwoUserTaskProcess() {
    return deployAndStartSimpleTwoUserTaskProcessWithVariables(Collections.emptyMap());
  }

  private ProcessInstanceEngineDto deployAndStartSimpleTwoUserTaskProcessWithVariables(
    final Map<String, Object> variables) {
    return engineIntegrationExtension.deployAndStartProcessWithVariables(
      BpmnModels.getDoubleUserTaskDiagram(PROC_DEF_KEY), variables);
  }

}
