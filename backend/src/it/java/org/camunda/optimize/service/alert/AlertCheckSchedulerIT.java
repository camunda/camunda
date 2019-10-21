/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.alert;

import com.icegreen.greenmail.util.GreenMail;
import org.camunda.optimize.AbstractAlertIT;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.FlowNodesGroupByDto;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerKey;

import javax.mail.internet.MimeMessage;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class AlertCheckSchedulerIT extends AbstractAlertIT {

  private GreenMail greenMail;

  @Before
  public void cleanUp() throws Exception {
    embeddedOptimizeExtensionRule.getAlertService().getScheduler().clear();
    greenMail = initGreenMail();
  }

  @After
  public void tearDown() {
    greenMail.stop();
  }

  @Test
  public void reportUpdateToNotNumberRemovesAlert() throws Exception {
    //given
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess();
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    String reportId = createAndStoreProcessNumberReport(processDefinition);
    AlertCreationDto simpleAlert = createSimpleAlert(reportId);

    Response response =
      embeddedOptimizeExtensionRule
        .getRequestExecutor()
        .buildCreateAlertRequest(simpleAlert)
        .execute();

    assertThat(response.getStatus(), is(200));

    // when
    SingleProcessReportDefinitionDto report = getProcessNumberReportDefinitionDto(processDefinition);
    report.getData().setGroupBy(new FlowNodesGroupByDto());
    report.getData().setVisualization(ProcessVisualization.HEAT);
    updateSingleProcessReport(simpleAlert.getReportId(), report);

    // then
    // scheduler does not contain any triggers
    assertThat(
      embeddedOptimizeExtensionRule.getAlertService().getScheduler().getJobGroupNames().size(),
      is(0)
    );

    //alert is deleted from ES
    List<AlertDefinitionDto> alertDefinitionDtos =
      embeddedOptimizeExtensionRule
        .getRequestExecutor()
        .buildGetAllAlertsRequest()
        .executeAndReturnList(AlertDefinitionDto.class, 200);

    assertThat(alertDefinitionDtos.size(), is(0));
  }

  @Test
  public void reportDeletionRemovesAlert() throws Exception {
    //given
    AlertCreationDto simpleAlert = setupBasicProcessAlert();

    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateAlertRequest(simpleAlert)
      .execute();

    assertThat(response.getStatus(), is(200));

    // when
    response =
      embeddedOptimizeExtensionRule
        .getRequestExecutor()
        .buildDeleteReportRequest(simpleAlert.getReportId(), true)
        .execute();

    // then
    assertThat(response.getStatus(), is(204));
    assertThat(
      embeddedOptimizeExtensionRule.getAlertService().getScheduler().getJobGroupNames().size(),
      is(0)
    );

    response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildGetAllAlertsRequest()
      .execute();

    assertThat(response.getStatus(), is(200));
    List<AlertDefinitionDto> alertDefinitionDtos = response.readEntity(
      new GenericType<List<AlertDefinitionDto>>() {
      }
    );
    assertThat(alertDefinitionDtos.size(), is(0));
  }

  @Test
  public void createNewAlertPropagatedToScheduler() throws Exception {
    //given
    AlertCreationDto simpleAlert = setupBasicProcessAlert();

    // when
    String id =
      embeddedOptimizeExtensionRule
        .getRequestExecutor()
        .buildCreateAlertRequest(simpleAlert)
        .execute(String.class, 200);

    // then
    assertThat(id, is(notNullValue()));
    assertThat(
      embeddedOptimizeExtensionRule.getAlertService().getScheduler().getJobGroupNames().size(),
      is(1)
    );
  }

  @Test
  public void createNewAlertDecisionReport() {
    //given
    AlertCreationDto simpleAlert = setupBasicDecisionAlert();
    setEmailConfiguration();

    // when
    String id =
      embeddedOptimizeExtensionRule
        .getRequestExecutor()
        .buildCreateAlertRequest(simpleAlert)
        .execute(String.class, 200);

    // then
    assertThat(greenMail.waitForIncomingEmail(3000, 1), is(true));
  }

  @Test
  public void deletedAlertsAreRemovedFromScheduler() throws Exception {
    //given
    AlertCreationDto simpleAlert = setupBasicProcessAlert();

    String alertId = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateAlertRequest(simpleAlert)
      .execute(IdDto.class, 200)
      .getId();

    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildDeleteAlertRequest(alertId)
      .execute();

    // then
    assertThat(response.getStatus(), is(204));
    assertThat(
      embeddedOptimizeExtensionRule.getAlertService().getScheduler().getJobGroupNames().size(),
      is(0)
    );
  }

  @Test
  public void updatedAlertIsRescheduled() throws Exception {
    //given
    AlertCreationDto simpleAlert = setupBasicProcessAlert();

    String alertId =
      embeddedOptimizeExtensionRule
        .getRequestExecutor()
        .buildCreateAlertRequest(simpleAlert)
        .execute(IdDto.class, 200)
        .getId();

    Trigger trigger = embeddedOptimizeExtensionRule.getAlertService().getScheduler().getTrigger(getTriggerKey(alertId));
    assertThat(
      getNextFireTime(trigger).truncatedTo(ChronoUnit.SECONDS),
      is(
        Instant.now().plus(1, ChronoUnit.SECONDS).truncatedTo(ChronoUnit.SECONDS)
      )
    );

    // when
    simpleAlert.getCheckInterval().setValue(30);

    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildUpdateAlertRequest(alertId, simpleAlert)
      .execute();

    // then
    assertThat(response.getStatus(), is(204));

    List<AlertDefinitionDto> allAlerts = getAllAlerts();
    assertThat(allAlerts.get(0).isTriggered(), is(false));

    trigger = embeddedOptimizeExtensionRule.getAlertService().getScheduler().getTrigger(getTriggerKey(alertId));
    int secondsUntilItShouldFireNext = 30;
    assertThatTriggerIsInRange(trigger, secondsUntilItShouldFireNext);
  }

  private void assertThatTriggerIsInRange(Trigger trigger, int secondsUntilItShouldFireNext) {
    // we cannot check for exact time since
    // time is running while we check for the supposed next trigger time
    // and then the check might be by one second off. Thus we check if the
    // the next trigger is within +/- 1 second bound.
    Instant nextTimeToFire = getNextFireTime(trigger);
    Instant lowerBound = Instant.now()
      .plus(secondsUntilItShouldFireNext - 1, ChronoUnit.SECONDS)
      .truncatedTo(ChronoUnit.SECONDS);
    Instant upperBound = Instant.now()
      .plus(secondsUntilItShouldFireNext + 1, ChronoUnit.SECONDS)
      .truncatedTo(ChronoUnit.SECONDS);
    assertTrue(lowerBound.isBefore(nextTimeToFire));
    assertTrue(upperBound.isAfter(nextTimeToFire));
  }

  private TriggerKey getTriggerKey(String alertId) {
    return new TriggerKey(alertId + "-check-trigger", "statusCheck-trigger");
  }

  @Test
  public void testScheduleTriggers() throws Exception {

    //given
    String reportId = startProcessAndCreateReport();
    setEmailConfiguration();

    // when
    AlertCreationDto simpleAlert = createSimpleAlert(reportId);
    embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateAlertRequest(simpleAlert)
      .execute();

    assertThat(greenMail.waitForIncomingEmail(3000, 1), is(true));

    //then
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails.length, is(1));
    assertThat(emails[0].getSubject(), is("[Camunda-Optimize] - Report status"));
    String content = emails[0].getContent().toString();
    assertThat(content, containsString(simpleAlert.getName()));
    assertThat(
      content,
      containsString(String.format("http://localhost:%d/#/report/" + reportId, getOptimizeHttpPort()))
    );
  }

  @Test
  public void testAccessUrlInAlertNotification() throws Exception {
    //given
    String reportId = startProcessAndCreateReport();
    setEmailConfiguration();
    embeddedOptimizeExtensionRule.getConfigurationService().setContainerAccessUrlValue("http://test.de:8090");


    // when
    AlertCreationDto simpleAlert = createSimpleAlert(reportId);
    embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateAlertRequest(simpleAlert)
      .execute();

    assertThat(greenMail.waitForIncomingEmail(3000, 1), is(true));

    //then
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails.length, is(1));
    String content = emails[0].getContent().toString();
    assertThat(content, containsString("http://test.de:8090/#/report/" + reportId));
  }


  private String startProcessAndCreateReport() {
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess();
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    return createAndStoreProcessNumberReport(processDefinition);
  }


  @Test
  public void testCronMinutesInterval() throws Exception {
    //given
    AlertService alertService = embeddedOptimizeExtensionRule.getAlertService();
    int intervalValue = 11;
    AlertDefinitionDto fakeReportAlert = getAlertDefinitionDto(intervalValue, "Minutes");

    JobDetail jobDetail = alertService.createStatusCheckJobDetails(fakeReportAlert);
    Trigger trigger = alertService.createStatusCheckTrigger(fakeReportAlert, jobDetail);
    Instant now = Instant.now();
    alertService.getScheduler().scheduleJob(jobDetail, trigger);
    Instant nextFireTime = getNextFireTime(trigger).truncatedTo(ChronoUnit.MINUTES);

    assertThat(
      nextFireTime,
      is(now.truncatedTo(ChronoUnit.MINUTES)
           .plus(intervalValue, ChronoUnit.MINUTES)
           .truncatedTo(ChronoUnit.MINUTES))
    );
  }

  @Test
  public void testCronHoursInterval() throws Exception {
    //given
    AlertService alertService = embeddedOptimizeExtensionRule.getAlertService();
    int intervalValue = 11;
    AlertDefinitionDto fakeReportAlert = getAlertDefinitionDto(intervalValue, "Hours");

    JobDetail jobDetail = alertService.createStatusCheckJobDetails(fakeReportAlert);
    Trigger trigger = alertService.createStatusCheckTrigger(fakeReportAlert, jobDetail);
    Instant now = Instant.now();
    alertService.getScheduler().scheduleJob(jobDetail, trigger);
    Instant nextFireTime = getNextFireTime(trigger);

    Instant targetTime = now.plus(intervalValue, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);

    assertThat(nextFireTime.truncatedTo(ChronoUnit.HOURS), is(targetTime));
  }

  @Test
  public void testCronDaysInterval() throws Exception {
    //given
    AlertService alertService = embeddedOptimizeExtensionRule.getAlertService();
    int intervalValue = 5;
    AlertDefinitionDto fakeReportAlert = getAlertDefinitionDto(intervalValue, "Days");

    JobDetail jobDetail = alertService.createStatusCheckJobDetails(fakeReportAlert);
    Trigger trigger = alertService.createStatusCheckTrigger(fakeReportAlert, jobDetail);
    Instant now = Instant.now();
    alertService.getScheduler().scheduleJob(jobDetail, trigger);
    Instant nextFireTime = getNextFireTime(trigger);

    Instant targetTime = now.truncatedTo(ChronoUnit.DAYS).plus(intervalValue, ChronoUnit.DAYS);

    assertThat(nextFireTime.truncatedTo(ChronoUnit.DAYS), is(targetTime));
  }

  @Test
  public void testCronWeeksInterval() throws Exception {
    //given
    AlertService alertService = embeddedOptimizeExtensionRule.getAlertService();
    int intervalValue = 5;
    AlertDefinitionDto fakeReportAlert = getAlertDefinitionDto(intervalValue, "Weeks");

    JobDetail jobDetail = alertService.createStatusCheckJobDetails(fakeReportAlert);
    Trigger trigger = alertService.createStatusCheckTrigger(fakeReportAlert, jobDetail);
    alertService.getScheduler().scheduleJob(jobDetail, trigger);
    Instant nextFireTime = getNextFireTime(trigger);

    assertThat(
      nextFireTime.truncatedTo(ChronoUnit.SECONDS),
      is(Instant.now().plus(intervalValue * 7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS))
    );
  }

  private Instant getNextFireTime(Trigger cronTrigger) {
    return cronTrigger.getNextFireTime().toInstant();
  }

  private AlertDefinitionDto getAlertDefinitionDto(int intervalValue, String intervalUnit) {
    AlertCreationDto simpleAlert = createSimpleAlert("fakeReport", intervalValue, intervalUnit);

    AlertDefinitionDto alert = createFakeReport(simpleAlert);
    alert.setId(UUID.randomUUID().toString());
    return alert;
  }

  private AlertDefinitionDto createFakeReport(AlertCreationDto fakeReportAlert) {
    AlertDefinitionDto result = new AlertDefinitionDto();

    AlertUtil.mapBasicFields(fakeReportAlert, result);
    return result;
  }

  private List<AlertDefinitionDto> getAllAlerts() {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildGetAllAlertsRequest()
      .executeAndReturnList(AlertDefinitionDto.class, 200);
  }
}
