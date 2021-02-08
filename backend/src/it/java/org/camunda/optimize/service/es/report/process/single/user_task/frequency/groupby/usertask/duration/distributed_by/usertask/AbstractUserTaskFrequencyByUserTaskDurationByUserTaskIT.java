/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.user_task.frequency.groupby.usertask.duration.distributed_by.usertask;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
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
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_DURATION_BY_USER_TASK;

public abstract class AbstractUserTaskFrequencyByUserTaskDurationByUserTaskIT
  extends ModelElementFrequencyByModelElementDurationByModelElementIT {

  protected static final ImmutableList<String> USER_TASKS = ImmutableList.of(USER_TASK_1);

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
      .setReportDataType(USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_DURATION_BY_USER_TASK)
      .setUserTaskDurationTime(getUserTaskDurationTime())
      .build();
  }

  @Override
  protected List<String> getExpectedModelElements() {
    return USER_TASKS;
  }

  @Test
  public void multipleProcessInstances_runningInstanceDurationIsCalculated() {
    // given
    final int completedModelElementInstanceDuration = 1000;
    final OffsetDateTime startTime = DateCreationFreezer.dateFreezer(OffsetDateTime.now()).freezeDateAndReturn();
    final ProcessDefinitionEngineDto definition = deploySimpleOneUserTasksDefinition();
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), completedModelElementInstanceDuration);

    final ProcessInstanceEngineDto runningProcessInstance =
      engineIntegrationExtension.startProcessInstance(definition.getId());
    changeRunningInstanceReferenceDate(runningProcessInstance, startTime);
    importAllEngineEntitiesFromScratch();

    // when
    final OffsetDateTime currentTime = DateCreationFreezer
      // just one more ms to ensure we only get back two buckets for easier assertion
      .dateFreezer(startTime.plus(completedModelElementInstanceDuration + 1, ChronoUnit.MILLIS))
      .freezeDateAndReturn();
    final ProcessReportDataDto reportData = createReport(definition.getKey(), definition.getVersionAsString());
    AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto resultDto = evaluationResponse.getResult();
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .groupByContains(createDurationBucketKey(completedModelElementInstanceDuration))
      .distributedByContains(USER_TASK_1, 1., USER_TASK_1)
      .groupByContains(createDurationBucketKey((int) Duration.between(startTime, currentTime).toMillis()))
      .distributedByContains(USER_TASK_1, 1., USER_TASK_1)
      .doAssert(resultDto);
    // @formatter:on
  }
}
