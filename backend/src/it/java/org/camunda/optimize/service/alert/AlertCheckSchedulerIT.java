/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.alert;

import org.camunda.optimize.JettyConfig;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationRequestDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertIntervalUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.FlowNodesGroupByDto;
import org.junit.jupiter.api.Test;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerKey;

import javax.mail.internet.MimeMessage;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants.HTTP_PORT_KEY;

public class AlertCheckSchedulerIT extends AbstractAlertEmailIT {

  @Test
  public void reportUpdateToNotNumberRemovesAlert() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    String collectionId = collectionClient.createNewCollectionWithProcessScope(processDefinition);
    String reportId = createNewProcessReportAsUser(collectionId, processDefinition);
    AlertCreationRequestDto simpleAlert = alertClient.createSimpleAlert(reportId);

    alertClient.createAlert(simpleAlert);

    // when
    SingleProcessReportDefinitionRequestDto report = getProcessNumberReportDefinitionDto(collectionId, processDefinition);
    report.getData().setGroupBy(new FlowNodesGroupByDto());
    report.getData().setVisualization(ProcessVisualization.HEAT);
    reportClient.updateSingleProcessReport(simpleAlert.getReportId(), report, true);

    // then
    // scheduler does not contain any triggers
    assertThat(embeddedOptimizeExtension.getAlertService().getScheduler().getJobGroupNames()).isEmpty();

    //alert is deleted from ES
    List<AlertDefinitionDto> alertDefinitionDtos = alertClient.getAllAlerts();

