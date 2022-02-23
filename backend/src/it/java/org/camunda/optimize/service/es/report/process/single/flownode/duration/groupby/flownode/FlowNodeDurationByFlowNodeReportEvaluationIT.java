/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.flownode.duration.groupby.flownode;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.assertj.core.groups.Tuple;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.measure.MeasureResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.TenantService;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.es.report.util.MapResultAsserter;
import org.camunda.optimize.service.es.report.util.MapResultUtil;
import org.camunda.optimize.test.util.DateCreationFreezer;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.AVERAGE;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.LESS_THAN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.NOT_IN;
import static org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel.VIEW;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurations;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.camunda.optimize.test.util.DurationAggregationUtil.getSupportedAggregationTypes;
import static org.camunda.optimize.util.BpmnModels.START_EVENT_ID;
import static org.camunda.optimize.util.BpmnModels.getDoubleUserTaskDiagram;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;
import static org.camunda.optimize.util.BpmnModels.getTripleUserTaskDiagram;

public class FlowNodeDurationByFlowNodeReportEvaluationIT extends AbstractProcessDefinitionIT {

  private static final String PROCESS_DEFINITION_KEY = "123";
  private static final String START_EVENT = "startEvent";
  private static final String END_EVENT = "endEvent";
  private static final String SERVICE_TASK_ID = "aSimpleServiceTask";
  private static final String SERVICE_TASK_ID_2 = "aSimpleServiceTask2";

