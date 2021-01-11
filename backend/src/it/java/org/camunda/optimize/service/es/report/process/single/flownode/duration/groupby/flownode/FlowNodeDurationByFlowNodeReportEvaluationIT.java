/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.flownode.duration.groupby.flownode;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.TenantService;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.test.util.DateCreationFreezer;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.LESS_THAN;
import static org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel.VIEW;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurations;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.camunda.optimize.util.BpmnModels.START_EVENT_ID;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class FlowNodeDurationByFlowNodeReportEvaluationIT extends AbstractProcessDefinitionIT {

  private static final String PROCESS_DEFINITION_KEY = "123";
  private static final String START_EVENT = "startEvent";
  private static final String END_EVENT = "endEvent";
  private static final String SERVICE_TASK_ID = "aSimpleServiceTask";
  private static final String SERVICE_TASK_ID_2 = "aSimpleServiceTask2";

  private final List<AggregationType> aggregationTypes = AggregationType.getAggregationTypesAsListWithoutSum();

  @Test
  public void reportEvaluationForOneProcess() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, 20.);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    final ReportMapResultDto result = evaluationResponse.getResult();
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processDefinition.getKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processDefinition.getVersionAsString());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.FLOW_NODE);
    assertThat(resultReportDataDto.getView().getProperty()).isEqualTo(ProcessViewProperty.DURATION);
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).hasSize(3);
    assertThat(result.getEntryForKey(SERVICE_TASK_ID)).isPresent();
    assertThat(result.getEntryForKey(SERVICE_TASK_ID).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(20.));
    assertThat(result.getEntryForKey(START_EVENT)).isPresent();
    assertThat(result.getEntryForKey(START_EVENT).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(20.));
    assertThat(result.getEntryForKey(END_EVENT).isPresent()).isTrue();
    assertThat(result.getEntryForKey(END_EVENT).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(20.));
  }

  @Test
  public void reportEvaluationForSeveralProcesses() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, 10.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, 30.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, 20.);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData()).hasSize(3);
    assertThat(result.getEntryForKey(SERVICE_TASK_ID)).isPresent();
    assertThat(result.getEntryForKey(SERVICE_TASK_ID).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(10., 30., 20.));
  }

  @Test
  public void evaluateReportForMultipleEvents() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployProcessWithTwoTasks();

    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, SERVICE_TASK_ID, 100.);
    changeActivityDuration(processInstanceDto, SERVICE_TASK_ID_2, 20.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, SERVICE_TASK_ID, 200.);
    changeActivityDuration(processInstanceDto, SERVICE_TASK_ID_2, 10.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, SERVICE_TASK_ID, 900.);
    changeActivityDuration(processInstanceDto, SERVICE_TASK_ID_2, 90.);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData =
      getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getIsComplete()).isTrue();
    assertThat(result.getData().size()).isEqualTo(4);
    assertThat(result.getEntryForKey(SERVICE_TASK_ID)).isPresent();
    assertThat(result.getEntryForKey(SERVICE_TASK_ID).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(100., 200., 900.));
    assertThat(result.getEntryForKey(SERVICE_TASK_ID_2).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(20., 10., 90.));
  }

  @Test
  public void evaluateReportForMultipleEvents_resultLimitedByConfig() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployProcessWithTwoTasks();

    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, SERVICE_TASK_ID, 100.);
    changeActivityDuration(processInstanceDto, SERVICE_TASK_ID_2, 20.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, SERVICE_TASK_ID, 200.);
    changeActivityDuration(processInstanceDto, SERVICE_TASK_ID_2, 10.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, SERVICE_TASK_ID, 900.);
    changeActivityDuration(processInstanceDto, SERVICE_TASK_ID_2, 90.);

    importAllEngineEntitiesFromScratch();

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(1);

    // when
    ProcessReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    final ReportMapResultDto resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount()).isEqualTo(3L);
    assertThat(resultDto.getData()).isNotNull();
    assertThat(resultDto.getData()).hasSize(4);
    assertThat(getExecutedFlowNodeCount(resultDto)).isEqualTo(1L);
    assertThat(resultDto.getIsComplete()).isFalse();
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployProcessWithTwoTasks();

    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, SERVICE_TASK_ID, 100.);
    changeActivityDuration(processInstanceDto, SERVICE_TASK_ID_2, 20.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, SERVICE_TASK_ID, 200.);
    changeActivityDuration(processInstanceDto, SERVICE_TASK_ID_2, 10.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, SERVICE_TASK_ID, 900.);
    changeActivityDuration(processInstanceDto, SERVICE_TASK_ID_2, 90.);


    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.ASC));
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    List<MapResultEntryDto> resultData = evaluationResponse.getResult().getData();
    assertThat(resultData).hasSize(4);
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    assertThat(resultKeys).isSortedAccordingTo(Comparator.naturalOrder());
  }

  @Test
  public void testEvaluationResultForAllAggregationTypes() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, 10.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, 30.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, 20.);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    final Map<AggregationType, ReportMapResultDto> results = evaluateMapReportForAllAggTypes(
      reportData);

    // then
    assertDurationMapReportResults(results, new Double[]{10., 30., 20.});
  }

  private void assertDurationMapReportResults(final Map<AggregationType, ReportMapResultDto> results,
                                              Double[] expectedDurations) {
    aggregationTypes.forEach((AggregationType aggType) -> {
      final ReportMapResultDto result = results.get(aggType);
      assertThat(result.getEntryForKey(SERVICE_TASK_ID)).isPresent();
      assertThat(result.getEntryForKey(SERVICE_TASK_ID).get().getValue())
        .isEqualTo(calculateExpectedValueGivenDurations(expectedDurations).get(aggType));
    });
  }

  @Test
  public void testCustomOrderOnResultValueIsApplied() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployProcessWithTwoTasks();

    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, SERVICE_TASK_ID, 100.);
    changeActivityDuration(processInstanceDto, SERVICE_TASK_ID_2, 20.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, SERVICE_TASK_ID, 200.);
    changeActivityDuration(processInstanceDto, SERVICE_TASK_ID_2, 10.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, SERVICE_TASK_ID, 900.);
    changeActivityDuration(processInstanceDto, SERVICE_TASK_ID_2, 90.);

    importAllEngineEntitiesFromScratch();

    aggregationTypes.forEach((AggregationType aggType) -> {
      // when
      final ProcessReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
      reportData.getConfiguration().setAggregationType(aggType);
      reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_VALUE, SortOrder.ASC));
      AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse =
        reportClient.evaluateMapReport(reportData);

      // then
      final List<MapResultEntryDto> resultData = evaluationResponse.getResult().getData();
      assertThat(resultData).hasSize(4);
      final List<Double> bucketValues = resultData.stream()
        .map(MapResultEntryDto::getValue)
        .collect(Collectors.toList());
      assertThat(bucketValues).isSortedAccordingTo(Comparator.naturalOrder());
    });
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() {
    // given
    ProcessDefinitionEngineDto firstDefinition = deploySimpleServiceTaskProcessDefinition();
    ProcessDefinitionEngineDto latestDefinition = deployProcessWithTwoTasks();
    assertThat(latestDefinition.getVersion()).isEqualTo(2);

    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(firstDefinition.getId());
    changeActivityDuration(processInstanceDto, 20.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(firstDefinition.getId());
    changeActivityDuration(processInstanceDto, 30.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    changeActivityDuration(processInstanceDto, 50.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    changeActivityDuration(processInstanceDto, 120.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    changeActivityDuration(processInstanceDto, 100.);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      latestDefinition.getKey(),
      ALL_VERSIONS
    );
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData()).hasSize(4);
    assertThat(result.getEntryForKey(SERVICE_TASK_ID).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(20., 30., 50., 120., 100.));
    assertThat(result.getEntryForKey(SERVICE_TASK_ID_2).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(50., 120., 100.));
  }

  @Test
  public void multipleVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() {
    // given
    ProcessDefinitionEngineDto firstDefinition = deploySimpleServiceTaskProcessDefinition();
    deploySimpleServiceTaskProcessDefinition();
    ProcessDefinitionEngineDto latestDefinition = deployProcessWithTwoTasks();
    assertThat(latestDefinition.getVersion()).isEqualTo(3);

    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(firstDefinition.getId());
    changeActivityDuration(processInstanceDto, 20.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(firstDefinition.getId());
    changeActivityDuration(processInstanceDto, 30.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    changeActivityDuration(processInstanceDto, 50.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    changeActivityDuration(processInstanceDto, 120.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    changeActivityDuration(processInstanceDto, 100.);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      latestDefinition.getKey(),
      ImmutableList.of(firstDefinition.getVersionAsString(), latestDefinition.getVersionAsString())
    );
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData()).hasSize(4);
    assertThat(result.getEntryForKey(SERVICE_TASK_ID).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(20., 30., 50., 120., 100.));
    assertThat(result.getEntryForKey(SERVICE_TASK_ID_2).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(50., 120., 100.));
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasLessNodes() {
    // given
    ProcessDefinitionEngineDto firstDefinition = deployProcessWithTwoTasks();
    ProcessDefinitionEngineDto latestDefinition = deploySimpleServiceTaskProcessDefinition();
    assertThat(latestDefinition.getVersion()).isEqualTo(2);

    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(firstDefinition.getId());
    changeActivityDuration(processInstanceDto, 20.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(firstDefinition.getId());
    changeActivityDuration(processInstanceDto, 30.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(firstDefinition.getId());
    changeActivityDuration(processInstanceDto, 50.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    changeActivityDuration(processInstanceDto, 120.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    changeActivityDuration(processInstanceDto, 100.);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      latestDefinition.getKey(),
      ALL_VERSIONS
    );
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData()).hasSize(3);
    assertThat(result.getEntryForKey(SERVICE_TASK_ID).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(20., 30., 50., 120., 100.));
  }

  @Test
  public void multipleVersionsRespectLatestNodesOnlyWhereLatestHasLessNodes() {
    // given
    ProcessDefinitionEngineDto firstDefinition = deployProcessWithTwoTasks();
    deployProcessWithTwoTasks();
    ProcessDefinitionEngineDto latestDefinition = deploySimpleServiceTaskProcessDefinition();
    assertThat(latestDefinition.getVersion()).isEqualTo(3);

    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(firstDefinition.getId());
    changeActivityDuration(processInstanceDto, 20.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(firstDefinition.getId());
    changeActivityDuration(processInstanceDto, 30.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(firstDefinition.getId());
    changeActivityDuration(processInstanceDto, 50.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    changeActivityDuration(processInstanceDto, 120.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    changeActivityDuration(processInstanceDto, 100.);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData =
      createReport(
        latestDefinition.getKey(),
        ImmutableList.of(firstDefinition.getVersionAsString(), latestDefinition.getVersionAsString())
      );
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData()).hasSize(3);
    assertThat(result.getEntryForKey(SERVICE_TASK_ID).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(20., 30., 50., 120., 100.));
  }

  @Test
  public void reportEvaluationSingleBucketFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = newArrayList(tenantId1);
    final String processKey = deployAndStartMultiTenantSimpleServiceTaskProcess(
      newArrayList(null, tenantId1, tenantId2)
    );

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(processKey, ALL_VERSIONS);
    reportData.setTenantIds(selectedTenants);
    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo((long) selectedTenants.size());
  }

  @Test
  public void orderOfTenantSelectionDoesNotAffectResult() {
    // given
    final String definitionKey = "aKey";
    final String noneTenantId = TenantService.TENANT_NOT_DEFINED.getId();
    final String otherTenantId = "tenant1";

    engineIntegrationExtension.createTenant(otherTenantId);

    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(definitionKey), noneTenantId);

    importAllEngineEntitiesFromScratch();

    List<String> tenantListNoneTenantFirst = Lists.newArrayList(noneTenantId, otherTenantId);
    List<String> tenantListOtherTenantFirst = Lists.newArrayList(otherTenantId, noneTenantId);

    // when
    final ReportMapResultDto resultNoneTenantFirst =
      getReportEvaluationResult(definitionKey, ALL_VERSIONS, tenantListNoneTenantFirst);
    final ReportMapResultDto resultOtherTenantFirst =
      getReportEvaluationResult(definitionKey, ALL_VERSIONS, tenantListOtherTenantFirst);

    // then
    assertThat(resultNoneTenantFirst.getData()).isNotEmpty();
    assertThat(resultOtherTenantFirst.getData()).isEqualTo(resultNoneTenantFirst.getData());
  }

  @SneakyThrows
  @Test
  public void reportEvaluationForSharedDefinitionAndInstancesOnSpecificTenants() {
    // given
    final String definitionKey = "aKey";
    final String tenantId1 = "tenantId1";
    final String noneTenantId = TenantService.TENANT_NOT_DEFINED.getId();
    engineIntegrationExtension.createTenant(tenantId1);

    final BpmnModelInstance modelInstance = getSimpleBpmnDiagram(definitionKey);

    // To create specific tenant instances with a shared def, start instance on noneTenant and update tenantID after
    ProcessInstanceEngineDto instance1 = engineIntegrationExtension.deployAndStartProcess(modelInstance, noneTenantId);
    engineDatabaseExtension.changeProcessInstanceTenantId(instance1.getId(), tenantId1);

    changeActivityDuration(instance1, 10.);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(definitionKey, ALL_VERSIONS);
    reportData.setTenantIds(newArrayList(tenantId1));
    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getData()).isNotEmpty();
    assertThat(result.getEntryForKey(START_EVENT).get().getValue()).isEqualTo(10.);
    assertThat(result.getEntryForKey(END_EVENT).get().getValue()).isEqualTo(10.);
  }

  @Test
  public void otherProcessDefinitionsDoNotInfluenceResult() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessDefinition();
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleServiceTaskProcessDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, 80.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, 40.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, 120.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition2.getId());
    changeActivityDuration(processInstanceDto, 20.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition2.getId());
    changeActivityDuration(processInstanceDto, 100.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition2.getId());
    changeActivityDuration(processInstanceDto, 1000.);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData1 = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse1 =
      reportClient.evaluateMapReport(reportData1);
    ProcessReportDataDto reportData2 = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition2);
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse2 =
      reportClient.evaluateMapReport(reportData2);

    // then
    final ReportMapResultDto result1 = evaluationResponse1.getResult();
    assertThat(result1.getData()).hasSize(3);
    assertThat(result1.getEntryForKey(SERVICE_TASK_ID).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(80., 40., 120.));
    final ReportMapResultDto result2 = evaluationResponse2.getResult();
    assertThat(result2.getData()).hasSize(3);
    assertThat(result2.getEntryForKey(SERVICE_TASK_ID).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(20., 100., 1000.));
  }

  @Test
  public void evaluateReportWithIrrationalAverageNumberAsResult() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, 100.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, 300.);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, 600.);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    final Map<AggregationType, ReportMapResultDto> results = evaluateMapReportForAllAggTypes(
      reportData);


    // then
    assertDurationMapReportResults(results, new Double[]{100., 300., 600.});
  }

  @Test
  public void noEventMatchesReturnsEmptyResult() {

    // when
    ProcessReportDataDto reportData =
      createReport("nonExistingProcessDefinitionId", "1");
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    assertThat(evaluationResponse.getResult().getData()).isEmpty();
  }

  @Test
  public void evaluateReportWithFlowNodeStatusRunning() {
    // given
    OffsetDateTime now = DateCreationFreezer.dateFreezer().freezeDateAndReturn();

    ProcessDefinitionEngineDto processDefinition = deploySimpleUserTaskDefinition();
    ProcessInstanceEngineDto runningInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineDatabaseExtension.changeActivityInstanceStartDate(
      runningInstance.getId(),
      USER_TASK_1,
      now.minus(200L, ChronoUnit.MILLIS)
    );
    final ProcessInstanceEngineDto completeInstance = engineIntegrationExtension.startProcessInstance(
      runningInstance.getDefinitionId());
    engineIntegrationExtension.finishAllRunningUserTasks(completeInstance.getId());

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData =
      createReport(
        processDefinition.getKey(),
        processDefinition.getVersionAsString()
      );
    reportData.setFilter(ProcessFilterBuilder.filter().runningFlowNodesOnly().filterLevel(VIEW).add().buildList());
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(result.getData()).hasSize(1);
    assertThat(result.getEntryForKey(USER_TASK_1)).isPresent().get().extracting(MapResultEntryDto::getValue)
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(200.));
  }

  @Test
  public void evaluateReportWithFlowNodeStatusCanceled() {
    // given
    OffsetDateTime now = DateCreationFreezer.dateFreezer().freezeDateAndReturn();

    ProcessDefinitionEngineDto processDefinition = deploySimpleUserTaskDefinition();
    ProcessInstanceEngineDto instanceWithCanceledActivity =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.cancelActivityInstance(instanceWithCanceledActivity.getId(), USER_TASK_1);
    changeActivityDuration(instanceWithCanceledActivity, USER_TASK_1, 100.);
    final ProcessInstanceEngineDto completeInstance = engineIntegrationExtension.startProcessInstance(
      instanceWithCanceledActivity.getDefinitionId());
    engineIntegrationExtension.finishAllRunningUserTasks(completeInstance.getId());

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData =
      createReport(
        processDefinition.getKey(),
        processDefinition.getVersionAsString()
      );
    reportData.setFilter(ProcessFilterBuilder.filter().canceledFlowNodesOnly().filterLevel(VIEW).add().buildList());
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(result.getData()).hasSize(1);
    assertThat(result.getEntryForKey(USER_TASK_1)).isPresent().get().extracting(MapResultEntryDto::getValue)
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(100.));
  }

  @Test
  public void evaluateReportWithFlowNodeStatusCompletedOrCanceled() {
    // given
    OffsetDateTime now = DateCreationFreezer.dateFreezer().freezeDateAndReturn();

    ProcessDefinitionEngineDto processDefinition = deploySimpleUserTaskDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, START_EVENT, 100.);
    engineIntegrationExtension.cancelActivityInstance(processInstanceDto.getId(), USER_TASK_1);
    changeActivityDuration(processInstanceDto, USER_TASK_1, 200.);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData =
      createReport(
        processDefinition.getKey(),
        processDefinition.getVersionAsString()
      );
    reportData.setFilter(
      ProcessFilterBuilder.filter().completedOrCanceledFlowNodesOnly().filterLevel(VIEW).add().buildList());
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1L);
    assertThat(result.getData()).hasSize(2);
    assertThat(result.getEntryForKey(START_EVENT)).isPresent().get().extracting(MapResultEntryDto::getValue)
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(100.));
    assertThat(result.getEntryForKey(USER_TASK_1)).isPresent().get().extracting(MapResultEntryDto::getValue)
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(200.));
  }

  @Test
  public void processDefinitionContainsMultiInstanceBody() throws Exception {
    // given
    // @formatter:off
    BpmnModelInstance subProcess = Bpmn.createExecutableProcess("subProcess")
        .startEvent()
          .serviceTask(SERVICE_TASK_ID)
            .camundaExpression("${true}")
        .endEvent()
        .done();

    BpmnModelInstance miProcess = Bpmn.createExecutableProcess("miProcess")
        .name("MultiInstance")
          .startEvent("miStart")
          .callActivity("callActivity")
            .calledElement("subProcess")
            .camundaIn("activityDuration", "activityDuration")
            .multiInstance()
              .cardinality("2")
            .multiInstanceDone()
          .endEvent("miEnd")
        .done();
    // @formatter:on
    ProcessDefinitionEngineDto subProcessDefinition = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      subProcess);
    String processDefinitionId = engineIntegrationExtension.deployProcessAndGetId(miProcess);
    engineIntegrationExtension.startProcessInstance(processDefinitionId);
    engineDatabaseExtension.changeActivityDurationForProcessDefinition(subProcessDefinition.getId(), 10L);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData =
      createReport(subProcessDefinition.getKey(), subProcessDefinition.getVersionAsString());
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData()).hasSize(3);
    assertThat(getExecutedFlowNodeCount(result)).isEqualTo(3L);
    assertThat(result.getEntryForKey(SERVICE_TASK_ID).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(10.));
  }

  @Test
  public void evaluateReportForMoreThanTenEvents() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessDefinition();

    ProcessInstanceEngineDto processInstanceDto;
    for (int i = 0; i < 11; i++) {
      processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
      changeActivityDuration(processInstanceDto, 10.);
    }
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData()).hasSize(3);
    Double[] durationSet = new Double[11];
    Arrays.fill(durationSet, 10.);
    assertThat(result.getEntryForKey(SERVICE_TASK_ID)).isPresent().get().extracting(MapResultEntryDto::getValue)
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(durationSet));
  }

  @Test
  public void resultContainsNonExecutedFlowNodes() {
    // given
    ProcessInstanceEngineDto engineDto =
      engineIntegrationExtension.deployAndStartProcess(BpmnModels.getSingleUserTaskDiagram());

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      engineDto.getProcessDefinitionKey(),
      engineDto.getProcessDefinitionVersion()
    );
    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData()).hasSize(3);
    Double notExecutedFlowNodeResult = result.getData()
      .stream()
      .filter(r -> r.getKey().equals("endEvent"))
      .findFirst().get().getValue();
    assertThat(notExecutedFlowNodeResult).isNull();
  }

  @Test
  public void filterInReport() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessDefinition();
    ProcessInstanceEngineDto processInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstance, 10.);
    OffsetDateTime past = engineIntegrationExtension.getHistoricProcessInstance(processInstance.getId()).getStartTime();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    reportData.setFilter(createStartDateFilter(null, past.minusSeconds(1L)));
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).hasSize(3);
    assertThat(getExecutedFlowNodeCount(result)).isEqualTo(0L);

    // when
    reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    reportData.setFilter(createStartDateFilter(past, null));
    evaluationResponse = reportClient.evaluateMapReport(reportData);

    // then
    result = evaluationResponse.getResult();
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).hasSize(3);
    assertThat(result.getEntryForKey(SERVICE_TASK_ID)).isPresent().get().extracting(MapResultEntryDto::getValue)
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(10.));
  }

  @Test
  public void viewLevelFlowNodeDurationFilterOnlyIncludesFlowNodesMatchingFilter() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessDefinition();
    ProcessInstanceEngineDto firstInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(firstInstance, 5000.);
    ProcessInstanceEngineDto secondInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(secondInstance, 10000.);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);

    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .flowNodeDuration()
        .flowNode(START_EVENT_ID, durationFilterData(DurationFilterUnit.SECONDS, 10L, LESS_THAN))
        .filterLevel(VIEW)
        .add()
        .buildList());
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(result.getData()).isNotNull().hasSize(1);
    assertThat(result.getEntryForKey(START_EVENT)).isPresent().get().extracting(MapResultEntryDto::getValue)
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(5000.));
  }

  @ParameterizedTest
  @MethodSource("viewLevelFilters")
  public void viewLevelFiltersOnlyAppliedToInstances(final List<ProcessFilterDto<?>> filtersToApply) {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    reportData.setFilter(filtersToApply);
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isZero();
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1L);
  }

  @Test
  public void optimizeExceptionOnViewEntityIsNull() {
    // given
    ProcessReportDataDto dataDto =
      createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getView().setEntity(null);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    ProcessReportDataDto dataDto =
      createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getView().setProperty(null);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    ProcessReportDataDto dataDto =
      createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getGroupBy().setType(null);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  private ProcessDefinitionEngineDto deploySimpleServiceTaskProcessDefinition() {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(BpmnModels.getSingleServiceTaskProcess(
      "aProcess",
      SERVICE_TASK_ID
    ));
  }

  private ProcessDefinitionEngineDto deploySimpleUserTaskDefinition() {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(BpmnModels.getSingleUserTaskDiagram());
  }

  private ProcessReportDataDto getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(ProcessDefinitionEngineDto processDefinition) {
    return createReport(processDefinition.getKey(), processDefinition.getVersionAsString());
  }


  private long getExecutedFlowNodeCount(ReportMapResultDto resultList) {
    return resultList.getData()
      .stream()
      .map(MapResultEntryDto::getValue)
      .filter(Objects::nonNull)
      .count();
  }

  private ProcessDefinitionEngineDto deployProcessWithTwoTasks() {
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .serviceTask(SERVICE_TASK_ID)
        .camundaExpression("${true}")
      .serviceTask(SERVICE_TASK_ID_2)
        .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    // @formatter:on
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private Map<AggregationType, ReportMapResultDto> evaluateMapReportForAllAggTypes(final ProcessReportDataDto reportData) {

    Map<AggregationType, ReportMapResultDto> resultsMap =
      new HashMap<>();
    aggregationTypes.forEach((AggregationType aggType) -> {
      reportData.getConfiguration().setAggregationType(aggType);
      resultsMap.put(aggType, reportClient.evaluateMapReport(reportData).getResult());
    });
    return resultsMap;
  }

  private ProcessReportDataDto createReport(String definitionKey, String definitionVersion) {
    return createReport(definitionKey, ImmutableList.of(definitionVersion), Collections.singletonList(null));
  }

  private ProcessReportDataDto createReport(String definitionKey, String definitionVersion, List<String> tenantIds) {
    return createReport(definitionKey, ImmutableList.of(definitionVersion), tenantIds);
  }

  private ProcessReportDataDto createReport(String definitionKey, List<String> definitionVersions) {
    return createReport(definitionKey, definitionVersions, Collections.singletonList(null));
  }

  private ProcessReportDataDto createReport(String definitionKey, List<String> definitionVersions,
                                            List<String> tenantIds) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(definitionKey)
      .setProcessDefinitionVersions(definitionVersions)
      .setTenantIds(tenantIds)
      .setReportDataType(ProcessReportDataType.FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE)
      .build();
  }

  @SneakyThrows
  private void changeActivityDuration(final ProcessInstanceEngineDto processInstanceDto,
                                      final String serviceTaskId,
                                      final Double durationInMs) {
    engineDatabaseExtension.changeActivityDuration(processInstanceDto.getId(), serviceTaskId, durationInMs.longValue());
  }

  private ReportMapResultDto getReportEvaluationResult(final String definitionKey,
                                                       final String version,
                                                       final List<String> tenantIds) {
    ProcessReportDataDto reportData = createReport(
      definitionKey,
      version,
      tenantIds
    );
    return reportClient.evaluateMapReport(reportData).getResult();
  }

  private List<ProcessFilterDto<?>> createStartDateFilter(OffsetDateTime startDate, OffsetDateTime endDate) {
    return ProcessFilterBuilder.filter().fixedStartDate().start(startDate).end(endDate).add().buildList();
  }

}
