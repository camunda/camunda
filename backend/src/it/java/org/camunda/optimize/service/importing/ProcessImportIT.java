/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.test.it.extension.ErrorResponseMock;
import org.camunda.optimize.util.BpmnModels;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.metrics.ValueCount;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.VerificationTimes;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static javax.ws.rs.HttpMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.EXTERNALLY_TERMINATED_STATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.EVENTS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.camunda.optimize.util.BpmnModels.START_EVENT;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;
import static org.camunda.optimize.util.BpmnModels.getSingleServiceTaskProcess;
import static org.elasticsearch.search.aggregations.AggregationBuilders.count;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.mockserver.model.HttpRequest.request;

public class ProcessImportIT extends AbstractImportIT {

  private static final Set<String> PROCESS_INSTANCE_NULLABLE_FIELDS =
    Collections.singleton(ProcessInstanceIndex.TENANT_ID);
  private static final Set<String> PROCESS_DEFINITION_NULLABLE_FIELDS =
    Collections.singleton(ProcessDefinitionIndex.TENANT_ID);

  @Test
  public void importCanBeDisabled() {
    // given
    embeddedOptimizeExtension.getConfigurationService().getConfiguredEngines().values()
      .forEach(engineConfiguration -> engineConfiguration.setImportEnabled(false));
    embeddedOptimizeExtension.reloadConfiguration();

    // when
    deployAndStartSimpleServiceTask();
    engineIntegrationExtension.deployAndStartDecisionDefinition();
    BpmnModelInstance exampleProcess = getSimpleBpmnDiagram();
    engineIntegrationExtension.deployAndStartProcess(exampleProcess);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(embeddedOptimizeExtension.getImportSchedulerFactory().getImportSchedulers()).hasSizeGreaterThan(0);
    embeddedOptimizeExtension.getImportSchedulerFactory().getImportSchedulers()
      .forEach(engineImportScheduler -> assertThat(engineImportScheduler.isScheduledToRun()).isFalse());
    allEntriesInElasticsearchHaveAllDataWithCount(PROCESS_INSTANCE_INDEX_NAME, 0L);
    allEntriesInElasticsearchHaveAllDataWithCount(PROCESS_DEFINITION_INDEX_NAME, 0L);
    allEntriesInElasticsearchHaveAllDataWithCount(DECISION_DEFINITION_INDEX_NAME, 0L);
    allEntriesInElasticsearchHaveAllDataWithCount(DECISION_INSTANCE_INDEX_NAME, 0L);
  }

  @Test
  public void allProcessDefinitionFieldDataIsAvailable() {
    //given
    deployAndStartSimpleServiceTask();

    //when
    importAllEngineEntitiesFromScratch();

    //then
    allEntriesInElasticsearchHaveAllData(PROCESS_DEFINITION_INDEX_NAME, PROCESS_DEFINITION_NULLABLE_FIELDS);
  }

  @Test
  public void processDefinitionTenantIdIsImportedIfPresent() {
    //given
    final String tenantId = "reallyAwesomeTenantId";
    deployProcessDefinitionWithTenant(tenantId);

    //when
    importAllEngineEntitiesFromScratch();

    //then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_DEFINITION_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap()).containsEntry(ProcessDefinitionIndex.TENANT_ID, tenantId);
  }

  @Test
  public void processDefinitionDefaultEngineTenantIdIsApplied() {
    //given
    final String tenantId = "reallyAwesomeTenantId";
    embeddedOptimizeExtension.getDefaultEngineConfiguration().getDefaultTenant().setId(tenantId);
    deployAndStartSimpleServiceTask();

    //when
    importAllEngineEntitiesFromScratch();

    //then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_DEFINITION_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap()).containsEntry(ProcessDefinitionIndex.TENANT_ID, tenantId);
  }

