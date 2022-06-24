/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.alert;

import com.google.common.collect.ImmutableMap;
import io.github.netmikey.logunit.api.LogCapturer;
import org.camunda.optimize.AbstractAlertIT;
import org.camunda.optimize.JettyConfig;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.alert.AlertNotificationType;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationRequestDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants;
import org.camunda.optimize.service.util.configuration.ProxyConfiguration;
import org.camunda.optimize.service.util.configuration.WebhookConfiguration;
import org.camunda.optimize.service.util.configuration.WebhookConfiguration.Placeholder;
import org.camunda.optimize.test.optimize.AlertClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.matchers.MatchType;
import org.mockserver.verify.VerificationTimes;

import javax.ws.rs.HttpMethod;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.camunda.optimize.service.util.configuration.WebhookConfiguration.Placeholder.ALERT_CURRENT_VALUE;
import static org.camunda.optimize.service.util.configuration.WebhookConfiguration.Placeholder.ALERT_MESSAGE;
import static org.camunda.optimize.service.util.configuration.WebhookConfiguration.Placeholder.ALERT_NAME;
import static org.camunda.optimize.test.optimize.UiConfigurationClient.TEST_CUSTOM_CONTENT_TYPE_WEBHOOK_NAME;
import static org.camunda.optimize.test.optimize.UiConfigurationClient.TEST_INVALID_PORT_WEBHOOK_NAME;
import static org.camunda.optimize.test.optimize.UiConfigurationClient.TEST_WEBHOOK_HOST;
import static org.camunda.optimize.test.optimize.UiConfigurationClient.TEST_WEBHOOK_METHOD;
import static org.camunda.optimize.test.optimize.UiConfigurationClient.TEST_WEBHOOK_NAME;
import static org.camunda.optimize.test.optimize.UiConfigurationClient.TEST_WEBHOOK_URL_PATH;
import static org.camunda.optimize.test.optimize.UiConfigurationClient.TEST_WEBHOOK_WITH_PROXY_NAME;
import static org.camunda.optimize.test.optimize.UiConfigurationClient.createWebhookHostUrl;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.JsonBody.json;

@ExtendWith(MockServerExtension.class)
@MockServerSettings
public class AlertWebhookIT extends AbstractAlertIT {

  @RegisterExtension
  protected final LogCapturer alertJobLogs = LogCapturer.create().captureForType(AlertJob.class);

  @Test
  public void sendWebhookRequestWithAllPlaceholderBody(MockServerClient client) {
    // given
    setWebhookConfiguration(client.getPort());

    final ProcessInstanceEngineDto processInstance = deployWithTimeShift(0L, 2L);
    final String collectionId = collectionClient.createNewCollectionWithProcessScope(processInstance);
    final String reportId = createAndStoreDurationNumberReport(
      collectionId, processInstance.getProcessDefinitionKey(), ReportConstants.ALL_VERSIONS
    );
    final AlertCreationRequestDto simpleAlert = alertClient.createSimpleAlert(reportId);
    simpleAlert.setWebhook(TEST_WEBHOOK_NAME);
    simpleAlert.setFixNotification(true);
    addReminderToAlert(simpleAlert);

    final String alertId = alertClient.createAlert(simpleAlert);

    // when
    triggerAndCompleteCheckJob(alertId);

    // then
    final String expectedNewAlertPayload = createActiveAlertExpectedWebhookPayloadWithAllPlaceholders(
      collectionId, reportId, simpleAlert, AlertNotificationType.NEW, "2000.0"
    );
    verifyWebhookCallWithPayload(client, expectedNewAlertPayload);

    // when
    triggerAndCompleteReminderJob(alertId);

    // then
    final String expectedReminderAlertPayload = createActiveAlertExpectedWebhookPayloadWithAllPlaceholders(
      collectionId, reportId, simpleAlert, AlertNotificationType.REMINDER, "2000.0"
    );
    verifyWebhookCallWithPayload(client, expectedReminderAlertPayload);

    // when
    deployWithTimeShift(0L, 1L);
    triggerAndCompleteReminderJob(alertId);

    // then
    final String expectedResolvedAlertPayload = createResolvedAlertExpectedWebhookPayloadWithAllPlaceholders(
      collectionId, reportId, simpleAlert, AlertNotificationType.RESOLVED, "1500.0"
    );
    verifyWebhookCallWithPayload(client, expectedResolvedAlertPayload);
  }

