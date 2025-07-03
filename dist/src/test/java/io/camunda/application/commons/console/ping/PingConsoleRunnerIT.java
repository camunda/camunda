/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.console.ping;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.AssertionsKt.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.application.commons.console.ping.PingConsoleRunner.ConsolePingConfiguration;
import io.camunda.application.commons.console.ping.PingConsoleRunner.ConsolePingConfiguration.RetryConfiguration;
import io.camunda.service.ManagementServices;
import io.camunda.service.license.LicenseType;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PingConsoleRunnerIT {

  private MockWebServer mockWebServer;
  private ManagementServices managementServices;
  private final boolean validLicense = true;
  private final boolean isCommercial = true;
  private final LicenseType licenseType = LicenseType.SAAS;
  private final String clusterId = "test-cluster-id";
  private final String clusterName = "test-cluster-name";
  private final String expectedBody =
      String.format(
          """
    {
      "license": {
        "validLicense": %s,
        "licenseType": "%s",
        "isCommercial": %s
      },
      "clusterId": "%s",
      "clusterName": "%s"
    }
  """,
          validLicense, licenseType, isCommercial, clusterId, clusterName);

  @BeforeEach
  void setup() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
    managementServices = mock(ManagementServices.class);
    when(managementServices.getCamundaLicenseType()).thenReturn(licenseType);
    when(managementServices.isCommercialCamundaLicense()).thenReturn(isCommercial);
    when(managementServices.isCamundaLicenseValid()).thenReturn(validLicense);
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  void shouldSendCorrectPayload() throws InterruptedException, JsonProcessingException {
    // given
    final String mockUrl = mockWebServer.url("/ping").toString();

    // we have the server answer with a valid response for 3 times.
    mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("PONG"));
    mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("PONG"));
    mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("PONG"));

    final Duration pingPeriod = Duration.ofMillis(200);
    final ConsolePingConfiguration.RetryConfiguration retryConfig =
        new RetryConfiguration(1, 1, Duration.ofMillis(100));

    final ConsolePingConfiguration config =
        new ConsolePingConfiguration(
            true, URI.create(mockUrl), clusterId, clusterName, pingPeriod, retryConfig, null);

    final PingConsoleRunner pingConsoleRunner = new PingConsoleRunner(config, managementServices);

    // when
    pingConsoleRunner.run(null);

    // then
    await().atMost(10, TimeUnit.SECONDS).until(() -> mockWebServer.getRequestCount() >= 3);

    // we validate the first request received by the mock server
    final RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
    assertNotNull(request);
    assertEquals("POST", request.getMethod());

    final String body = request.getBody().readUtf8();

    final ObjectMapper mapper = new ObjectMapper();
    final JsonNode expected = mapper.readTree(expectedBody);

    assertEquals(expected.toString(), body, "JSON payload did not match expected structure");
  }
}
