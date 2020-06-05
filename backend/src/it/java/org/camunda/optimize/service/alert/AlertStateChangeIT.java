/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.alert;

import com.icegreen.greenmail.util.GreenMail;
import lombok.SneakyThrows;
import org.camunda.optimize.AbstractAlertIT;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.verify.VerificationTimes;

import javax.mail.internet.MimeMessage;

import static org.camunda.optimize.test.optimize.UiConfigurationClient.TEST_CUSTOM_CONTENT_TYPE_WEBHOOK_NAME;
import static org.camunda.optimize.test.optimize.UiConfigurationClient.TEST_INVALID_PORT_WEBHOOK_NAME;
import static org.camunda.optimize.test.optimize.UiConfigurationClient.TEST_WEBHOOK_METHOD;
import static org.camunda.optimize.test.optimize.UiConfigurationClient.TEST_WEBHOOK_NAME;
import static org.camunda.optimize.test.optimize.UiConfigurationClient.TEST_WEBHOOK_URL_PATH;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.model.HttpRequest.request;

@ExtendWith(MockServerExtension.class)
@MockServerSettings(ports = {8787})
public class AlertStateChangeIT extends AbstractAlertIT {

  private GreenMail greenMail;

  @BeforeEach
  public void cleanUp() throws Exception {
    embeddedOptimizeExtension.getAlertService().getScheduler().clear();
    greenMail = initGreenMail();
  }

  @AfterEach
  public void tearDown() {
    greenMail.stop();
  }

  @Test
  public void reminderJobsSendEmailEveryTime() throws Exception {
    //given
    setEmailConfiguration();
    long daysToShift = 0L;
    long durationInSec = 2L;
    ProcessInstanceEngineDto processInstance = deployWithTimeShift(daysToShift, durationInSec);
    String reportId = createAndStoreDurationNumberReportInNewCollection(processInstance);
    AlertCreationDto simpleAlert = createAlertWithReminder(reportId);
    String id = alertClient.createAlert(simpleAlert);

    triggerAndCompleteCheckJob(id);

    //when
    greenMail.purgeEmailFromAllMailboxes();
    triggerAndCompleteReminderJob(id);

    //reminder received once
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails.length, is(1));

    //when
    greenMail.purgeEmailFromAllMailboxes();
    triggerAndCompleteReminderJob(id);

    //then
    //reminder received twice
    emails = greenMail.getReceivedMessages();
    assertThat(emails.length, is(1));

    //when
    greenMail.purgeEmailFromAllMailboxes();
    triggerAndCompleteCheckJob(id);

