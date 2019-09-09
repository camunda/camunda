/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.cleanup;

import com.google.common.collect.Lists;
import org.apache.commons.collections.ListUtils;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.util.configuration.cleanup.CleanupMode;
import org.camunda.optimize.service.util.configuration.cleanup.DecisionDefinitionCleanupConfiguration;
import org.camunda.optimize.service.util.configuration.cleanup.OptimizeCleanupConfiguration;
import org.camunda.optimize.service.util.configuration.cleanup.ProcessDefinitionCleanupConfiguration;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.VariableTestUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.io.IOException;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.DECISION_INSTANCE_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_INSTANCE_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

public class OptimizeCleanupServiceIT {
  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule(engineRule.getEngineName());

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule).around(engineDatabaseRule);

  @Test
  public void verifyCleanupDisabledByDefault() {
    assertThat(getCleanupConfiguration().getEnabled(), is(false));
    assertThat(embeddedOptimizeRule.getCleanupScheduler().isScheduledToRun(), is(false));
  }

  @Test
  public void testCleanupIsScheduledSuccessfully() {
    embeddedOptimizeRule.getCleanupScheduler().startCleanupScheduling();
    try {
      assertThat(embeddedOptimizeRule.getCleanupScheduler().isScheduledToRun(), is(true));
    } finally {
      embeddedOptimizeRule.getCleanupScheduler().stopCleanupScheduling();
    }
  }

  @Test
  public void testCleanupScheduledStoppedSuccessfully() {
    embeddedOptimizeRule.getCleanupScheduler().startCleanupScheduling();
    embeddedOptimizeRule.getCleanupScheduler().stopCleanupScheduling();
    assertThat(embeddedOptimizeRule.getCleanupScheduler().isScheduledToRun(), is(false));
  }

  @Test
  public void testCleanupWithProcessInstanceDelete() throws SQLException, IOException {
    // given
    getCleanupConfiguration().setDefaultProcessDataCleanupMode(CleanupMode.ALL);
    final List<String> clearedProcessDefinitionsIds = deployTwoProcessInstancesWithEndTimeLessThanTtl();

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    embeddedOptimizeRule.getCleanupScheduler().runCleanup();
    elasticSearchRule.refreshAllOptimizeIndices();

    //then
    assertProcessInstanceDataCompleteInEs(Collections.emptyList());
  }

  @Test
  public void testCleanupWithProcessInstanceDeleteVerifyThatNewOnesAreUnaffected() throws SQLException, IOException {
    // given
    getCleanupConfiguration().setDefaultProcessDataCleanupMode(CleanupMode.ALL);
    final List<String> clearedProcessDefinitionsIds = deployTwoProcessInstancesWithEndTimeLessThanTtl();
    final List<String> unaffectedProcessDefinitionsIds = deployTwoProcessInstancesWithEndTime(OffsetDateTime.now());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    embeddedOptimizeRule.getCleanupScheduler().runCleanup();
    elasticSearchRule.refreshAllOptimizeIndices();

    //then
    assertProcessInstanceDataCompleteInEs(unaffectedProcessDefinitionsIds);
  }

  @Test
  public void testCleanupProcessInstanceVariablesCleared() throws SQLException, IOException {
    // given
    getCleanupConfiguration().setDefaultProcessDataCleanupMode(CleanupMode.VARIABLES);
    final List<String> clearedProcessDefinitionsIds = deployTwoProcessInstancesWithEndTimeLessThanTtl();

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    embeddedOptimizeRule.getCleanupScheduler().runCleanup();
    elasticSearchRule.refreshAllOptimizeIndices();

    //then
    assertVariablesEmptyInProcessInstances(clearedProcessDefinitionsIds);
  }

  @Test
  public void testCleanupProcessInstanceVariablesClearedVerifyThatNewOnesAreUnaffected() throws
                                                                                         SQLException,
                                                                                         IOException {
    // given
    getCleanupConfiguration().setDefaultProcessDataCleanupMode(CleanupMode.VARIABLES);
    final List<String> clearedProcessDefinitionsIds = deployTwoProcessInstancesWithEndTimeLessThanTtl();
    final List<String> unaffectedProcessDefinitionsIds = deployTwoProcessInstancesWithEndTime(OffsetDateTime.now());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    embeddedOptimizeRule.getCleanupScheduler().runCleanup();
    elasticSearchRule.refreshAllOptimizeIndices();

    //then
    assertVariablesEmptyInProcessInstances(clearedProcessDefinitionsIds);
    assertProcessInstanceDataCompleteInEs(unaffectedProcessDefinitionsIds);
  }

  @Test
  public void testFailCleanupOnSpecificKeyConfigWithNoMatchingProcessDefinitionNoInstancesCleaned()
    throws SQLException, IOException {
    // given I have a key specific config
    final String configuredKey = "myMistypedKey";
    getCleanupConfiguration().getProcessDefinitionSpecificConfiguration().put(
      configuredKey,
      new ProcessDefinitionCleanupConfiguration(CleanupMode.VARIABLES)
    );
    // and deploy processes with different keys
    final List<String> processDefinitionsWithEndTimeLessThanTtl = deployTwoProcessInstancesWithEndTimeLessThanTtl();
    final List<String> unaffectedProcessDefinitionsIds = deployTwoProcessInstancesWithEndTime(OffsetDateTime.now());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    embeddedOptimizeRule.getCleanupScheduler().runCleanup();
    elasticSearchRule.refreshAllOptimizeIndices();

    // all data is still there
    assertProcessInstanceDataCompleteInEs(
      ListUtils.union(processDefinitionsWithEndTimeLessThanTtl, unaffectedProcessDefinitionsIds)
    );
  }

  @Test
  public void testCleanupWithDecisionInstanceDelete() throws SQLException, IOException {
    // given
    final List<String> clearedDecisionDefinitionsIds = deployTwoDecisionInstancesWithEvaluationTimeLessThanTtl();

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    embeddedOptimizeRule.getCleanupScheduler().runCleanup();
    elasticSearchRule.refreshAllOptimizeIndices();

    //then
    assertDecisionInstancesExistInEs(Collections.emptyList());
  }

  @Test
  public void testCleanupWithDecisionInstanceDeleteVerifyThatNewOnesAreUnaffected() throws SQLException, IOException {
    // given
    final List<String> clearedDecisionDefinitionsIds = deployTwoDecisionInstancesWithEvaluationTimeLessThanTtl();
    final List<String> unaffectedDecisionDefinitionsIds = deployTwoDecisionInstancesWithEvaluationTime(
      OffsetDateTime.now()
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    embeddedOptimizeRule.getCleanupScheduler().runCleanup();
    elasticSearchRule.refreshAllOptimizeIndices();

    //then
    assertDecisionInstancesExistInEs(unaffectedDecisionDefinitionsIds);
  }

  @Test
  public void testFailCleanupOnSpecificKeyConfigWithNoMatchingDecisionDefinitionNoInstancesCleaned()
    throws SQLException, IOException {
    // given I have a key specific config
    final String configuredKey = "myMistypedKey";
    getCleanupConfiguration().getDecisionDefinitionSpecificConfiguration().put(
      configuredKey,
      new DecisionDefinitionCleanupConfiguration(getCleanupConfiguration().getDefaultTtl())
    );
    // and deploy processes with different keys
    final List<String> decisionDefinitionsWithEvaluationTimeLessThanTtl =
      deployTwoDecisionInstancesWithEvaluationTimeLessThanTtl();
    final List<String> unaffectedDecisionDefinitionsIds =
      deployTwoDecisionInstancesWithEvaluationTime(OffsetDateTime.now());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    embeddedOptimizeRule.getCleanupScheduler().runCleanup();

    elasticSearchRule.refreshAllOptimizeIndices();

    // all data is still there
    assertDecisionInstancesExistInEs(
      ListUtils.union(decisionDefinitionsWithEvaluationTimeLessThanTtl, unaffectedDecisionDefinitionsIds)
    );
  }

  private ProcessInstanceEngineDto startNewProcessWithSameProcessDefinitionId(String processDefinitionId) {
    return engineRule.startProcessInstance(processDefinitionId, VariableTestUtil.createAllPrimitiveTypeVariables());
  }

  private List<String> deployTwoDecisionInstancesWithEvaluationTimeLessThanTtl() throws SQLException {
    return deployTwoDecisionInstancesWithEvaluationTime(
      OffsetDateTime.now().minus(getCleanupConfiguration().getDefaultTtl()).minusSeconds(1)
    );
  }

  private List<String> deployTwoDecisionInstancesWithEvaluationTime(OffsetDateTime evaluationTime) throws SQLException {
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto = engineRule.deployDecisionDefinition();

    OffsetDateTime lastEvaluationDateFilter = OffsetDateTime.now();
    engineRule.startDecisionInstance(decisionDefinitionEngineDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionEngineDto.getId());

    engineDatabaseRule.changeDecisionInstanceEvaluationDate(lastEvaluationDateFilter, evaluationTime);

    return engineDatabaseRule.getDecisionInstanceIdsWithEvaluationDateEqualTo(evaluationTime);
  }

  private List<String> deployTwoProcessInstancesWithEndTimeLessThanTtl() throws SQLException {
    return deployTwoProcessInstancesWithEndTime(
      OffsetDateTime.now().minus(getCleanupConfiguration().getDefaultTtl()).minusSeconds(1)
    );
  }

  private List<String> deployTwoProcessInstancesWithEndTime(OffsetDateTime endTime) throws SQLException {
    final ProcessInstanceEngineDto firstProcInst = deployAndStartSimpleServiceTask();
    final ProcessInstanceEngineDto secondProcInst = startNewProcessWithSameProcessDefinitionId(
      firstProcInst.getDefinitionId()
    );

    Map<String, OffsetDateTime> procInstEndDateUpdates = new HashMap<>();
    procInstEndDateUpdates.put(firstProcInst.getId(), endTime);
    procInstEndDateUpdates.put(secondProcInst.getId(), endTime);
    engineDatabaseRule.updateProcessInstanceEndDates(procInstEndDateUpdates);

    return Lists.newArrayList(firstProcInst.getId(), secondProcInst.getId());
  }

  private void assertVariablesEmptyInProcessInstances(List<String> processIds) throws IOException {

    SearchResponse idsResp = getProcessInstancesById(processIds);

    assertThat(idsResp.getHits().getTotalHits(), is(Long.valueOf(processIds.size())));
    for (SearchHit searchHit : idsResp.getHits().getHits()) {
      assertThat(
        VARIABLES + " is empty",
        searchHit.getSourceAsMap().get(VARIABLES),
        is(Collections.emptyList())
      );
    }
  }

  private SearchResponse getProcessInstancesById(List<String> processIds) throws IOException {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(termsQuery(PROCESS_INSTANCE_ID, processIds))
      .size(100);

    SearchRequest searchRequest = new SearchRequest()
      .indices(PROCESS_INSTANCE_INDEX_NAME)
      .types(PROCESS_INSTANCE_INDEX_NAME)
      .source(searchSourceBuilder);

    return elasticSearchRule.getOptimizeElasticClient().search(searchRequest, RequestOptions.DEFAULT);
  }

  private void assertProcessInstanceDataCompleteInEs(List<String> processIds) throws IOException {
    SearchResponse idsResp = getProcessInstancesById(processIds);
    assertThat(idsResp.getHits().getTotalHits(), is(Long.valueOf(processIds.size())));

    for (SearchHit searchHit : idsResp.getHits().getHits()) {
      assertThat(
        VARIABLES + " is not empty",
        ((Collection) searchHit.getSourceAsMap().get(VARIABLES)).size(),
        is(greaterThan(0))
      );
    }
  }

  private SearchResponse getDecisionInstancesById(List<String> decisionInstanceIds) throws IOException {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(termsQuery(DECISION_INSTANCE_ID, decisionInstanceIds))
      .size(100);

    SearchRequest searchRequest = new SearchRequest()
      .indices(DECISION_INSTANCE_INDEX_NAME)
      .types(DECISION_INSTANCE_INDEX_NAME)
      .source(searchSourceBuilder);

    return elasticSearchRule.getOptimizeElasticClient().search(searchRequest, RequestOptions.DEFAULT);
  }

  private void assertDecisionInstancesExistInEs(List<String> decisionInstanceIds) throws IOException {
    SearchResponse idsResp = getDecisionInstancesById(decisionInstanceIds);
    assertThat(idsResp.getHits().getTotalHits(), is(Long.valueOf(decisionInstanceIds.size())));
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTask() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .serviceTask()
      .camundaExpression("${true}")
      .endEvent()
      .done();
    return engineRule.deployAndStartProcessWithVariables(
      processModel,
      VariableTestUtil.createAllPrimitiveTypeVariables()
    );
  }


  private OptimizeCleanupConfiguration getCleanupConfiguration() {
    return embeddedOptimizeRule.getConfigurationService().getCleanupServiceConfiguration();
  }
}
