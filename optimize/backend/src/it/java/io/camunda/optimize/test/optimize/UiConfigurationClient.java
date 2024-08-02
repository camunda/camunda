/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.optimize;

import io.camunda.optimize.OptimizeRequestExecutor;
import io.camunda.optimize.dto.optimize.query.ui_configuration.UIConfigurationResponseDto;
import jakarta.ws.rs.core.Response;
import java.util.function.Supplier;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class UiConfigurationClient {

  public static final String TEST_WEBHOOK_HOST = "127.0.0.1";
  public static final String TEST_WEBHOOK_NAME = "testWebhook";
  public static final String TEST_WEBHOOK_WITH_PROXY_NAME = "testProxyWebhook";
  public static final String TEST_CUSTOM_CONTENT_TYPE_WEBHOOK_NAME =
      "testWebhook_NonStandardContentType";
  public static final String TEST_INVALID_PORT_WEBHOOK_NAME = "testWebhook_InvalidUrl";
  public static final String TEST_WEBHOOK_METHOD = "POST";
  public static final String TEST_WEBHOOK_URL_INVALID_PORT =
      "http://" + TEST_WEBHOOK_HOST + ":1080";
  public static final String TEST_WEBHOOK_URL_PATH = "/webhookpath";
  private static final String TEST_WEBHOOK_URL = "http://" + TEST_WEBHOOK_HOST + ":%d";
  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public static String createWebhookHostUrl(final int port) {
    return String.format(TEST_WEBHOOK_URL, port);
  }

  public UIConfigurationResponseDto getUIConfiguration() {
    return requestExecutorSupplier
        .get()
        .withoutAuthentication()
        .buildGetUIConfigurationRequest()
        .execute(UIConfigurationResponseDto.class, Response.Status.OK.getStatusCode());
  }
}
