/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.util.configuration.engine.DefaultTenant;
import org.camunda.optimize.util.BpmnModels;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.metrics.ValueCount;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.FREQUENCY_AGGREGATION;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.util.BpmnModels.END_EVENT;
import static org.camunda.optimize.util.BpmnModels.SERVICE_TASK;
import static org.camunda.optimize.util.BpmnModels.START_EVENT;
import static org.camunda.optimize.util.BpmnModels.USER_TASK_1;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;
import static org.elasticsearch.search.aggregations.AggregationBuilders.count;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

public class EngineActivityImportIT extends AbstractImportIT {
  private static final Set<String> PROCESS_INSTANCE_NULLABLE_FIELDS =
    Collections.singleton(ProcessInstanceIndex.TENANT_ID);

  @Test
  public void definitionDataIsPresentOnFlowNodesAfterActivityImport() {
    // given
    deployAndStartUserTaskProcess();
    final String tenantId = "tenant1";
    engineIntegrationExtension.createTenant(tenantId);
    engineIntegrationExtension.deployAndStartProcess(BpmnModels.getSimpleBpmnDiagram(), tenantId);

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final List<ProcessInstanceDto> allProcessInstances = elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(allProcessInstances)
      .hasSize(2)
      .allSatisfy(processInstanceDto -> assertThat(processInstanceDto.getFlowNodeInstances())
        .allSatisfy(flowNodeInstanceDto -> {
          assertThat(flowNodeInstanceDto.getDefinitionKey()).isEqualTo(processInstanceDto.getProcessDefinitionKey());
          assertThat(flowNodeInstanceDto.getDefinitionVersion()).isEqualTo(processInstanceDto.getProcessDefinitionVersion());
          assertThat(flowNodeInstanceDto.getTenantId()).isEqualTo(processInstanceDto.getTenantId());
        })
      );
  }

  @Test
  public void defaultTenantIsUsedOnActivityImport() {
    // given
    final String defaultTenant = "lobster";
    embeddedOptimizeExtension.getDefaultEngineConfiguration()
      .setDefaultTenant(new DefaultTenant(defaultTenant, defaultTenant));
    deployAndStartUserTaskProcess();

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final List<ProcessInstanceDto> allProcessInstances = elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(allProcessInstances)
      .singleElement()
      .extracting(ProcessInstanceDto::getFlowNodeInstances)
      .satisfies(flowNodes -> assertThat(flowNodes)
        .allSatisfy(flowNode -> assertThat(flowNode.getTenantId()).isEqualTo(defaultTenant)));
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
    assertThat(allProcessInstances.get(0).getFlowNodeInstances())
      .extracting(FlowNodeInstanceDto::getFlowNodeId, FlowNodeInstanceDto::getCanceled)
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
    assertThat(allProcessInstances.get(0).getFlowNodeInstances())
      .extracting(FlowNodeInstanceDto::getFlowNodeId, FlowNodeInstanceDto::getCanceled)
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
    assertThat(allProcessInstances.get(0).getFlowNodeInstances())
      .extracting(FlowNodeInstanceDto::getFlowNodeId, FlowNodeInstanceDto::getCanceled)
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
    assertThat(allProcessInstances.get(0).getFlowNodeInstances())
      .extracting(FlowNodeInstanceDto::getFlowNodeId, FlowNodeInstanceDto::getCanceled)
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
    assertThat(allProcessInstances.get(0).getFlowNodeInstances())
      .extracting(FlowNodeInstanceDto::getFlowNodeId, FlowNodeInstanceDto::getCanceled)
      .containsExactlyInAnyOrder(
        tuple(START_EVENT, false),
        tuple(USER_TASK_1, true)
      );
  }

  @Test
  public void flowNodesWithoutProcessDefinitionKeyCanBeImported() {
    // given
    deployAndStartSimpleServiceTaskProcess();
    engineDatabaseExtension.removeProcessDefinitionKeyFromAllHistoricFlowNodes();

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final List<ProcessInstanceDto> allProcessInstances = elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(allProcessInstances).hasSize(1);
    assertThat(allProcessInstances.get(0).getFlowNodeInstances())
      .extracting(FlowNodeInstanceDto::getFlowNodeId)
      .containsExactlyInAnyOrder(START_EVENT, SERVICE_TASK, END_EVENT);
  }

  @Test
  public void runningActivitiesAreNotSkippedDuringImport() {
    // given
    deployAndStartUserTaskProcess();
    deployAndStartSimpleServiceTaskProcess();

    // when
    engineIntegrationExtension.finishAllRunningUserTasks();
    importAllEngineEntitiesFromScratch();

    // then
    SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_MULTI_ALIAS);
    for (SearchHit searchHitFields : idsResp.getHits()) {
      List<?> events = (List<?>) searchHitFields.getSourceAsMap().get(FLOW_NODE_INSTANCES);
      assertThat(events).hasSize(3);
    }
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
    List<ProcessVariableNameResponseDto> variablesResponseDtos = variablesClient
      .getProcessVariableNames(variableRequestDto);

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
    List<?> events = (List<?>) hit.getSourceAsMap().get(FLOW_NODE_INSTANCES);
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
    List<Map> events = (List) hit.getSourceAsMap().get(FLOW_NODE_INSTANCES);
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
    List<Map> events = (List) hit.getSourceAsMap().get(FLOW_NODE_INSTANCES);
    boolean allEventsHaveEndDate = events.stream().allMatch(e -> e.get("endDate") != null);
    assertThat(allEventsHaveEndDate).isTrue();
  }

  @Test
  public void afterRestartOfOptimizeOnlyNewActivitiesAreImported() {
    // given
    startAndUseNewOptimizeInstance();
    deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    // when
    startAndUseNewOptimizeInstance();
    importAllEngineEntitiesFromScratch();

    // then
    assertThat(getImportedActivityCount()).isEqualTo(3L);
  }

  private ProcessInstanceEngineDto createImportAndDeleteTwoProcessInstancesWithVariables(Map<String, Object> variables) {
    ProcessInstanceEngineDto firstProcInst = deployAndStartSimpleServiceProcessTaskWithVariables(variables);
    ProcessInstanceEngineDto secondProcInst = engineIntegrationExtension.startProcessInstance(
      firstProcInst.getDefinitionId(),
      variables
    );
    importAllEngineEntitiesFromScratch();
    engineIntegrationExtension.deleteHistoricProcessInstance(firstProcInst.getId());
    engineIntegrationExtension.deleteHistoricProcessInstance(secondProcInst.getId());
    return firstProcInst;
  }

  @SneakyThrows
  private Long getImportedActivityCount() {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(QueryBuilders.matchAllQuery())
      .size(0)
      .fetchSource(false)
      .aggregation(
        nested(FLOW_NODE_INSTANCES, FLOW_NODE_INSTANCES)
          .subAggregation(
            count(FLOW_NODE_INSTANCES + FREQUENCY_AGGREGATION)
              .field(FLOW_NODE_INSTANCES + "." + ProcessInstanceIndex.FLOW_NODE_INSTANCE_ID)
          )
      );

    SearchRequest searchRequest = new SearchRequest()
      .indices(PROCESS_INSTANCE_MULTI_ALIAS)
      .source(searchSourceBuilder);

    SearchResponse response = elasticSearchIntegrationTestExtension.getOptimizeElasticClient().search(searchRequest);

    Nested nested = response.getAggregations()
      .get(FLOW_NODE_INSTANCES);
    ValueCount countAggregator =
      nested.getAggregations()
        .get(FLOW_NODE_INSTANCES + FREQUENCY_AGGREGATION);
    return countAggregator.getValue();
  }

}
