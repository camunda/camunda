/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.configuration.processing.flowcontrol.Limit;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@MultiDbTest
final class ConfigurationPropertiesIT {

  @MultiDbTestApplication
  private static final TestCamundaApplication CAMUNDA_APPLICATION =
      new TestCamundaApplication()
          .withProperty("management.endpoint.configprops.access", "unrestricted")
          .withProperty("management.endpoint.configprops.show-values", "always")
          .withUnifiedConfig(
              config -> {
                config.getProcessing().setMaxCommandsInBatch(17);
                final var request = new Limit();
                request.getAimd().setInitialLimit(123);
                config.getProcessing().getFlowControl().setRequest(request);
              });

  @AutoClose private final HttpClient httpClient = HttpClient.newHttpClient();

  @ParameterizedTest
  @CsvSource({
    "zeebe.broker, /processing/maxCommandsInBatch, 17",
    "zeebe.broker, /processing/maxRecoverableRetries, 1000",
    "zeebe.broker, /processing/maxPendingSideEffects, 1000",
    "zeebe.broker, /processing/scheduledTaskCheckInterval, PT1S",
    "camunda, /processing/flowControl/request/enabled, true",
    "camunda, /processing/flowControl/request/windowed, true",
    "camunda, /processing/flowControl/request/algorithm, aimd",
    "camunda, /processing/flowControl/request/aimd/requestTimeout, PT0.2S",
    "camunda, /processing/flowControl/request/aimd/initialLimit, 123",
    "camunda, /processing/flowControl/request/aimd/minLimit, 1",
    "camunda, /processing/flowControl/request/aimd/maxLimit, 1000",
    "camunda, /processing/flowControl/request/aimd/backoffRatio, 0.9",
    "camunda, /processing/flowControl/request/fixed/limit, 20",
    "camunda, /processing/flowControl/request/vegas/alpha, 3",
    "camunda, /processing/flowControl/request/vegas/beta, 6",
    "camunda, /processing/flowControl/request/vegas/initialLimit, 20",
    "camunda, /processing/flowControl/request/gradient/minLimit, 10",
    "camunda, /processing/flowControl/request/gradient/initialLimit, 20",
    "camunda, /processing/flowControl/request/gradient/rttTolerance, 2.0",
    "camunda, /processing/flowControl/request/gradient2/minLimit, 10",
    "camunda, /processing/flowControl/request/gradient2/initialLimit, 20",
    "camunda, /processing/flowControl/request/gradient2/rttTolerance, 2.0",
    "camunda, /processing/flowControl/request/gradient2/longWindow, 600",
    "camunda, /processing/flowControl/request/legacyVegas/initialLimit, 1024",
    "camunda, /processing/flowControl/request/legacyVegas/maxConcurrency, 32768",
    "camunda, /processing/flowControl/request/legacyVegas/alphaLimit, 0.7",
    "camunda, /processing/flowControl/request/legacyVegas/betaLimit, 0.95",
  })
  void shouldReportConfigurationProperty(
      final String prefix, final String path, final String expectedValue)
      throws IOException, InterruptedException {
    // given
    final var request =
        HttpRequest.newBuilder(CAMUNDA_APPLICATION.actuatorAddress("configprops")).GET().build();

    // when
    final var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    // then
    assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
    final var report = new ObjectMapper().readTree(response.body());
    final var beans =
        report
            .path("contexts")
            .valueStream()
            .flatMap(context -> context.path("beans").valueStream())
            .filter(bean -> prefix.equals(bean.path("prefix").asText()))
            .toList();
    assertThat(beans).as("configuration beans with prefix '%s'", prefix).isNotEmpty();
    assertThat(beans)
        .allSatisfy(
            bean -> {
              final JsonNode property = bean.path("properties").at(path);
              assertThat(property.isMissingNode())
                  .as("reported property '%s%s'", prefix, path)
                  .isFalse();
              assertThat(property.asText()).as("%s%s", prefix, path).isEqualTo(expectedValue);
            });
  }
}
