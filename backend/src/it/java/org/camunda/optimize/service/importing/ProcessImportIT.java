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
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.filter.CanceledInstancesOnlyQueryFilter;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.metrics.valuecount.ValueCount;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.EVENTS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.elasticsearch.search.aggregations.AggregationBuilders.count;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

public class ProcessImportIT extends AbstractImportIT {

  private static final Set<String> PROCESS_INSTANCE_NULLABLE_FIELDS =
    Collections.singleton(ProcessInstanceIndex.TENANT_ID);
  private static final Set<String> PROCESS_DEFINITION_NULLABLE_FIELDS =
    Collections.singleton(ProcessDefinitionIndex.TENANT_ID);

  @Test
  public void importCanBeDisabled() throws IOException {
    // given
    embeddedOptimizeExtensionRule.getConfigurationService().getConfiguredEngines().values()
      .forEach(engineConfiguration -> engineConfiguration.setImportEnabled(false));
    embeddedOptimizeExtensionRule.reloadConfiguration();

    deployAndStartSimpleServiceTask();
    engineIntegrationExtensionRule.deployAndStartDecisionDefinition();
    BpmnModelInstance exampleProcess = Bpmn.createExecutableProcess().name("foo").startEvent().endEvent().done();
    engineIntegrationExtensionRule.deployAndStartProcess(exampleProcess);

    // when
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // then
    allEntriesInElasticsearchHaveAllDataWithCount(PROCESS_INSTANCE_INDEX_NAME, 0L);
    allEntriesInElasticsearchHaveAllDataWithCount(PROCESS_DEFINITION_INDEX_NAME, 0L);
    allEntriesInElasticsearchHaveAllDataWithCount(DECISION_DEFINITION_INDEX_NAME, 0L);
    allEntriesInElasticsearchHaveAllDataWithCount(DECISION_INSTANCE_INDEX_NAME, 0L);
    assertThat(embeddedOptimizeExtensionRule.getImportSchedulerFactory().getImportSchedulers().size(), is(greaterThan(0)));
    embeddedOptimizeExtensionRule.getImportSchedulerFactory().getImportSchedulers()
      .forEach(engineImportScheduler -> assertThat(engineImportScheduler.isEnabled(), is(false)));
  }

  @Test
  public void allProcessDefinitionFieldDataIsAvailable() throws IOException {
    //given
    deployAndStartSimpleServiceTask();

    //when
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    //then
    allEntriesInElasticsearchHaveAllData(PROCESS_DEFINITION_INDEX_NAME, PROCESS_DEFINITION_NULLABLE_FIELDS);
  }

  @Test
  public void processDefinitionTenantIdIsImportedIfPresent() throws IOException {
    //given
    final String tenantId = "reallyAwesomeTenantId";
    deployProcessDefinitionWithTenant(tenantId);

    //when
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = getSearchResponseForAllDocumentsOfIndex(PROCESS_DEFINITION_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap().get(ProcessDefinitionIndex.TENANT_ID), is(tenantId));
  }

  @Test
  public void processDefinitionDefaultEngineTenantIdIsApplied() throws IOException {
    //given
    final String tenantId = "reallyAwesomeTenantId";
    embeddedOptimizeExtensionRule.getDefaultEngineConfiguration().getDefaultTenant().setId(tenantId);
    deployAndStartSimpleServiceTask();

    //when
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = getSearchResponseForAllDocumentsOfIndex(PROCESS_DEFINITION_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap().get(ProcessDefinitionIndex.TENANT_ID), is(tenantId));
  }

  @Test
  public void processDefinitionEngineTenantIdIsPreferredOverDefaultTenantId() throws IOException {
    //given
    final String defaultTenantId = "reallyAwesomeTenantId";
    final String expectedTenantId = "evenMoreAwesomeTenantId";
    embeddedOptimizeExtensionRule.getDefaultEngineConfiguration().getDefaultTenant().setId(defaultTenantId);
    deployProcessDefinitionWithTenant(expectedTenantId);

    //when
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = getSearchResponseForAllDocumentsOfIndex(PROCESS_DEFINITION_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap().get(ProcessDefinitionIndex.TENANT_ID), is(expectedTenantId));
  }

