/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.cleanup;

import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.apache.commons.text.RandomStringGenerator;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.persistence.BusinessKeyDto;
import org.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.schema.index.VariableUpdateInstanceIndex;
import org.camunda.optimize.service.es.schema.index.events.EventIndex;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
import org.camunda.optimize.service.util.configuration.cleanup.CleanupMode;
import org.camunda.optimize.service.util.configuration.cleanup.ProcessCleanupConfiguration;
import org.camunda.optimize.service.util.configuration.cleanup.ProcessDefinitionCleanupConfiguration;
import org.camunda.optimize.test.util.VariableTestUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_INSTANCE_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.BUSINESS_KEY_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

public abstract class AbstractCleanupIT extends AbstractIT {
  private static final RandomStringGenerator KEY_GENERATOR = new RandomStringGenerator.Builder()
    .withinRange('a', 'z').build();

  protected void cleanUpEventIndices() {
    elasticSearchIntegrationTestExtension.deleteAllExternalEventIndices();
    elasticSearchIntegrationTestExtension.deleteAllVariableUpdateInstanceIndices();
    embeddedOptimizeExtension.getElasticSearchSchemaManager().createOrUpdateOptimizeIndex(
      embeddedOptimizeExtension.getOptimizeElasticClient(),
      new EventIndex()
    );
    embeddedOptimizeExtension.getElasticSearchSchemaManager().createOrUpdateOptimizeIndex(
      embeddedOptimizeExtension.getOptimizeElasticClient(),
      new VariableUpdateInstanceIndex()
    );
  }

  protected void configureHigherProcessSpecificTtl(final String processDefinitionKey) {
    getCleanupConfiguration().getProcessDataCleanupConfiguration()
      .getProcessDefinitionSpecificConfiguration()
      .put(
        processDefinitionKey,
        ProcessDefinitionCleanupConfiguration.builder()
          .cleanupMode(CleanupMode.ALL)
          // higher ttl than default
          .ttl(getCleanupConfiguration().getTtl().plusYears(5L))
          .build()
      );
  }

  @SneakyThrows
  protected void assertNoProcessInstanceDataExists(final List<ProcessInstanceEngineDto> instances) {
    assertThat(getProcessInstancesById(extractProcessInstanceIds(instances)).getHits().getTotalHits().value).isZero();
  }

  protected ProcessInstanceEngineDto startNewInstanceWithEndTimeLessThanTtl(final ProcessInstanceEngineDto originalInstance) {
    return startNewInstanceWithEndTime(getEndTimeLessThanGlobalTtl(), originalInstance);
  }

  protected ProcessInstanceEngineDto startNewInstanceWithEndTime(final OffsetDateTime endTime,
                                                                 final ProcessInstanceEngineDto originalInstance) {
    final ProcessInstanceEngineDto processInstance = startNewProcessWithSameProcessDefinitionId(originalInstance);
    modifyProcessInstanceEndTime(endTime, processInstance);
    return processInstance;
  }

  protected List<String> extractProcessInstanceIds(final List<ProcessInstanceEngineDto> unaffectedProcessInstances) {
    return unaffectedProcessInstances.stream().map(ProcessInstanceEngineDto::getId).collect(Collectors.toList());
  }

  protected ProcessInstanceEngineDto startNewProcessWithSameProcessDefinitionId(ProcessInstanceEngineDto processInstance) {
    return engineIntegrationExtension.startProcessInstance(
      processInstance.getDefinitionId(), VariableTestUtil.createAllPrimitiveTypeVariables()
    );
  }

  protected OffsetDateTime getEndTimeLessThanGlobalTtl() {
    return LocalDateUtil.getCurrentDateTime().minus(getCleanupConfiguration().getTtl()).minusSeconds(1);
  }

  @SneakyThrows
  protected List<ProcessInstanceEngineDto> deployProcessAndStartTwoProcessInstancesWithEndTimeLessThanTtl() {
    return deployProcessAndStartTwoProcessInstancesWithEndTime(getEndTimeLessThanGlobalTtl());
  }

