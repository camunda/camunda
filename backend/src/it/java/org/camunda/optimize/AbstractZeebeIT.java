/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize;

import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import lombok.SneakyThrows;
import org.awaitility.Awaitility;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceRecordDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.test.it.extension.ZeebeExtension;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.ZeebeBpmnModels.SERVICE_TASK;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

public abstract class AbstractZeebeIT extends AbstractIT {

  @RegisterExtension
  @Order(5)
  protected ZeebeExtension zeebeExtension = new ZeebeExtension();

  @BeforeEach
  public void setupZeebeImportAndReloadConfiguration() {
    final String embeddedZeebePrefix = zeebeExtension.getZeebeRecordPrefix();
    // set the new record prefix for the next test
    embeddedOptimizeExtension.getConfigurationService().getConfiguredZeebe().setEnabled(true);
    embeddedOptimizeExtension.getConfigurationService().getConfiguredZeebe().setName(embeddedZeebePrefix);
    embeddedOptimizeExtension.reloadConfiguration();
  }

  @AfterEach
  public void after() {
    // Clear all potential existing Zeebe records in Optimize
    elasticSearchIntegrationTestExtension.deleteAllZeebeRecordsForPrefix(zeebeExtension.getZeebeRecordPrefix());
  }

  protected void importAllZeebeEntitiesFromScratch() {
    embeddedOptimizeExtension.importAllZeebeEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  protected void importAllZeebeEntitiesFromLastIndex() {
    embeddedOptimizeExtension.importAllZeebeEntitiesFromLastIndex();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  protected ProcessInstanceEvent deployAndStartInstanceForProcess(final BpmnModelInstance process) {
    final Process deployedProcess = zeebeExtension.deployProcess(process);
    return zeebeExtension.startProcessInstanceForProcess(deployedProcess.getBpmnProcessId());
  }

  @SneakyThrows
  protected void waitUntilMinimumDataExportedCount(final int minExportedEventCount, final String indexName,
                                                   final BoolQueryBuilder boolQueryBuilder) {
    final String expectedIndex =
      zeebeExtension.getZeebeRecordPrefix() + "-" + indexName;
    final OptimizeElasticsearchClient esClient =
      elasticSearchIntegrationTestExtension.getOptimizeElasticClient();
    Awaitility.given().ignoreExceptions()
      .timeout(5, TimeUnit.SECONDS)
      .untilAsserted(() -> assertThat(
        esClient
          .getHighLevelClient()
          .indices()
          .exists(new GetIndexRequest(expectedIndex), esClient.requestOptions())
      ).isTrue());
    final CountRequest definitionCountRequest =
      new CountRequest(expectedIndex)
        .query(boolQueryBuilder);
    Awaitility.given().ignoreExceptions()
      .timeout(5, TimeUnit.SECONDS)
      .untilAsserted(() -> assertThat(
        esClient
          .getHighLevelClient()
          .count(definitionCountRequest, esClient.requestOptions())
          .getCount())
        .isGreaterThanOrEqualTo(minExportedEventCount));
  }

  protected BoolQueryBuilder getQueryForProcessableEvents() {
    return boolQuery().must(termsQuery(
      ZeebeProcessInstanceRecordDto.Fields.intent,
      ProcessInstanceIntent.ELEMENT_ACTIVATING,
      ProcessInstanceIntent.ELEMENT_COMPLETED,
      ProcessInstanceIntent.ELEMENT_TERMINATED
    ));
  }

  protected String getConfiguredZeebeName() {
    return embeddedOptimizeExtension.getConfigurationService().getConfiguredZeebe().getName();
  }

  protected void waitUntilMinimumProcessInstanceEventsExportedCount(final int minExportedEventCount) {
    waitUntilMinimumDataExportedCount(
      minExportedEventCount,
      ElasticsearchConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME,
      getQueryForProcessableEvents()
    );
  }

  protected String getServiceTaskFlowNodeIdFromProcessInstance(final ProcessInstanceDto processInstanceDto) {
    return processInstanceDto.getFlowNodeInstances()
      .stream()
      .filter(flowNodeInstanceDto -> flowNodeInstanceDto.getFlowNodeId().equals(SERVICE_TASK))
      .map(FlowNodeInstanceDto::getFlowNodeInstanceId)
      .findFirst()
      .orElseThrow(() -> new OptimizeIntegrationTestException(
        "Could not find service task for process instance with key: " + processInstanceDto.getProcessDefinitionKey()));
  }
}
