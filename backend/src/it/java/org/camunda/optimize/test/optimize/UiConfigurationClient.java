/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.optimize;

import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.ui_configuration.UIConfigurationResponseDto;
import org.camunda.optimize.service.util.configuration.WebhookConfiguration;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@AllArgsConstructor
public class UiConfigurationClient {
  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;
  private static final String TEST_WEBHOOK_URL_HOST = "http://127.0.0.1:%d";

  public static final String TEST_WEBHOOK_NAME = "testWebhook";
  public static final String TEST_CUSTOM_CONTENT_TYPE_WEBHOOK_NAME = "testWebhook_NonStandardContentType";
  public static final String TEST_INVALID_PORT_WEBHOOK_NAME = "testWebhook_InvalidUrl";
  public static final String TEST_WEBHOOK_METHOD = "POST";
  public static final String TEST_WEBHOOK_URL_INVALID_PORT = "http://127.0.0.1:1080";
  public static final String TEST_WEBHOOK_URL_PATH = "/webhookpath";

  public Map<String, WebhookConfiguration> createSimpleWebhookConfigurationMap(Set<String> names) {
    Map<String, WebhookConfiguration> webhookMap = new HashMap<>();
    names.forEach(name -> webhookMap.put(name, createSimpleWebhookConfiguration()));
    return webhookMap;
  }

  public static String createWebhookHostUrl(final int port) {
    return String.format(TEST_WEBHOOK_URL_HOST, port);
  }

  public WebhookConfiguration createSimpleWebhookConfiguration() {
    return createWebhookConfiguration(
      TEST_WEBHOOK_URL_HOST + TEST_WEBHOOK_URL_PATH,
      Collections.emptyMap(),
      TEST_WEBHOOK_METHOD,
      WebhookConfiguration.Placeholder.ALERT_MESSAGE.getPlaceholderString()
    );
  }

  public UIConfigurationResponseDto getUIConfiguration() {
    return requestExecutorSupplier.get()
      .withoutAuthentication()
      .buildGetUIConfigurationRequest()
      .execute(UIConfigurationResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public WebhookConfiguration createWebhookConfiguration(final String url,
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
}
