/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.alert;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.camunda.optimize.dto.optimize.alert.AlertNotificationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ProxyConfiguration;
import org.camunda.optimize.service.util.configuration.WebhookConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class WebhookNotificationService implements NotificationService, ConfigurationReloadable {

  private final ConfigurationService configurationService;
  private Map<String, CloseableHttpClient> webhookClientsByWebhookName;

  public WebhookNotificationService(final ConfigurationService configurationService) {
    this.configurationService = configurationService;
    webhookClientsByWebhookName = buildHttpClientMap();
  }

  @PreDestroy
  public void close() {
    webhookClientsByWebhookName.forEach((key, value) -> {
      try {
        value.close();
      } catch (IOException e) {
        log.error("Could not close Http client for webhook with name: {}. Exception: {}", key, e);
      }
    });
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    close();
    webhookClientsByWebhookName = buildHttpClientMap();
  }

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
    sendWebhookRequest(notification, webhookConfiguration, destination);
  }

  private void sendWebhookRequest(final AlertNotificationDto notification, final WebhookConfiguration webhook,
                                  final String webhookName) {
    HttpEntityEnclosingRequestBase request = buildRequestFromMethod(webhook);
    request.setEntity(new StringEntity(composePayload(notification, webhook), resolveContentTypeFromHeaders(webhook)));

    if (webhook.getHeaders() != null) {
      request.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
      for (Map.Entry<String, String> headerEntry : webhook.getHeaders().entrySet()) {
        request.setHeader(headerEntry.getKey(), headerEntry.getValue());
      }
    }

    try (final CloseableHttpResponse response = webhookClientsByWebhookName.get(webhookName).execute(request)) {
      final Response.Status statusCode = Response.Status.fromStatusCode(
        response.getStatusLine().getStatusCode()
      );
      if (!Response.Status.Family.familyOf(statusCode.getStatusCode()).equals(Response.Status.Family.SUCCESSFUL)) {
        log.error("Unexpected response when sending webhook notification: " + statusCode);
      }
    } catch (IOException e) {
      throw new OptimizeRuntimeException("There was a problem when sending webhook notification.", e);
    }
  }

  private HttpEntityEnclosingRequestBase buildRequestFromMethod(final WebhookConfiguration webhook) {
    final String httpMethod = webhook.getHttpMethod();
    HttpEntityEnclosingRequestBase request;
    switch (httpMethod) {
      case HttpMethod.POST:
        request = new HttpPost(webhook.getUrl());
        break;
      case HttpMethod.PATCH:
        request = new HttpPatch(webhook.getUrl());
        break;
      case HttpMethod.PUT:
        request = new HttpPut(webhook.getUrl());
        break;
      default:
        throw new OptimizeRuntimeException("Http method not possible for webhook notifications: " + httpMethod);
    }
    return request;
  }

  private String composePayload(final AlertNotificationDto notification, final WebhookConfiguration webhook) {
    String payloadString = webhook.getDefaultPayload();
    for (WebhookConfiguration.Placeholder placeholder : WebhookConfiguration.Placeholder.values()) {
      final String value = placeholder.extractValue(notification);
      // replace potential real new lines with escape
      payloadString = payloadString.replace(placeholder.getPlaceholderString(), value.replace("\n", "\\n"));
    }
    return payloadString;
  }

  private ContentType resolveContentTypeFromHeaders(final WebhookConfiguration webhook) {
    if (webhook.getHeaders() == null || !webhook.getHeaders().containsKey(HttpHeaders.CONTENT_TYPE)) {
      return ContentType.APPLICATION_JSON;
    } else {
      return ContentType.create(webhook.getHeaders().get(HttpHeaders.CONTENT_TYPE));
    }
  }

  private CloseableHttpClient createClientForWebhook(final WebhookConfiguration webhookConfig) {
    final ProxyConfiguration proxyConfiguration = webhookConfig.getProxy();
    if (proxyConfiguration == null || !proxyConfiguration.isEnabled()) {
      return HttpClients.createDefault();
    }
    HttpHost proxy = new HttpHost(
      proxyConfiguration.getHost(),
      proxyConfiguration.getPort(),
      proxyConfiguration.isSslEnabled() ? "https" : "http"
    );
    return HttpClients.custom()
      .setRoutePlanner(new DefaultProxyRoutePlanner(proxy))
      .build();
  }

  private Map<String, CloseableHttpClient> buildHttpClientMap() {
    Map<String, CloseableHttpClient> httpClientMap = new HashMap<>();
    configurationService.getConfiguredWebhooks()
      .entrySet()
      .forEach((entry -> httpClientMap.put(entry.getKey(), createClientForWebhook(entry.getValue()))));
    return httpClientMap;
  }

}