  @Test
  public void configureWebhookWithUnsupportedHttpGETMethod(MockServerClient client) {
    // given
    client.reset();
    final Map<String, WebhookConfiguration> webhookConfigurationMap = Map.of(
      TEST_WEBHOOK_NAME,
      uiConfigurationClient.createWebhookConfiguration(
        createWebhookHostUrl(7654) + TEST_WEBHOOK_URL_PATH,
        ImmutableMap.of("Content-Type", "application/json"),
        HttpMethod.GET,
        "payload"
      )
    );
    embeddedOptimizeExtension.getConfigurationService().setConfiguredWebhooks(webhookConfigurationMap);
    embeddedOptimizeExtension.reloadConfiguration();
    final String alertId = setupWebhookAlert(TEST_WEBHOOK_NAME);

    // when
    triggerAndCompleteCheckJob(alertId);

    // then
    client.verifyZeroInteractions();
    alertJobLogs.assertContains("Exception thrown while trying to send notification");
  }

  @Test
  public void configureWebhookWithUnsupportedHttpDELETEMethod(MockServerClient client) {
    // given
    client.reset();
    final Map<String, WebhookConfiguration> webhookConfigurationMap = Map.of(
      TEST_WEBHOOK_NAME,
      uiConfigurationClient.createWebhookConfiguration(
        createWebhookHostUrl(7654) + TEST_WEBHOOK_URL_PATH,
        ImmutableMap.of("Content-Type", "application/json"),
        HttpMethod.DELETE,
        "payload"
      )
    );
    embeddedOptimizeExtension.getConfigurationService().setConfiguredWebhooks(webhookConfigurationMap);
    embeddedOptimizeExtension.reloadConfiguration();
    final String alertId = setupWebhookAlert(TEST_WEBHOOK_NAME);

    // when
    triggerAndCompleteCheckJob(alertId);

    // then
    client.verifyZeroInteractions();
    alertJobLogs.assertContains("Exception thrown while trying to send notification");
  }

  @Test
  public void sendWebhookRequestWithSomePlaceholderBody(MockServerClient client) {
    // given
    client.reset();
    final String payloadTemplate = "{\n" +
      Stream.of(ALERT_NAME, ALERT_CURRENT_VALUE)
        .map(placeholder -> String.format("\"%s\": \"%s\"", placeholder.name(), placeholder.getPlaceholderString()))
        .collect(Collectors.joining(",\n"))
      + "\n}";
    setWebhookConfiguration(client.getPort(), payloadTemplate);

    final String alertId = setupWebhookAlert(TEST_WEBHOOK_NAME);

    // when
    triggerAndCompleteCheckJob(alertId);

    // then
    final String expectedAlertPayload = "{\n" +
      String.format("\"%s\": \"%s\",\n", ALERT_NAME.name(), AlertClient.TEST_ALERT_NAME) +
      String.format("\"%s\": \"%s\",\n", ALERT_CURRENT_VALUE.name(), "2000.0")
      + "\n}";
    verifyWebhookCallWithPayload(client, expectedAlertPayload);
  }

  @Test
  public void sendWebhookRequestWithProxyConfigured(MockServerClient client) {
    // given
    final String payloadTemplate = "{\n" +
      Stream.of(ALERT_NAME, ALERT_CURRENT_VALUE)
        .map(placeholder -> String.format("\"%s\": \"%s\"", placeholder.name(), placeholder.getPlaceholderString()))
        .collect(Collectors.joining(",\n"))
      + "\n}";

    final String webhookHost = "128.0.0.1:7654";
    Map<String, WebhookConfiguration> webhookConfigurationMap = Map.of(
      TEST_WEBHOOK_WITH_PROXY_NAME,
      uiConfigurationClient.createWebhookConfiguration(
        "http://" + webhookHost + TEST_WEBHOOK_URL_PATH,
        ImmutableMap.of("Content-Type", "application/json"),
        TEST_WEBHOOK_METHOD,
        payloadTemplate,
        // We use the Mock Server as the proxy to capture the request
        new ProxyConfiguration(true, TEST_WEBHOOK_HOST, client.getPort(), false)
      )
    );
    embeddedOptimizeExtension.getConfigurationService().setConfiguredWebhooks(webhookConfigurationMap);
    embeddedOptimizeExtension.reloadConfiguration();

    final String alertId = setupWebhookAlert(TEST_WEBHOOK_WITH_PROXY_NAME);

    // when
    triggerAndCompleteCheckJob(alertId);

    // then
    client.verify(
      request()
        .withPath(TEST_WEBHOOK_URL_PATH)
        .withMethod(TEST_WEBHOOK_METHOD)
        .withHeader("Host", webhookHost)
        .withHeader("Proxy-Connection", "Keep-Alive")
        .withKeepAlive(true)
        .withSecure(false),
      VerificationTimes.once()
    );
  }