  @Test
  public void allProcessInstanceDataIsAvailable() throws IOException {
    //given
    deployAndStartSimpleServiceTask();

    //when
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    //then
    allEntriesInElasticsearchHaveAllData(PROCESS_INSTANCE_INDEX_NAME, PROCESS_INSTANCE_NULLABLE_FIELDS);
  }

  @Test
  public void processInstanceTenantIdIsImportedIfPresent() throws IOException {
    //given
    final String tenantId = "myTenant";
    deployAndStartSimpleServiceTaskWithTenant(tenantId);

    //when
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap().get(ProcessInstanceIndex.TENANT_ID), is(tenantId));
  }

  @Test
  public void processInstanceDefaultEngineTenantIdIsApplied() throws IOException {
    //given
    final String tenantId = "reallyAwesomeTenantId";
    embeddedOptimizeExtensionRule.getDefaultEngineConfiguration().getDefaultTenant().setId(tenantId);
    deployAndStartSimpleServiceTask();

    //when
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap().get(ProcessInstanceIndex.TENANT_ID), is(tenantId));
  }

  @Test
  public void processInstanceEngineTenantIdIsPreferredOverDefaultTenantId() throws IOException {
    //given
    final String defaultTenantId = "reallyAwesomeTenantId";
    final String expectedTenantId = "evenMoreAwesomeTenantId";
    embeddedOptimizeExtensionRule.getDefaultEngineConfiguration().getDefaultTenant().setId(defaultTenantId);
    deployAndStartSimpleServiceTaskWithTenant(expectedTenantId);

    //when
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap().get(ProcessInstanceIndex.TENANT_ID), is(expectedTenantId));
  }

  @Test
  public void failingJobDoesNotUpdateImportIndex() throws IOException {
    //given
    ProcessInstanceEngineDto dto1 = deployAndStartSimpleServiceTask();
    OffsetDateTime endTime = engineIntegrationExtensionRule.getHistoricProcessInstance(dto1.getId()).getEndTime();

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromLastIndex();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    elasticSearchIntegrationTestExtensionRule.blockProcInstIndex(true);

    ProcessInstanceEngineDto dto2 = deployAndStartSimpleServiceTask();

    Thread thread = new Thread(() -> embeddedOptimizeExtensionRule.importAllEngineEntitiesFromLastIndex());
    thread.start();

    OffsetDateTime lastImportTimestamp = elasticSearchIntegrationTestExtensionRule.getLastProcessInstanceImportTimestamp();
    assertThat(lastImportTimestamp, is(endTime));

    elasticSearchIntegrationTestExtensionRule.blockProcInstIndex(false);
    endTime = engineIntegrationExtensionRule.getHistoricProcessInstance(dto2.getId()).getEndTime();

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromLastIndex();
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromLastIndex();

    lastImportTimestamp = elasticSearchIntegrationTestExtensionRule.getLastProcessInstanceImportTimestamp();

    assertThat(lastImportTimestamp, is(endTime));
  }

  @AfterEach
  public void unblockIndex() throws IOException {
    elasticSearchIntegrationTestExtensionRule.blockProcInstIndex(false);
  }

  @Test
  public void xmlFetchingIsNotRetriedOn4xx() throws IOException {
    final ProcessDefinitionOptimizeDto procDef = new ProcessDefinitionOptimizeDto()
      .setId("123")
      .setKey("lol")
      .setVersion("1")
      .setEngine("1");

    elasticSearchIntegrationTestExtensionRule.addEntryToElasticsearch(PROCESS_DEFINITION_INDEX_NAME, procDef.getId(), procDef);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    ProcessInstanceEngineDto serviceTask = deployAndStartSimpleServiceTask();
    String definitionId = serviceTask.getDefinitionId();
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromLastIndex();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    SearchResponse response = getSearchResponseForAllDocumentsOfIndex(PROCESS_DEFINITION_INDEX_NAME);
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
    engineIntegrationExtensionRule.finishAllRunningUserTasks();
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
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
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(
      idsResp.getHits().getAt(0).getSourceAsMap().get(ProcessInstanceIndex.STATE),
      is(CanceledInstancesOnlyQueryFilter.EXTERNALLY_TERMINATED)
    );
  }

  @Test
  public void runningProcessesIndexedAfterFinish() throws IOException {
    // given
    deployAndStartUserTaskProcess();
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();

    //then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    for (SearchHit searchHitFields : idsResp.getHits()) {
      List events = (List) searchHitFields.getSourceAsMap().get(EVENTS);
      assertThat(events.size(), is(2));
      Object date = searchHitFields.getSourceAsMap().get(END_DATE);
      assertThat(date, is(nullValue()));
    }

    // when
    engineIntegrationExtensionRule.finishAllRunningUserTasks();
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // then
    idsResp = getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
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
    engineIntegrationExtensionRule.startProcessInstance(firstProcInst.getDefinitionId());
    engineIntegrationExtensionRule.startProcessInstance(firstProcInst.getDefinitionId());
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // then
    allEntriesInElasticsearchHaveAllDataWithCount(PROCESS_INSTANCE_INDEX_NAME, 4L, PROCESS_INSTANCE_NULLABLE_FIELDS);
  }

  @Test
  public void deletionOfProcessInstancesDoesNotDistortActivityInstanceImport() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("aVariable", "aStringVariable");
    ProcessInstanceEngineDto firstProcInst = createImportAndDeleteTwoProcessInstancesWithVariables(variables);

    // when
    variables.put("secondVar", "foo");
    engineIntegrationExtensionRule.startProcessInstance(firstProcInst.getDefinitionId(), variables);
    variables.put("thirdVar", "bar");
    engineIntegrationExtensionRule.startProcessInstance(firstProcInst.getDefinitionId(), variables);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // then
    ProcessVariableNameRequestDto variableRequestDto = new ProcessVariableNameRequestDto();
    variableRequestDto.setProcessDefinitionKey(firstProcInst.getProcessDefinitionKey());
    variableRequestDto.setProcessDefinitionVersion(firstProcInst.getProcessDefinitionVersion());
    List<ProcessVariableNameResponseDto> variablesResponseDtos =
      embeddedOptimizeExtensionRule
        .getRequestExecutor()
        .buildProcessVariableNamesRequest(variableRequestDto)
        .executeAndReturnList(ProcessVariableNameResponseDto.class, 200);

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
    engineIntegrationExtensionRule.deployAndStartProcess(processModel);

    //when
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    //then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
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
    engineIntegrationExtensionRule.deployAndStartProcess(processModel);

    //when
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    engineIntegrationExtensionRule.finishAllRunningUserTasks();
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    //then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    SearchHit hit = idsResp.getHits().getAt(0);
    List<Map> events = (List) hit.getSourceAsMap().get(EVENTS);
    boolean allEventsHaveEndDate = events.stream().allMatch(e -> e.get("endDate") != null);
    assertThat(allEventsHaveEndDate, is(true));
  }

  @Test
  public void runningActivitiesDoNotOverwriteCompletedActivities() throws IOException {
    //given
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
        .name("startEvent")
      .endEvent()
        .name("endEvent")
      .done();
    // @formatter:on
    engineIntegrationExtensionRule.deployAndStartProcess(processModel);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();

    //when
    HistoricActivityInstanceEngineDto startEvent =
      engineIntegrationExtensionRule.getHistoricActivityInstances()
        .stream()
        .filter(a -> "startEvent".equals(a.getActivityName()))
        .findFirst()
        .get();
    startEvent.setEndTime(null);
    startEvent.setDurationInMillis(null);
    embeddedOptimizeExtensionRule.importRunningActivityInstance(Collections.singletonList(startEvent));
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    //then
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
    SearchHit hit = idsResp.getHits().getAt(0);
    List<Map> events = (List) hit.getSourceAsMap().get(EVENTS);
    boolean allEventsHaveEndDate = events.stream().allMatch(e -> e.get("endDate") != null);
    assertThat(allEventsHaveEndDate, is(true));
  }

  @Test
  public void afterRestartOfOptimizeOnlyNewActivitiesAreImported() throws Exception {
    // given
    deployAndStartSimpleServiceTask();
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    embeddedOptimizeExtensionRule.stopOptimize();
    embeddedOptimizeExtensionRule.startOptimize();
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // then
    assertThat(getImportedActivityCount(), is(3L));
  }

  @Test
  public void doNotSkipProcessInstancesWithSameEndTime() throws Exception {
    // given
    int originalMaxPageSize = embeddedOptimizeExtensionRule.getConfigurationService()
      .getEngineImportProcessInstanceMaxPageSize();
    embeddedOptimizeExtensionRule.getConfigurationService().setEngineImportProcessInstanceMaxPageSize(1);
    startTwoProcessInstancesWithSameEndTime();

    // when
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromLastIndex();
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromLastIndex();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // then
    allEntriesInElasticsearchHaveAllDataWithCount(PROCESS_INSTANCE_INDEX_NAME, 2L, PROCESS_INSTANCE_NULLABLE_FIELDS);
    embeddedOptimizeExtensionRule.getConfigurationService().setEngineImportProcessInstanceMaxPageSize(originalMaxPageSize);
  }

  private void createStartAndCancelUserTaskProcess() {
    ProcessInstanceEngineDto processInstance = deployAndStartUserTaskProcess();
    engineIntegrationExtensionRule.externallyTerminateProcessInstance(processInstance.getId());
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
              .field(EVENTS + "." + ProcessInstanceIndex.EVENT_ID)
          )
      );

    SearchRequest searchRequest = new SearchRequest()
      .indices(PROCESS_INSTANCE_INDEX_NAME)
      .types(PROCESS_INSTANCE_INDEX_NAME)
      .source(searchSourceBuilder);

    SearchResponse response = elasticSearchIntegrationTestExtensionRule.getOptimizeElasticClient().search(searchRequest, RequestOptions.DEFAULT);

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
      engineIntegrationExtensionRule.startProcessInstance(firstProcInst.getDefinitionId());
    Map<String, OffsetDateTime> procInstEndDateUpdates = new HashMap<>();
    procInstEndDateUpdates.put(firstProcInst.getId(), endTime);
    procInstEndDateUpdates.put(secondProcInst.getId(), endTime);
    engineDatabaseExtensionRule.updateProcessInstanceEndDates(procInstEndDateUpdates);
  }

  private ProcessDefinitionEngineDto deployProcessDefinitionWithTenant(String tenantId) {
    BpmnModelInstance processModel = createSimpleProcessDefinition();
    return engineIntegrationExtensionRule.deployProcessAndGetProcessDefinition(processModel, tenantId);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskWithTenant(String tenantId) {
    final ProcessDefinitionEngineDto processDefinitionEngineDto = deployProcessDefinitionWithTenant(tenantId);
    return engineIntegrationExtensionRule.startProcessInstance(processDefinitionEngineDto.getId());
  }

  private ProcessInstanceEngineDto createImportAndDeleteTwoProcessInstances() {
    return createImportAndDeleteTwoProcessInstancesWithVariables(new HashMap<>());
  }

  private ProcessInstanceEngineDto createImportAndDeleteTwoProcessInstancesWithVariables(Map<String, Object> variables) {
    ProcessInstanceEngineDto firstProcInst = deployAndStartSimpleServiceTaskWithVariables(variables);
    ProcessInstanceEngineDto secondProcInst = engineIntegrationExtensionRule.startProcessInstance(
      firstProcInst.getDefinitionId(),
      variables
    );
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();
    engineIntegrationExtensionRule.deleteHistoricProcessInstance(firstProcInst.getId());
    engineIntegrationExtensionRule.deleteHistoricProcessInstance(secondProcInst.getId());
    return firstProcInst;
  }

}