  @Test
  public void reportEvaluationForOneProcessInstance() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, 20.);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processDefinition.getKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processDefinition.getVersionAsString());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.FLOW_NODE);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.DURATION);
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(3);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), SERVICE_TASK_ID)).isPresent();
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), SERVICE_TASK_ID).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(20.));
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), START_EVENT)).isPresent();
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), START_EVENT).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(20.));
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), END_EVENT).isPresent()).isTrue();
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), END_EVENT).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(20.));
  }

  @Test
  public void reportEvaluationForSeveralProcessesInstances() {
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
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getFirstMeasureData()).hasSize(3);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), SERVICE_TASK_ID)).isPresent();
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), SERVICE_TASK_ID).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(10., 30., 20.));
  }

  @Test
  public void reportEvaluationForSeveralProcessDefinitions() {
    // given
    final String key1 = "key1";
    final String key2 = "key2";
    final ProcessDefinitionEngineDto processDefinition1 = engineIntegrationExtension
      .deployProcessAndGetProcessDefinition(BpmnModels.getSingleServiceTaskProcess(key1, SERVICE_TASK_ID));
    final ProcessInstanceEngineDto processInstanceDto1 =
      engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    changeActivityDuration(processInstanceDto1, 10.);
    final ProcessDefinitionEngineDto processDefinition2 = engineIntegrationExtension
      .deployProcessAndGetProcessDefinition(BpmnModels.getSingleServiceTaskProcess(key2, SERVICE_TASK_ID_2));
    final ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processDefinition2.getId());
    changeActivityDuration(processInstanceDto2, 30.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition1);
    reportData.getDefinitions().add(createReportDataDefinitionDto(processDefinition2.getKey()));
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    // @formatter:off
    MapResultAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, new AggregationDto(AVERAGE))
        .groupedByContains(SERVICE_TASK_ID, calculateExpectedValueGivenDurationsDefaultAggr(10.))
        .groupedByContains(SERVICE_TASK_ID_2, calculateExpectedValueGivenDurationsDefaultAggr(30.))
        .groupedByContains(END_EVENT, calculateExpectedValueGivenDurationsDefaultAggr(10., 30.))
        .groupedByContains(START_EVENT, calculateExpectedValueGivenDurationsDefaultAggr(10., 30.))
      .doAssert(result);
    // @formatter:on
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
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getFirstMeasureData().size()).isEqualTo(4);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), SERVICE_TASK_ID)).isPresent();
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), SERVICE_TASK_ID).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(100., 200., 900.));
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), SERVICE_TASK_ID_2).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(20., 10., 90.));
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
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    List<MapResultEntryDto> resultData = evaluationResponse.getResult().getFirstMeasureData();
    assertThat(resultData).hasSize(4);
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    assertThat(resultKeys).isSortedAccordingTo(Comparator.naturalOrder());
  }

  @Test
  public void testEvaluationResultForAllAggregationTypes() {
    // given
    final Double[] expectedDurations = {10., 30., 20.};
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, expectedDurations[0]);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, expectedDurations[1]);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, expectedDurations[2]);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    reportData.getConfiguration().setAggregationTypes(getSupportedAggregationTypes());
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getMeasures())
      .extracting(MeasureResponseDto::getAggregationType)
      .containsExactly(getSupportedAggregationTypes());
    result.getMeasures().forEach(measureResult -> {
      final List<MapResultEntryDto> measureData = measureResult.getData();
      assertThat(MapResultUtil.getEntryForKey(measureData, SERVICE_TASK_ID))
        .isPresent().get()
        .extracting(MapResultEntryDto::getValue)
        .isEqualTo(calculateExpectedValueGivenDurations(expectedDurations).get(measureResult.getAggregationType()));
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

    // when
    final ProcessReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_VALUE, SortOrder.ASC));
    reportData.getConfiguration().setAggregationTypes(getSupportedAggregationTypes());
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getMeasures())
      .extracting(MeasureResponseDto::getAggregationType)
      .containsExactly(getSupportedAggregationTypes());
    result.getMeasures().forEach(measureResult -> {
      final List<MapResultEntryDto> measureData = measureResult.getData();
      assertThat(measureData)
        .hasSize(4)
        .extracting(MapResultEntryDto::getValue)
        .isSortedAccordingTo(Comparator.naturalOrder());
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
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getFirstMeasureData()).hasSize(4);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), SERVICE_TASK_ID).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(20., 30., 50., 120., 100.));
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), SERVICE_TASK_ID_2).get().getValue())
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
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getFirstMeasureData()).hasSize(4);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), SERVICE_TASK_ID).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(20., 30., 50., 120., 100.));
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), SERVICE_TASK_ID_2).get().getValue())
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
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getFirstMeasureData()).hasSize(3);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), SERVICE_TASK_ID).get().getValue())
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
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getFirstMeasureData()).hasSize(3);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), SERVICE_TASK_ID).get().getValue())
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
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(selectedTenants.size());
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
    final ReportResultResponseDto<List<MapResultEntryDto>> resultNoneTenantFirst =
      getReportEvaluationResult(definitionKey, ALL_VERSIONS, tenantListNoneTenantFirst);
    final ReportResultResponseDto<List<MapResultEntryDto>> resultOtherTenantFirst =
      getReportEvaluationResult(definitionKey, ALL_VERSIONS, tenantListOtherTenantFirst);

    // then
    assertThat(resultNoneTenantFirst.getFirstMeasureData()).isNotEmpty();
    assertThat(resultOtherTenantFirst.getFirstMeasureData()).isEqualTo(resultNoneTenantFirst.getFirstMeasureData());
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
    engineDatabaseExtension.changeProcessInstanceAndActivitiesTenantId(instance1.getId(), tenantId1);

    changeActivityDuration(instance1, 10.);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(definitionKey, ALL_VERSIONS);
    reportData.setTenantIds(newArrayList(tenantId1));
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getFirstMeasureData()).isNotEmpty();
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), START_EVENT).get().getValue()).isEqualTo(10.);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), END_EVENT).get().getValue()).isEqualTo(10.);
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
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse1 =
      reportClient.evaluateMapReport(reportData1);
    ProcessReportDataDto reportData2 = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition2);
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse2 =
      reportClient.evaluateMapReport(reportData2);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result1 = evaluationResponse1.getResult();
    assertThat(result1.getFirstMeasureData()).hasSize(3);
    assertThat(MapResultUtil.getEntryForKey(result1.getFirstMeasureData(), SERVICE_TASK_ID).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(80., 40., 120.));
    final ReportResultResponseDto<List<MapResultEntryDto>> result2 = evaluationResponse2.getResult();
    assertThat(result2.getFirstMeasureData()).hasSize(3);
    assertThat(MapResultUtil.getEntryForKey(result2.getFirstMeasureData(), SERVICE_TASK_ID).get().getValue())
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(20., 100., 1000.));
  }

  @Test
  public void evaluateReportWithIrrationalAverageNumberAsResult() {
    // given
    final Double[] expectedDurations = {100., 300., 600.};
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, expectedDurations[0]);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, expectedDurations[1]);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeActivityDuration(processInstanceDto, expectedDurations[2]);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    reportData.getConfiguration().setAggregationTypes(getSupportedAggregationTypes());
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getMeasures())
      .extracting(MeasureResponseDto::getAggregationType)
      .containsExactly(getSupportedAggregationTypes());
    result.getMeasures().forEach(measureResult -> {
      final List<MapResultEntryDto> measureData = measureResult.getData();
      assertThat(MapResultUtil.getEntryForKey(measureData, SERVICE_TASK_ID))
        .isPresent().get()
        .extracting(MapResultEntryDto::getValue)
        .isEqualTo(calculateExpectedValueGivenDurations(expectedDurations).get(measureResult.getAggregationType()));
    });
  }

  @Test
  public void noEventMatchesReturnsEmptyResult() {

    // when
    ProcessReportDataDto reportData =
      createReport("nonExistingProcessDefinitionId", "1");
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    assertThat(evaluationResponse.getResult().getFirstMeasureData()).isEmpty();
  }

  @Test
  public void evaluateReportWithFlowNodeStatusRunning() {
    // given
    OffsetDateTime now = DateCreationFreezer.dateFreezer().freezeDateAndReturn();

    ProcessDefinitionEngineDto processDefinition = deploySimpleUserTaskDefinition();
    ProcessInstanceEngineDto runningInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineDatabaseExtension.changeFlowNodeStartDate(
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
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(result.getFirstMeasureData()).hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_1)).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(200.));
  }

  @Test
  public void evaluateReportWithFlowNodeStatusCompleted() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleUserTaskDefinition();
    ProcessInstanceEngineDto completedInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeActivityDuration(completedInstance, START_EVENT, 1000.);
    changeActivityDuration(completedInstance, USER_TASK_1, 2000.);
    changeActivityDuration(completedInstance, END_EVENT, 3000.);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData =
      createReport(
        processDefinition.getKey(),
        processDefinition.getVersionAsString()
      );
    reportData.setFilter(ProcessFilterBuilder.filter().completedFlowNodesOnly().filterLevel(VIEW).add().buildList());
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1L);
    assertThat(result.getFirstMeasureData()).hasSize(3);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), START_EVENT)).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(1000.));
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_1)).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(2000.));
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), END_EVENT)).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(3000.));
  }

  @Test
  public void evaluateReportWithFlowNodeStatusCanceled() {
    // given
    DateCreationFreezer.dateFreezer().freezeDateAndReturn();

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
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(result.getFirstMeasureData()).hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_1)).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(100.));
  }

  @Test
  public void evaluateReportWithFlowNodeStatusCompletedOrCanceled() {
    // given
    DateCreationFreezer.dateFreezer().freezeDateAndReturn();

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
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1L);
    assertThat(result.getFirstMeasureData()).hasSize(2);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), START_EVENT)).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(100.));
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_1)).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
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
    engineDatabaseExtension.changeFlowNodeTotalDurationForProcessDefinition(subProcessDefinition.getId(), 10L);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData =
      createReport(subProcessDefinition.getKey(), subProcessDefinition.getVersionAsString());
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getFirstMeasureData()).hasSize(3);
    assertThat(getExecutedFlowNodeCount(result)).isEqualTo(3L);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), SERVICE_TASK_ID).get().getValue())
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
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getFirstMeasureData()).hasSize(3);
    Double[] durationSet = new Double[11];
    Arrays.fill(durationSet, 10.);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), SERVICE_TASK_ID)).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
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
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).hasSize(3);
    Double notExecutedFlowNodeResult = result.getFirstMeasureData()
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
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(3);
    assertThat(getExecutedFlowNodeCount(result)).isEqualTo(0L);

    // when
    reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    reportData.setFilter(createStartDateFilter(past, null));
    evaluationResponse = reportClient.evaluateMapReport(reportData);

    // then
    result = evaluationResponse.getResult();
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(3);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), SERVICE_TASK_ID)).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(calculateExpectedValueGivenDurationsDefaultAggr(10.));
  }

  @ParameterizedTest
  @MethodSource("viewLevelAssigneeFilterScenarios")
  public void viewLevelFilterByAssigneeOnlyIncludesFlowNodesMatchingFilter(final MembershipFilterOperator filterOperator,
                                                                           final String[] filterValues,
                                                                           final List<Tuple> expectedResult) {
    // given
    engineIntegrationExtension.addUser(SECOND_USER, SECOND_USER_FIRST_NAME, SECOND_USER_LAST_NAME);
    engineIntegrationExtension.grantAllAuthorizations(SECOND_USER);
    final ProcessDefinitionEngineDto processDefinition = deployThreeUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceDto.getId()
    );
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER, SECOND_USERS_PASSWORD, processInstanceDto.getId()
    );
    engineIntegrationExtension.completeUserTaskWithoutClaim(processInstanceDto.getId());

    changeActivityDuration(processInstanceDto, START_EVENT, 10.);
    changeActivityDuration(processInstanceDto, USER_TASK_1, 20.);
    changeActivityDuration(processInstanceDto, USER_TASK_2, 30.);
    changeActivityDuration(processInstanceDto, USER_TASK_3, 40.);
    changeActivityDuration(processInstanceDto, END_EVENT, 50.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    final List<ProcessFilterDto<?>> assigneeFilter = ProcessFilterBuilder
      .filter().assignee().ids(filterValues).operator(filterOperator)
      .filterLevel(FilterApplicationLevel.VIEW)
      .add().buildList();
    reportData.setFilter(assigneeFilter);
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getFirstMeasureData())
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(expectedResult);
  }

  @ParameterizedTest
  @MethodSource("viewLevelCandidateGroupFilterScenarios")
  public void viewLevelFilterByCandidateGroupOnlyIncludesFlowNodesMatchingFilter(final MembershipFilterOperator filterOperator,
                                                                                 final String[] filterValues,
                                                                                 final List<Tuple> expectedResult) {
    // given
    engineIntegrationExtension.createGroup(FIRST_CANDIDATE_GROUP_ID, FIRST_CANDIDATE_GROUP_NAME);
    engineIntegrationExtension.createGroup(SECOND_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_NAME);
    final ProcessDefinitionEngineDto processDefinition = deployThreeUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeActivityDuration(processInstanceDto, START_EVENT, 10.);
    changeActivityDuration(processInstanceDto, USER_TASK_1, 20.);
    changeActivityDuration(processInstanceDto, USER_TASK_2, 30.);
    changeActivityDuration(processInstanceDto, USER_TASK_3, 40.);
    changeActivityDuration(processInstanceDto, END_EVENT, 50.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    final List<ProcessFilterDto<?>> assigneeFilter = ProcessFilterBuilder
      .filter().candidateGroups().ids(filterValues).operator(filterOperator)
      .filterLevel(FilterApplicationLevel.VIEW)
      .add().buildList();
    reportData.setFilter(assigneeFilter);
    // set sorting to allow asserting in the same order for all scenarios
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.ASC));
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getFirstMeasureData())
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(expectedResult);
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
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(result.getFirstMeasureData()).isNotNull().hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), START_EVENT)).isPresent()
      .get()
      .extracting(MapResultEntryDto::getValue)
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
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData)
      .getResult();

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
    dataDto.getView().setProperties((ViewProperty) null);

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

  private ProcessDefinitionEngineDto deployTwoUserTasksDefinition() {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getDoubleUserTaskDiagram());
  }

  private ProcessDefinitionEngineDto deployThreeUserTasksDefinition() {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getTripleUserTaskDiagram());
  }

  private ProcessReportDataDto getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(ProcessDefinitionEngineDto processDefinition) {
    return createReport(processDefinition.getKey(), processDefinition.getVersionAsString());
  }

  private long getExecutedFlowNodeCount(ReportResultResponseDto<List<MapResultEntryDto>> resultList) {
    return resultList.getFirstMeasureData()
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
      .setReportDataType(ProcessReportDataType.FLOW_NODE_DUR_GROUP_BY_FLOW_NODE)
      .build();
  }

  @SneakyThrows
  private void changeActivityDuration(final ProcessInstanceEngineDto processInstanceDto,
                                      final String serviceTaskId,
                                      final Double durationInMs) {
    engineDatabaseExtension.changeFlowNodeTotalDuration(
      processInstanceDto.getId(),
      serviceTaskId,
      durationInMs.longValue()
    );
  }

  private ReportResultResponseDto<List<MapResultEntryDto>> getReportEvaluationResult(final String definitionKey,
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
    return ProcessFilterBuilder.filter().fixedInstanceStartDate().start(startDate).end(endDate).add().buildList();
  }

  private static Stream<Arguments> viewLevelAssigneeFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN,
        new String[]{SECOND_USER},
        Collections.singletonList(Tuple.tuple(USER_TASK_2, 30.))
      ),
      Arguments.of(
        IN,
        new String[]{DEFAULT_USERNAME, SECOND_USER, null},
        List.of(
          Tuple.tuple(USER_TASK_1, 20.),
          Tuple.tuple(USER_TASK_2, 30.),
          Tuple.tuple(USER_TASK_3, 40.)
        )
      ),
      Arguments.of(
        NOT_IN,
        new String[]{SECOND_USER},
        List.of(
          Tuple.tuple(USER_TASK_1, 20.),
          Tuple.tuple(USER_TASK_3, 40.)
        )
      ),
      Arguments.of(
        NOT_IN,
        new String[]{DEFAULT_USERNAME, SECOND_USER},
        Collections.singletonList(
          Tuple.tuple(USER_TASK_3, 40.)
        )
      )
    );
  }

  private static Stream<Arguments> viewLevelCandidateGroupFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN,
        new String[]{SECOND_CANDIDATE_GROUP_ID},
        Collections.singletonList(Tuple.tuple(USER_TASK_2, 30.))
      ),
      Arguments.of(
        IN,
        new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID, null},
        List.of(
          Tuple.tuple(USER_TASK_1, 20.),
          Tuple.tuple(USER_TASK_2, 30.),
          Tuple.tuple(USER_TASK_3, 40.)
        )
      ),
      Arguments.of(
        NOT_IN,
        new String[]{SECOND_CANDIDATE_GROUP_ID},
        List.of(
          Tuple.tuple(USER_TASK_1, 20.),
          Tuple.tuple(USER_TASK_3, 40.)
        )
      ),
      Arguments.of(
        NOT_IN,
        new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID},
        Collections.singletonList(
          Tuple.tuple(USER_TASK_3, 40.)
        )
      )
    );
  }

}
