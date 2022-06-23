/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter.process;

import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.util.MapResultUtil;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.GREATER_THAN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.LESS_THAN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.LESS_THAN_EQUALS;
import static org.camunda.optimize.util.BpmnModels.USER_TASK_1;
import static org.camunda.optimize.util.BpmnModels.USER_TASK_2;

public class FlowNodeDurationFilterIT extends AbstractDurationFilterIT {

  @Test
  public void testSingleCompletedInstanceLevelFlowNodeDurationFilter() {
    // given
    final ProcessDefinitionEngineDto processDefinitionEngineDto = deployTwoUserTasksProcessDefinition();
    final ProcessInstanceEngineDto firstInstance = engineIntegrationExtension.startProcessInstance(
      processDefinitionEngineDto.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeFlowNodeDuration(firstInstance.getId(), USER_TASK_1, 20000);
    engineIntegrationExtension.startProcessInstance(processDefinitionEngineDto.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> greaterThanTen =
      reportClient.evaluateMapReport(createUserTaskReportWithFilters(
        processDefinitionEngineDto,
        ProcessFilterBuilder.filter()
          .flowNodeDuration()
          .flowNode(USER_TASK_1, filterData(DurationUnit.SECONDS, 10L, GREATER_THAN))
          .filterLevel(FilterApplicationLevel.INSTANCE)
          .add()
          .buildList()
      )).getResult();
    final ReportResultResponseDto<List<MapResultEntryDto>> lessThanEqualsTen =
      reportClient.evaluateMapReport(createUserTaskReportWithFilters(
        processDefinitionEngineDto,
        ProcessFilterBuilder.filter()
          .flowNodeDuration()
          .flowNode(USER_TASK_1, filterData(DurationUnit.SECONDS, 10L, LESS_THAN_EQUALS))
          .filterLevel(FilterApplicationLevel.INSTANCE)
          .add()
          .buildList()
      )).getResult();

    // then
    assertThat(greaterThanTen.getInstanceCount()).isEqualTo(1L);
    assertThat(greaterThanTen.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(greaterThanTen.getFirstMeasureData()).hasSize(2).extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(Arrays.asList(Tuple.tuple(USER_TASK_1, 1.), Tuple.tuple(USER_TASK_2, 1.)));
    assertThat(lessThanEqualsTen.getInstanceCount()).isEqualTo(1L);
    assertThat(lessThanEqualsTen.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(lessThanEqualsTen.getFirstMeasureData()).hasSize(2)
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(Arrays.asList(Tuple.tuple(USER_TASK_1, 1.), Tuple.tuple(USER_TASK_2, 1.)));
  }

  @Test
  public void testSingleCompletedViewLevelFlowNodeDurationFilter() {
    // given
    final ProcessDefinitionEngineDto definition = deployTwoUserTasksProcessDefinition();
    final ProcessInstanceEngineDto instance = engineIntegrationExtension.startProcessInstance(definition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeFlowNodeDuration(instance.getId(), USER_TASK_1, 20000);
    changeFlowNodeDuration(instance.getId(), USER_TASK_2, 5000);

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> greaterThanFilter = reportClient.evaluateMapReport(createUserTaskReportWithFilters(
      definition,
      ProcessFilterBuilder.filter()
        .flowNodeDuration()
        .flowNode(USER_TASK_1, filterData(DurationUnit.SECONDS, 10L, GREATER_THAN))
        .filterLevel(FilterApplicationLevel.VIEW)
        .add()
        .buildList()
    )).getResult();

    final ReportResultResponseDto<List<MapResultEntryDto>> lessThanFilter = reportClient.evaluateMapReport(createUserTaskReportWithFilters(
      definition,
      ProcessFilterBuilder.filter()
        .flowNodeDuration()
        .flowNode(USER_TASK_2, filterData(DurationUnit.SECONDS, 10L, LESS_THAN))
        .filterLevel(FilterApplicationLevel.VIEW)
        .add()
        .buildList()
    )).getResult();

    // then
    assertThat(greaterThanFilter.getInstanceCount()).isEqualTo(1L);
    assertThat(greaterThanFilter.getFirstMeasureData()).hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(greaterThanFilter.getFirstMeasureData(), USER_TASK_1)).isPresent().get()
      .extracting(MapResultEntryDto::getValue).isEqualTo(1.);

    assertThat(lessThanFilter.getInstanceCount()).isEqualTo(1L);
    assertThat(lessThanFilter.getFirstMeasureData()).hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(lessThanFilter.getFirstMeasureData(), USER_TASK_2)).isPresent().get()
      .extracting(MapResultEntryDto::getValue).isEqualTo(1.);
  }

  @Test
  public void testSingleRunningInstanceLevelFlowNodeDurationFilter() {
    // given
    final ProcessDefinitionEngineDto processDefinitionEngineDto = deployTwoUserTasksProcessDefinition();
    final OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    final ProcessInstanceEngineDto firstInstance = engineIntegrationExtension.startProcessInstance(
      processDefinitionEngineDto.getId());
    changeFlowNodeStartDate(firstInstance.getId(), USER_TASK_1, now.minusDays(1));
    engineIntegrationExtension.startProcessInstance(processDefinitionEngineDto.getId());

    importAllEngineEntitiesFromScratch();

    // when
    LocalDateUtil.setCurrentTime(now.plusSeconds(10));
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(createUserTaskReportWithFilters(
      processDefinitionEngineDto,
      ProcessFilterBuilder.filter()
        .flowNodeDuration()
        .flowNode(USER_TASK_1, filterData(DurationUnit.SECONDS, 9L, GREATER_THAN))
        .flowNode(USER_TASK_1, filterData(DurationUnit.SECONDS, 11L, LESS_THAN))
        .filterLevel(FilterApplicationLevel.INSTANCE)
        .add()
        .buildList()
    )).getResult();

    // then only the data from the second instance will be included in the result
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(result.getFirstMeasureData()).hasSize(2)
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(Arrays.asList(Tuple.tuple(USER_TASK_1, 1.), Tuple.tuple(USER_TASK_2, null)));
  }

  @Test
  public void testSingleRunningViewLevelFlowNodeDurationFilter() {
    // given
    final ProcessDefinitionEngineDto processDefinitionEngineDto = deployTwoUserTasksProcessDefinition();
    final OffsetDateTime now = OffsetDateTime.now();
    engineIntegrationExtension.startProcessInstance(processDefinitionEngineDto.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();

    importAllEngineEntitiesFromScratch();

    // when
    LocalDateUtil.setCurrentTime(now.plusSeconds(10));
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(createUserTaskReportWithFilters(
      processDefinitionEngineDto,
      ProcessFilterBuilder.filter()
        .flowNodeDuration()
        .flowNode(USER_TASK_2, filterData(DurationUnit.SECONDS, 9L, GREATER_THAN))
        .flowNode(USER_TASK_2, filterData(DurationUnit.SECONDS, 11L, LESS_THAN))
        .filterLevel(FilterApplicationLevel.VIEW)
        .add()
        .buildList()
    )).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getFirstMeasureData()).hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_2)).isPresent().get()
      .extracting(MapResultEntryDto::getValue).isEqualTo(1.);
  }

  @Test
  public void testInstanceLevelFlowNodeDurationFilterForMultipleFlowNodes() {
    // given
    final ProcessDefinitionEngineDto processDefinitionEngineDto = deployTwoUserTasksProcessDefinition();
    final ProcessInstanceEngineDto firstInstance = engineIntegrationExtension.startProcessInstance(
      processDefinitionEngineDto.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeFlowNodeDuration(firstInstance.getId(), USER_TASK_1, 20000);
    changeFlowNodeDuration(firstInstance.getId(), USER_TASK_2, 15000);
    engineIntegrationExtension.startProcessInstance(processDefinitionEngineDto.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> resultBothTasksDurationGreaterTen =
      reportClient.evaluateMapReport(createUserTaskReportWithFilters(
        processDefinitionEngineDto,
        ProcessFilterBuilder.filter()
          .flowNodeDuration()
          .flowNode(USER_TASK_1, filterData(DurationUnit.SECONDS, 10L, GREATER_THAN))
          .flowNode(USER_TASK_2, filterData(DurationUnit.SECONDS, 10L, GREATER_THAN))
          .filterLevel(FilterApplicationLevel.INSTANCE)
          .add()
          .buildList()
      )).getResult();
    final ReportResultResponseDto<List<MapResultEntryDto>> resultOneTaskWithLowerTen =
      reportClient.evaluateMapReport(createUserTaskReportWithFilters(
        processDefinitionEngineDto,
        ProcessFilterBuilder.filter()
          .flowNodeDuration()
          .flowNode(USER_TASK_1, filterData(DurationUnit.SECONDS, 10L, GREATER_THAN))
          // although this will not match any results, the previous one will
          .flowNode(USER_TASK_2, filterData(DurationUnit.SECONDS, 10L, LESS_THAN))
          .filterLevel(FilterApplicationLevel.INSTANCE)
          .add()
          .buildList()
      )).getResult();

    // then
    assertThat(resultBothTasksDurationGreaterTen.getInstanceCount()).isEqualTo(1L);
    assertThat(resultBothTasksDurationGreaterTen.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(resultBothTasksDurationGreaterTen.getFirstMeasureData()).hasSize(2)
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(Arrays.asList(Tuple.tuple(USER_TASK_1, 1.), Tuple.tuple(USER_TASK_2, 1.)));
    assertThat(resultOneTaskWithLowerTen.getInstanceCount()).isEqualTo(1L);
    assertThat(resultOneTaskWithLowerTen.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(resultOneTaskWithLowerTen.getFirstMeasureData()).hasSize(2)
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(Arrays.asList(Tuple.tuple(USER_TASK_1, 1.), Tuple.tuple(USER_TASK_2, 1.)));
  }

  @Test
  public void testViewLevelFlowNodeDurationFilterForMultipleFlowNodes() {
    // given
    final ProcessDefinitionEngineDto processDefinitionEngineDto = deployTwoUserTasksProcessDefinition();
    final ProcessInstanceEngineDto firstInstance = engineIntegrationExtension.startProcessInstance(
      processDefinitionEngineDto.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineDatabaseExtension.changeFlowNodeTotalDuration(firstInstance.getId(), USER_TASK_1, 20000);
    engineDatabaseExtension.changeFlowNodeTotalDuration(firstInstance.getId(), USER_TASK_2, 15000);

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> resultBothTasksDurationGreaterTen =
      reportClient.evaluateMapReport(createUserTaskReportWithFilters(
        processDefinitionEngineDto,
        ProcessFilterBuilder.filter()
          .flowNodeDuration()
          .flowNode(USER_TASK_1, filterData(DurationUnit.SECONDS, 10L, GREATER_THAN))
          .flowNode(USER_TASK_2, filterData(DurationUnit.SECONDS, 10L, GREATER_THAN))
          .filterLevel(FilterApplicationLevel.VIEW)
          .add()
          .buildList()
      )).getResult();
    final ReportResultResponseDto<List<MapResultEntryDto>> resultOneTaskWithLowerTen =
      reportClient.evaluateMapReport(createUserTaskReportWithFilters(
        processDefinitionEngineDto,
        ProcessFilterBuilder.filter()
          .flowNodeDuration()
          .flowNode(USER_TASK_1, filterData(DurationUnit.SECONDS, 10L, GREATER_THAN))
          .flowNode(USER_TASK_2, filterData(DurationUnit.SECONDS, 10L, LESS_THAN))
          .filterLevel(FilterApplicationLevel.VIEW)
          .add()
          .buildList()
      )).getResult();

    // then
    assertThat(resultBothTasksDurationGreaterTen.getInstanceCount()).isEqualTo(1L);
    assertThat(resultBothTasksDurationGreaterTen.getFirstMeasureData()).hasSize(2)
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(Arrays.asList(Tuple.tuple(USER_TASK_1, 1.), Tuple.tuple(USER_TASK_2, 1.)));
    assertThat(resultOneTaskWithLowerTen.getInstanceCount()).isEqualTo(1L);
    assertThat(resultOneTaskWithLowerTen.getFirstMeasureData()).hasSize(1)
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(Collections.singletonList(Tuple.tuple(USER_TASK_1, 1.)));
  }

  @Test
  public void testMultiInstanceLevelFlowNodeDurationFilter() {
    // given
    final ProcessDefinitionEngineDto processDefinitionEngineDto = deployTwoUserTasksProcessDefinition();

    final ProcessInstanceEngineDto firstInstance = engineIntegrationExtension.startProcessInstance(
      processDefinitionEngineDto.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineDatabaseExtension.changeFlowNodeTotalDuration(firstInstance.getId(), USER_TASK_2, 15000);
    engineIntegrationExtension.startProcessInstance(processDefinitionEngineDto.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> resultBothTasksDurationGreaterTen =
      reportClient.evaluateMapReport(createUserTaskReportWithFilters(
        processDefinitionEngineDto,
        ProcessFilterBuilder.filter()
          .flowNodeDuration()
          .flowNode(USER_TASK_1, filterData(DurationUnit.SECONDS, 10L, GREATER_THAN))
          .flowNode(USER_TASK_2, filterData(DurationUnit.SECONDS, 10L, GREATER_THAN))
          .filterLevel(FilterApplicationLevel.INSTANCE)
          .add()
          .buildList()
      )).getResult();

    final ReportResultResponseDto<List<MapResultEntryDto>> resultOneTaskWithLowerTen =
      reportClient.evaluateMapReport(createUserTaskReportWithFilters(
        processDefinitionEngineDto,
        ProcessFilterBuilder.filter()
          .flowNodeDuration()
          .flowNode(USER_TASK_1, filterData(DurationUnit.SECONDS, 100L, GREATER_THAN))
          .add()
          .flowNodeDuration()
          .flowNode(USER_TASK_2, filterData(DurationUnit.SECONDS, 10L, LESS_THAN))
          .filterLevel(FilterApplicationLevel.INSTANCE)
          .add()
          .buildList()
      )).getResult();

    final ReportResultResponseDto<List<MapResultEntryDto>> resultBothTasksWithLowerTen =
      reportClient.evaluateMapReport(createUserTaskReportWithFilters(
        processDefinitionEngineDto,
        ProcessFilterBuilder.filter()
          .flowNodeDuration()
          .flowNode(USER_TASK_1, filterData(DurationUnit.SECONDS, 10L, LESS_THAN))
          .add()
          .flowNodeDuration()
          .flowNode(USER_TASK_2, filterData(DurationUnit.SECONDS, 10L, LESS_THAN))
          .filterLevel(FilterApplicationLevel.INSTANCE)
          .add()
          .buildList()
      )).getResult();

    // then
    assertThat(resultBothTasksDurationGreaterTen.getInstanceCount()).isEqualTo(1L);
    assertThat(resultBothTasksDurationGreaterTen.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(resultOneTaskWithLowerTen.getInstanceCount()).isZero();
    assertThat(resultOneTaskWithLowerTen.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(resultBothTasksWithLowerTen.getInstanceCount()).isZero();
    assertThat(resultBothTasksWithLowerTen.getInstanceCountWithoutFilters()).isEqualTo(2L);
  }

  @Test
  public void testMultiViewLevelFlowNodeDurationFilter() {
    // given
    final ProcessDefinitionEngineDto processDefinitionEngineDto = deployTwoUserTasksProcessDefinition();

    final ProcessInstanceEngineDto firstInstance = engineIntegrationExtension.startProcessInstance(
      processDefinitionEngineDto.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeFlowNodeDuration(firstInstance.getId(), USER_TASK_1, 20000);
    changeFlowNodeDuration(firstInstance.getId(), USER_TASK_2, 15000);
    engineIntegrationExtension.startProcessInstance(processDefinitionEngineDto.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> resultBothTasksDurationGreaterTen =
      reportClient.evaluateMapReport(createUserTaskReportWithFilters(
        processDefinitionEngineDto,
        ProcessFilterBuilder.filter()
          .flowNodeDuration()
          .flowNode(USER_TASK_1, filterData(DurationUnit.SECONDS, 10L, GREATER_THAN))
          .flowNode(USER_TASK_2, filterData(DurationUnit.SECONDS, 10L, GREATER_THAN))
          .filterLevel(FilterApplicationLevel.VIEW)
          .add()
          .buildList()
      )).getResult();

    final ReportResultResponseDto<List<MapResultEntryDto>> resultOneTaskWithLowerTen =
      reportClient.evaluateMapReport(createUserTaskReportWithFilters(
        processDefinitionEngineDto,
        ProcessFilterBuilder.filter()
          .flowNodeDuration()
          .flowNode(USER_TASK_1, filterData(DurationUnit.SECONDS, 10L, GREATER_THAN))
          .add()
          .flowNodeDuration()
          .flowNode(USER_TASK_2, filterData(DurationUnit.SECONDS, 10L, LESS_THAN))
          .filterLevel(FilterApplicationLevel.VIEW)
          .add()
          .buildList()
      )).getResult();

    final ReportResultResponseDto<List<MapResultEntryDto>> resultBothTasksWithLowerTen =
      reportClient.evaluateMapReport(createUserTaskReportWithFilters(
        processDefinitionEngineDto,
        ProcessFilterBuilder.filter()
          .flowNodeDuration()
          .flowNode(USER_TASK_1, filterData(DurationUnit.SECONDS, 10L, LESS_THAN))
          .add()
          .flowNodeDuration()
          .flowNode(USER_TASK_2, filterData(DurationUnit.SECONDS, 10L, LESS_THAN))
          .filterLevel(FilterApplicationLevel.VIEW)
          .add()
          .buildList()
      )).getResult();

    // then
    assertThat(resultBothTasksDurationGreaterTen.getInstanceCount()).isEqualTo(1L);
    assertThat(resultBothTasksDurationGreaterTen.getFirstMeasureData()).hasSize(2)
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(Arrays.asList(Tuple.tuple(USER_TASK_1, 1.), Tuple.tuple(USER_TASK_2, 1.)));
    assertThat(resultOneTaskWithLowerTen.getInstanceCount()).isZero();
    assertThat(resultOneTaskWithLowerTen.getFirstMeasureData()).isEmpty();
    assertThat(resultBothTasksWithLowerTen.getInstanceCount()).isZero();
    assertThat(resultBothTasksWithLowerTen.getFirstMeasureData()).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(DurationUnit.class)
  public void testInstanceLevelFlowNodeDurationFilterUnits(final DurationUnit unit) {
    // given
    final ProcessDefinitionEngineDto processDefinitionEngineDto = deployTwoUserTasksProcessDefinition();
    final int actualUserTaskDuration = 2;

    final String processInstanceId = engineIntegrationExtension
      .startProcessInstance(processDefinitionEngineDto.getId()).getId();
    engineIntegrationExtension.finishAllRunningUserTasks();

    engineDatabaseExtension.changeFlowNodeTotalDuration(
      processInstanceId,
      USER_TASK_1,
      ChronoUnit.valueOf(unit.name()).getDuration().toMillis() * actualUserTaskDuration
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> resultGreaterActualDuration =
      reportClient.evaluateMapReport(createUserTaskReportWithFilters(
        processDefinitionEngineDto,
        ProcessFilterBuilder.filter()
          .flowNodeDuration()
          .flowNode(USER_TASK_1, filterData(unit, actualUserTaskDuration - 1L, GREATER_THAN))
          .filterLevel(FilterApplicationLevel.INSTANCE)
          .add()
          .buildList()
      )).getResult();
    final ReportResultResponseDto<List<MapResultEntryDto>> resultLowerActualDuration = reportClient
      .evaluateMapReport(createUserTaskReportWithFilters(
        processDefinitionEngineDto,
        ProcessFilterBuilder.filter()
          .flowNodeDuration()
          .flowNode(USER_TASK_1, filterData(unit, (long) actualUserTaskDuration, LESS_THAN))
          .filterLevel(FilterApplicationLevel.INSTANCE)
          .add()
          .buildList()
      )).getResult();

    // then
    assertThat(resultGreaterActualDuration.getInstanceCount()).isEqualTo(1L);
    assertThat(resultLowerActualDuration.getInstanceCount()).isZero();
  }

  @ParameterizedTest
  @EnumSource(DurationUnit.class)
  public void testViewLevelFlowNodeDurationFilterUnits(final DurationUnit unit) {
    // given
    final ProcessDefinitionEngineDto processDefinitionEngineDto = deployTwoUserTasksProcessDefinition();
    final int actualUserTaskDuration = 2;

    final String processInstanceId = engineIntegrationExtension
      .startProcessInstance(processDefinitionEngineDto.getId()).getId();
    engineIntegrationExtension.finishAllRunningUserTasks();

    engineDatabaseExtension.changeFlowNodeTotalDuration(
      processInstanceId,
      USER_TASK_1,
      ChronoUnit.valueOf(unit.name()).getDuration().toMillis() * actualUserTaskDuration
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> resultGreaterActualDuration =
      reportClient.evaluateMapReport(createUserTaskReportWithFilters(
        processDefinitionEngineDto,
        ProcessFilterBuilder.filter()
          .flowNodeDuration()
          .flowNode(USER_TASK_1, filterData(unit, actualUserTaskDuration - 1L, GREATER_THAN))
          .filterLevel(FilterApplicationLevel.VIEW)
          .add()
          .buildList()
      )).getResult();
    final ReportResultResponseDto<List<MapResultEntryDto>> resultLowerActualDuration = reportClient
      .evaluateMapReport(createUserTaskReportWithFilters(
        processDefinitionEngineDto,
        ProcessFilterBuilder.filter()
          .flowNodeDuration()
          .flowNode(USER_TASK_1, filterData(unit, (long) actualUserTaskDuration, LESS_THAN))
          .filterLevel(FilterApplicationLevel.VIEW)
          .add()
          .buildList()
      )).getResult();

    // then
    assertThat(resultGreaterActualDuration.getInstanceCount()).isEqualTo(1L);
    assertThat(resultLowerActualDuration.getInstanceCount()).isZero();
  }

  @Test
  public void testMixedLevelFlowNodeDurationFilters() {
    // given
    final ProcessDefinitionEngineDto definition = deployTwoUserTasksProcessDefinition();
    final ProcessInstanceEngineDto instance = engineIntegrationExtension.startProcessInstance(definition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeFlowNodeDuration(instance.getId(), USER_TASK_1, 20000);
    changeFlowNodeDuration(instance.getId(), USER_TASK_2, 5000);

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> greaterViewAndInstanceFilters = reportClient.evaluateMapReport(
      createUserTaskReportWithFilters(
        definition,
        ProcessFilterBuilder.filter()
          .flowNodeDuration()
          .flowNode(USER_TASK_1, filterData(DurationUnit.SECONDS, 10L, GREATER_THAN))
          .filterLevel(FilterApplicationLevel.VIEW)
          .add()
          .flowNodeDuration()
          .flowNode(USER_TASK_1, filterData(DurationUnit.SECONDS, 10L, GREATER_THAN))
          .filterLevel(FilterApplicationLevel.INSTANCE)
          .add()
          .buildList()
      )).getResult();

    final ReportResultResponseDto<List<MapResultEntryDto>> lessThanViewAndInstanceFilters = reportClient.evaluateMapReport(
      createUserTaskReportWithFilters(
        definition,
        ProcessFilterBuilder.filter()
          .flowNodeDuration()
          .flowNode(USER_TASK_2, filterData(DurationUnit.SECONDS, 10L, LESS_THAN))
          .filterLevel(FilterApplicationLevel.VIEW)
          .add()
          .flowNodeDuration()
          .flowNode(USER_TASK_2, filterData(DurationUnit.SECONDS, 10L, LESS_THAN))
          .filterLevel(FilterApplicationLevel.INSTANCE)
          .add()
          .buildList()
      )).getResult();

    final ReportResultResponseDto<List<MapResultEntryDto>> greaterViewAndLessInstanceFilters = reportClient.evaluateMapReport(
      createUserTaskReportWithFilters(
        definition,
        ProcessFilterBuilder.filter()
          .flowNodeDuration()
          .flowNode(USER_TASK_1, filterData(DurationUnit.SECONDS, 10L, GREATER_THAN))
          .filterLevel(FilterApplicationLevel.VIEW)
          .add()
          .flowNodeDuration()
          .flowNode(USER_TASK_1, filterData(DurationUnit.SECONDS, 10L, LESS_THAN))
          .filterLevel(FilterApplicationLevel.INSTANCE)
          .add()
          .buildList()
      )).getResult();

    // then
    assertThat(greaterViewAndInstanceFilters.getInstanceCount()).isEqualTo(1L);
    assertThat(greaterViewAndInstanceFilters.getFirstMeasureData()).hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(greaterViewAndInstanceFilters.getFirstMeasureData(), USER_TASK_1)).isPresent().get()
      .extracting(MapResultEntryDto::getValue).isEqualTo(1.);

    assertThat(lessThanViewAndInstanceFilters.getInstanceCount()).isEqualTo(1L);
    assertThat(lessThanViewAndInstanceFilters.getFirstMeasureData()).hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(lessThanViewAndInstanceFilters.getFirstMeasureData(), USER_TASK_2)).isPresent().get()
      .extracting(MapResultEntryDto::getValue).isEqualTo(1.);

    assertThat(greaterViewAndLessInstanceFilters.getInstanceCount()).isZero();
    assertThat(greaterViewAndLessInstanceFilters.getFirstMeasureData()).isEmpty();
  }

  private ProcessReportDataDto createUserTaskReportWithFilters(final ProcessDefinitionEngineDto definition,
                                                               final List<ProcessFilterDto<?>> filters) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(definition.getKey())
      .setProcessDefinitionVersion(definition.getVersionAsString())
      .setReportDataType(ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_USER_TASK)
      .setFilter(filters)
      .build();
  }

  private DurationFilterDataDto filterData(final DurationUnit unit, final Long value,
                                           final ComparisonOperator operator) {
    return DurationFilterDataDto.builder().unit(unit).value(value).operator(operator).build();
  }

  private void changeFlowNodeDuration(final String instanceId, final String flowNodeId, Number durationInMs) {
    engineDatabaseExtension.changeFlowNodeTotalDuration(instanceId, flowNodeId, durationInMs);
  }

  private void changeFlowNodeStartDate(final String instanceId, final String flowNodeId, OffsetDateTime startDate) {
    engineDatabaseExtension.changeFlowNodeStartDate(instanceId, flowNodeId, startDate);
  }

}