    //then
    //reminder is not received
    emails = greenMail.getReceivedMessages();
    assertThat(emails.length, is(0));
  }

  @Test
  @SneakyThrows
  public void reminderJobsSendWebhookRequestEveryTime(MockServerClient client) {
    // given
    setWebhookConfiguration();

    long daysToShift = 0L;
    long durationInSec = 2L;
    ProcessInstanceEngineDto processInstance = deployWithTimeShift(daysToShift, durationInSec);
    String reportId = createAndStoreDurationNumberReportInNewCollection(processInstance);
    AlertCreationDto simpleAlert = alertClient.createSimpleAlert(reportId);
    simpleAlert.setWebhook(TEST_WEBHOOK_NAME);
    addReminderToAlert(simpleAlert);
    String alertId = alertClient.createAlert(simpleAlert);

    // when/then
    triggerAndCompleteCheckJob(alertId);
    clearWebhookRequestsFromClient(client);

    triggerAndCompleteReminderJob(alertId);
    assertWebhookRequestReceived(client, 1);

    triggerAndCompleteReminderJob(alertId);
    assertWebhookRequestReceived(client, 2);
  }

  @Test
  @SneakyThrows
  public void sendWebhookRequestWithCustomContentType(MockServerClient client) {
    // given
    setWebhookConfiguration();

    long daysToShift = 0L;
    long durationInSec = 2L;
    ProcessInstanceEngineDto processInstance = deployWithTimeShift(daysToShift, durationInSec);
    String reportId = createAndStoreDurationNumberReportInNewCollection(processInstance);
    AlertCreationDto simpleAlert = alertClient.createSimpleAlert(reportId);
    simpleAlert.setWebhook(TEST_CUSTOM_CONTENT_TYPE_WEBHOOK_NAME);
    addReminderToAlert(simpleAlert);
    String alertId = alertClient.createAlert(simpleAlert);

    // when/then
    clearWebhookRequestsFromClient(client);
    triggerAndCompleteCheckJob(alertId);
    assertWebhookRequestReceived(client, 1);
  }

  @Test
  @SneakyThrows
  public void sendWebhookRequestWithInvalidUrlDoesNotFail(MockServerClient client) {
    // given
    setWebhookConfiguration();

    long daysToShift = 0L;
    long durationInSec = 2L;
    ProcessInstanceEngineDto processInstance = deployWithTimeShift(daysToShift, durationInSec);
    String reportId = createAndStoreDurationNumberReportInNewCollection(processInstance);
    AlertCreationDto simpleAlert = alertClient.createSimpleAlert(reportId);
    simpleAlert.setWebhook(TEST_INVALID_PORT_WEBHOOK_NAME);
    addReminderToAlert(simpleAlert);
    String alertId = alertClient.createAlert(simpleAlert);

    // when/then
    clearWebhookRequestsFromClient(client);
    triggerAndCompleteCheckJob(alertId);
    assertWebhookRequestReceived(client, 0);
  }

  @Test
  public void changeNotificationIsNotSentByDefault(MockServerClient client) throws Exception {
    //given
    setEmailConfiguration();
    setWebhookConfiguration();
    long daysToShift = 0L;
    long durationInSec = 2L;

    ProcessInstanceEngineDto processInstance = deployWithTimeShift(daysToShift, durationInSec);

    // when
    String reportId = createAndStoreDurationNumberReportInNewCollection(processInstance);
    AlertCreationDto simpleAlert = alertClient.createSimpleAlert(reportId);
    simpleAlert.setWebhook(TEST_WEBHOOK_NAME);
    addReminderToAlert(simpleAlert);
    String id = alertClient.createAlert(simpleAlert);

    triggerAndCompleteCheckJob(id);

    //when
    engineIntegrationExtension.startProcessInstance(processInstance.getDefinitionId());
    importAllEngineEntitiesFromScratch();
    greenMail.purgeEmailFromAllMailboxes();
    clearWebhookRequestsFromClient(client);

    //then
    triggerAndCompleteReminderJob(id);

    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails.length, is(0));
    assertWebhookRequestReceived(client, 0);
  }

  @Test
  public void changeNotificationIsSent(MockServerClient client) throws Exception {
    //given
    setEmailConfiguration();
    setWebhookConfiguration();

    long daysToShift = 0L;
    long durationInSec = 2L;
    ProcessInstanceEngineDto processInstance = deployWithTimeShift(daysToShift, durationInSec);
    final String collectionId = collectionClient.createNewCollectionWithProcessScope(processInstance);
    final String reportId = createAndStoreDurationNumberReport(
      collectionId,
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion()
    );
    AlertCreationDto simpleAlert = alertClient.createSimpleAlert(reportId);
    simpleAlert.setWebhook(TEST_WEBHOOK_NAME);
    simpleAlert.setFixNotification(true);
    addReminderToAlert(simpleAlert);
    String alertId = alertClient.createAlert(simpleAlert);

    triggerAndCompleteCheckJob(alertId);

    //when
    engineIntegrationExtension.startProcessInstance(processInstance.getDefinitionId());
    importAllEngineEntitiesFromScratch();

    greenMail.purgeEmailFromAllMailboxes();
    clearWebhookRequestsFromClient(client);
    triggerAndCompleteReminderJob(alertId);
    //then

    assertWebhookRequestReceived(client, 1);
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails.length, is(1));
    assertThat(emails[0].getSubject(), is("[Camunda-Optimize] - Report status"));
    String content = emails[0].getContent().toString();
    assertThat(content, containsString(simpleAlert.getName()));
    assertThat(content, containsString("is not exceeded anymore."));
    assertThat(
      content,
      containsString(String.format(
        "http://localhost:%d/#/collection/%s/report/%s/",
        getOptimizeHttpPort(),
        collectionId,
        reportId
      ))
    );
  }

  @Test
  @SneakyThrows
  public void emailNotificationStillSentWhenWebhookFails() {
    // given
    setEmailConfiguration();
    setWebhookConfiguration();

    long daysToShift = 0L;
    long durationInSec = 2L;
    ProcessInstanceEngineDto processInstance = deployWithTimeShift(daysToShift, durationInSec);
    String reportId = createAndStoreDurationNumberReportInNewCollection(processInstance);
    AlertCreationDto simpleAlert = alertClient.createSimpleAlert(reportId);
    simpleAlert.setWebhook(TEST_WEBHOOK_NAME);
    addReminderToAlert(simpleAlert);
    String alertId = alertClient.createAlert(simpleAlert);

    // when
    triggerAndCompleteCheckJob(alertId);

    // then
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails.length, is(1));
  }

  @Test
  @SneakyThrows
  public void webhookNotificationStillSentWhenEmailFails(MockServerClient client) {
    // given
    setWebhookConfiguration();
    setEmailConfiguration();
    embeddedOptimizeExtension.getConfigurationService()
      .setAlertEmailPort(9999); // set to incorrect port so that email notifications fail

    long daysToShift = 0L;
    long durationInSec = 2L;
    ProcessInstanceEngineDto processInstance = deployWithTimeShift(daysToShift, durationInSec);
    String reportId = createAndStoreDurationNumberReportInNewCollection(processInstance);
    AlertCreationDto simpleAlert = alertClient.createSimpleAlert(reportId);
    simpleAlert.setWebhook(TEST_WEBHOOK_NAME);
    addReminderToAlert(simpleAlert);
    String alertId = alertClient.createAlert(simpleAlert);

    // when
    clearWebhookRequestsFromClient(client);
    triggerAndCompleteCheckJob(alertId);

    // then
    assertWebhookRequestReceived(client, 1);
  }

  @Test
  public void notificationFormatsDurationThresholdCorrectly() throws Exception {
    //given
    setEmailConfiguration();

    long daysToShift = 0L;
    long durationInSec = 30000000L;

    ProcessInstanceEngineDto processInstance = deployWithTimeShift(daysToShift, durationInSec);
    String reportId = createAndStoreDurationNumberReportInNewCollection(processInstance);
    AlertCreationDto simpleAlert = createAlertWithReminder(reportId);
    simpleAlert.setFixNotification(true);
    simpleAlert.setThreshold(258165800L); // = 2d 23h 42min 45s 800ms

    String id = alertClient.createAlert(simpleAlert);

    triggerAndCompleteCheckJob(id);

    // when
    engineIntegrationExtension.startProcessInstance(processInstance.getDefinitionId());
    importAllEngineEntitiesFromScratch();

    greenMail.purgeEmailFromAllMailboxes();
    triggerAndCompleteReminderJob(id);

    // then
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails.length, is(1));
    String content = emails[0].getContent().toString();
    assertThat(content, containsString("2d 23h 42min 45s 800ms"));
  }

  private void assertWebhookRequestReceived(final MockServerClient client, final Integer times) {
    client.verify(
      request()
        .withMethod(TEST_WEBHOOK_METHOD)
        .withPath(TEST_WEBHOOK_URL_PATH),
      VerificationTimes.exactly(times)
    );
  }

  private void clearWebhookRequestsFromClient(final MockServerClient client) {
    client.clear(request().withPath(TEST_WEBHOOK_URL_PATH));
  }

  private AlertCreationDto createAlertWithReminder(String reportId) {
    AlertCreationDto simpleAlert = alertClient.createSimpleAlert(reportId);
    return addReminderToAlert(simpleAlert);
  }

  private AlertCreationDto addReminderToAlert(AlertCreationDto alert) {
    AlertInterval reminderInterval = new AlertInterval();
    reminderInterval.setValue(3);
    reminderInterval.setUnit("Seconds");
    alert.setReminder(reminderInterval);
    alert.setThreshold(1500);
    alert.getCheckInterval().setValue(5);
    return alert;
  }
}
