/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.flownode.frequency.groupby.date.distributedby.flownode;

import com.google.common.collect.ImmutableMap;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.single.ModelElementFrequencyByModelElementDateByModelElementReportEvaluationIT;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.util.DateCreationFreezer;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.LESS_THAN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.NOT_IN;
import static org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnitMapper.mapToChronoUnit;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_PASSWORD;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.DateCreationFreezer.dateFreezer;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.camunda.optimize.util.BpmnModels.SERVICE_TASK_ID_1;
import static org.camunda.optimize.util.BpmnModels.SERVICE_TASK_ID_2;

public abstract class FlowNodeFrequencyByFlowNodeDateByFlowNodeReportEvaluationIT
  extends ModelElementFrequencyByModelElementDateByModelElementReportEvaluationIT {

  @Test
  public void reportEvaluationForOneProcessDefinition() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processDefinition.getKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).containsExactly(processDefinition.getVersionAsString());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.FLOW_NODE);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.FREQUENCY);
    assertThat(resultReportDataDto.getGroupBy()).isNotNull();
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(getGroupByType());
    assertThat(resultReportDataDto.getGroupBy().getValue())
      .extracting(DateGroupByValueDto.class::cast)
      .extracting(DateGroupByValueDto::getUnit)
      .isEqualTo(AggregateByDateUnit.DAY);
    assertThat(resultReportDataDto.getDistributedBy().getType()).isEqualTo(DistributedByType.FLOW_NODE);

    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = evaluationResponse.getResult();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(localDateTimeToString(startOfToday))
          .distributedByContains(END_EVENT, 1., END_EVENT)
          .distributedByContains(START_EVENT, 1., START_EVENT)
          .distributedByContains(USER_TASK_1, 1., USER_TASK_1_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForMultipleProcessDefinitions() {
    // given
    final String key1 = "key1";
    final String key2 = "key2";
    // freeze date to avoid instability when test runs on the edge of the day
    final OffsetDateTime now = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    final ProcessDefinitionEngineDto processDefinition1 = engineIntegrationExtension
      .deployProcessAndGetProcessDefinition(BpmnModels.getSingleServiceTaskProcess(key1, SERVICE_TASK_ID_1));
    engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    final ProcessDefinitionEngineDto processDefinition2 = engineIntegrationExtension
      .deployProcessAndGetProcessDefinition(BpmnModels.getSingleServiceTaskProcess(key2, SERVICE_TASK_ID_2));
    engineIntegrationExtension.startProcessInstance(processDefinition2.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition1);
    reportData.getDefinitions().add(createReportDataDefinitionDto(key2));
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = evaluationResponse.getResult();
    ZonedDateTime startOfToday = truncateToStartOfUnit(now, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.FREQUENCY)
      .groupByContains(localDateTimeToString(startOfToday))
      .distributedByContains(END_EVENT, 2., END_EVENT)
      .distributedByContains(SERVICE_TASK_ID_1, 1., SERVICE_TASK_ID_1)
      .distributedByContains(SERVICE_TASK_ID_2, 1., SERVICE_TASK_ID_2)
      .distributedByContains(START_EVENT, 2., START_EVENT)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void simpleReportEvaluationById() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();

    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setData(reportData);
    final String reportId = reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReportById(reportId);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = evaluationResponse.getResult();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(localDateTimeToString(startOfToday))
          .distributedByContains(END_EVENT, 1., END_EVENT)
          .distributedByContains(START_EVENT, 1., START_EVENT)
          .distributedByContains(USER_TASK_1, 1., USER_TASK_1_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void resultContainsAlsoNotExecutedUserTasks() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    // @formatter:off
    final String splittingGatewayId = "splittingGateway";
    final String mergingGatewayId = "mergingGateway";
    final String splittingGatewayLabel = "Should we go to task 1?";
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .exclusiveGateway(splittingGatewayId)
        .name(splittingGatewayLabel)
        .condition("yes", "${goToTask1}")
        .userTask(USER_TASK_1)
          .name(USER_TASK_1_NAME)
      .exclusiveGateway(mergingGatewayId)
      .endEvent(END_EVENT)
      .moveToNode(splittingGatewayId)
        .condition("no", "${!goToTask1}")
        .userTask(USER_TASK_2)
          .name(USER_TASK_2_NAME)
        .connectTo(mergingGatewayId)
      .done();
    // @formatter:on
    final ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), ImmutableMap.of("goToTask1", true));
    engineIntegrationExtension.finishAllRunningUserTasks();

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(groupedByDayDateAsString(referenceDate))
          .distributedByContains(END_EVENT, 1., END_EVENT)
          .distributedByContains(mergingGatewayId, 1., mergingGatewayId)
          .distributedByContains(splittingGatewayId, 1., splittingGatewayLabel)
          .distributedByContains(START_EVENT, 1., START_EVENT)
          .distributedByContains(USER_TASK_1, 1., USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void resultIsSortedInDescendingOrder() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployStartEndDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeModelElementDate(processInstance1, START_EVENT, referenceDate.minusDays(3));
    changeModelElementDate(processInstance1, END_EVENT, referenceDate.minusDays(1));

    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeModelElementDate(processInstance2, START_EVENT, referenceDate.minusDays(2));
    changeModelElementDate(processInstance2, END_EVENT, referenceDate.minusDays(4));

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(4)))
          .distributedByContains(END_EVENT, 1., END_EVENT)
          .distributedByContains(START_EVENT, null, START_EVENT)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(3)))
          .distributedByContains(END_EVENT, null, END_EVENT)
          .distributedByContains(START_EVENT, 1., START_EVENT)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(2)))
          .distributedByContains(END_EVENT, null, END_EVENT)
          .distributedByContains(START_EVENT, 1., START_EVENT)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(1)))
          .distributedByContains(END_EVENT, 1., END_EVENT)
          .distributedByContains(START_EVENT, null, START_EVENT)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployStartEndDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeModelElementDate(processInstance1, START_EVENT, referenceDate.minusDays(3));
    changeModelElementDate(processInstance1, END_EVENT, referenceDate.minusDays(1));

    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeModelElementDate(processInstance2, START_EVENT, referenceDate.minusDays(2));
    changeModelElementDate(processInstance2, END_EVENT, referenceDate.minusDays(4));

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.DESC));
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(1)))
          .distributedByContains(END_EVENT, 1., END_EVENT)
          .distributedByContains(START_EVENT, null, START_EVENT)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(2)))
          .distributedByContains(END_EVENT, null, END_EVENT)
          .distributedByContains(START_EVENT, 1., START_EVENT)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(3)))
          .distributedByContains(END_EVENT, null, END_EVENT)
          .distributedByContains(START_EVENT, 1., START_EVENT)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(4)))
          .distributedByContains(END_EVENT, 1., END_EVENT)
          .distributedByContains(START_EVENT, null, START_EVENT)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void testCustomOrderOnResultValueIsApplied() {
    // given

    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployStartEndDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeModelElementDate(processInstance1, START_EVENT, referenceDate.minusDays(3));
    changeModelElementDate(processInstance1, END_EVENT, referenceDate.minusDays(1));

    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeModelElementDate(processInstance2, START_EVENT, referenceDate.minusDays(2));
    changeModelElementDate(processInstance2, END_EVENT, referenceDate.minusDays(2));

    ProcessInstanceEngineDto processInstance3 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeModelElementDate(processInstance3, START_EVENT, referenceDate.minusDays(3));
    changeModelElementDate(processInstance3, END_EVENT, referenceDate.minusDays(3));

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_VALUE, SortOrder.DESC));
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(3L)
      .processInstanceCountWithoutFilters(3L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(1)))
          .distributedByContains(END_EVENT, 1., END_EVENT)
          .distributedByContains(START_EVENT, null, START_EVENT)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(2)))
          .distributedByContains(END_EVENT, 1., END_EVENT)
          .distributedByContains(START_EVENT, 1., START_EVENT)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(3)))
          .distributedByContains(END_EVENT, 1., END_EVENT)
          .distributedByContains(START_EVENT, 2., START_EVENT)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void flowNodesStartedAtSameIntervalAreGroupedTogether() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployStartEndDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeModelElementDate(processInstance1, START_EVENT, referenceDate.minusDays(1));
    changeModelElementDate(processInstance1, END_EVENT, referenceDate.minusDays(2));

    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeModelElementDate(processInstance2, START_EVENT, referenceDate.minusDays(1));
    changeModelElementDate(processInstance2, END_EVENT, referenceDate.minusDays(2));

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(2)))
          .distributedByContains(END_EVENT, 2., END_EVENT)
          .distributedByContains(START_EVENT, null, START_EVENT)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(1)))
          .distributedByContains(END_EVENT, null, END_EVENT)
          .distributedByContains(START_EVENT, 2., START_EVENT)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void emptyIntervalBetweenTwoUserTaskDates() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployStartEndDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeModelElementDate(processInstance1, START_EVENT, referenceDate.minusDays(1));
    changeModelElementDate(processInstance1, END_EVENT, referenceDate.minusDays(3));

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(3)))
          .distributedByContains(END_EVENT, 1., END_EVENT)
          .distributedByContains(START_EVENT, null, START_EVENT)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(2)))
          .distributedByContains(END_EVENT, null, END_EVENT)
          .distributedByContains(START_EVENT, null, START_EVENT)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(1)))
          .distributedByContains(END_EVENT, null, END_EVENT)
          .distributedByContains(START_EVENT, 1., START_EVENT)
      .doAssert(result);
    // @formatter:on
  }

  @ParameterizedTest
  @MethodSource("staticAggregateByDateUnits")
  public void countGroupByDateUnit(final AggregateByDateUnit groupByDateUnit) {
    // given
    final ChronoUnit groupByUnitAsChrono = mapToChronoUnit(groupByDateUnit);
    final int groupingCount = 5;
    OffsetDateTime referenceDate = OffsetDateTime.now();

    ProcessDefinitionEngineDto processDefinition = deployStartEndDefinition();
    List<ProcessInstanceEngineDto> processInstanceDtos = IntStream.range(0, groupingCount)
      .mapToObj(i -> {
        ProcessInstanceEngineDto processInstanceEngineDto =
          engineIntegrationExtension.startProcessInstance(processDefinition.getId());
        processInstanceEngineDto.setProcessDefinitionKey(processDefinition.getKey());
        processInstanceEngineDto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
        return processInstanceEngineDto;
      })
      .collect(Collectors.toList());
    updateUserTaskTime(processInstanceDtos, referenceDate, groupByUnitAsChrono);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, groupByDateUnit);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    HyperMapAsserter.GroupByAdder groupByAdder = HyperMapAsserter.asserter()
      .processInstanceCount(groupingCount)
      .processInstanceCountWithoutFilters(groupingCount)
      .measure(ViewProperty.FREQUENCY)
      .groupByContains(groupedByDateAsString(referenceDate.plus(0, groupByUnitAsChrono), groupByUnitAsChrono))
      .distributedByContains(END_EVENT, 1., END_EVENT)
      .distributedByContains(START_EVENT, 1., START_EVENT);

    for (int i = 1; i < groupingCount; i++) {
      groupByAdder = groupByAdder
        .groupByContains(groupedByDateAsString(referenceDate.plus(i, groupByUnitAsChrono), groupByUnitAsChrono))
        .distributedByContains(END_EVENT, 1., END_EVENT)
        .distributedByContains(START_EVENT, 1., START_EVENT);
    }
    groupByAdder.doAssert(result);
  }

  @Test
  public void otherProcessDefinitionsDoNotAffectResult() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition1 = deployStartEndDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    changeModelElementDate(processInstance1, START_EVENT, referenceDate.minusDays(1));
    changeModelElementDate(processInstance1, END_EVENT, referenceDate.minusDays(1));

    ProcessDefinitionEngineDto processDefinition2 = deployStartEndDefinition();
    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition2.getId());
    changeModelElementDate(processInstance2, START_EVENT, referenceDate.minusDays(1));
    changeModelElementDate(processInstance2, END_EVENT, referenceDate.minusDays(1));

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition1);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(1)))
          .distributedByContains(END_EVENT, 1., END_EVENT)
          .distributedByContains(START_EVENT, 1., START_EVENT)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void filterWorks() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final List<ProcessFilterDto<?>> processFilterDtoList = ProcessFilterBuilder.filter()
      .completedInstancesOnly().add().buildList();
    reportData.setFilter(processFilterDtoList);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(groupedByDayDateAsString(referenceDate))
          .distributedByContains(END_EVENT, 1., END_EVENT)
          .distributedByContains(START_EVENT, 1., START_EVENT)
          .distributedByContains(USER_TASK_1, 1., USER_TASK_1_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void viewLevelFlowNodeDurationFilterOnlyIncludesFlowNodesMatchingFilter() {
    // given
    final OffsetDateTime referenceDate = LocalDateUtil.getCurrentDateTime();
    ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    final ProcessInstanceEngineDto firstInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeActivityDuration(firstInstance, 5000.);
    final ProcessInstanceEngineDto secondInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeActivityDuration(secondInstance, 10000.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .flowNodeDuration()
        .flowNode(USER_TASK_1, durationFilterData(DurationUnit.SECONDS, 10L, LESS_THAN))
        .filterLevel(FilterApplicationLevel.VIEW)
        .add()
        .buildList());
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(groupedByDayDateAsString(referenceDate))
          .distributedByContains(END_EVENT, null, END_EVENT)
          .distributedByContains(START_EVENT, null, START_EVENT)
          .distributedByContains(USER_TASK_1, 1., USER_TASK_1_NAME)
      .doAssert(result);
    // @formatter:on
  }

  private static Stream<Arguments> viewLevelAssigneeFilterScenarios() {
    return Stream.of(
      Arguments.of(IN,
        new String[]{SECOND_USER},
        Map.of(USER_TASK_2, 1.)
      ),
      Arguments.of(IN,
        new String[]{DEFAULT_USERNAME, SECOND_USER, null},
        Map.of(USER_TASK_1, 1., USER_TASK_2, 1., USER_TASK_3, 1.)
      ),
      Arguments.of(NOT_IN,
        new String[]{SECOND_USER},
        Map.of(USER_TASK_1, 1., USER_TASK_3, 1.)
      ),
      Arguments.of(NOT_IN,
        new String[]{DEFAULT_USERNAME, SECOND_USER},
        Map.of(USER_TASK_3, 1.)
      )
    );
  }

  @ParameterizedTest
  @MethodSource("viewLevelAssigneeFilterScenarios")
  public void viewLevelAssigneeFilterOnlyIncludesFlowNodesMatchingFilter(final MembershipFilterOperator filterOperator,
                                                                         final String[] filterValues,
                                                                         final Map<String, Double> expectedResults) {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn().truncatedTo(ChronoUnit.DAYS);
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

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .assignee()
        .ids(filterValues)
        .operator(filterOperator)
        .filterLevel(FilterApplicationLevel.VIEW)
        .add()
        .buildList());
    // set sorting to allow asserting in the same order for all scenarios
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.ASC));
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result =
      reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
      .groupByContains(groupedByDayDateAsString(referenceDate))
        .distributedByContains(USER_TASK_1, expectedResults.getOrDefault(USER_TASK_1, null), USER_TASK_1_NAME)
        .distributedByContains(USER_TASK_2, expectedResults.getOrDefault(USER_TASK_2, null), USER_TASK_2_NAME)
        .distributedByContains(USER_TASK_3, expectedResults.getOrDefault(USER_TASK_3, null), USER_TASK_3_NAME)
      .doAssert(result);
    // @formatter:on
  }

  private static Stream<Arguments> viewLevelCandidateGroupFilterScenarios() {
    return Stream.of(
      Arguments.of(IN,
        new String[]{SECOND_CANDIDATE_GROUP_ID},
        Map.of(USER_TASK_2, 1.)
      ),
      Arguments.of(IN,
        new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID, null},
        Map.of(USER_TASK_1, 1., USER_TASK_2, 1., USER_TASK_3, 1.)
      ),
      Arguments.of(NOT_IN,
        new String[]{SECOND_CANDIDATE_GROUP_ID},
        Map.of(USER_TASK_1, 1., USER_TASK_3, 1.)
      ),
      Arguments.of(NOT_IN,
        new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID},
        Map.of(USER_TASK_3, 1.)
      )
    );
  }

  @ParameterizedTest
  @MethodSource("viewLevelCandidateGroupFilterScenarios")
  public void viewLevelCandidateGroupFilterOnlyIncludesFlowNodesMatchingFilter(final MembershipFilterOperator filterOperator,
                                                                               final String[] filterValues,
                                                                               final Map<String, Double> expectedResults) {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn().truncatedTo(ChronoUnit.DAYS);
    engineIntegrationExtension.createGroup(FIRST_CANDIDATE_GROUP_ID, FIRST_CANDIDATE_GROUP_NAME);
    engineIntegrationExtension.createGroup(SECOND_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_NAME);
    final ProcessDefinitionEngineDto processDefinition = deployThreeUserTasksDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .candidateGroups()
        .ids(filterValues)
        .operator(filterOperator)
        .filterLevel(FilterApplicationLevel.VIEW)
        .add()
        .buildList());
    // set sorting to allow asserting in the same order for all scenarios
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.ASC));
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result =
      reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.FREQUENCY)
      .groupByContains(groupedByDayDateAsString(referenceDate))
        .distributedByContains(USER_TASK_1, expectedResults.getOrDefault(USER_TASK_1, null), USER_TASK_1_NAME)
        .distributedByContains(USER_TASK_2, expectedResults.getOrDefault(USER_TASK_2, null), USER_TASK_2_NAME)
        .distributedByContains(USER_TASK_3, expectedResults.getOrDefault(USER_TASK_3, null), USER_TASK_3_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void automaticIntervalSelection_takesAllUserTasksIntoAccount() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployStartEndDefinition();
    ProcessInstanceEngineDto processInstanceDto1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    Map<String, OffsetDateTime> updates = new HashMap<>();
    OffsetDateTime startOfToday = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS);
    updates.put(processInstanceDto1.getId(), startOfToday);
    updates.put(processInstanceDto2.getId(), startOfToday.plusDays(2));
    updates.put(processInstanceDto3.getId(), startOfToday.plusDays(5));
    changeModelElementDates(updates);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, AggregateByDateUnit.AUTOMATIC);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    final List<HyperMapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
    assertFirstValueEquals(resultData, 1.);
    assertLastValueEquals(resultData, 1.);
    final int sumOfAllValues = resultData.stream()
      .map(HyperMapResultEntryDto::getValue)
      .flatMap(List::stream)
      .filter(Objects::nonNull)
      .map(MapResultEntryDto::getValue)
      .filter(Objects::nonNull)
      .mapToInt(Double::intValue).sum();
    assertThat(sumOfAllValues).isEqualTo(6);
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() {
    // given
    ProcessDefinitionEngineDto processDefinition1 = deployOneUserTaskDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();

    ProcessDefinitionEngineDto latestDefinition = deployTwoUserTasksDefinition();
    engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(latestDefinition);
    reportData.setProcessDefinitionVersion(ALL_VERSIONS);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(groupedByDayDateAsString(OffsetDateTime.now()))
          .distributedByContains(END_EVENT, 2., END_EVENT)
          .distributedByContains(START_EVENT, 2., START_EVENT)
          .distributedByContains(USER_TASK_1, 2., USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, 1., USER_TASK_2_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void multipleVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() {
    // given
    ProcessDefinitionEngineDto firstDefinition = deployOneUserTaskDefinition();
    engineIntegrationExtension.startProcessInstance(firstDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();

    ProcessDefinitionEngineDto latestDefinition = deployTwoUserTasksDefinition();
    engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(latestDefinition);
    reportData.setProcessDefinitionVersions(Arrays.asList(
      firstDefinition.getVersionAsString(),
      latestDefinition.getVersionAsString()
    ));
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(groupedByDayDateAsString(OffsetDateTime.now()))
          .distributedByContains(END_EVENT, 2., END_EVENT)
          .distributedByContains(START_EVENT, 2., START_EVENT)
          .distributedByContains(USER_TASK_1, 2., USER_TASK_1_NAME)
          .distributedByContains(USER_TASK_2, 1., USER_TASK_2_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasFewerNodes() {
    // given
    ProcessDefinitionEngineDto processDefinition1 = deployTwoUserTasksDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();

    ProcessDefinitionEngineDto latestDefinition = deployOneUserTaskDefinition();
    engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(latestDefinition);
    reportData.setProcessDefinitionVersion(ALL_VERSIONS);
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(groupedByDayDateAsString(OffsetDateTime.now()))
          .distributedByContains(END_EVENT, 2., END_EVENT)
          .distributedByContains(START_EVENT, 2., START_EVENT)
          .distributedByContains(USER_TASK_1, 2., USER_TASK_1_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void multipleVersionsRespectLatestNodesOnlyWhereLatestHasFewerNodes() {
    // given
    ProcessDefinitionEngineDto firstDefinition = deployTwoUserTasksDefinition();
    engineIntegrationExtension.startProcessInstance(firstDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();

    ProcessDefinitionEngineDto latestDefinition = deployOneUserTaskDefinition();
    engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(latestDefinition);
    reportData.setProcessDefinitionVersions(Arrays.asList(
      firstDefinition.getVersionAsString(),
      latestDefinition.getVersionAsString()
    ));
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> result = reportClient.evaluateHyperMapReport(reportData)
      .getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(groupedByDayDateAsString(OffsetDateTime.now()))
          .distributedByContains(END_EVENT, 2., END_EVENT)
          .distributedByContains(START_EVENT, 2., START_EVENT)
          .distributedByContains(USER_TASK_1, 2., USER_TASK_1_NAME)
      .doAssert(result);
    // @formatter:on
  }

}