  @Test
  public void processDefinitionEngineTenantIdIsPreferredOverDefaultTenantId() {
    //given
    final String defaultTenantId = "reallyAwesomeTenantId";
    final String expectedTenantId = "evenMoreAwesomeTenantId";
    embeddedOptimizeExtension.getDefaultEngineConfiguration().getDefaultTenant().setId(defaultTenantId);
    deployProcessDefinitionWithTenant(expectedTenantId);

    //when
    importAllEngineEntitiesFromScratch();

    //then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_DEFINITION_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap()).containsEntry(ProcessDefinitionIndex.TENANT_ID, expectedTenantId);
  }

  @Test
  public void allProcessInstanceDataIsAvailable() {
    //given
    deployAndStartSimpleServiceTask();

    //when
    importAllEngineEntitiesFromScratch();

    //then
    allEntriesInElasticsearchHaveAllData(PROCESS_INSTANCE_INDEX_NAME, PROCESS_INSTANCE_NULLABLE_FIELDS);
  }

  @Test
  public void importsAllDefinitionsEvenIfTotalAmountIsAboveMaxPageSize() {
    //given
    embeddedOptimizeExtension.getConfigurationService().setEngineImportProcessDefinitionMaxPageSize(1);
    deploySimpleProcess();
    deploySimpleProcess();
    deploySimpleProcess();

    // when
    importAllEngineEntitiesFromScratch();
    importAllEngineEntitiesFromLastIndex();

    // then
    assertThat(getProcessDefinitionCount()).isEqualTo(2L);

    // when
    importAllEngineEntitiesFromLastIndex();

    // then
    assertThat(getProcessDefinitionCount()).isEqualTo(3L);
  }

  @Test
  public void processInstanceTenantIdIsImportedIfPresent() {
    //given
    final String tenantId = "myTenant";
    deployAndStartSimpleServiceTaskWithTenant(tenantId);

    //when
    importAllEngineEntitiesFromScratch();

    //then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap()).containsEntry(ProcessInstanceIndex.TENANT_ID, tenantId);
  }

  @Test
  public void processInstanceDefaultEngineTenantIdIsApplied() {
    //given
    final String tenantId = "reallyAwesomeTenantId";
    embeddedOptimizeExtension.getDefaultEngineConfiguration().getDefaultTenant().setId(tenantId);
    deployAndStartSimpleServiceTask();

    //when
    importAllEngineEntitiesFromScratch();

    //then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap()).containsEntry(ProcessInstanceIndex.TENANT_ID, tenantId);
  }

  @Test
  public void processInstanceEngineTenantIdIsPreferredOverDefaultTenantId() {
    //given
    final String defaultTenantId = "reallyAwesomeTenantId";
    final String expectedTenantId = "evenMoreAwesomeTenantId";
    embeddedOptimizeExtension.getDefaultEngineConfiguration().getDefaultTenant().setId(defaultTenantId);
    deployAndStartSimpleServiceTaskWithTenant(expectedTenantId);

    //when
    importAllEngineEntitiesFromScratch();

    //then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap()).containsEntry(ProcessInstanceIndex.TENANT_ID, expectedTenantId);
  }

  @Test
  public void failingJobDoesNotUpdateImportIndex() throws IOException {
    //given
    ProcessInstanceEngineDto dto1 = deployAndStartSimpleServiceTask();
    OffsetDateTime endTime = engineIntegrationExtension.getHistoricProcessInstance(dto1.getId()).getEndTime();

    importAllEngineEntitiesFromScratch();
    importAllEngineEntitiesFromLastIndex();

    elasticSearchIntegrationTestExtension.blockProcInstIndex(true);

    ProcessInstanceEngineDto dto2 = deployAndStartSimpleServiceTask();

    Thread thread = new Thread(this::importAllEngineEntitiesFromLastIndex);
    thread.start();

    OffsetDateTime lastImportTimestamp = elasticSearchIntegrationTestExtension.getLastProcessInstanceImportTimestamp();
    assertThat(lastImportTimestamp).isEqualTo(endTime);

    elasticSearchIntegrationTestExtension.blockProcInstIndex(false);
    endTime = engineIntegrationExtension.getHistoricProcessInstance(dto2.getId()).getEndTime();

    importAllEngineEntitiesFromLastIndex();
    importAllEngineEntitiesFromLastIndex();

    lastImportTimestamp = elasticSearchIntegrationTestExtension.getLastProcessInstanceImportTimestamp();

    assertThat(lastImportTimestamp).isEqualTo(endTime);
  }

