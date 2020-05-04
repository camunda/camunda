/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class FlowNodeDurationFilterIT extends AbstractDurationFilterIT {

  @Test
  public void testSingleCompletedFlowNodeDurationFilter() {
    // given
    final ProcessDefinitionEngineDto processDefinitionEngineDto = deployTwoUserTasksProcessDefinition();

    engineIntegrationExtension.startProcessInstance(processDefinitionEngineDto.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> resultGreaterZero =
      reportClient.evaluateReportWithRawDataResult(createRawDataReportWithFilters(
        processDefinitionEngineDto,
        ProcessFilterBuilder
          .filter()
          .flowNodeDuration()
          .flowNode(
            USER_TASK_ACTIVITY_ID,
            DurationFilterDataDto.builder().unit(DurationFilterUnit.SECONDS).value(0L)
              .operator(FilterOperatorConstants.GREATER_THAN).build()
          )
          .add()
          .buildList()
      ));
    final AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> resultLowerOrEqualZero =
      reportClient.evaluateReportWithRawDataResult(createRawDataReportWithFilters(
        processDefinitionEngineDto,
        ProcessFilterBuilder
          .filter()
          .flowNodeDuration()
          .flowNode(
            USER_TASK_ACTIVITY_ID,
            DurationFilterDataDto.builder().unit(DurationFilterUnit.SECONDS).value(0L)
              .operator(FilterOperatorConstants.LESS_THAN_EQUALS).build()
          )
          .add()
          .buildList()
      ));

    // then
    assertThat(resultGreaterZero.getResult().getInstanceCount()).isEqualTo(1L);
    assertThat(resultLowerOrEqualZero.getResult().getInstanceCount()).isEqualTo(0L);
  }

  @Test
  public void testSingleRunningFlowNodeDurationFilter() {
    // given
    final ProcessDefinitionEngineDto processDefinitionEngineDto = deployTwoUserTasksProcessDefinition();
    final OffsetDateTime now = OffsetDateTime.now();
    engineIntegrationExtension.startProcessInstance(processDefinitionEngineDto.getId());

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    LocalDateUtil.setCurrentTime(now.plusSeconds(10));
    final AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> result =
      reportClient.evaluateReportWithRawDataResult(createRawDataReportWithFilters(
        processDefinitionEngineDto,
        ProcessFilterBuilder
          .filter()
          .flowNodeDuration()
          .flowNode(
            USER_TASK_ACTIVITY_ID,
            DurationFilterDataDto.builder().unit(DurationFilterUnit.SECONDS).value(9L)
              .operator(FilterOperatorConstants.GREATER_THAN).build()
          )
          .add()
          .flowNodeDuration()
          .flowNode(
            USER_TASK_ACTIVITY_ID,
            DurationFilterDataDto.builder().unit(DurationFilterUnit.SECONDS).value(11L)
              .operator(FilterOperatorConstants.LESS_THAN).build()
          )
          .add()
          .buildList()
      ));

    // then
    assertThat(result.getResult().getInstanceCount()).isEqualTo(1L);
  }

  @Test
  public void testFlowNodeDurationFilterForMultipleFlowNodes() {
    // given
    final ProcessDefinitionEngineDto processDefinitionEngineDto = deployTwoUserTasksProcessDefinition();

    engineIntegrationExtension.startProcessInstance(processDefinitionEngineDto.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> resultBothTasksDurationGreaterZero =
      reportClient.evaluateReportWithRawDataResult(createRawDataReportWithFilters(
        processDefinitionEngineDto,
        ProcessFilterBuilder
          .filter()
          .flowNodeDuration()
          .flowNode(
            USER_TASK_ACTIVITY_ID,
            DurationFilterDataDto.builder().unit(DurationFilterUnit.SECONDS).value(0L)
              .operator(FilterOperatorConstants.GREATER_THAN).build()
          )
          .flowNode(
            USER_TASK_ACTIVITY_ID_2,
            DurationFilterDataDto.builder().unit(DurationFilterUnit.SECONDS).value(0L)
              .operator(FilterOperatorConstants.GREATER_THAN).build()
          )
          .add()
          .buildList()
      ));
    final AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> resultOneTaskWithLowerZero =
      reportClient.evaluateReportWithRawDataResult(createRawDataReportWithFilters(
        processDefinitionEngineDto,
        ProcessFilterBuilder
          .filter()
          .flowNodeDuration()
          .flowNode(
            USER_TASK_ACTIVITY_ID,
            DurationFilterDataDto.builder().unit(DurationFilterUnit.SECONDS).value(0L)
              .operator(FilterOperatorConstants.GREATER_THAN).build()
          )
          .flowNode(
            USER_TASK_ACTIVITY_ID_2,
            // although this will not match any results, the previous one will
            DurationFilterDataDto.builder().unit(DurationFilterUnit.SECONDS).value(0L)
              .operator(FilterOperatorConstants.LESS_THAN).build()
          )
          .add()
          .buildList()
      ));

    // then
    assertThat(resultBothTasksDurationGreaterZero.getResult().getInstanceCount()).isEqualTo(1L);
    assertThat(resultOneTaskWithLowerZero.getResult().getInstanceCount()).isEqualTo(1L);
  }

  @Test
  public void testMultiFlowNodeDurationFilter() {
    // given
    final ProcessDefinitionEngineDto processDefinitionEngineDto = deployTwoUserTasksProcessDefinition();

    engineIntegrationExtension.startProcessInstance(processDefinitionEngineDto.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.finishAllRunningUserTasks();

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> resultBothTasksDurationGreaterZero =
      reportClient.evaluateReportWithRawDataResult(createRawDataReportWithFilters(
        processDefinitionEngineDto,
        ProcessFilterBuilder
          .filter()
          .flowNodeDuration()
          .flowNode(
            USER_TASK_ACTIVITY_ID,
            DurationFilterDataDto.builder().unit(DurationFilterUnit.SECONDS).value(0L)
              .operator(FilterOperatorConstants.GREATER_THAN).build()
          )
          .add()
          .flowNodeDuration()
          .flowNode(
            USER_TASK_ACTIVITY_ID_2,
            DurationFilterDataDto.builder().unit(DurationFilterUnit.SECONDS).value(0L)
              .operator(FilterOperatorConstants.GREATER_THAN).build()
          )
          .add()
          .buildList()
      ));
    final AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> resultOneTaskWithLowerZero =
      reportClient.evaluateReportWithRawDataResult(createRawDataReportWithFilters(
        processDefinitionEngineDto,
        ProcessFilterBuilder
          .filter()
          .flowNodeDuration()
          .flowNode(
            USER_TASK_ACTIVITY_ID,
            DurationFilterDataDto.builder().unit(DurationFilterUnit.SECONDS).value(0L)
              .operator(FilterOperatorConstants.GREATER_THAN).build()
          )
          .add()
          .flowNodeDuration()
          .flowNode(
            USER_TASK_ACTIVITY_ID_2,
            DurationFilterDataDto.builder().unit(DurationFilterUnit.SECONDS).value(0L)
              .operator(FilterOperatorConstants.LESS_THAN).build()
          )
          .add()
          .buildList()
      ));
    final AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> resultBothTasksWithLowerZero =
      reportClient.evaluateReportWithRawDataResult(createRawDataReportWithFilters(
        processDefinitionEngineDto,
        ProcessFilterBuilder
          .filter()
          .flowNodeDuration()
          .flowNode(
            USER_TASK_ACTIVITY_ID,
            DurationFilterDataDto.builder().unit(DurationFilterUnit.SECONDS).value(0L)
              .operator(FilterOperatorConstants.LESS_THAN).build()
          )
          .add()
          .flowNodeDuration()
          .flowNode(
            USER_TASK_ACTIVITY_ID_2,
            DurationFilterDataDto.builder().unit(DurationFilterUnit.SECONDS).value(0L)
              .operator(FilterOperatorConstants.LESS_THAN).build()
          )
          .add()
          .buildList()
      ));

    // then
    assertThat(resultBothTasksDurationGreaterZero.getResult().getInstanceCount()).isEqualTo(1L);
    assertThat(resultOneTaskWithLowerZero.getResult().getInstanceCount()).isEqualTo(0L);
    assertThat(resultBothTasksWithLowerZero.getResult().getInstanceCount()).isEqualTo(0L);
  }

  @ParameterizedTest
  @EnumSource(DurationFilterUnit.class)
  public void testFlowNodeDurationFilterUnits(final DurationFilterUnit unit) throws SQLException {
    // given
    final ProcessDefinitionEngineDto processDefinitionEngineDto = deployTwoUserTasksProcessDefinition();
    final int actualUserTaskDuration = 2;

    final String processInstanceId = engineIntegrationExtension
      .startProcessInstance(processDefinitionEngineDto.getId()).getId();
    engineIntegrationExtension.finishAllRunningUserTasks();

    engineDatabaseExtension.changeActivityDuration(
      processInstanceId,
      USER_TASK_ACTIVITY_ID,
      ChronoUnit.valueOf(unit.name()).getDuration().toMillis() * actualUserTaskDuration
    );

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> resultGreaterActualDuration =
      reportClient.evaluateReportWithRawDataResult(createRawDataReportWithFilters(
        processDefinitionEngineDto,
        ProcessFilterBuilder
          .filter()
          .flowNodeDuration()
          .flowNode(
            USER_TASK_ACTIVITY_ID,
            DurationFilterDataDto.builder().unit(unit).value(actualUserTaskDuration - 1L)
              .operator(FilterOperatorConstants.GREATER_THAN).build()
          )
          .add()
          .buildList()
      ));
    final AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> resultLowerActualDuration =
      reportClient.evaluateReportWithRawDataResult(createRawDataReportWithFilters(
        processDefinitionEngineDto,
        ProcessFilterBuilder
          .filter()
          .flowNodeDuration()
          .flowNode(
            USER_TASK_ACTIVITY_ID,
            DurationFilterDataDto.builder().unit(unit).value((long) actualUserTaskDuration)
              .operator(FilterOperatorConstants.LESS_THAN).build()
          )
          .add()
          .buildList()
      ));

    // then
    assertThat(resultGreaterActualDuration.getResult().getInstanceCount()).isEqualTo(1L);
    assertThat(resultLowerActualDuration.getResult().getInstanceCount()).isEqualTo(0L);
  }

  private ProcessReportDataDto createRawDataReportWithFilters(final ProcessDefinitionEngineDto definition,
                                                              final List<ProcessFilterDto<?>> filters) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(definition.getKey())
      .setProcessDefinitionVersion(definition.getVersionAsString())
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .setFilter(filters)
      .build();
  }

}
