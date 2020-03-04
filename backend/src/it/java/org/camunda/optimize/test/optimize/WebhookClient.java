/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.service.util.configuration.WebhookConfiguration;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

@AllArgsConstructor
public class WebhookClient {
  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public List<String> getAllWebhooks() {
    return getRequestExecutor()
      .buildGetAllWebhooksRequest()
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .executeAndReturnList(String.class, Response.Status.OK.getStatusCode());
  }

  public Response getAllWebhooksWithoutAuthentication() {
    return getRequestExecutor()
      .buildGetAllWebhooksRequest()
      .withoutAuthentication()
      .execute();
  }

  public Map<String, WebhookConfiguration> createSimpleWebhookConfigurationMap(Set<String> names) {
    Map<String, WebhookConfiguration> webhookMap = new HashMap<>();
    names.forEach(name -> webhookMap.put(name, createSimpleWebhookConfiguration()));
    return webhookMap;
  }

  public WebhookConfiguration createSimpleWebhookConfiguration() {
    return createWebhookConfiguration(
      "someUrl",
      Collections.emptyMap(),
      "POST",
      WebhookConfiguration.ALERT_MESSAGE_PLACEHOLDER
    );
  }

  private WebhookConfiguration createWebhookConfiguration(final String url,
                                                          final Map<String, String> headers,
                                                          final String httpMethod,
                                                          final String payload) {
    WebhookConfiguration webhookConfiguration = new WebhookConfiguration();
    webhookConfiguration.setUrl(url);
    webhookConfiguration.setHeaders(headers);
    webhookConfiguration.setHttpMethod(httpMethod);
    webhookConfiguration.setDefaultPayload(payload);
    return webhookConfiguration;
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