  @AfterEach
  public void unblockIndex() throws IOException {
    elasticSearchIntegrationTestExtension.blockProcInstIndex(false);
  }

  @Test
  public void xmlFetchingIsNotRetriedOn4xx() {
    final ProcessDefinitionOptimizeDto procDef = ProcessDefinitionOptimizeDto.builder()
      .id("123")
      .key("lol")
      .version("1")
      .engine("1")
      .build();

    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      PROCESS_DEFINITION_INDEX_NAME,
      procDef.getId(),
      procDef
    );
    importAllEngineEntitiesFromScratch();

    ProcessInstanceEngineDto serviceTask = deployAndStartSimpleServiceTask();
    String definitionId = serviceTask.getDefinitionId();
    importAllEngineEntitiesFromLastIndex();

    SearchResponse response = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_DEFINITION_INDEX_NAME);
    assertThat(response.getHits().getTotalHits().value).isEqualTo(2L);
    response.getHits().forEach((SearchHit hit) -> {
      Map<String, Object> source = hit.getSourceAsMap();
      if (source.get("id").equals(definitionId)) {
        assertThat(source.get("bpmn20Xml")).isNotNull();
      }
    });
  }

  @Test
  public void runningActivitiesAreNotSkippedDuringImport() {
    // given
    deployAndStartUserTaskProcess();
    deployAndStartSimpleServiceTask();

    // when
    engineIntegrationExtension.finishAllRunningUserTasks();
    importAllEngineEntitiesFromScratch();

    // then
    SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    for (SearchHit searchHitFields : idsResp.getHits()) {
      List<?> events = (List<?>) searchHitFields.getSourceAsMap().get(EVENTS);
      assertThat(events).hasSize(3);
    }
  }

  @Test
  public void processInstanceStateIsImported() {
    // given
    createStartAndCancelUserTaskProcess();

    // when
    importAllEngineEntitiesFromScratch();

    // then
    SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getAt(0).getSourceAsMap())
      .containsEntry(ProcessInstanceIndex.STATE, EXTERNALLY_TERMINATED_STATE);
  }

  @Test
  public void runningProcessesIndexedAfterFinish() {
    // given
    deployAndStartUserTaskProcess();
    importAllEngineEntitiesFromScratch();

    //then
    SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    for (SearchHit searchHitFields : idsResp.getHits()) {
      List<?> events = (List<?>) searchHitFields.getSourceAsMap().get(EVENTS);
      assertThat(events).hasSize(2);
      Object date = searchHitFields.getSourceAsMap().get(END_DATE);
      assertThat(date).isNull();
    }

    // when
    engineIntegrationExtension.finishAllRunningUserTasks();
    importAllEngineEntitiesFromScratch();

    // then
    idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    for (SearchHit searchHitFields : idsResp.getHits()) {
      Object date = searchHitFields.getSourceAsMap().get(END_DATE);
      assertThat(date).isNotNull();
    }
  }

  @Test
  public void deletionOfProcessInstancesDoesNotDistortProcessInstanceImport() {
    // given
    ProcessInstanceEngineDto firstProcInst = createImportAndDeleteTwoProcessInstances();

    // when
    engineIntegrationExtension.startProcessInstance(firstProcInst.getDefinitionId());
    engineIntegrationExtension.startProcessInstance(firstProcInst.getDefinitionId());
    importAllEngineEntitiesFromScratch();

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
    engineIntegrationExtension.startProcessInstance(firstProcInst.getDefinitionId(), variables);
    variables.put("thirdVar", "bar");
    engineIntegrationExtension.startProcessInstance(firstProcInst.getDefinitionId(), variables);
    importAllEngineEntitiesFromScratch();

    // then
    ProcessVariableNameRequestDto variableRequestDto = new ProcessVariableNameRequestDto();
    variableRequestDto.setProcessDefinitionKey(firstProcInst.getProcessDefinitionKey());
    variableRequestDto.setProcessDefinitionVersion(firstProcInst.getProcessDefinitionVersion());
    List<ProcessVariableNameResponseDto> variablesResponseDtos = variablesClient.getProcessVariableNames(
      variableRequestDto);

    assertThat(variablesResponseDtos).hasSize(3);
  }

  @Test
  public void importRunningAndCompletedHistoricActivityInstances() {
    //given
    engineIntegrationExtension.deployAndStartProcess(BpmnModels.getSingleUserTaskDiagram());

    //when
    importAllEngineEntitiesFromScratch();

    //then
    SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    SearchHit hit = idsResp.getHits().getAt(0);
    List<?> events = (List<?>) hit.getSourceAsMap().get(EVENTS);
    assertThat(events).hasSize(2);
  }

  @Test
  public void completedActivitiesOverwriteRunningActivities() {
    //given
    engineIntegrationExtension.deployAndStartProcess(BpmnModels.getSingleUserTaskDiagram());

    //when
    importAllEngineEntitiesFromScratch();
    engineIntegrationExtension.finishAllRunningUserTasks();
    importAllEngineEntitiesFromScratch();

    //then
    SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    SearchHit hit = idsResp.getHits().getAt(0);
    List<Map> events = (List) hit.getSourceAsMap().get(EVENTS);
    boolean allEventsHaveEndDate = events.stream().allMatch(e -> e.get("endDate") != null);
    assertThat(allEventsHaveEndDate).isTrue();
  }

  @Test
  public void runningActivitiesDoNotOverwriteCompletedActivities() {
    //given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram());
    importAllEngineEntitiesFromScratch();

    //when
    HistoricActivityInstanceEngineDto startEvent =
      engineIntegrationExtension.getHistoricActivityInstances()
        .stream()
        .filter(a -> START_EVENT.equals(a.getActivityName()))
        .findFirst()
        .get();
    startEvent.setEndTime(null);
    startEvent.setDurationInMillis(null);
    embeddedOptimizeExtension.importRunningActivityInstance(Collections.singletonList(startEvent));
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    SearchHit hit = idsResp.getHits().getAt(0);
    List<Map> events = (List) hit.getSourceAsMap().get(EVENTS);
    boolean allEventsHaveEndDate = events.stream().allMatch(e -> e.get("endDate") != null);
    assertThat(allEventsHaveEndDate).isTrue();
  }

  @Test
  public void afterRestartOfOptimizeOnlyNewActivitiesAreImported() throws Exception {
    // given
    deployAndStartSimpleServiceTask();
    importAllEngineEntitiesFromScratch();

    // when
    embeddedOptimizeExtension.stopOptimize();
    embeddedOptimizeExtension.startOptimize();
    importAllEngineEntitiesFromScratch();

    // then
    assertThat(getImportedActivityCount()).isEqualTo(3L);
  }

  @ParameterizedTest
  @MethodSource("engineErrors")
  public void definitionImportWorksEvenIfDeploymentRequestFails(ErrorResponseMock errorResponseMock) {
    // given
    final ClientAndServer engineMockServer = useAndGetEngineMockServer();
    final HttpRequest requestMatcher = request()
      .withPath(engineIntegrationExtension.getEnginePath() + "/deployment/.*")
      .withMethod(GET);
    errorResponseMock.mock(requestMatcher, Times.once(), engineMockServer);

    // when
    deployAndStartSimpleServiceTask();
    importAllEngineEntitiesFromScratch();

    // then
    engineMockServer.verify(requestMatcher, VerificationTimes.exactly(2));
    List<ProcessDefinitionOptimizeDto> processDefinitions = definitionClient.getAllProcessDefinitions();
    assertThat(processDefinitions).hasSize(1);
  }

  @Test
  public void doNotSkipProcessInstancesWithSameEndTime() {
    // given
    int originalMaxPageSize = embeddedOptimizeExtension.getConfigurationService()
      .getEngineImportProcessInstanceMaxPageSize();
    embeddedOptimizeExtension.getConfigurationService().setEngineImportProcessInstanceMaxPageSize(2);
    startTwoProcessInstancesWithSameEndTime();
    startTwoProcessInstancesWithSameEndTime();

    // when
    importAllEngineEntitiesFromLastIndex();
    importAllEngineEntitiesFromLastIndex();

    // then
    allEntriesInElasticsearchHaveAllDataWithCount(PROCESS_INSTANCE_INDEX_NAME, 4L, PROCESS_INSTANCE_NULLABLE_FIELDS);
    embeddedOptimizeExtension.getConfigurationService().setEngineImportProcessInstanceMaxPageSize(originalMaxPageSize);
  }

  private void createStartAndCancelUserTaskProcess() {
    ProcessInstanceEngineDto processInstance = deployAndStartUserTaskProcess();
    engineIntegrationExtension.externallyTerminateProcessInstance(processInstance.getId());
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
      .source(searchSourceBuilder);

    SearchResponse response = elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
      .search(searchRequest, RequestOptions.DEFAULT);

    Nested nested = response.getAggregations()
      .get(EVENTS);
    ValueCount countAggregator =
      nested.getAggregations()
        .get(EVENTS + "_count");
    return countAggregator.getValue();
  }

  private void startTwoProcessInstancesWithSameEndTime() {
    OffsetDateTime endTime = OffsetDateTime.now();
    ProcessInstanceEngineDto firstProcInst = deployAndStartSimpleServiceTask();
    ProcessInstanceEngineDto secondProcInst =
      engineIntegrationExtension.startProcessInstance(firstProcInst.getDefinitionId());
    Map<String, OffsetDateTime> procInstEndDateUpdates = new HashMap<>();
    procInstEndDateUpdates.put(firstProcInst.getId(), endTime);
    procInstEndDateUpdates.put(secondProcInst.getId(), endTime);
    engineDatabaseExtension.changeProcessInstanceEndDates(procInstEndDateUpdates);
  }

  private ProcessDefinitionEngineDto deployProcessDefinitionWithTenant(String tenantId) {
    BpmnModelInstance processModel = getSingleServiceTaskProcess();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(processModel, tenantId);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskWithTenant(String tenantId) {
    final ProcessDefinitionEngineDto processDefinitionEngineDto = deployProcessDefinitionWithTenant(tenantId);
    return engineIntegrationExtension.startProcessInstance(processDefinitionEngineDto.getId());
  }

  private ProcessInstanceEngineDto createImportAndDeleteTwoProcessInstances() {
    return createImportAndDeleteTwoProcessInstancesWithVariables(new HashMap<>());
  }

  private ProcessInstanceEngineDto createImportAndDeleteTwoProcessInstancesWithVariables(Map<String, Object> variables) {
    ProcessInstanceEngineDto firstProcInst = deployAndStartSimpleServiceTaskWithVariables(variables);
    ProcessInstanceEngineDto secondProcInst = engineIntegrationExtension.startProcessInstance(
      firstProcInst.getDefinitionId(),
      variables
    );
    importAllEngineEntitiesFromScratch();
    engineIntegrationExtension.deleteHistoricProcessInstance(firstProcInst.getId());
    engineIntegrationExtension.deleteHistoricProcessInstance(secondProcInst.getId());
    return firstProcInst;
  }

  private long getProcessDefinitionCount() {
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_DEFINITION_INDEX_NAME);
    return idsResp.getHits().getTotalHits().value;
  }

  private void deploySimpleProcess() {
    BpmnModelInstance processModel = getSingleServiceTaskProcess();
    engineIntegrationExtension.deployProcessAndGetId(processModel);
  }

}
