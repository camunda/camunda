/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableRetrievalDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.filter.CanceledInstancesOnlyQueryFilter;
import org.camunda.optimize.service.es.schema.type.ProcessDefinitionType;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.metrics.valuecount.ValueCount;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.END_DATE;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.EVENTS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_DEF_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;
import static org.elasticsearch.search.aggregations.AggregationBuilders.count;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertTrue;

public class ProcessImportIT extends AbstractImportIT {

  protected static final Set<String> PROCESS_INSTANCE_NULLABLE_FIELDS =
    Collections.singleton(ProcessInstanceType.TENANT_ID);
  protected static final Set<String> PROCESS_DEFINITION_NULLABLE_FIELDS =
    Collections.singleton(ProcessDefinitionType.TENANT_ID);

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
  public void allProcessDefinitionFieldDataIsAvailable() throws IOException {
    //given
    deployAndStartSimpleServiceTask();

    //when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //then
    allEntriesInElasticsearchHaveAllData(PROC_DEF_TYPE, PROCESS_DEFINITION_NULLABLE_FIELDS);
  }

  @Test
  public void processDefinitionTenantIdIsImportedIfPresent() throws IOException {
    //given
    final String tenantId = "reallyAwesomeTenantId";
    deployProcessDefinitionWithTenant(tenantId);

    //when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_DEF_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap().get(ProcessDefinitionType.TENANT_ID), is(tenantId));
  }

  @Test
  public void processDefinitionDefaultEngineTenantIdIsApplied() throws IOException {
    //given
    final String tenantId = "reallyAwesomeTenantId";
    embeddedOptimizeRule.getDefaultEngineConfiguration().getDefaultTenant().setId(tenantId);
    deployAndStartSimpleServiceTask();

    //when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_DEF_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap().get(ProcessDefinitionType.TENANT_ID), is(tenantId));
  }

  @Test
  public void processDefinitionEngineTenantIdIsPreferredOverDefaultTenantId() throws IOException {
    //given
    final String defaultTenantId = "reallyAwesomeTenantId";
    final String expectedTenantId = "evenMoreAwesomeTenantId";
    embeddedOptimizeRule.getDefaultEngineConfiguration().getDefaultTenant().setId(defaultTenantId);
    deployProcessDefinitionWithTenant(expectedTenantId);

    //when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_DEF_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap().get(ProcessDefinitionType.TENANT_ID), is(expectedTenantId));
  }

  @Test
  public void allProcessInstanceDataIsAvailable() throws IOException {
    //given
    deployAndStartSimpleServiceTask();

    //when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //then
    allEntriesInElasticsearchHaveAllData(PROC_INSTANCE_TYPE, PROCESS_INSTANCE_NULLABLE_FIELDS);
  }

  @Test
  public void processInstanceTenantIdIsImportedIfPresent() throws IOException {
    //given
    final String tenantId = "myTenant";
    deployAndStartSimpleServiceTaskWithTenant(tenantId);

    //when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap().get(ProcessInstanceType.TENANT_ID), is(tenantId));
  }

  @Test
  public void processInstanceDefaultEngineTenantIdIsApplied() throws IOException {
    //given
    final String tenantId = "reallyAwesomeTenantId";
    embeddedOptimizeRule.getDefaultEngineConfiguration().getDefaultTenant().setId(tenantId);
    deployAndStartSimpleServiceTask();

    //when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap().get(ProcessInstanceType.TENANT_ID), is(tenantId));
  }

  @Test
  public void processInstanceEngineTenantIdIsPreferredOverDefaultTenantId() throws IOException {
    //given
    final String defaultTenantId = "reallyAwesomeTenantId";
    final String expectedTenantId = "evenMoreAwesomeTenantId";
    embeddedOptimizeRule.getDefaultEngineConfiguration().getDefaultTenant().setId(defaultTenantId);
    deployAndStartSimpleServiceTaskWithTenant(expectedTenantId);

    //when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(PROC_INSTANCE_TYPE);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap().get(ProcessInstanceType.TENANT_ID), is(expectedTenantId));
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
  public void xmlFetchingIsNotRetriedOn4xx() throws IOException {
    final ProcessDefinitionOptimizeDto procDef = new ProcessDefinitionOptimizeDto()
      .setId("123")
      .setKey("lol")
      .setVersion("1")
      .setEngine("1");

    elasticSearchRule.addEntryToElasticsearch(PROC_DEF_TYPE, procDef.getId(), procDef);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    ProcessInstanceEngineDto serviceTask = deployAndStartSimpleServiceTask();
    String definitionId = serviceTask.getDefinitionId();
    embeddedOptimizeRule.importAllEngineEntitiesFromLastIndex();
    elasticSearchRule.refreshAllOptimizeIndices();

    SearchResponse response = getSearchResponseForAllDocumentsOfType(PROC_DEF_TYPE);
    assertThat(response.getHits().getTotalHits(), is(2L));
    response.getHits().forEach((SearchHit hit) -> {
      Map<String, Object> source = hit.getSourceAsMap();
      if (source.get("id").equals(definitionId)) {
        assertThat(source.get("bpmn20Xml"), is(not(nullValue())));
      }
    });
  }

  @Test
  public void runningActivitiesAreNotSkippedDuringImport() throws IOException {
    // given
    deployAndStartUserTaskProcess();
    deployAndStartSimpleServiceTask();

    // when
    engineRule.finishAllRunningUserTasks();
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
    engineRule.finishAllRunningUserTasks();
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
    allEntriesInElasticsearchHaveAllDataWithCount(PROC_INSTANCE_TYPE, 4L, PROCESS_INSTANCE_NULLABLE_FIELDS);
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
    engineRule.finishAllRunningUserTasks();
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
    allEntriesInElasticsearchHaveAllDataWithCount(PROC_INSTANCE_TYPE, 2L, PROCESS_INSTANCE_NULLABLE_FIELDS);
    embeddedOptimizeRule.getConfigurationService().setEngineImportProcessInstanceMaxPageSize(originalMaxPageSize);
  }

  private void createStartAndCancelUserTaskProcess() {
    ProcessInstanceEngineDto processInstance = deployAndStartUserTaskProcess();
    engineRule.externallyTerminateProcessInstance(processInstance.getId());
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
      .indices(PROC_INSTANCE_TYPE)
      .types(PROC_INSTANCE_TYPE)
      .source(searchSourceBuilder);

    SearchResponse response = elasticSearchRule.getOptimizeElasticClient().search(searchRequest, RequestOptions.DEFAULT);

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

  private ProcessDefinitionEngineDto deployProcessDefinitionWithTenant(String tenantId) {
    BpmnModelInstance processModel = createSimpleProcessDefinition();
    return engineRule.deployProcessAndGetProcessDefinition(processModel, tenantId);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskWithTenant(String tenantId) {
    final ProcessDefinitionEngineDto processDefinitionEngineDto = deployProcessDefinitionWithTenant(tenantId);
    return engineRule.startProcessInstance(processDefinitionEngineDto.getId());
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
