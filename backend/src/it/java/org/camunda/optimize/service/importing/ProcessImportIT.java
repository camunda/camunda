/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import io.github.netmikey.logunit.api.LogCapturer;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.importing.engine.fetcher.definition.ProcessDefinitionFetcher;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.verify.VerificationTimes;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static javax.ws.rs.HttpMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.EXTERNALLY_TERMINATED_STATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.EVENTS;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_TENANT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.util.BpmnModels.START_EVENT;
import static org.camunda.optimize.util.BpmnModels.USER_TASK_1;
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

  @RegisterExtension
  @Order(5)
  protected final LogCapturer logCapturer =
    LogCapturer.create().captureForType(ProcessDefinitionFetcher.class);

  @BeforeEach
  public void cleanUpExistingProcessInstanceIndices() {
    elasticSearchIntegrationTestExtension.deleteAllProcessInstanceIndices();
    elasticSearchIntegrationTestExtension.deleteAllDecisionInstanceIndices();
  }

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
    assertThat(embeddedOptimizeExtension.getImportSchedulerManager().getImportSchedulers()).hasSizeGreaterThan(0);
    embeddedOptimizeExtension.getImportSchedulerManager().getImportSchedulers()
      .forEach(engineImportScheduler -> assertThat(engineImportScheduler.isScheduledToRun()).isFalse());
    assertAllEntriesInElasticsearchHaveAllDataWithCount(PROCESS_DEFINITION_INDEX_NAME, 0L);
    assertThat(indexExist(PROCESS_INSTANCE_MULTI_ALIAS)).isFalse();
    assertAllEntriesInElasticsearchHaveAllDataWithCount(DECISION_DEFINITION_INDEX_NAME, 0L);
    assertThat(indexExist(DECISION_INSTANCE_MULTI_ALIAS)).isFalse();
  }

  @Test
  public void importCreatesDedicatedProcessInstanceIndicesPerDefinition() {
    // given two new process definitions
    final String key1 = "processKey1";
    final String key2 = "processKey2";
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(key1));
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(key2));

    // when
    importAllEngineEntitiesFromScratch();

    // then both instance indices exist
    assertThat(indicesExist(Arrays.asList(
      new ProcessInstanceIndex(key1),
      new ProcessInstanceIndex(key2)
    ))).isTrue();

    // there is one instance in each index
    final SearchResponse idsInIndex1 = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(getProcessInstanceIndexAliasName(key1));
    final SearchResponse idsInIndex2 = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(getProcessInstanceIndexAliasName(key2));
    assertThat(idsInIndex1.getHits().getTotalHits().value).isEqualTo(1L);
    assertThat(idsInIndex2.getHits().getTotalHits().value).isEqualTo(1L);

    // both instances can be found via the multi alias
    final SearchResponse idsInIndices = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_MULTI_ALIAS);
    assertThat(idsInIndices.getHits().getTotalHits().value).isEqualTo(2L);
  }

  @Test
  public void importInstancesToCorrectIndexWhenIndexAlreadyExists() {
    // given
    final String key = "processKey";
    final ProcessDefinitionEngineDto definition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSimpleBpmnDiagram(key));
    engineIntegrationExtension.startProcessInstance(definition.getId());
    importAllEngineEntitiesFromScratch();
    engineIntegrationExtension.startProcessInstance(definition.getId());

    // when
    importAllEngineEntitiesFromScratch();

    // then there are two instances in one process index
    final SearchResponse idsInIndex = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(getProcessInstanceIndexAliasName(key));
    assertThat(idsInIndex.getHits().getTotalHits().value).isEqualTo(2L);
  }

  @Test
  public void allProcessDefinitionFieldDataIsAvailable() {
    // given
    deployAndStartSimpleServiceTask();

    // when
    importAllEngineEntitiesFromScratch();

    // then
    assertAllEntriesInElasticsearchHaveAllData(PROCESS_DEFINITION_INDEX_NAME, PROCESS_DEFINITION_NULLABLE_FIELDS);
  }

  @Test
  public void processDefinitionTenantIdIsImportedIfPresent() {
    // given
    final String tenantId = "reallyAwesomeTenantId";
    deployProcessDefinitionWithTenant(tenantId);

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_DEFINITION_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap()).containsEntry(ProcessDefinitionIndex.TENANT_ID, tenantId);
  }

  @Test
  public void processDefinitionDefaultEngineTenantIdIsApplied() {
    // given
    final String tenantId = "reallyAwesomeTenantId";
    embeddedOptimizeExtension.getDefaultEngineConfiguration().getDefaultTenant().setId(tenantId);
    deployAndStartSimpleServiceTask();

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_DEFINITION_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap()).containsEntry(ProcessDefinitionIndex.TENANT_ID, tenantId);
  }

  @Test
  public void processDefinitionEngineTenantIdIsPreferredOverDefaultTenantId() {
    // given
    final String defaultTenantId = "reallyAwesomeTenantId";
    final String expectedTenantId = "evenMoreAwesomeTenantId";
    embeddedOptimizeExtension.getDefaultEngineConfiguration().getDefaultTenant().setId(defaultTenantId);
    deployProcessDefinitionWithTenant(expectedTenantId);

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_DEFINITION_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap()).containsEntry(ProcessDefinitionIndex.TENANT_ID, expectedTenantId);
  }

  @Test
  public void allProcessInstanceDataIsAvailable() {
    // given
    deployAndStartSimpleServiceTask();

    // when
    importAllEngineEntitiesFromScratch();

    // then
    assertAllEntriesInElasticsearchHaveAllData(PROCESS_INSTANCE_MULTI_ALIAS, PROCESS_INSTANCE_NULLABLE_FIELDS);
  }

  @Test
  public void canceledFlowNodesAreImportedWithCorrectValue() {
    // given
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartUserTaskProcess();
    engineIntegrationExtension.cancelActivityInstance(processInstanceEngineDto.getId(), USER_TASK_1);

    // when
    importAllEngineEntitiesFromScratch();

    // then
    assertAllEntriesInElasticsearchHaveAllData(PROCESS_INSTANCE_MULTI_ALIAS, PROCESS_INSTANCE_NULLABLE_FIELDS);
    final List<ProcessInstanceDto> allProcessInstances = elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(allProcessInstances).hasSize(1);
    assertThat(allProcessInstances.get(0).getEvents())
      .extracting(FlowNodeInstanceDto::getActivityId, FlowNodeInstanceDto::getCanceled)
      .containsExactlyInAnyOrder(
        tuple(START_EVENT, false),
        tuple(USER_TASK_1, true)
      );
  }

  @Test
  public void canceledFlowNodesAreUpdatedIfAlreadyImported() {
    // given
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartUserTaskProcess();
    importAllEngineEntitiesFromScratch();

    // then
    List<ProcessInstanceDto> allProcessInstances = elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(allProcessInstances).hasSize(1);
    assertThat(allProcessInstances.get(0).getEvents())
      .extracting(FlowNodeInstanceDto::getActivityId, FlowNodeInstanceDto::getCanceled)
      .containsExactlyInAnyOrder(
        tuple(START_EVENT, false),
        tuple(USER_TASK_1, false)
      );

    // when
    engineIntegrationExtension.cancelActivityInstance(processInstanceEngineDto.getId(), USER_TASK_1);
    importAllEngineEntitiesFromLastIndex();

    // then
    assertAllEntriesInElasticsearchHaveAllData(PROCESS_INSTANCE_MULTI_ALIAS, PROCESS_INSTANCE_NULLABLE_FIELDS);
    allProcessInstances = elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(allProcessInstances).hasSize(1);
    assertThat(allProcessInstances.get(0).getEvents())
      .extracting(FlowNodeInstanceDto::getActivityId, FlowNodeInstanceDto::getCanceled)
      .containsExactlyInAnyOrder(
        tuple(START_EVENT, false),
        tuple(USER_TASK_1, true)
      );
  }

  @Test
  public void canceledFlowNodesByCanceledProcessInstanceAreUpdatedIfAlreadyImported() {
    // given
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartUserTaskProcess();
    importAllEngineEntitiesFromScratch();

    // then
    List<ProcessInstanceDto> allProcessInstances = elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(allProcessInstances).hasSize(1);
    assertThat(allProcessInstances.get(0).getEvents())
      .extracting(FlowNodeInstanceDto::getActivityId, FlowNodeInstanceDto::getCanceled)
      .containsExactlyInAnyOrder(
        tuple(START_EVENT, false),
        tuple(USER_TASK_1, false)
      );

    // when the process instance is canceled
    engineIntegrationExtension.deleteProcessInstance(processInstanceEngineDto.getId());
    importAllEngineEntitiesFromLastIndex();

    // then
    assertAllEntriesInElasticsearchHaveAllData(PROCESS_INSTANCE_MULTI_ALIAS, PROCESS_INSTANCE_NULLABLE_FIELDS);
    allProcessInstances = elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(allProcessInstances).hasSize(1);
    assertThat(allProcessInstances.get(0).getEvents())
      .extracting(FlowNodeInstanceDto::getActivityId, FlowNodeInstanceDto::getCanceled)
      .containsExactlyInAnyOrder(
        tuple(START_EVENT, false),
        tuple(USER_TASK_1, true)
      );
  }

  @Test
  public void importsAllDefinitionsEvenIfTotalAmountIsAboveMaxPageSize() {
    // given
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
    // given
    final String tenantId = "myTenant";
    deployAndStartSimpleServiceTaskWithTenant(tenantId);

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_MULTI_ALIAS);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap()).containsEntry(ProcessInstanceIndex.TENANT_ID, tenantId);
  }

  @Test
  public void processInstanceDefaultEngineTenantIdIsApplied() {
    // given
    final String tenantId = "reallyAwesomeTenantId";
    embeddedOptimizeExtension.getDefaultEngineConfiguration().getDefaultTenant().setId(tenantId);
    deployAndStartSimpleServiceTask();

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_MULTI_ALIAS);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap()).containsEntry(ProcessInstanceIndex.TENANT_ID, tenantId);
  }

  @Test
  public void processInstanceEngineTenantIdIsPreferredOverDefaultTenantId() {
    // given
    final String defaultTenantId = "reallyAwesomeTenantId";
    final String expectedTenantId = "evenMoreAwesomeTenantId";
    embeddedOptimizeExtension.getDefaultEngineConfiguration().getDefaultTenant().setId(defaultTenantId);
    deployAndStartSimpleServiceTaskWithTenant(expectedTenantId);

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_MULTI_ALIAS);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap()).containsEntry(ProcessInstanceIndex.TENANT_ID, expectedTenantId);
  }

  @Test
  public void failingJobDoesNotUpdateImportIndex() throws IOException {
    // given
    ProcessInstanceEngineDto dto1 = deployAndStartSimpleServiceTask();
    OffsetDateTime endTime = engineIntegrationExtension.getHistoricProcessInstance(dto1.getId()).getEndTime();

    importAllEngineEntitiesFromScratch();
    importAllEngineEntitiesFromLastIndex();

    elasticSearchIntegrationTestExtension.blockProcInstIndex(dto1.getProcessDefinitionKey(), true);

    ProcessInstanceEngineDto dto2 = deployAndStartSimpleServiceTask();

    Thread thread = new Thread(this::importAllEngineEntitiesFromLastIndex);
    thread.start();

    OffsetDateTime lastImportTimestamp = elasticSearchIntegrationTestExtension.getLastProcessInstanceImportTimestamp();
    assertThat(lastImportTimestamp).isEqualTo(endTime);

    elasticSearchIntegrationTestExtension.blockProcInstIndex(dto1.getProcessDefinitionKey(), false);
    endTime = engineIntegrationExtension.getHistoricProcessInstance(dto2.getId()).getEndTime();

    importAllEngineEntitiesFromLastIndex();
    importAllEngineEntitiesFromLastIndex();

    lastImportTimestamp = elasticSearchIntegrationTestExtension.getLastProcessInstanceImportTimestamp();

    assertThat(lastImportTimestamp).isEqualTo(endTime);
  }

  @AfterEach
  public void unblockIndex() throws IOException {
    elasticSearchIntegrationTestExtension.blockAllProcInstIndices(false);
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
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_MULTI_ALIAS);
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
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_MULTI_ALIAS);
    assertThat(idsResp.getHits().getAt(0).getSourceAsMap())
      .containsEntry(ProcessInstanceIndex.STATE, EXTERNALLY_TERMINATED_STATE);
  }

  @Test
  public void runningProcessesIndexedAfterFinish() {
    // given
    deployAndStartUserTaskProcess();
    importAllEngineEntitiesFromScratch();

    // then
    SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_MULTI_ALIAS);
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
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_MULTI_ALIAS);
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
    assertAllEntriesInElasticsearchHaveAllDataWithCount(
      PROCESS_INSTANCE_MULTI_ALIAS,
      4L,
      PROCESS_INSTANCE_NULLABLE_FIELDS
    );
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
    // given
    engineIntegrationExtension.deployAndStartProcess(BpmnModels.getSingleUserTaskDiagram());

    // when
    importAllEngineEntitiesFromScratch();

    // then
    SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_MULTI_ALIAS);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    SearchHit hit = idsResp.getHits().getAt(0);
    List<?> events = (List<?>) hit.getSourceAsMap().get(EVENTS);
    assertThat(events).hasSize(2);
  }

  @Test
  public void completedActivitiesOverwriteRunningActivities() {
    // given
    engineIntegrationExtension.deployAndStartProcess(BpmnModels.getSingleUserTaskDiagram());

    // when
    importAllEngineEntitiesFromScratch();
    engineIntegrationExtension.finishAllRunningUserTasks();
    importAllEngineEntitiesFromScratch();

    // then
    SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_MULTI_ALIAS);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    SearchHit hit = idsResp.getHits().getAt(0);
    List<Map> events = (List) hit.getSourceAsMap().get(EVENTS);
    boolean allEventsHaveEndDate = events.stream().allMatch(e -> e.get("endDate") != null);
    assertThat(allEventsHaveEndDate).isTrue();
  }

  @Test
  public void runningActivitiesDoNotOverwriteCompletedActivities() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram());
    importAllEngineEntitiesFromScratch();

    // when
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

    // then
    SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_MULTI_ALIAS);
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

  @ParameterizedTest
  @MethodSource("engineAuthorizationErrors")
  public void definitionImportMissingAuthorizationLogsMessage(final ErrorResponseMock authorizationError) {
    // given
    final ClientAndServer engineMockServer = useAndGetEngineMockServer();
    final HttpRequest requestMatcher = request()
      .withPath(engineIntegrationExtension.getEnginePath() + "/deployment/.*")
      .withMethod(GET);
    authorizationError.mock(requestMatcher, Times.once(), engineMockServer);

    // when
    deployAndStartSimpleServiceTask();
    importAllEngineEntitiesFromScratch();

    // then the second request will have succeeded
    engineMockServer.verify(requestMatcher, VerificationTimes.exactly(2));
    logCapturer.assertContains(String.format(
      "Error during fetching of entities. Please check the connection with [%s]!" +
        " Make sure all required engine authorizations exist", DEFAULT_ENGINE_ALIAS));
  }

  @Test
  public void definitionImportBadAuthorizationLogsMessage() {
    // given
    final ClientAndServer engineMockServer = useAndGetEngineMockServer();
    final HttpRequest requestMatcher = request()
      .withPath(engineIntegrationExtension.getEnginePath() + "/deployment/.*")
      .withMethod(GET);
    engineMockServer.when(requestMatcher, Times.once())
      .respond(HttpResponse.response().withStatusCode(Response.Status.UNAUTHORIZED.getStatusCode()));

    // when
    deployAndStartSimpleServiceTask();
    importAllEngineEntitiesFromScratch();

    // then the second request will have succeeded
    engineMockServer.verify(requestMatcher, VerificationTimes.exactly(2));
    logCapturer.assertContains(String.format(
      "Error during fetching of entities. Please check the connection with [%s]!" +
        " Make sure you have configured an authorized user", DEFAULT_ENGINE_ALIAS));
  }

  private static Stream<String> tenants() {
    return Stream.of("someTenant", DEFAULT_TENANT);
  }

  @ParameterizedTest
  @MethodSource("tenants")
  public void processDefinitionMarkedAsDeletedIfNewDefinitionIdButSameKeyVersionTenant(String tenantId) {
    // given
    BpmnModelInstance processModel = getSingleServiceTaskProcess();
    final ProcessDefinitionEngineDto originalDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(processModel, tenantId);

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final List<ProcessDefinitionOptimizeDto> allProcessDefinitions =
      elasticSearchIntegrationTestExtension.getAllProcessDefinitions();
    assertThat(allProcessDefinitions).singleElement()
      .satisfies(definition -> {
        assertThat(definition.getId()).isEqualTo(originalDefinition.getId());
        assertThat(definition.isDeleted()).isFalse();
      });

    // when the original definition is deleted and a new one deployed with the same key, version and tenant
    engineIntegrationExtension.deleteProcessDefinition(originalDefinition.getId());
    final ProcessDefinitionEngineDto newDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(
        processModel,
        tenantId
      );
    importAllEngineEntitiesFromLastIndex();

    // then the original definition is marked as deleted
    final List<ProcessDefinitionOptimizeDto> updatedDefinitions =
      elasticSearchIntegrationTestExtension.getAllProcessDefinitions();
    assertThat(updatedDefinitions).hasSize(2)
      .allSatisfy(definition -> {
        assertThat(definition.getKey()).isEqualTo(originalDefinition.getKey());
        assertThat(definition.getVersion()).isEqualTo(originalDefinition.getVersionAsString());
        assertThat(definition.getTenantId()).isEqualTo(tenantId);
      })
      .extracting(DefinitionOptimizeResponseDto::getId, DefinitionOptimizeResponseDto::isDeleted)
      .containsExactlyInAnyOrder(
        tuple(originalDefinition.getId(), true),
        tuple(newDefinition.getId(), false)
      );
    // and the definition cache includes the deleted and new definition
    assertThat(embeddedOptimizeExtension.getProcessDefinitionFromResolverService(originalDefinition.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isTrue());
    assertThat(embeddedOptimizeExtension.getProcessDefinitionFromResolverService(newDefinition.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isFalse());
  }

  @Test
  public void processDefinitionsMarkedAsDeletedIfMultipleNewDeployments() {
    // given
    BpmnModelInstance processModel = getSingleServiceTaskProcess();
    final ProcessDefinitionEngineDto firstDeletedDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(processModel, DEFAULT_TENANT);

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final List<ProcessDefinitionOptimizeDto> firstDefinitionImported =
      elasticSearchIntegrationTestExtension.getAllProcessDefinitions();
    assertThat(firstDefinitionImported).singleElement()
      .satisfies(definition -> {
        assertThat(definition.getId()).isEqualTo(firstDeletedDefinition.getId());
        assertThat(definition.isDeleted()).isFalse();
      });

    // when the original definition is deleted and a new one deployed with the same key, version and tenant
    engineIntegrationExtension.deleteProcessDefinition(firstDeletedDefinition.getId());
    final ProcessDefinitionEngineDto secondDeletedDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(processModel, DEFAULT_TENANT);
    importAllEngineEntitiesFromLastIndex();

    // then the original definition is marked as deleted
    final List<ProcessDefinitionOptimizeDto> firstTwoDefinitionsImported =
      elasticSearchIntegrationTestExtension.getAllProcessDefinitions();
    assertThat(firstTwoDefinitionsImported).hasSize(2)
      .allSatisfy(definition -> {
        assertThat(definition.getKey()).isEqualTo(firstDeletedDefinition.getKey());
        assertThat(definition.getVersion()).isEqualTo(firstDeletedDefinition.getVersionAsString());
        assertThat(definition.getTenantId()).isEqualTo(DEFAULT_TENANT);
      })
      .extracting(DefinitionOptimizeResponseDto::getId, DefinitionOptimizeResponseDto::isDeleted)
      .containsExactlyInAnyOrder(
        tuple(firstDeletedDefinition.getId(), true),
        tuple(secondDeletedDefinition.getId(), false)
      );
    // and the definition cache includes the deleted and new definition
    assertThat(embeddedOptimizeExtension.getProcessDefinitionFromResolverService(firstDeletedDefinition.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isTrue());
    assertThat(embeddedOptimizeExtension.getProcessDefinitionFromResolverService(secondDeletedDefinition.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isFalse());

    // when the second definition is deleted and a new one deployed with the same key, version and tenant
    engineIntegrationExtension.deleteProcessDefinition(secondDeletedDefinition.getId());
    final ProcessDefinitionEngineDto nonDeletedDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(processModel, DEFAULT_TENANT);
    importAllEngineEntitiesFromLastIndex();

    // then the first two definitions are marked as deleted
    final List<ProcessDefinitionOptimizeDto> allDefinitionsImported =
      elasticSearchIntegrationTestExtension.getAllProcessDefinitions();
    assertThat(allDefinitionsImported).hasSize(3)
      .allSatisfy(definition -> {
        assertThat(definition.getKey()).isEqualTo(firstDeletedDefinition.getKey());
        assertThat(definition.getVersion()).isEqualTo(firstDeletedDefinition.getVersionAsString());
        assertThat(definition.getTenantId()).isEqualTo(DEFAULT_TENANT);
      })
      .extracting(DefinitionOptimizeResponseDto::getId, DefinitionOptimizeResponseDto::isDeleted)
      .containsExactlyInAnyOrder(
        tuple(firstDeletedDefinition.getId(), true),
        tuple(secondDeletedDefinition.getId(), true),
        tuple(nonDeletedDefinition.getId(), false)
      );
    // and the definition cache includes correct deletion states
    assertThat(embeddedOptimizeExtension.getProcessDefinitionFromResolverService(firstDeletedDefinition.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isTrue());
    assertThat(embeddedOptimizeExtension.getProcessDefinitionFromResolverService(secondDeletedDefinition.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isTrue());
    assertThat(embeddedOptimizeExtension.getProcessDefinitionFromResolverService(nonDeletedDefinition.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isFalse());
  }

  @Test
  public void processDefinitionMarkedAsDeletedOtherTenantUnaffected() {
    // given
    BpmnModelInstance processModel = getSingleServiceTaskProcess();
    final ProcessDefinitionEngineDto originalDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(processModel, DEFAULT_TENANT);
    final ProcessDefinitionEngineDto originalDefinitionWithTenant =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(processModel, "someTenant");

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final List<ProcessDefinitionOptimizeDto> allProcessDefinitions =
      elasticSearchIntegrationTestExtension.getAllProcessDefinitions();
    assertThat(allProcessDefinitions).hasSize(2)
      .extracting(ProcessDefinitionOptimizeDto::getId, ProcessDefinitionOptimizeDto::isDeleted)
      .containsExactlyInAnyOrder(
        tuple(originalDefinition.getId(), false),
        tuple(originalDefinitionWithTenant.getId(), false)
      );

    // when the original definition is deleted and a new one deployed with the same key, version and tenant
    engineIntegrationExtension.deleteProcessDefinition(originalDefinition.getId());
    final ProcessDefinitionEngineDto newDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(processModel);
    importAllEngineEntitiesFromLastIndex();

    // then the original definition is marked as deleted, the new one exists and the one with tenant is unaffected
    final List<ProcessDefinitionOptimizeDto> updatedDefinitions =
      elasticSearchIntegrationTestExtension.getAllProcessDefinitions();
    assertThat(updatedDefinitions).hasSize(3)
      .extracting(ProcessDefinitionOptimizeDto::getId, ProcessDefinitionOptimizeDto::isDeleted)
      .containsExactlyInAnyOrder(
        tuple(originalDefinition.getId(), true),
        tuple(originalDefinitionWithTenant.getId(), false),
        tuple(newDefinition.getId(), false)
      );
    // and the definition cache includes the deleted and new definition
    assertThat(embeddedOptimizeExtension.getProcessDefinitionFromResolverService(originalDefinition.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isTrue());
    assertThat(embeddedOptimizeExtension.getProcessDefinitionFromResolverService(originalDefinitionWithTenant.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isFalse());
    assertThat(embeddedOptimizeExtension.getProcessDefinitionFromResolverService(newDefinition.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isFalse());
  }

  @Test
  public void processDefinitionMarkedAsDeletedOtherVersionUnaffected() {
    // given
    BpmnModelInstance processModel = getSingleServiceTaskProcess();
    final ProcessDefinitionEngineDto originalDefinitionV1 =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(processModel);
    final ProcessDefinitionEngineDto originalDefinitionV2 =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(processModel);

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final List<ProcessDefinitionOptimizeDto> allProcessDefinitions =
      elasticSearchIntegrationTestExtension.getAllProcessDefinitions();
    assertThat(allProcessDefinitions).hasSize(2)
      .extracting(ProcessDefinitionOptimizeDto::getId, ProcessDefinitionOptimizeDto::isDeleted)
      .containsExactlyInAnyOrder(
        tuple(originalDefinitionV1.getId(), false),
        tuple(originalDefinitionV2.getId(), false)
      );

    // when the v2 definition is deleted and a new one deployed with the same key, version and tenant
    engineIntegrationExtension.deleteProcessDefinition(originalDefinitionV2.getId());
    final ProcessDefinitionEngineDto newDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(processModel);
    importAllEngineEntitiesFromLastIndex();

    // then the original definition is unaffected, the original v2 is marked as deleted, and the new one exists
    final List<ProcessDefinitionOptimizeDto> updatedDefinitions =
      elasticSearchIntegrationTestExtension.getAllProcessDefinitions();
    assertThat(updatedDefinitions).hasSize(3)
      .extracting(ProcessDefinitionOptimizeDto::getId, ProcessDefinitionOptimizeDto::isDeleted)
      .containsExactlyInAnyOrder(
        tuple(originalDefinitionV1.getId(), false),
        tuple(originalDefinitionV2.getId(), true),
        tuple(newDefinition.getId(), false)
      );
    // and the definition cache includes the deleted and new definition
    assertThat(embeddedOptimizeExtension.getProcessDefinitionFromResolverService(originalDefinitionV1.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isFalse());
    assertThat(embeddedOptimizeExtension.getProcessDefinitionFromResolverService(originalDefinitionV2.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isTrue());
    assertThat(embeddedOptimizeExtension.getProcessDefinitionFromResolverService(newDefinition.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isFalse());
  }

  @Test
  public void processDefinitionMarkedAsDeletedOtherDefinitionKeyUnaffected() {
    // given
    BpmnModelInstance processModel = getSingleServiceTaskProcess();
    final ProcessDefinitionEngineDto originalDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(processModel);
    final ProcessDefinitionEngineDto originalDefinitionWithOtherKey =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSimpleBpmnDiagram("otherKey"));

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final List<ProcessDefinitionOptimizeDto> allProcessDefinitions =
      elasticSearchIntegrationTestExtension.getAllProcessDefinitions();
    assertThat(allProcessDefinitions).hasSize(2)
      .extracting(ProcessDefinitionOptimizeDto::getId, ProcessDefinitionOptimizeDto::isDeleted)
      .containsExactlyInAnyOrder(
        tuple(originalDefinition.getId(), false),
        tuple(originalDefinitionWithOtherKey.getId(), false)
      );

    // when the original definition is deleted and a new one deployed with the same key, version and tenant
    engineIntegrationExtension.deleteProcessDefinition(originalDefinition.getId());
    final ProcessDefinitionEngineDto newDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(processModel);
    importAllEngineEntitiesFromLastIndex();

    // then the original definition is marked as deleted, the other key is unaffected, and the new one exists
    final List<ProcessDefinitionOptimizeDto> updatedDefinitions =
      elasticSearchIntegrationTestExtension.getAllProcessDefinitions();
    assertThat(updatedDefinitions).hasSize(3)
      .extracting(ProcessDefinitionOptimizeDto::getId, ProcessDefinitionOptimizeDto::isDeleted)
      .containsExactlyInAnyOrder(
        tuple(originalDefinition.getId(), true),
        tuple(originalDefinitionWithOtherKey.getId(), false),
        tuple(newDefinition.getId(), false)
      );
    // and the definition cache includes the deleted and new definition
    assertThat(embeddedOptimizeExtension.getProcessDefinitionFromResolverService(originalDefinition.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isTrue());
    assertThat(embeddedOptimizeExtension.getProcessDefinitionFromResolverService(originalDefinitionWithOtherKey.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isFalse());
    assertThat(embeddedOptimizeExtension.getProcessDefinitionFromResolverService(newDefinition.getId()))
      .isPresent().get().satisfies(definition -> assertThat(definition.isDeleted()).isFalse());
  }

  @Test
  public void processDefinitionMarkedAsDeletedIfImportedInSameBatchAsNewerDeployment() {
    // given
    BpmnModelInstance processModel = getSingleServiceTaskProcess();
    final ProcessDefinitionEngineDto originalDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(processModel);
    final ProcessDefinitionEngineDto newDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(processModel);
    engineDatabaseExtension.changeVersionOfProcessDefinitionWithDeploymentId(
      newDefinition.getVersionAsString(),
      originalDefinition.getDeploymentId()
    );

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final List<ProcessDefinitionOptimizeDto> allProcessDefinitions =
      elasticSearchIntegrationTestExtension.getAllProcessDefinitions();
    assertThat(allProcessDefinitions).hasSize(2)
      .extracting(ProcessDefinitionOptimizeDto::isDeleted)
      .containsExactlyInAnyOrder(true, false);
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
    assertAllEntriesInElasticsearchHaveAllDataWithCount(
      PROCESS_INSTANCE_MULTI_ALIAS,
      4L,
      PROCESS_INSTANCE_NULLABLE_FIELDS
    );
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
      .indices(PROCESS_INSTANCE_MULTI_ALIAS)
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

  protected boolean indexExist(final String indexName) {
    return embeddedOptimizeExtension.getElasticSearchSchemaManager()
      .indexExists(embeddedOptimizeExtension.getOptimizeElasticClient(), indexName);
  }

}
