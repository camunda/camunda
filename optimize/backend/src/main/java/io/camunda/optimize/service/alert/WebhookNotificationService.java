/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.alert;

import io.camunda.optimize.dto.optimize.alert.AlertNotificationDto;
import io.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ProxyConfiguration;
import io.camunda.optimize.service.util.configuration.WebhookConfiguration;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.Method;
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
import org.slf4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class WebhookNotificationService
    implements AlertNotificationService, ConfigurationReloadable {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(WebhookNotificationService.class);
  private final ConfigurationService configurationService;
  private Map<String, CloseableHttpClient> webhookClientsByWebhookName;

  public WebhookNotificationService(final ConfigurationService configurationService) {
    this.configurationService = configurationService;
    webhookClientsByWebhookName = buildHttpClientMap();
  }

  @PreDestroy
  public void close() {
    webhookClientsByWebhookName.forEach(
        (key, value) -> {
          try {
            value.close();
          } catch (final IOException e) {
            LOG.error(
                "Could not close Http client for webhook with name: {}. Exception: {}", key, e);
          }
        });
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    close();
    webhookClientsByWebhookName = buildHttpClientMap();
  }

  @Override
  public void notify(final AlertNotificationDto notification) {
    if (notification == null) {
      throw new OptimizeRuntimeException("Notification cannot be null");
    }

    final AlertDefinitionDto alert = notification.getAlert();
    final String destination = alert.getWebhook();
    if (StringUtils.isEmpty(destination)) {
      LOG.debug(
          "No webhook configured for alert [id: {}, name: {}], no action performed.",
          alert.getId(),
          alert.getName());
      return;
    }

    final Map<String, WebhookConfiguration> webhookConfigurationMap =
        configurationService.getConfiguredWebhooks();
    if (!webhookConfigurationMap.containsKey(destination)) {
      LOG.error(
          "Could not send webhook notification as the configuration for webhook with name {} "
              + "no longer exists in the configuration file.",
          destination);
      return;
    }

    final WebhookConfiguration webhookConfiguration = webhookConfigurationMap.get(destination);
    LOG.info(
        "Sending webhook notification for alert [id: {}, name: {}] to webhook: '{}'.",
        alert.getId(),
        alert.getName(),
        destination);
    sendWebhookRequest(notification, webhookConfiguration, destination);
  }

  @Override
  public String getNotificationDescription() {
    return "webhook notification";
  }

  private void sendWebhookRequest(
      final AlertNotificationDto notification,
      final WebhookConfiguration webhook,
      final String webhookName) {
    final HttpEntityEnclosingRequestBase request = buildRequestFromMethod(webhook);
    request.setEntity(
        new StringEntity(
            composePayload(notification, webhook), resolveContentTypeFromHeaders(webhook)));

    if (webhook.getHeaders() != null) {
      request.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
      for (final Map.Entry<String, String> headerEntry : webhook.getHeaders().entrySet()) {
        request.setHeader(headerEntry.getKey(), headerEntry.getValue());
      }
    }

    try (final CloseableHttpResponse response =
        webhookClientsByWebhookName.get(webhookName).execute(request)) {
      final HttpStatus status = HttpStatus.resolve(response.getStatusLine().getStatusCode());
      if (!status.is2xxSuccessful()) {
        LOG.error("Unexpected response when sending webhook notification: " + status);
      }
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(
          "There was a problem when sending webhook notification.", e);
    }
  }

  private HttpEntityEnclosingRequestBase buildRequestFromMethod(
      final WebhookConfiguration webhook) {
    final String httpMethod = webhook.getHttpMethod();
    final HttpEntityEnclosingRequestBase request;
    switch (Method.normalizedValueOf(httpMethod)) {
      case Method.POST:
        request = new HttpPost(webhook.getUrl());
        break;
      case Method.PATCH:
        request = new HttpPatch(webhook.getUrl());
        break;
      case Method.PUT:
        request = new HttpPut(webhook.getUrl());
        break;
      default:
        throw new OptimizeRuntimeException(
            "Http method not possible for webhook notifications: " + httpMethod);
    }
    return request;
  }

  private String composePayload(
      final AlertNotificationDto notification, final WebhookConfiguration webhook) {
    String payloadString = webhook.getDefaultPayload();
    for (final WebhookConfiguration.Placeholder placeholder :
        WebhookConfiguration.Placeholder.values()) {
      final String value = placeholder.extractValue(notification);
      // replace potential real new lines with escape
      payloadString =
          payloadString.replace(placeholder.getPlaceholderString(), value.replace("\n", "\\n"));
    }
    return payloadString;
  }

  private ContentType resolveContentTypeFromHeaders(final WebhookConfiguration webhook) {
    if (webhook.getHeaders() == null
        || !webhook.getHeaders().containsKey(HttpHeaders.CONTENT_TYPE)) {
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
    final HttpHost proxy =
        new HttpHost(
            proxyConfiguration.getHost(),
            proxyConfiguration.getPort(),
            proxyConfiguration.isSslEnabled() ? "https" : "http");
    return HttpClients.custom().setRoutePlanner(new DefaultProxyRoutePlanner(proxy)).build();
  }

  private Map<String, CloseableHttpClient> buildHttpClientMap() {
    final Map<String, CloseableHttpClient> httpClientMap = new HashMap<>();
    configurationService
        .getConfiguredWebhooks()
        .entrySet()
        .forEach(
            (entry -> httpClientMap.put(entry.getKey(), createClientForWebhook(entry.getValue()))));
    return httpClientMap;
  }
}