  @Test
  public void sendMultipleWebhookRequestWithProxyConfiguredForOneWebhook(MockServerClient client) {
    // given
    final String payloadTemplate = "{\n" +
      Stream.of(ALERT_NAME, ALERT_CURRENT_VALUE)
        .map(placeholder -> String.format("\"%s\": \"%s\"", placeholder.name(), placeholder.getPlaceholderString()))
        .collect(Collectors.joining(",\n"))
      + "\n}";

    final String proxyWebhookHost = "128.0.0.1:7654";
    Map<String, WebhookConfiguration> webhookConfigurationMap = Map.of(
      TEST_WEBHOOK_WITH_PROXY_NAME,
      uiConfigurationClient.createWebhookConfiguration(
        "http://" + proxyWebhookHost + TEST_WEBHOOK_URL_PATH,
        ImmutableMap.of("Content-Type", "application/json"),
        TEST_WEBHOOK_METHOD,
        payloadTemplate,
        // We use the Mock Server as the proxy to capture the request
        new ProxyConfiguration(true, TEST_WEBHOOK_HOST, client.getPort(), false)
      ),
      TEST_WEBHOOK_NAME,
      uiConfigurationClient.createWebhookConfiguration(
        createWebhookHostUrl(client.getPort()) + TEST_WEBHOOK_URL_PATH,
        ImmutableMap.of("Content-Type", "application/json"),
        TEST_WEBHOOK_METHOD,
        payloadTemplate
      )
    );
    embeddedOptimizeExtension.getConfigurationService().setConfiguredWebhooks(webhookConfigurationMap);
    embeddedOptimizeExtension.reloadConfiguration();

    final String proxyWebhookAlertId = setupWebhookAlert(TEST_WEBHOOK_WITH_PROXY_NAME);
    final String nonProxyWebhookAlertId = setupWebhookAlert(TEST_WEBHOOK_NAME);

    // when
    triggerAndCompleteCheckJob(proxyWebhookAlertId);
    triggerAndCompleteCheckJob(nonProxyWebhookAlertId);

    // then the proxy webhook notification request is sent
    client.verify(
      request()
        .withPath(TEST_WEBHOOK_URL_PATH)
        .withMethod(TEST_WEBHOOK_METHOD)
        .withHeader("Host", proxyWebhookHost)
        .withHeader("Proxy-Connection", "Keep-Alive")
        .withKeepAlive(true)
        .withSecure(false)
    );
    // and so is the non-proxied request
    client.verify(
      request()
        .withPath(TEST_WEBHOOK_URL_PATH)
        .withMethod(TEST_WEBHOOK_METHOD)
        .withHeader("Host", TEST_WEBHOOK_HOST + ":" + client.getPort())
    );
  }

  @Test
  public void reminderJobsSendWebhookRequestEveryTime(MockServerClient client) {
    // given
    setWebhookConfiguration(client.getPort());

    String alertId = setupWebhookAlert(TEST_WEBHOOK_NAME);

    // when
    triggerAndCompleteCheckJob(alertId);
    clearWebhookRequestsFromClient(client);

    triggerAndCompleteReminderJob(alertId);

    // then
    assertWebhookRequestReceived(client, 1);

    // when
    triggerAndCompleteReminderJob(alertId);
    // then
    assertWebhookRequestReceived(client, 2);
  }

  @Test
  public void sendWebhookRequestWithCustomContentType(MockServerClient client) {
    // given
    setWebhookConfiguration(client.getPort());

    String alertId = setupWebhookAlert(TEST_CUSTOM_CONTENT_TYPE_WEBHOOK_NAME);

    // when
    clearWebhookRequestsFromClient(client);
    triggerAndCompleteCheckJob(alertId);

    // then
    assertWebhookRequestReceived(client, 1);
  }

  @Test
  public void sendWebhookRequestWithInvalidUrlDoesNotFail(MockServerClient client) {
    // given
    setWebhookConfiguration(client.getPort());

    String alertId = setupWebhookAlert(TEST_INVALID_PORT_WEBHOOK_NAME);

    // when
    clearWebhookRequestsFromClient(client);
    triggerAndCompleteCheckJob(alertId);

    // then
    assertWebhookRequestReceived(client, 0);
  }

  @Test
  public void webhookNotificationStillSentWhenEmailFails(MockServerClient client) {
    // given
    client.reset();
    setWebhookConfiguration(client.getPort());
    setEmailConfiguration();
    embeddedOptimizeExtension.getConfigurationService()
      .setAlertEmailPort(9999); // set to incorrect port so that email notifications fail

    String alertId = setupWebhookAlert(TEST_WEBHOOK_NAME);

    // when
    clearWebhookRequestsFromClient(client);
    triggerAndCompleteCheckJob(alertId);

    // then
    assertWebhookRequestReceived(client, 1);
  }

