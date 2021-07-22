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
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.job.importing.VariableUpdateElasticsearchImportJob;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.LoggingEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.es.schema.IndexSettingsBuilder.buildDynamicSettings;

public class ImportIT extends AbstractImportIT {
  private static final String START_EVENT = "startEvent";
  private static final String END_EVENT = "endEvent";
  private static final String USER_TASK_1 = "userTask1";
  private static final String USER_TASK_2 = "userTask2";

  @RegisterExtension
  @Order(5)
  protected final LogCapturer logCapturer =
    LogCapturer.create().captureForType(VariableUpdateElasticsearchImportJob.class);


  @SneakyThrows
  @Test
  public void nestedDocsLimitExceptionLogIncludesConfigHint() {
    // given a process instance with more nested docs than the limit
    final int originalNestedDocLimit = embeddedOptimizeExtension.getConfigurationService().getEsNestedDocumentsLimit();
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
                                          ".index.nested_documents_limit). See Optimize documentation for details.")));
    } finally {
      updateProcessInstanceNestedDocLimit(instance.getProcessDefinitionKey(), originalNestedDocLimit);
    }
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
