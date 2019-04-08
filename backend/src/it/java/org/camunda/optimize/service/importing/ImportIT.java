/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableRetrievalDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.filter.CanceledInstancesOnlyQueryFilter;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.metrics.valuecount.ValueCount;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.io.IOException;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.END_DATE;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.EVENTS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_DEF_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.count;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertTrue;


public class ImportIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule).around(engineDatabaseRule);

  @Test
  public void importCanBeDisabled() throws IOException {
    // given
    embeddedOptimizeRule.getConfigurationService().getConfiguredEngines().values()
      .forEach(engineConfiguration -> engineConfiguration.setImportEnabled(false));
    embeddedOptimizeRule.reloadConfiguration();

    deployAndStartSimpleServiceTask();
    engineRule.deployAndStartDecisionDefinition();
    BpmnModelInstance exampleProcess = Bpmn.createExecutableProcess().name("foo").startEvent().endEvent().done();
    engineRule.deployAndStartProcess(exampleProcess);

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    allEntriesInElasticsearchHaveAllDataWithCount(PROC_INSTANCE_TYPE, 0L);
    allEntriesInElasticsearchHaveAllDataWithCount(PROC_DEF_TYPE, 0L);
    allEntriesInElasticsearchHaveAllDataWithCount(DECISION_DEFINITION_TYPE, 0L);
    allEntriesInElasticsearchHaveAllDataWithCount(DECISION_INSTANCE_TYPE, 0L);
    assertThat(embeddedOptimizeRule.getImportSchedulerFactory().getImportSchedulers().size(), is(greaterThan(0)));
    embeddedOptimizeRule.getImportSchedulerFactory().getImportSchedulers()
      .forEach(engineImportScheduler -> assertThat(engineImportScheduler.isEnabled(), is(false)));
  }

  @Test
  public void allProcessDefinitionFieldDataOfImportIsAvailable() throws IOException {
    //given
    deployAndStartSimpleServiceTask();

    //when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //then
    allEntriesInElasticsearchHaveAllData(PROC_DEF_TYPE);
  }

  @Test
  public void allEventFieldDataOfImportIsAvailable() throws IOException {
    //given
    deployAndStartSimpleServiceTask();

    //when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //then
    allEntriesInElasticsearchHaveAllData(PROC_INSTANCE_TYPE);
  }

  @Test
  public void failingJobDoesNotUpdateImportIndex() throws IOException {
    //given
    ProcessInstanceEngineDto dto1 = deployAndStartSimpleServiceTask();
    OffsetDateTime endTime = engineRule.getHistoricProcessInstance(dto1.getId()).getEndTime();

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    embeddedOptimizeRule.importAllEngineEntitiesFromLastIndex();
    elasticSearchRule.refreshAllOptimizeIndices();

    elasticSearchRule.blockProcInstIndex(true);

    ProcessInstanceEngineDto dto2 = deployAndStartSimpleServiceTask();

    Thread thread = new Thread(() -> embeddedOptimizeRule.importAllEngineEntitiesFromLastIndex());
    thread.start();

    OffsetDateTime lastImportTimestamp = elasticSearchRule.getLastProcessInstanceImportTimestamp();
    assertThat(lastImportTimestamp, is(endTime));

    elasticSearchRule.blockProcInstIndex(false);
    endTime = engineRule.getHistoricProcessInstance(dto2.getId()).getEndTime();

    embeddedOptimizeRule.importAllEngineEntitiesFromLastIndex();
    embeddedOptimizeRule.importAllEngineEntitiesFromLastIndex();

    lastImportTimestamp = elasticSearchRule.getLastProcessInstanceImportTimestamp();

    assertThat(lastImportTimestamp, is(endTime));
  }

  @After
  public void unblockIndex() throws IOException {
    elasticSearchRule.blockProcInstIndex(false);
  }

  @Test
  public void runningActivitiesAreNotSkippedDuringImport() throws IOException {
    // given
    deployAndStartUserTaskProcess();
    deployAndStartSimpleServiceTask();

    // when
    engineRule.finishAllUserTasks();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    for (SearchHit searchHitFields : idsResp.getHits()) {
      List events = (List) searchHitFields.getSourceAsMap().get(EVENTS);
      assertThat(events.size(), is(3));
    }
  }

  @Test
  public void processInstanceStateIsImported() throws IOException {
    // given
    createStartAndCancelUserTaskProcess();

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(
      idsResp.getHits().getAt(0).getSourceAsMap().get(ProcessInstanceType.STATE),
      is(CanceledInstancesOnlyQueryFilter.EXTERNALLY_TERMINATED)
    );
  }

  private ProcessInstanceEngineDto deployAndStartUserTaskProcess() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .userTask()
      .endEvent()
      .done();
    Map<String, Object> variables = new HashMap<>();
    variables.put("aVariable", "aStringVariable");
    return engineRule.deployAndStartProcessWithVariables(processModel, variables);
  }

  private void createStartAndCancelUserTaskProcess() {
    ProcessInstanceEngineDto processInstance = deployAndStartUserTaskProcess();
    engineRule.externallyTerminateProcessInstance(processInstance.getId());
  }

  @Test
  public void runningProcessesIndexedAfterFinish() throws IOException {
    // given
    deployAndStartUserTaskProcess();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();

    //then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    for (SearchHit searchHitFields : idsResp.getHits()) {
      List events = (List) searchHitFields.getSourceAsMap().get(EVENTS);
      assertThat(events.size(), is(2));
      Object date = searchHitFields.getSourceAsMap().get(END_DATE);
      assertThat(date, is(nullValue()));
    }

    // when
    engineRule.finishAllUserTasks();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    for (SearchHit searchHitFields : idsResp.getHits()) {
      Object date = searchHitFields.getSourceAsMap().get(END_DATE);
      assertThat(date, is(notNullValue()));
    }
  }

  @Test
  public void deletionOfProcessInstancesDoesNotDistortProcessInstanceImport() throws IOException {
    // given
    ProcessInstanceEngineDto firstProcInst = createImportAndDeleteTwoProcessInstances();

    // when
    engineRule.startProcessInstance(firstProcInst.getDefinitionId());
    engineRule.startProcessInstance(firstProcInst.getDefinitionId());
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    allEntriesInElasticsearchHaveAllDataWithCount(PROC_INSTANCE_TYPE, 4L);
  }

  @Test
  public void deletionOfProcessInstancesDoesNotDistortActivityInstanceImport() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("aVariable", "aStringVariable");
    ProcessInstanceEngineDto firstProcInst = createImportAndDeleteTwoProcessInstancesWithVariables(variables);

    // when
    variables.put("secondVar", "foo");
    engineRule.startProcessInstance(firstProcInst.getDefinitionId(), variables);
    variables.put("thirdVar", "bar");
    engineRule.startProcessInstance(firstProcInst.getDefinitionId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    List<VariableRetrievalDto> variablesResponseDtos =
      embeddedOptimizeRule
        .getRequestExecutor()
        .buildGetVariablesRequest(firstProcInst.getProcessDefinitionKey(), firstProcInst.getProcessDefinitionVersion())
        .executeAndReturnList(VariableRetrievalDto.class, 200);

    assertThat(variablesResponseDtos.size(), is(3));
  }

  @Test
  public void importRunningAndCompletedHistoricActivityInstances() throws IOException {
    //given
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .userTask()
      .endEvent()
      .done();
    engineRule.deployAndStartProcess(processModel);

    //when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    SearchHit hit = idsResp.getHits().getAt(0);
    List events = (List) hit.getSourceAsMap().get(EVENTS);
    assertThat(events.size(), is(2));
  }

  @Test
  public void completedActivitiesOverwriteRunningActivities() throws IOException {
    //given
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .userTask()
      .endEvent()
      .done();
    engineRule.deployAndStartProcess(processModel);

    //when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    engineRule.finishAllUserTasks();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    SearchHit hit = idsResp.getHits().getAt(0);
    List<Map> events = (List) hit.getSourceAsMap().get(EVENTS);
    boolean allEventsHaveEndDate = events.stream().allMatch(e -> e.get("endDate") != null);
    assertTrue("All end events should have an end date", allEventsHaveEndDate);
  }

  @Test
  public void runningActivitiesDoNotOverwriteCompletedActivities() throws IOException {
    //given
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .name("startEvent")
      .endEvent()
      .done();
    engineRule.deployAndStartProcess(processModel);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();

    //when
    HistoricActivityInstanceEngineDto startEvent =
      engineRule.getHistoricActivityInstances()
        .stream()
        .filter(a -> a.getActivityName().equals("startEvent"))
        .findFirst()
        .get();
    startEvent.setEndTime(null);
    startEvent.setDurationInMillis(null);
    embeddedOptimizeRule.importRunningActivityInstance(Collections.singletonList(startEvent));
    elasticSearchRule.refreshAllOptimizeIndices();

    //then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    SearchHit hit = idsResp.getHits().getAt(0);
    List<Map> events = (List) hit.getSourceAsMap().get(EVENTS);
    boolean allEventsHaveEndDate = events.stream().allMatch(e -> e.get("endDate") != null);
    assertTrue("All end events should have an end date", allEventsHaveEndDate);
  }

  @Test
  public void importIndexIsZeroIfNothingIsImportedYet() {
    // when
    List<Long> indexes = embeddedOptimizeRule.getImportIndexes();

    // then
    for (Long index : indexes) {
      assertThat(index, is(0L));
    }
  }

  @Test
  public void indexLastTimestampIsEqualEvenAfterReset() throws InterruptedException {
    // given
    final int currentTimeBackOff = 1000;
    embeddedOptimizeRule.getConfigurationService().setCurrentTimeBackoffMilliseconds(currentTimeBackOff);
    deployAndStartSimpleServiceTask();
    deployAndStartSimpleServiceTask();

    // sleep in order to avoid the timestamp import backoff window that modifies the latestTimestamp stored
    Thread.sleep(currentTimeBackOff);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    List<Long> firstRoundIndexes = embeddedOptimizeRule.getImportIndexes();

    // then
    embeddedOptimizeRule.resetImportStartIndexes();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    List<Long> secondsRoundIndexes = embeddedOptimizeRule.getImportIndexes();

    // then
    for (int i = 0; i < firstRoundIndexes.size(); i++) {
      assertThat(firstRoundIndexes.get(i), is(secondsRoundIndexes.get(i)));
    }
  }

  @Test
  public void latestImportIndexAfterRestartOfOptimize() throws Exception {
    // given
    deployAndStartUserTaskProcess();
    // we need finished ones
    engineRule.finishAllUserTasks();
    // as well as running
    deployAndStartUserTaskProcess();
    deployAndStartSimpleServiceTask();
    engineRule.deployAndStartDecisionDefinition();

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    embeddedOptimizeRule.storeImportIndexesToElasticsearch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    embeddedOptimizeRule.stopOptimize();
    embeddedOptimizeRule.startOptimize();
    List<Long> indexes = embeddedOptimizeRule.getImportIndexes();

    // then
    for (Long index : indexes) {
      assertThat(index, greaterThan(0L));
    }
  }

  @Test
  public void indexAfterRestartOfOptimizeHasCorrectProcessDefinitionsToImport() throws Exception {
    // given
    deployAndStartSimpleServiceTask();
    deployAndStartSimpleServiceTask();
    deployAndStartSimpleServiceTask();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    embeddedOptimizeRule.storeImportIndexesToElasticsearch();
    elasticSearchRule.refreshAllOptimizeIndices();
    List<Long> firstRoundIndexes = embeddedOptimizeRule.getImportIndexes();

    // when
    embeddedOptimizeRule.stopOptimize();
    embeddedOptimizeRule.startOptimize();
    List<Long> secondsRoundIndexes = embeddedOptimizeRule.getImportIndexes();

    // then
    for (int i = 0; i < firstRoundIndexes.size(); i++) {
      assertThat(firstRoundIndexes.get(i), is(secondsRoundIndexes.get(i)));
    }
  }

  @Test
  public void afterRestartOfOptimizeAlsoNewDataIsImported() throws Exception {
    // given
    deployAndStartSimpleServiceTask();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    List<Long> firstRoundIndexes = embeddedOptimizeRule.getImportIndexes();

    // and
    deployAndStartSimpleServiceTask();

    // when
    embeddedOptimizeRule.stopOptimize();
    embeddedOptimizeRule.startOptimize();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    List<Long> secondsRoundIndexes = embeddedOptimizeRule.getImportIndexes();

    // then
    for (int i = 0; i < firstRoundIndexes.size(); i++) {
      assertThat(firstRoundIndexes.get(i), lessThanOrEqualTo(secondsRoundIndexes.get(i)));
    }
  }

  @Test
  public void afterRestartOfOptimizeOnlyNewActivitiesAreImported() throws Exception {
    // given
    deployAndStartSimpleServiceTask();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    embeddedOptimizeRule.stopOptimize();
    embeddedOptimizeRule.startOptimize();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    assertThat(getImportedActivityCount(), is(3L));
  }

  @Test
  public void itIsPossibleToResetTheImportIndex() throws Exception {
    // given
    deployAndStartSimpleServiceTask();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    embeddedOptimizeRule.resetImportStartIndexes();
    embeddedOptimizeRule.storeImportIndexesToElasticsearch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    embeddedOptimizeRule.stopOptimize();
    embeddedOptimizeRule.startOptimize();
    List<Long> indexes = embeddedOptimizeRule.getImportIndexes();

    // then
    for (Long index : indexes) {
      assertThat(index, is(0L));
    }
  }

  @Test
  public void doNotSkipProcessInstancesWithSameEndTime() throws Exception {
    // given
    int originalMaxPageSize = embeddedOptimizeRule.getConfigurationService()
      .getEngineImportProcessInstanceMaxPageSize();
    embeddedOptimizeRule.getConfigurationService().setEngineImportProcessInstanceMaxPageSize(1);
    startTwoProcessInstancesWithSameEndTime();

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromLastIndex();
    embeddedOptimizeRule.importAllEngineEntitiesFromLastIndex();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    allEntriesInElasticsearchHaveAllDataWithCount(PROC_INSTANCE_TYPE, 2L);
    embeddedOptimizeRule.getConfigurationService().setEngineImportProcessInstanceMaxPageSize(originalMaxPageSize);
  }

  private Long getImportedActivityCount() throws IOException {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(QueryBuilders.matchAllQuery())
      .size(0)
      .fetchSource(false)
      .aggregation(
        nested(EVENTS, EVENTS)
          .subAggregation(
            count(EVENTS + "_count")
              .field(EVENTS + "." + ProcessInstanceType.EVENT_ID)
          )
      );

    SearchRequest searchRequest = new SearchRequest()
      .indices(getOptimizeIndexAliasForType(PROC_INSTANCE_TYPE))
      .types(PROC_INSTANCE_TYPE)
      .source(searchSourceBuilder);

    SearchResponse response = elasticSearchRule.getEsClient().search(searchRequest, RequestOptions.DEFAULT);

    Nested nested = response.getAggregations()
      .get(EVENTS);
    ValueCount countAggregator =
      nested.getAggregations()
        .get(EVENTS + "_count");
    return countAggregator.getValue();
  }

  private void startTwoProcessInstancesWithSameEndTime() throws SQLException {
    OffsetDateTime endTime = OffsetDateTime.now();
    ProcessInstanceEngineDto firstProcInst = deployAndStartSimpleServiceTask();
    ProcessInstanceEngineDto secondProcInst =
      engineRule.startProcessInstance(firstProcInst.getDefinitionId());
    Map<String, OffsetDateTime> procInstEndDateUpdates = new HashMap<>();
    procInstEndDateUpdates.put(firstProcInst.getId(), endTime);
    procInstEndDateUpdates.put(secondProcInst.getId(), endTime);
    engineDatabaseRule.updateProcessInstanceEndDates(procInstEndDateUpdates);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTask() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("aVariable", "aStringVariables");
    return deployAndStartSimpleServiceTaskWithVariables(variables);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .serviceTask()
      .camundaExpression("${true}")
      .endEvent()
      .done();
    return engineRule.deployAndStartProcessWithVariables(processModel, variables);
  }

  private void allEntriesInElasticsearchHaveAllData(String elasticsearchType) throws IOException {
    allEntriesInElasticsearchHaveAllDataWithCount(elasticsearchType, 1L);
  }

  private void allEntriesInElasticsearchHaveAllDataWithCount(String elasticsearchType, long count) throws IOException {
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(elasticsearchType);

    assertThat(idsResp.getHits().getTotalHits(), is(count));
    for (SearchHit searchHit : idsResp.getHits().getHits()) {
      for (Entry searchHitField : searchHit.getSourceAsMap().entrySet()) {
        String errorMessage = "Something went wrong during fetching of field: " + searchHitField.getKey() +
          ". Should actually have a value!";
        assertThat(errorMessage, searchHitField.getValue(), is(notNullValue()));
        if (searchHitField.getValue() instanceof String) {
          String value = (String) searchHitField.getValue();
          assertThat(errorMessage, value.isEmpty(), is(false));
        }
      }
    }
  }

  private SearchResponse getSearchResponseForAllDocumentsOfType(String elasticsearchType) throws IOException {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(matchAllQuery())
      .size(100);

    SearchRequest searchRequest = new SearchRequest()
      .indices(getOptimizeIndexAliasForType(elasticsearchType))
      .types(elasticsearchType)
      .source(searchSourceBuilder);

    return elasticSearchRule.getEsClient().search(searchRequest, RequestOptions.DEFAULT);
  }

  private ProcessInstanceEngineDto createImportAndDeleteTwoProcessInstances() {
    return createImportAndDeleteTwoProcessInstancesWithVariables(new HashMap<>());
  }

  private ProcessInstanceEngineDto createImportAndDeleteTwoProcessInstancesWithVariables(Map<String, Object> variables) {
    ProcessInstanceEngineDto firstProcInst = deployAndStartSimpleServiceTaskWithVariables(variables);
    ProcessInstanceEngineDto secondProcInst = engineRule.startProcessInstance(
      firstProcInst.getDefinitionId(),
      variables
    );
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
    engineRule.deleteHistoricProcessInstance(firstProcInst.getId());
    engineRule.deleteHistoricProcessInstance(secondProcInst.getId());
    return firstProcInst;
  }

}