  private String setupWebhookAlert(final String testWebhookName) {
    final ProcessInstanceEngineDto processInstance = deployWithTimeShift(0L, 2L);
    final String reportId = createAndStoreDurationNumberReportInNewCollection(processInstance);
    final AlertCreationRequestDto simpleAlert = alertClient.createSimpleAlert(reportId);
    simpleAlert.setWebhook(testWebhookName);
    addReminderToAlert(simpleAlert);
    return alertClient.createAlert(simpleAlert);
  }

  private void verifyWebhookCallWithPayload(final MockServerClient client, final String expectedNewAlertPayload) {
    client.verify(
      request()
        .withPath(TEST_WEBHOOK_URL_PATH)
        .withMethod(TEST_WEBHOOK_METHOD)
        .withBody(json(expectedNewAlertPayload, MatchType.STRICT)),
      VerificationTimes.once()
    );
  }

  private String createActiveAlertExpectedWebhookPayloadWithAllPlaceholders(final String collectionId,
                                                                            final String reportId,
                                                                            final AlertCreationRequestDto simpleAlert,
                                                                            final AlertNotificationType alertType,
                                                                            final String currentValue) {
    final String expectedLink = createReportLink(collectionId, reportId, alertType);
    final String expectedMessage = createAlertMessage(expectedLink);
    return createPayloadJson(simpleAlert, alertType, currentValue, expectedLink, expectedMessage);
  }

  private String createResolvedAlertExpectedWebhookPayloadWithAllPlaceholders(final String collectionId,
                                                                              final String reportId,
                                                                              final AlertCreationRequestDto simpleAlert,
                                                                              final AlertNotificationType alertType,
                                                                              final String currentValue) {
    final String expectedLink = createReportLink(collectionId, reportId, alertType);
    final String expectedMessage = createAlertResolvedMessage(expectedLink);
    return createPayloadJson(simpleAlert, alertType, currentValue, expectedLink, expectedMessage);
  }

  private String createPayloadJson(final AlertCreationRequestDto simpleAlert, final AlertNotificationType alertType,
                                   final String currentValue, final String expectedLink, final String expectedMessage) {
    return "{\n" +
      createJsonPropertyLine(ALERT_MESSAGE, expectedMessage) + ",\n" +
      createJsonPropertyLine(Placeholder.ALERT_NAME, simpleAlert.getName()) + ",\n" +
      createJsonPropertyLine(Placeholder.ALERT_REPORT_LINK, expectedLink) + ",\n" +
      createJsonPropertyLine(ALERT_CURRENT_VALUE, currentValue) + ",\n" +
      createJsonPropertyLine(Placeholder.ALERT_THRESHOLD_VALUE, "1500.0") + ",\n" +
      createJsonPropertyLine(Placeholder.ALERT_THRESHOLD_OPERATOR, ">") + ",\n" +
      createJsonPropertyLine(Placeholder.ALERT_TYPE, alertType.getId()) + ",\n" +
      createJsonPropertyLine(Placeholder.ALERT_INTERVAL, "5") + ",\n" +
      createJsonPropertyLine(Placeholder.ALERT_INTERVAL_UNIT, "seconds") +
      "\n}";
  }

  private String createReportLink(final String collectionId, final String reportId, final AlertNotificationType type) {
    return String.format(
      "http://localhost:%d/#/collection/%s/report/%s?utm_source=" + type.getUtmSource() + "&utm_medium=webhook",
      embeddedOptimizeExtension.getBean(JettyConfig.class).getPort(EnvironmentPropertiesConstants.HTTP_PORT_KEY),
      collectionId,
      reportId
    );
  }

  private String createAlertMessage(final String expectedLink) {
    return "Camunda Optimize - Report Status\\nAlert name:" +
      " test alert\\nReport name: something\\nStatus: Given threshold [0d 0h 0min 1s 500ms] was exceeded. Current" +
      " value: 0d 0h 0min 2s 0ms. Please check your Optimize report for more information! \\n" + expectedLink;
  }

  private String createAlertResolvedMessage(final String expectedLink) {
    return "Camunda Optimize - Report Status\\nAlert name: test alert\\nReport name: something\\nStatus: Given " +
      "threshold [0d 0h 0min 1s 500ms] is not exceeded anymore. Current value: 0d 0h 0min 1s 500ms. Please check your" +
      " Optimize report for more information! \\n" + expectedLink;
  }

  private String createJsonPropertyLine(final Placeholder alertName, final String name) {
    return String.format("\"%s\": \"%s\"", alertName, name);
  }

}
