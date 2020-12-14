/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.flownode.frequency.groupby.flownode.duration;

import com.google.common.collect.ImmutableList;
import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.single.ModelElementFrequencyByModelElementDurationByModelElementIT;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.test.util.DateCreationFreezer;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.LESS_THAN;
import static org.camunda.optimize.test.util.ProcessReportDataType.FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_DURATION_BY_FLOW_NODE;

public class FlowNodeFrequencyByFlowNodeDurationDistributeByFlowNodeIT
  extends ModelElementFrequencyByModelElementDurationByModelElementIT {

  private static final ImmutableList<String> FLOW_NODES = ImmutableList.of(END_EVENT, START_EVENT, USER_TASK_1);

  @Override
  protected ProcessInstanceEngineDto startProcessInstanceCompleteTaskAndModifyDuration(
    final String definitionId,
    final Number durationInMillis) {
    final ProcessInstanceEngineDto processInstance = engineIntegrationExtension.startProcessInstance(definitionId);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());
    engineDatabaseExtension.changeAllActivityDurations(processInstance.getId(), durationInMillis);
    return processInstance;
  }

  @Override
  protected ProcessViewEntity getProcessViewEntity() {
    return ProcessViewEntity.FLOW_NODE;
  }

  @Override
  protected DistributedByType getDistributedByType() {
    return DistributedByType.FLOW_NODE;
  }

  @Override
  protected ProcessReportDataDto createReport(final String processKey, final String definitionVersion) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processKey)
      .setProcessDefinitionVersion(definitionVersion)
      .setReportDataType(FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_DURATION_BY_FLOW_NODE)
      .build();
  }

  @Override
  protected List<String> getExpectedModelElements() {
    return FLOW_NODES;
  }

  @Test
  public void multipleProcessInstances_runningInstanceDurationIsCalculated() {
    // given
    final int completedActivityInstanceDurations = 1000;
    final OffsetDateTime startTime = DateCreationFreezer.dateFreezer(OffsetDateTime.now()).freezeDateAndReturn();
    final ProcessDefinitionEngineDto definition = deploySimpleOneUserTasksDefinition();
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), completedActivityInstanceDurations);

    final ProcessInstanceEngineDto runningProcessInstance =
      engineIntegrationExtension.startProcessInstance(definition.getId());
    engineDatabaseExtension.changeAllActivityDurations(
      runningProcessInstance.getId(), completedActivityInstanceDurations
    );
    engineDatabaseExtension.changeActivityInstanceStartDate(runningProcessInstance.getId(), USER_TASK_1, startTime);
    importAllEngineEntitiesFromScratch();

    // when
    final OffsetDateTime currentTime = DateCreationFreezer
      // just one more ms to ensure we only get back two buckets for easier assertion
      .dateFreezer(startTime.plus(completedActivityInstanceDurations + 1, ChronoUnit.MILLIS))
      .freezeDateAndReturn();
    final ProcessReportDataDto reportData = createReport(definition.getKey(), definition.getVersionAsString());
    AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getIsComplete()).isTrue();
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .groupByContains(createDurationBucketKey(completedActivityInstanceDurations))
      .distributedByContains(END_EVENT, 1., END_EVENT)
      .distributedByContains(START_EVENT, 2., START_EVENT)
      .distributedByContains(USER_TASK_1, 1., USER_TASK_1)
      .groupByContains(createDurationBucketKey((int) Duration.between(startTime, currentTime).toMillis()))
      .distributedByContains(END_EVENT, null, END_EVENT)
      .distributedByContains(START_EVENT, null, START_EVENT)
      .distributedByContains(USER_TASK_1, 1., USER_TASK_1)
      .doAssert(resultDto);
    // @formatter:on
  }

  @Test
  public void viewLevelFlowNodeDurationFilterOnlyIncludesFlowNodesMatchingFilter() {
    // given
    final ProcessDefinitionEngineDto definition = deploySimpleOneUserTasksDefinition();
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), 1000);
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), 5000);
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), 10000);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(definition.getKey(), definition.getVersionAsString());
    final List<ProcessFilterDto<?>> filterYieldingNoResults = ProcessFilterBuilder.filter()
      .flowNodeDuration()
      .flowNode(USER_TASK_1, filterData(DurationFilterUnit.SECONDS, 10L, LESS_THAN))
      .filterLevel(FilterApplicationLevel.VIEW)
      .add()
      .buildList();
    reportData.setFilter(filterYieldingNoResults);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(3L);
    assertThat(result.getDataEntryForKey(createDurationBucketKey(1000))).isPresent();
    assertThat(result.getDataEntryForKey(createDurationBucketKey(5000))).isPresent();
    assertThat(result.getDataEntryForKey(createDurationBucketKey(10000))).isNotPresent();
    assertThat(result.getData()).allSatisfy(bucket -> {
      if (bucket.getKey().equals(createDurationBucketKey(1000)) ||
        bucket.getKey().equals(createDurationBucketKey(5000))) {
        assertThat(bucket.getValue())
          .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue).hasSize(3)
          .contains(Tuple.tuple(START_EVENT, null), Tuple.tuple(USER_TASK_1, 1.), Tuple.tuple(END_EVENT, null));
      } else {
        assertThat(bucket.getValue())
          .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue).hasSize(3)
          .contains(Tuple.tuple(START_EVENT, null), Tuple.tuple(USER_TASK_1, null), Tuple.tuple(END_EVENT, null));
      }
    });
  }

}