  @SneakyThrows
  protected List<ProcessInstanceEngineDto> deployProcessAndStartTwoProcessInstancesWithEndTime(OffsetDateTime endTime) {
    final ProcessInstanceEngineDto firstProcInst = deployAndStartSimpleServiceTask();
    final ProcessInstanceEngineDto secondProcInst = startNewProcessWithSameProcessDefinitionId(firstProcInst);
    secondProcInst.setProcessDefinitionKey(firstProcInst.getProcessDefinitionKey());

    modifyProcessInstanceEndTime(endTime, firstProcInst, secondProcInst);

    return Lists.newArrayList(firstProcInst, secondProcInst);
  }

  @SneakyThrows
  protected void modifyProcessInstanceEndTime(final OffsetDateTime endTime,
                                              final ProcessInstanceEngineDto... processInstances) {
    Map<String, OffsetDateTime> procInstEndDateUpdates = new HashMap<>();
    for (final ProcessInstanceEngineDto instance : processInstances) {
      procInstEndDateUpdates.put(instance.getId(), endTime);
    }
    engineDatabaseExtension.changeProcessInstanceEndDates(procInstEndDateUpdates);
  }

  @SneakyThrows
  protected void assertVariablesEmptyInProcessInstances(final List<String> instanceIds) {

    SearchResponse idsResp = getProcessInstancesById(instanceIds);

    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(instanceIds.size());
    for (SearchHit searchHit : idsResp.getHits().getHits()) {
      assertThat((Collection<?>) searchHit.getSourceAsMap().get(VARIABLES)).isEmpty();
    }
  }

  protected SearchResponse getProcessInstancesById(final List<String> instanceIds) throws IOException {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(termsQuery(PROCESS_INSTANCE_ID, instanceIds))
      .trackTotalHits(true)
      .size(100);

    SearchRequest searchRequest = new SearchRequest()
      .indices(PROCESS_INSTANCE_MULTI_ALIAS)
      .source(searchSourceBuilder);

    return elasticSearchIntegrationTestExtension.getOptimizeElasticClient().search(searchRequest);
  }

  protected void assertProcessInstanceDataCompleteInEs(final String instanceId) throws IOException {
    assertProcessInstanceDataCompleteInEs(Collections.singletonList(instanceId));
  }

  protected void assertProcessInstanceDataCompleteInEs(final List<String> instanceIds) throws IOException {
    SearchResponse idsResp = getProcessInstancesById(instanceIds);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(instanceIds.size());

    for (SearchHit searchHit : idsResp.getHits().getHits()) {
      assertThat((Collection<?>) searchHit.getSourceAsMap().get(VARIABLES)).isNotEmpty();
    }
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleServiceTask() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess(KEY_GENERATOR.generate(8))
      .name("aProcessName")
      .startEvent()
      .serviceTask()
      .camundaExpression("${true}")
      .endEvent()
      .done();
    return engineIntegrationExtension.deployAndStartProcessWithVariables(
      processModel,
      VariableTestUtil.createAllPrimitiveTypeVariables()
    );
  }

  protected Instant getTimestampLessThanIngestedEventsTtl() {
    return OffsetDateTime.now().minus(getCleanupConfiguration().getTtl()).minusSeconds(1).toInstant();
  }

  protected ProcessCleanupConfiguration getProcessDataCleanupConfiguration() {
    return getCleanupConfiguration().getProcessDataCleanupConfiguration();
  }

  protected CleanupConfiguration getCleanupConfiguration() {
    return embeddedOptimizeExtension.getConfigurationService()
      .getCleanupServiceConfiguration();
  }

  protected List<CamundaActivityEventDto> getCamundaActivityEvents() {
    return elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
      CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX + "*", CamundaActivityEventDto.class
    );
  }

  protected List<BusinessKeyDto> getAllCamundaEventBusinessKeys() {
    return elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
      BUSINESS_KEY_INDEX_NAME,
      BusinessKeyDto.class
    );
  }
}
