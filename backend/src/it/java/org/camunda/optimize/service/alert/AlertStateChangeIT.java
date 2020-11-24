/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.alert;

import com.icegreen.greenmail.util.GreenMail;
import lombok.SneakyThrows;
import org.camunda.optimize.AbstractAlertIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationRequestDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.alert.AlertThresholdOperator;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.verify.VerificationTimes;

import javax.mail.internet.MimeMessage;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.optimize.UiConfigurationClient.TEST_CUSTOM_CONTENT_TYPE_WEBHOOK_NAME;
import static org.camunda.optimize.test.optimize.UiConfigurationClient.TEST_INVALID_PORT_WEBHOOK_NAME;
import static org.camunda.optimize.test.optimize.UiConfigurationClient.TEST_WEBHOOK_METHOD;
import static org.camunda.optimize.test.optimize.UiConfigurationClient.TEST_WEBHOOK_NAME;
import static org.camunda.optimize.test.optimize.UiConfigurationClient.TEST_WEBHOOK_URL_PATH;
import static org.camunda.optimize.test.util.ProcessReportDataType.VARIABLE_AGGREGATION_GROUP_BY_NONE;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;
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
    // given
    setEmailConfiguration();
    long daysToShift = 0L;
    long durationInSec = 2L;
    ProcessInstanceEngineDto processInstance = deployWithTimeShift(daysToShift, durationInSec);
    String reportId = createAndStoreDurationNumberReportInNewCollection(processInstance);
    AlertCreationRequestDto simpleAlert = createAlertWithReminder(reportId);
    String id = alertClient.createAlert(simpleAlert);

    triggerAndCompleteCheckJob(id);

    // when
    greenMail.purgeEmailFromAllMailboxes();
    triggerAndCompleteReminderJob(id);

    //reminder received once
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails).hasSize(1);

    // when
    greenMail.purgeEmailFromAllMailboxes();
    triggerAndCompleteReminderJob(id);

    // then
    //reminder received twice
    emails = greenMail.getReceivedMessages();
    assertThat(emails).hasSize(1);

    // when
    greenMail.purgeEmailFromAllMailboxes();
    triggerAndCompleteCheckJob(id);

    // then
    //reminder is not received
    emails = greenMail.getReceivedMessages();
    assertThat(emails).hasSize(0);
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
    AlertCreationRequestDto simpleAlert = alertClient.createSimpleAlert(reportId);
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
    AlertCreationRequestDto simpleAlert = alertClient.createSimpleAlert(reportId);
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
    AlertCreationRequestDto simpleAlert = alertClient.createSimpleAlert(reportId);
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
    // given
    setEmailConfiguration();
    setWebhookConfiguration();
    long daysToShift = 0L;
    long durationInSec = 2L;

    ProcessInstanceEngineDto processInstance = deployWithTimeShift(daysToShift, durationInSec);

    // when
    String reportId = createAndStoreDurationNumberReportInNewCollection(processInstance);
    AlertCreationRequestDto simpleAlert = alertClient.createSimpleAlert(reportId);
    simpleAlert.setWebhook(TEST_WEBHOOK_NAME);
    addReminderToAlert(simpleAlert);
    String id = alertClient.createAlert(simpleAlert);

    triggerAndCompleteCheckJob(id);

    // when
    engineIntegrationExtension.startProcessInstance(processInstance.getDefinitionId());
    importAllEngineEntitiesFromScratch();
    greenMail.purgeEmailFromAllMailboxes();
    clearWebhookRequestsFromClient(client);

    // then
    triggerAndCompleteReminderJob(id);

    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails).hasSize(0);
    assertWebhookRequestReceived(client, 0);
  }

  @Test
  public void changeNotificationIsSent(MockServerClient client) throws Exception {
    // given
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
    AlertCreationRequestDto simpleAlert = alertClient.createSimpleAlert(reportId);
    simpleAlert.setWebhook(TEST_WEBHOOK_NAME);
    simpleAlert.setFixNotification(true);
    addReminderToAlert(simpleAlert);
    String alertId = alertClient.createAlert(simpleAlert);

    triggerAndCompleteCheckJob(alertId);

    // when
    engineIntegrationExtension.startProcessInstance(processInstance.getDefinitionId());
    importAllEngineEntitiesFromScratch();

    greenMail.purgeEmailFromAllMailboxes();
    clearWebhookRequestsFromClient(client);
    triggerAndCompleteReminderJob(alertId);
    // then

    assertWebhookRequestReceived(client, 1);
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails).hasSize(1);
    assertThat(emails[0].getSubject()).isEqualTo("[Camunda-Optimize] - Report status");
    String content = emails[0].getContent().toString();
    assertThat(content).containsSequence(simpleAlert.getName());
    assertThat(content).containsSequence("is not exceeded anymore.");
    assertThat(content).containsSequence(
      String.format(
        "http://localhost:%d/#/collection/%s/report/%s/",
        getOptimizeHttpPort(),
        collectionId,
        reportId
      )
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
    AlertCreationRequestDto simpleAlert = alertClient.createSimpleAlert(reportId);
    simpleAlert.setWebhook(TEST_WEBHOOK_NAME);
    addReminderToAlert(simpleAlert);
    String alertId = alertClient.createAlert(simpleAlert);

    // when
    triggerAndCompleteCheckJob(alertId);

    // then
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails).hasSize(1);
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
    AlertCreationRequestDto simpleAlert = alertClient.createSimpleAlert(reportId);
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
    // given
    setEmailConfiguration();

    long daysToShift = 0L;
    long durationInSec = 30000000L;

    ProcessInstanceEngineDto processInstance = deployWithTimeShift(daysToShift, durationInSec);
    String reportId = createAndStoreDurationNumberReportInNewCollection(processInstance);
    AlertCreationRequestDto simpleAlert = createAlertWithReminder(reportId);
    simpleAlert.setFixNotification(true);
    simpleAlert.setThreshold(258165800.0); // = 2d 23h 42min 45s 800ms

    String id = alertClient.createAlert(simpleAlert);

    triggerAndCompleteCheckJob(id);

    // when
    engineIntegrationExtension.startProcessInstance(processInstance.getDefinitionId());
    importAllEngineEntitiesFromScratch();

    greenMail.purgeEmailFromAllMailboxes();
    triggerAndCompleteReminderJob(id);

    // then
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails).hasSize(1);
    String content = emails[0].getContent().toString();
    assertThat(content).containsSequence("2d 23h 42min 45s 800ms");
  }

  @Test
  public void noNotificationIsSendIfResultIsNull() throws Exception {
    // given
    setEmailConfiguration();

    ProcessInstanceEngineDto processInstance = engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram());
    processInstance.setProcessDefinitionKey("definitionKeyThatDoesNotExistAndWillLeadToNoResults");
    String reportId = createAndStoreDurationNumberReportInNewCollection(processInstance);
    AlertCreationRequestDto simpleAlert = createAlertWithReminder(reportId);
    String id = alertClient.createAlert(simpleAlert);

    // when
    greenMail.purgeEmailFromAllMailboxes();
    triggerAndCompleteCheckJob(id);

    // then
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails).hasSize(0);
  }

  @Test
  public void alertsWorkForVariableReports() throws Exception {
    // given
    setEmailConfiguration();

    Map<String, Object> variables = new HashMap<>();
    variables.put("var", 5.0);
    final ProcessDefinitionEngineDto definition = deploySimpleServiceTaskProcess("aProcess");
    engineIntegrationExtension.startProcessInstance(definition.getId(), variables);
    importAllEngineEntitiesFromScratch();

    String reportId = createAndStoreVariableAggregationReport(definition, "var", VariableType.DOUBLE);
    AlertCreationRequestDto simpleAlert = createAlertWithReminder(reportId);
    simpleAlert.setThreshold(10.0);
    simpleAlert.setThresholdOperator(AlertThresholdOperator.LESS);
    String id = alertClient.createAlert(simpleAlert);

    // when
    greenMail.purgeEmailFromAllMailboxes();
    triggerAndCompleteCheckJob(id);

    // then
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails).hasSize(1);
  }

  private String createAndStoreVariableAggregationReport(final ProcessDefinitionEngineDto definition,
                                                         final String variableName,
                                                         final VariableType variableType) {
    String collectionId = collectionClient.createNewCollectionWithProcessScope(definition);
    return createVariableReport(definition, collectionId, variableName, variableType);
  }

  private String createVariableReport(final ProcessDefinitionEngineDto definition,
                                      final String collectionId,
                                      final String variableName, final VariableType variableType) {
    final ProcessReportDataDto reportDataDto = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(definition.getKey())
      .setProcessDefinitionVersion(definition.getVersionAsString())
      .setVariableName(variableName)
      .setVariableType(variableType)
      .setReportDataType(VARIABLE_AGGREGATION_GROUP_BY_NONE)
      .build();
    SingleProcessReportDefinitionRequestDto report = new SingleProcessReportDefinitionRequestDto();
    report.setData(reportDataDto);
    report.setName("something");
    report.setCollectionId(collectionId);
    return reportClient.createSingleProcessReport(report);
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

  private AlertCreationRequestDto createAlertWithReminder(String reportId) {
    AlertCreationRequestDto simpleAlert = alertClient.createSimpleAlert(reportId);
    return addReminderToAlert(simpleAlert);
  }

  private AlertCreationRequestDto addReminderToAlert(AlertCreationRequestDto alert) {
    AlertInterval reminderInterval = new AlertInterval();
    reminderInterval.setValue(3);
    reminderInterval.setUnit("Seconds");
    alert.setReminder(reminderInterval);
    alert.setThreshold(1500.0);
    alert.getCheckInterval().setValue(5);
    return alert;
  }
}
