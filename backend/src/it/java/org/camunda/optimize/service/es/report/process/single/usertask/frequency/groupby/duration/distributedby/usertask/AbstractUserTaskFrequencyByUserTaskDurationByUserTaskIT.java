/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.usertask.frequency.groupby.duration.distributedby.usertask;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.single.ModelElementFrequencyByModelElementDurationByModelElementIT;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.camunda.optimize.test.util.DateCreationFreezer.dateFreezer;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_USER_TASK_DURATION_BY_USER_TASK;

public abstract class AbstractUserTaskFrequencyByUserTaskDurationByUserTaskIT
  extends ModelElementFrequencyByModelElementDurationByModelElementIT {

  protected static final ImmutableList<String> USER_TASKS = ImmutableList.of(USER_TASK_1);
  protected static final ImmutableList<String> USER_TASKS_2 = ImmutableList.of(USER_TASK_2);

  protected abstract void changeRunningInstanceReferenceDate(final ProcessInstanceEngineDto runningProcessInstance,
                                                             final OffsetDateTime startTime);

  protected abstract UserTaskDurationTime getUserTaskDurationTime();

  @Override
  protected ProcessViewEntity getProcessViewEntity() {
    return ProcessViewEntity.USER_TASK;
  }

  @Override
  protected DistributedByType getDistributedByType() {
    return DistributedByType.USER_TASK;
  }

  @Override
  protected ProcessReportDataDto createReport(final String processKey, final String definitionVersion) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processKey)
      .setProcessDefinitionVersion(definitionVersion)
      .setReportDataType(USER_TASK_FREQ_GROUP_BY_USER_TASK_DURATION_BY_USER_TASK)
      .setUserTaskDurationTime(getUserTaskDurationTime())
      .build();
  }

  @Override
  protected List<String> getExpectedModelElements() {
    return USER_TASKS;
  }

  @Override
  protected List<String> getSecondProcessExpectedModelElements() {
    return USER_TASKS_2;
  }

  @Test
  public void multipleProcessInstances_runningInstanceDurationIsCalculated() {
    // given
    final int completedModelElementInstanceDuration = 1000;
    final OffsetDateTime startTime = dateFreezer().freezeDateAndReturn();
      dateFreezer(startTime).freezeDateAndReturn();
    final ProcessDefinitionEngineDto definition = deploySimpleOneUserTasksDefinition();
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), completedModelElementInstanceDuration);

    final ProcessInstanceEngineDto runningProcessInstance =
      engineIntegrationExtension.startProcessInstance(definition.getId());
    changeRunningInstanceReferenceDate(runningProcessInstance, startTime);
    importAllEngineEntitiesFromScratch();

    // when
    final OffsetDateTime currentTime = // just one more ms to ensure we only get back two buckets for easier assertion
      dateFreezer(startTime.plus(completedModelElementInstanceDuration + 1, ChronoUnit.MILLIS))
      .freezeDateAndReturn();
    final ProcessReportDataDto reportData = createReport(definition.getKey(), definition.getVersionAsString());
    AuthorizedProcessReportEvaluationResponseDto<List<HyperMapResultEntryDto>> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportResultResponseDto<List<HyperMapResultEntryDto>> resultDto = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.FREQUENCY)
        .groupByContains(createDurationBucketKey(completedModelElementInstanceDuration))
          .distributedByContains(USER_TASK_1, 1., USER_TASK_1)
        .groupByContains(createDurationBucketKey((int) Duration.between(startTime, currentTime).toMillis()))
          .distributedByContains(USER_TASK_1, 1., USER_TASK_1)
      .doAssert(resultDto);
    // @formatter:on
  }
}
