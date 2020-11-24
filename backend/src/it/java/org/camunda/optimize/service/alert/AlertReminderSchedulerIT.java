/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.alert;

import org.camunda.optimize.AbstractAlertIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationRequestDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.FlowNodesGroupByDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

public class AlertReminderSchedulerIT extends AbstractAlertIT {

  @BeforeEach
  public void cleanUp() throws Exception {
    embeddedOptimizeExtension.getAlertService().getScheduler().clear();
  }

  @Test
  public void reminderJobsAreRemovedOnAlertDeletion() throws Exception {
    // given
    AlertCreationRequestDto simpleAlert = createBasicAlertWithReminder();
    String id = alertClient.createAlert(simpleAlert);
    triggerAndCompleteCheckJob(id);

    // when
    alertClient.deleteAlert(id);

    // then
    assertThat(
      embeddedOptimizeExtension.getAlertService().getScheduler().getJobGroupNames().size(),
      is(0)
    );
  }

  @Test
  public void reminderJobsAreRemovedOnReportDeletion() throws Exception {
    // given
    AlertCreationRequestDto simpleAlert = createBasicAlertWithReminder();
    String id = alertClient.createAlert(simpleAlert);
    triggerAndCompleteCheckJob(id);

    // when
    reportClient.deleteReport(simpleAlert.getReportId(), true);

    // then
    assertThat(
      embeddedOptimizeExtension.getAlertService().getScheduler().getJobGroupNames().size(),
      is(0)
    );
  }

  @Test
  public void reminderIsNotCreatedOnStartupIfNotDefinedInAlert() throws Exception {
    // given
    AlertCreationRequestDto alert = setupBasicProcessAlert();
    alertClient.createAlert(alert);

    // when
    embeddedOptimizeExtension.stopOptimize();
    embeddedOptimizeExtension.startOptimize();

    // then
    assertThat(alertClient.getAllAlerts().get(0).getReminder(), is(nullValue()));
  }

  @Test
  public void reminderJobsAreRemovedOnReportUpdate() throws Exception {
    ProcessDefinitionEngineDto processDefinition = deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    String collectionId = collectionClient.createNewCollectionWithProcessScope(processDefinition);
    String reportId = createNewProcessReportAsUser(collectionId, processDefinition);
    AlertCreationRequestDto simpleAlert = alertClient.createSimpleAlert(reportId);

    AlertInterval reminderInterval = new AlertInterval();
    reminderInterval.setValue(1);
    reminderInterval.setUnit("Seconds");
    simpleAlert.setReminder(reminderInterval);

    String id = alertClient.createAlert(simpleAlert);

    triggerAndCompleteCheckJob(id);

    assertThat(
      embeddedOptimizeExtension.getAlertService().getScheduler().getJobGroupNames().size(),
      is(2)
    );

    // when
    SingleProcessReportDefinitionRequestDto report = getProcessNumberReportDefinitionDto(collectionId, processDefinition);
    report.getData().setGroupBy(new FlowNodesGroupByDto());
    report.getData().setVisualization(ProcessVisualization.HEAT);
    reportClient.updateSingleProcessReport(simpleAlert.getReportId(), report, true);

    // then
    assertThat(
      embeddedOptimizeExtension.getAlertService().getScheduler().getJobGroupNames().size(),
      is(0)
    );
  }

  @Test
  public void reminderJobsAreRemovedOnEvaluationResultChange() throws Exception {
    // given
    long daysToShift = 0L;
    long durationInSec = 2L;

    ProcessInstanceEngineDto processInstance = deployWithTimeShift(daysToShift, durationInSec);

    // when
    String reportId = createAndStoreDurationNumberReportInNewCollection(processInstance);
    AlertCreationRequestDto simpleAlert = alertClient.createSimpleAlert(reportId, 10, "Seconds");
    AlertInterval reminderInterval = new AlertInterval();
    reminderInterval.setValue(1);
    reminderInterval.setUnit("Minutes");
    simpleAlert.setReminder(reminderInterval);

    simpleAlert.setThreshold(1500.0);

    String id = alertClient.createAlert(simpleAlert);

    triggerAndCompleteCheckJob(id);

    assertThat(
      embeddedOptimizeExtension.getAlertService().getScheduler().getJobGroupNames().size(),
      is(greaterThanOrEqualTo(2))
    );

    // when
    engineIntegrationExtension.startProcessInstance(processInstance.getDefinitionId());
    importAllEngineEntitiesFromScratch();

    triggerAndCompleteReminderJob(id);

    // then
    assertThat(
      embeddedOptimizeExtension.getAlertService().getScheduler().getJobGroupNames().size(),
      is(1)
    );
  }

  @Test
  public void reminderJobsAreRemovedOnAlertDefinitionChange() throws Exception {
    // given
    AlertCreationRequestDto simpleAlert = createBasicAlertWithReminder();

    String id = alertClient.createAlert(simpleAlert);

    triggerAndCompleteCheckJob(id);

    assertThat(
      embeddedOptimizeExtension.getAlertService().getScheduler().getJobGroupNames().size(),
      is(greaterThanOrEqualTo(2))
    );

    // when
    simpleAlert.getCheckInterval().setValue(30);

    alertClient.updateAlert(id, simpleAlert);


    // then
    assertThat(
      embeddedOptimizeExtension.getAlertService().getScheduler().getJobGroupNames().size(),
      is(1)
    );

    // when
    triggerAndCompleteCheckJob(id);

    // then
    assertThat(
      embeddedOptimizeExtension.getAlertService().getScheduler().getJobGroupNames().size(),
      is(2)
    );
  }

  @Test
  public void reminderJobsAreScheduledOnAlertCreation() throws Exception {
    // given
    AlertCreationRequestDto simpleAlert = createBasicAlertWithReminder();

    // when
    String id = alertClient.createAlert(simpleAlert);

    triggerAndCompleteCheckJob(id);

    // then
    assertThat(
      embeddedOptimizeExtension.getAlertService().getScheduler().getJobGroupNames().size(),
      is(2)
    );
  }

  @Test
  public void reminderJobsAreScheduledCorrectly() throws Exception {
    // given
    AlertCreationRequestDto simpleAlert = createBasicAlertWithReminder();
    AlertInterval checkInterval = new AlertInterval();
    checkInterval.setUnit("Minutes");
    checkInterval.setValue(10);
    simpleAlert.setCheckInterval(checkInterval);

    // when
    String id = alertClient.createAlert(simpleAlert);

    triggerAndCompleteCheckJob(id);

    // then
    OffsetDateTime nextTimeReminderIsExecuted = getNextReminderExecutionTime(id);
    OffsetDateTime upperBoundary = OffsetDateTime.now().plusSeconds(2L); // 1 second is too unstable
    assertThat(nextTimeReminderIsExecuted.isBefore(upperBoundary), is(true));
  }

  @Test
  public void reminderJobsAreScheduledAfterRestart() throws Exception {
    // given
    AlertCreationRequestDto simpleAlert = createBasicAlertWithReminder();

    String id = alertClient.createAlert(simpleAlert);

    triggerAndCompleteCheckJob(id);

    // when
    embeddedOptimizeExtension.stopOptimize();
    embeddedOptimizeExtension.startOptimize();

    assertThat(
      embeddedOptimizeExtension.getAlertService().getScheduler().getJobGroupNames().size(),
      is(2)
    );
  }

}
