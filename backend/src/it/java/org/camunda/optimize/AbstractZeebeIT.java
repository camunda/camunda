/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize;

import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import lombok.SneakyThrows;
import org.awaitility.Awaitility;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.dto.zeebe.definition.ZeebeProcessDefinitionRecordDto;
import org.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceDataDto;
import org.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceRecordDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import org.camunda.optimize.test.it.extension.ZeebeExtension;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Tag("Zeebe-test")
public abstract class AbstractZeebeIT extends AbstractIT {

  @RegisterExtension
  @Order(5)
  protected static ZeebeExtension zeebeExtension = new ZeebeExtension();

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

  protected void waitUntilMinimumDataExportedCount(final int minExportedEventCount, final String indexName,
                                                   final BoolQueryBuilder boolQueryBuilder) {
    waitUntilMinimumDataExportedCount(minExportedEventCount, indexName, boolQueryBuilder, 5);
  }

  protected BoolQueryBuilder getQueryForProcessableEvents() {
    return boolQuery().must(termsQuery(
      ZeebeProcessInstanceRecordDto.Fields.intent,
      ProcessInstanceIntent.ELEMENT_ACTIVATING.name(),
      ProcessInstanceIntent.ELEMENT_COMPLETED.name(),
      ProcessInstanceIntent.ELEMENT_TERMINATED.name()
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

  protected void waitUntilNumberOfDefinitionsExported(final int expectedDefinitionsCount) {
    waitUntilMinimumDataExportedCount(
      expectedDefinitionsCount,
      ElasticsearchConstants.ZEEBE_PROCESS_DEFINITION_INDEX_NAME,
      boolQuery().must(termQuery(ZeebeProcessDefinitionRecordDto.Fields.intent, ProcessIntent.CREATED.name()))
    );
  }

  protected void waitUntilInstanceRecordWithIdExported(final String instanceRecordId, final long timeoutInSeconds) {
    waitUntilMinimumDataExportedCount(
      1,
      ElasticsearchConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME,
      getInstanceRecordIdQuery(instanceRecordId),
      timeoutInSeconds
    );
  }

  protected String getFlowNodeInstanceIdFromProcessInstanceForActivity(final ProcessInstanceDto processInstanceDto,
                                                                       final String activityId) {
    return getPropertyIdFromProcessInstanceForActivity(
      processInstanceDto,
      activityId,
      FlowNodeInstanceDto::getFlowNodeInstanceId
    );
  }

  protected String getPropertyIdFromProcessInstanceForActivity(final ProcessInstanceDto processInstanceDto,
                                                               final String activityId,
                                                               final Function<FlowNodeInstanceDto, String> propertyFunction) {
    return processInstanceDto.getFlowNodeInstances()
      .stream()
      .filter(flowNodeInstanceDto -> flowNodeInstanceDto.getFlowNodeId().equals(activityId))
      .map(propertyFunction)
      .findFirst()
      .orElseThrow(() -> new OptimizeIntegrationTestException(
        "Could not find property for process instance with key: " + processInstanceDto.getProcessDefinitionKey()));
  }

  protected BpmnModelInstance readProcessDiagramAsInstance(final String diagramPath) {
    InputStream inputStream = getClass().getResourceAsStream(diagramPath);
    return Bpmn.readModelFromStream(inputStream);
  }

  protected static boolean isZeebeVersionPre82() {
    // data stores, date objects, link events, escalation events and undefined tasks were introduced with 8.2
    final Pattern zeebeVersionPreSequenceField = Pattern.compile("8.0.*|8.1.*");
    return zeebeVersionPreSequenceField.matcher(IntegrationTestConfigurationUtil.getZeebeDockerVersion()).matches();
  }

  private BoolQueryBuilder getInstanceRecordIdQuery(final String expectedRecordId) {
    return boolQuery().must(termQuery(
      ZeebeProcessInstanceRecordDto.Fields.value + "." + ZeebeProcessInstanceDataDto.Fields.elementId,
      expectedRecordId
    ));
  }

  @SneakyThrows
  private void waitUntilMinimumDataExportedCount(final long minimumCount, final String indexName,
                                                 final BoolQueryBuilder boolQueryBuilder, final long countTimeoutInSeconds) {
    final String expectedIndex = zeebeExtension.getZeebeRecordPrefix() + "-" + indexName;
    final OptimizeElasticsearchClient esClient = elasticSearchIntegrationTestExtension.getOptimizeElasticClient();
    Awaitility.given().ignoreExceptions()
      .timeout(10, TimeUnit.SECONDS)
      .untilAsserted(() -> assertThat(
        esClient
          .getHighLevelClient()
          .indices()
          .exists(new GetIndexRequest(expectedIndex), esClient.requestOptions())
      ).isTrue());
    final CountRequest countRequest = new CountRequest(expectedIndex).query(boolQueryBuilder);
    Awaitility.given().ignoreExceptions()
      .timeout(countTimeoutInSeconds, TimeUnit.SECONDS)
      .untilAsserted(() -> assertThat(
        esClient
          .getHighLevelClient()
          .count(countRequest, esClient.requestOptions())
          .getCount())
        .isGreaterThanOrEqualTo(minimumCount));
  }

}
