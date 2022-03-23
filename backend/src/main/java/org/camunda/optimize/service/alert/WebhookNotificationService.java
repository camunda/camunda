/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.alert;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.alert.AlertNotificationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.WebhookConfiguration;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Map;

@AllArgsConstructor
@Component
@Slf4j
public class WebhookNotificationService implements NotificationService {
  private final Client client = ClientBuilder.newClient();
  private final ConfigurationService configurationService;

  @Override
  public void notify(@NonNull final AlertNotificationDto notification) {
    final AlertDefinitionDto alert = notification.getAlert();
    final String destination = alert.getWebhook();
    if (StringUtils.isEmpty(destination)) {
      log.debug(
        "No webhook configured for alert [id: {}, name: {}], no action performed.", alert.getId(), alert.getName()
      );
      return;
    }

    final Map<String, WebhookConfiguration> webhookConfigurationMap = configurationService.getConfiguredWebhooks();
    if (!webhookConfigurationMap.containsKey(destination)) {
      log.error(
        "Could not send webhook notification as the configuration for webhook with name {} " +
          "no longer exists in the configuration file.",
        destination
      );
      return;
    }

    final WebhookConfiguration webhookConfiguration = webhookConfigurationMap.get(destination);
    log.debug(
      "Sending webhook notification for alert [id: {}, name: {}] to webhook: '{}'.",
      alert.getId(), alert.getName(), destination
    );
    sendWebhookRequest(notification, webhookConfiguration);
  }

  @PreDestroy
  public void close() {
    client.close();
  }

  private void sendWebhookRequest(final AlertNotificationDto notification, final WebhookConfiguration webhook) {
    final WebTarget webTarget = client.target(webhook.getUrl());

    Invocation.Builder builder = webTarget.request();
    if (webhook.getHeaders() != null) {
      for (Map.Entry<String, String> headerEntry : webhook.getHeaders().entrySet()) {
        builder = builder.header(headerEntry.getKey(), headerEntry.getValue());
      }
    }

    try (final Response response = builder.method(webhook.getHttpMethod(), composePayload(notification, webhook))) {
      if (!Response.Status.Family.familyOf(response.getStatus()).equals(Response.Status.Family.SUCCESSFUL)) {
        log.error("Webhook call failed, response status code: {}", response.getStatus());
      }
    }
  }

  private Entity<String> composePayload(final AlertNotificationDto notification, final WebhookConfiguration webhook) {
    String payloadString = webhook.getDefaultPayload();
    for (WebhookConfiguration.Placeholder placeholder : WebhookConfiguration.Placeholder.values()) {
      final String value = placeholder.extractValue(notification);
      // replace potential real new lines with escape
      payloadString = payloadString.replace(placeholder.getPlaceholderString(), value.replace("\n", "\\n"));
    }

    final MediaType mediaType = resolveMediaTypeFromHeaders(webhook);
    return Entity.entity(payloadString, mediaType);
  }

  private MediaType resolveMediaTypeFromHeaders(final WebhookConfiguration webhook) {
    if (webhook.getHeaders() == null || !webhook.getHeaders().containsKey(HttpHeaders.CONTENT_TYPE)) {
      return MediaType.APPLICATION_JSON_TYPE;
    } else {
      return MediaType.valueOf(webhook.getHeaders().get(HttpHeaders.CONTENT_TYPE));
    }
  }
}
