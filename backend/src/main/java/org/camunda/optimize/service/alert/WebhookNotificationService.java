/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.alert;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.WebhookConfiguration;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@Component
@Slf4j
public class WebhookNotificationService implements NotificationService {
  private final ConfigurationService configurationService;

  @Override
  public void notifyRecipients(final String text, final List<String> recipients) {
    recipients.forEach(recipient -> notifyRecipient(text, recipient));
  }

  private void notifyRecipient(final String alertContent, final String destination) {
    if (StringUtils.isEmpty(destination)) {
      return;
    }
    final Map<String, WebhookConfiguration> webhookConfigurationMap = configurationService.getConfiguredWebhooks();

    if (!webhookConfigurationMap.containsKey(destination)) {
      log.error(
        "Could not send webhook notification as the configuration for webhook with name {} " +
          "no longer exists in the service-config.",
        destination
      );
      return;
    }

    final WebhookConfiguration webhookConfiguration = webhookConfigurationMap.get(destination);
    log.debug("Sending webhook notification '{}' to webhook [{}].", alertContent, destination);
    sendWebhookRequest(alertContent, webhookConfiguration);
  }

  private void sendWebhookRequest(final String alertContent, final WebhookConfiguration webhook) {
    final Client client = ClientBuilder.newClient();
    final WebTarget webTarget = client.target(webhook.getUrl());
    Invocation.Builder builder = webTarget.request();

    if (webhook.getHeaders() != null) {
      for (Map.Entry<String, String> headerEntry : webhook.getHeaders().entrySet()) {
        builder = builder.header(headerEntry.getKey(), headerEntry.getValue());
      }
    }

    Response response = builder.method(webhook.getHttpMethod(), composePayload(webhook, alertContent));
    if (200 > response.getStatus() || response.getStatus() > 299) {
      log.error("Webhook call failed, response status code: {}", response.getStatus());
    }
  }

  private Entity composePayload(final WebhookConfiguration webhook, final String alertContent) {
    final String payloadString = webhook.getDefaultPayload()
      .replace(WebhookConfiguration.ALERT_MESSAGE_PLACEHOLDER, alertContent);
    final MediaType mediaType = resolveMediaTypeFromHeaders(webhook);
    return Entity.entity(payloadString, mediaType);
  }

  private MediaType resolveMediaTypeFromHeaders(final WebhookConfiguration webhook) {
    if (webhook.getHeaders() == null
      || !webhook.getHeaders().containsKey(HttpHeaders.CONTENT_TYPE)) {
      return MediaType.APPLICATION_JSON_TYPE;
    } else {
      return MediaType.valueOf(webhook.getHeaders().get(HttpHeaders.CONTENT_TYPE));
    }
  }
}