    assertThat(alertDefinitionDtos).isEmpty();
  }

  @Test
  public void reportDeletionRemovesAlert() throws Exception {
    // given
    AlertCreationRequestDto simpleAlert = setupBasicProcessAlert();

    alertClient.createAlert(simpleAlert);

    // when
    reportClient.deleteReport(simpleAlert.getReportId(), true);

    // then
    assertThat(embeddedOptimizeExtension.getAlertService().getScheduler().getJobGroupNames()).isEmpty();

    List<AlertDefinitionDto> alertDefinitionDtos = alertClient.getAllAlerts();
    assertThat(alertDefinitionDtos).isEmpty();
  }

  @Test
  public void createNewAlertPropagatedToScheduler() throws Exception {
    // given
    AlertCreationRequestDto simpleAlert = setupBasicProcessAlert();

    // when
    String id = alertClient.createAlert(simpleAlert);

    // then
    assertThat(id).isNotNull();
    assertThat(embeddedOptimizeExtension.getAlertService().getScheduler().getJobGroupNames()).hasSize(1);
  }

  @Test
  public void createNewAlertDecisionReport() {
    // given
    AlertCreationRequestDto simpleAlert = setupBasicDecisionAlert();
    setEmailConfiguration();

    // when
    alertClient.createAlert(simpleAlert);

    // then
    assertThat(greenMail.waitForIncomingEmail(3000, 1)).isTrue();
  }

  @Test
  public void deletedAlertsAreRemovedFromScheduler() throws Exception {
    // given
    AlertCreationRequestDto simpleAlert = setupBasicProcessAlert();

    String alertId = alertClient.createAlert(simpleAlert);

    // when
    alertClient.deleteAlert(alertId);

    // then
    assertThat(embeddedOptimizeExtension.getAlertService().getScheduler().getJobGroupNames()).isEmpty();
  }

  @Test
  public void updatedAlertIsRescheduled() throws Exception {
    // given
    AlertCreationRequestDto simpleAlert = setupBasicProcessAlert();

    String alertId = alertClient.createAlert(simpleAlert);

    Trigger trigger = embeddedOptimizeExtension.getAlertService().getScheduler().getTrigger(getTriggerKey(alertId));
    assertThat(getNextFireTime(trigger).truncatedTo(ChronoUnit.SECONDS))
      .isEqualTo(Instant.now().plus(1, ChronoUnit.SECONDS).truncatedTo(ChronoUnit.SECONDS));

    // when
    simpleAlert.getCheckInterval().setValue(30);

    alertClient.updateAlert(alertId, simpleAlert);

    // then
    List<AlertDefinitionDto> allAlerts = alertClient.getAllAlerts();
    assertThat(allAlerts.get(0).isTriggered()).isFalse();

    trigger = embeddedOptimizeExtension.getAlertService().getScheduler().getTrigger(getTriggerKey(alertId));
    int secondsUntilItShouldFireNext = 30;
    assertThatTriggerIsInRange(trigger, secondsUntilItShouldFireNext);
  }

  @Test
  public void testScheduleTriggers() throws Exception {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    final String collectionId = collectionClient.createNewCollectionWithProcessScope(processDefinition);
    final String reportId = createNewProcessReportAsUser(collectionId, processDefinition);
    setEmailConfiguration();

    // when
    AlertCreationRequestDto simpleAlert = alertClient.createSimpleAlert(reportId);
    alertClient.createAlert(simpleAlert);

    assertThat(greenMail.waitForIncomingEmail(3000, 1)).isTrue();

    // then
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails).hasSize(1);
    String branding = embeddedOptimizeExtension.getConfigurationService().getAlertEmailCompanyBranding();
    assertThat(emails[0].getSubject()).isEqualTo(
      "[" + branding + "-Optimize] - Report status");
    String content = emails[0].getContent().toString();
    assertThat(content)
      .contains(branding)
      .contains(simpleAlert.getName())
      .contains(String.format(
        "http://localhost:%d/#/collection/%s/report/%s?utm_source=alert_new_triggered&utm_medium=email",
        embeddedOptimizeExtension.getBean(JettyConfig.class).getPort(HTTP_PORT_KEY),
        collectionId,
        reportId
      ));
  }

  @Test
  public void testCompanyBrandingInSubjectAndBody() throws Exception {

    // given
    final ProcessDefinitionEngineDto processDefinition = deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();
    String testBrandingName = "Your name here";
    embeddedOptimizeExtension.getConfigurationService().setAlertEmailCompanyBranding(testBrandingName);

    final String collectionId = collectionClient.createNewCollectionWithProcessScope(processDefinition);
    final String reportId = createNewProcessReportAsUser(collectionId, processDefinition);
    setEmailConfiguration();

    // when
    AlertCreationRequestDto simpleAlert = alertClient.createSimpleAlert(reportId);
    alertClient.createAlert(simpleAlert);

    assertThat(greenMail.waitForIncomingEmail(3000, 1)).isTrue();

    // then
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails).hasSize(1);
    assertThat(emails[0].getSubject()).isEqualTo(
      "[" + testBrandingName + "-Optimize] - Report status");
    String content = emails[0].getContent().toString();
    assertThat(content).contains(testBrandingName);
  }

  @Test
  public void testAccessUrlInAlertNotification() throws Exception {
    // given

    final ProcessDefinitionEngineDto processDefinition = deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    final String collectionId = collectionClient.createNewCollectionWithProcessScope(processDefinition);
    final String reportId = createNewProcessReportAsUser(collectionId, processDefinition);
    setEmailConfiguration();
    embeddedOptimizeExtension.getConfigurationService().setContainerAccessUrlValue("http://test.de:8090");


    // when
    AlertCreationRequestDto simpleAlert = alertClient.createSimpleAlert(reportId);
    alertClient.createAlert(simpleAlert);

    assertThat(greenMail.waitForIncomingEmail(3000, 1)).isTrue();

    // then
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails).hasSize(1);
    String branding = embeddedOptimizeExtension.getConfigurationService().getAlertEmailCompanyBranding();
    assertThat(emails[0].getSubject()).isEqualTo(
      "[" + branding + "-Optimize] - Report status");
    String content = emails[0].getContent().toString();
    assertThat(content)
      .contains(branding)
      .contains(String.format(
        "http://test.de:8090/#/collection/%s/report/%s?utm_source=alert_new_triggered&utm_medium=email",
        collectionId,
        reportId
      ));
  }

  @Test
  public void testCronMinutesInterval() throws Exception {
    // given
    AlertService alertService = embeddedOptimizeExtension.getAlertService();
    int intervalValue = 11;
    AlertDefinitionDto fakeReportAlert = getAlertDefinitionDto(intervalValue, AlertIntervalUnit.MINUTES);

    JobDetail jobDetail = alertService.createStatusCheckJobDetails(fakeReportAlert);
    Trigger trigger = alertService.createStatusCheckTrigger(fakeReportAlert, jobDetail);
    Instant now = Instant.now();
    alertService.getScheduler().scheduleJob(jobDetail, trigger);
    Instant nextFireTime = getNextFireTime(trigger).truncatedTo(ChronoUnit.MINUTES);

    assertThat(nextFireTime).isEqualTo(
      now.truncatedTo(ChronoUnit.MINUTES)
        .plus(intervalValue, ChronoUnit.MINUTES)
        .truncatedTo(ChronoUnit.MINUTES));
  }

  @Test
  public void testCronHoursInterval() throws Exception {
    // given
    AlertService alertService = embeddedOptimizeExtension.getAlertService();
    int intervalValue = 11;
    AlertDefinitionDto fakeReportAlert = getAlertDefinitionDto(intervalValue, AlertIntervalUnit.HOURS);

    JobDetail jobDetail = alertService.createStatusCheckJobDetails(fakeReportAlert);
    Trigger trigger = alertService.createStatusCheckTrigger(fakeReportAlert, jobDetail);
    Instant now = Instant.now();
    alertService.getScheduler().scheduleJob(jobDetail, trigger);
    Instant nextFireTime = getNextFireTime(trigger);

    Instant targetTime = now.plus(intervalValue, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);

    assertThat(nextFireTime.truncatedTo(ChronoUnit.HOURS)).isEqualTo(targetTime);
  }

  @Test
  public void testCronDaysInterval() throws Exception {
    // given
    AlertService alertService = embeddedOptimizeExtension.getAlertService();
    int intervalValue = 5;
    AlertDefinitionDto fakeReportAlert = getAlertDefinitionDto(intervalValue, AlertIntervalUnit.DAYS);

    JobDetail jobDetail = alertService.createStatusCheckJobDetails(fakeReportAlert);
    Trigger trigger = alertService.createStatusCheckTrigger(fakeReportAlert, jobDetail);
    Instant now = Instant.now();
    alertService.getScheduler().scheduleJob(jobDetail, trigger);
    Instant nextFireTime = getNextFireTime(trigger);

    Instant targetTime = now.truncatedTo(ChronoUnit.DAYS).plus(intervalValue, ChronoUnit.DAYS);

    assertThat(nextFireTime.truncatedTo(ChronoUnit.DAYS)).isEqualTo(targetTime);
  }

  @Test
  public void testCronWeeksInterval() throws Exception {
    // given
    AlertService alertService = embeddedOptimizeExtension.getAlertService();
    int intervalValue = 5;
    AlertDefinitionDto fakeReportAlert = getAlertDefinitionDto(intervalValue, AlertIntervalUnit.WEEKS);

    JobDetail jobDetail = alertService.createStatusCheckJobDetails(fakeReportAlert);
    Trigger trigger = alertService.createStatusCheckTrigger(fakeReportAlert, jobDetail);
    alertService.getScheduler().scheduleJob(jobDetail, trigger);
    Instant nextFireTime = getNextFireTime(trigger);

    assertThat(nextFireTime.truncatedTo(ChronoUnit.SECONDS))
      .isEqualTo(Instant.now().plus(intervalValue * 7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS));
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
    assertThat(lowerBound.isBefore(nextTimeToFire)).isTrue();
    assertThat(upperBound.isAfter(nextTimeToFire)).isTrue();
  }

  private TriggerKey getTriggerKey(String alertId) {
    return new TriggerKey(alertId + "-check-trigger", "statusCheck-trigger");
  }

  private Instant getNextFireTime(Trigger cronTrigger) {
    return cronTrigger.getNextFireTime().toInstant();
  }

  private AlertDefinitionDto getAlertDefinitionDto(int intervalValue, AlertIntervalUnit intervalUnit) {
    AlertCreationRequestDto simpleAlert = alertClient.createSimpleAlert("fakeReport", intervalValue, intervalUnit);

    AlertDefinitionDto alert = createFakeReport(simpleAlert);
    alert.setId(UUID.randomUUID().toString());
    return alert;
  }

  private AlertDefinitionDto createFakeReport(AlertCreationRequestDto fakeReportAlert) {
    AlertDefinitionDto result = new AlertDefinitionDto();

    AlertUtil.mapBasicFields(fakeReportAlert, result);
    return result;
  }
}
