/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.console.ping;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.application.commons.console.ping.PingConsoleConfiguration.ConsolePingConfiguration;
import io.camunda.service.ManagementServices;
import io.camunda.service.license.LicenseType;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PingConsoleConfigurationTest {

  @Mock private ManagementServices managementServices;
  @Mock private ConsolePingConfiguration pingConfiguration;
  @Mock private HttpClient mockClient;

  @Test
  void shouldFailToStartConsolePing() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            new PingConsoleConfiguration(
                    new ConsolePingConfiguration(true, null, "clusterId", "clusterName", 5, null),
                    managementServices)
                ::validateConfiguration);
    assertEquals("Ping endpoint must not be null or empty.", exception.getMessage());

    exception =
        assertThrows(
            IllegalArgumentException.class,
            new PingConsoleConfiguration(
                    new ConsolePingConfiguration(
                        true, "http://localhost:8080", null, "clusterName", 5, null),
                    managementServices)
                ::validateConfiguration);
    assertEquals("Cluster ID must not be null or empty.", exception.getMessage());

    exception =
        assertThrows(
            IllegalArgumentException.class,
            new PingConsoleConfiguration(
                    new ConsolePingConfiguration(
                        true, "http://localhost:8080", "clusterId", "", 5, null),
                    managementServices)
                ::validateConfiguration);
    assertEquals("Cluster name must not be null or empty.", exception.getMessage());

    exception =
        assertThrows(
            IllegalArgumentException.class,
            new PingConsoleConfiguration(
                    new ConsolePingConfiguration(
                        true, "http" + "://localhost:8080", "clusterId", "clusterName", 0, null),
                    managementServices)
                ::validateConfiguration);
    assertEquals("Ping period must be greater than zero.", exception.getMessage());
  }

  @Test
  void shouldSucceedToStartConsolePing() {
    assertDoesNotThrow(
        () ->
            new PingConsoleConfiguration(
                new ConsolePingConfiguration(
                    true, "http://localhost:8080", "clusterId", "clusterName", 1, null),
                managementServices));

    // does not throw if the feature is disabled
    assertDoesNotThrow(
        () ->
            new PingConsoleConfiguration(
                new ConsolePingConfiguration(false, "", "", null, -32, null), managementServices));
  }

  @Test
  void doesNotRetryOnSuccess() throws Exception {
    final HttpResponse<String> mockResponse = mock(HttpResponse.class);
    // Simulate a successful response
    when(mockResponse.statusCode()).thenReturn(200);
    when(pingConfiguration.endpoint()).thenReturn("http://fake-endpoint.com");

    when(mockClient.send(
            ArgumentMatchers.<HttpRequest>any(),
            ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenReturn(mockResponse);

    when(managementServices.getCamundaLicenseType()).thenReturn(LicenseType.SAAS);
    when(managementServices.isCamundaLicenseValid()).thenReturn(true);
    when(managementServices.isCommercialCamundaLicense()).thenReturn(true);
    when(managementServices.getCamundaLicenseExpiresAt()).thenReturn(null);

    final PingConsoleTask realTask =
        new PingConsoleTask(managementServices, pingConfiguration, mockClient);

    final PingConsoleTask spyTask = Mockito.spy(realTask);

    spyTask.run();

    verify(spyTask, times(1)).tryPingConsole(any(HttpRequest.class));
  }

  @Test
  void shouldRetryOnSendException() throws Exception {
    final HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(200);
    when(pingConfiguration.endpoint()).thenReturn("http://fake-endpoint.com");

    // when we simulate 2 failures and 1 success
    when(mockClient.send(
            ArgumentMatchers.<HttpRequest>any(),
            ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenThrow(new IOException("IO error"))
        .thenThrow(new InterruptedException("Interrupted connection error"))
        .thenReturn(mockResponse); // 3rd try succeeds

    when(managementServices.getCamundaLicenseType()).thenReturn(LicenseType.SAAS);
    when(managementServices.isCamundaLicenseValid()).thenReturn(true);
    when(managementServices.isCommercialCamundaLicense()).thenReturn(true);
    when(managementServices.getCamundaLicenseExpiresAt()).thenReturn(null);

    final PingConsoleTask realTask =
        new PingConsoleTask(managementServices, pingConfiguration, mockClient);

    final PingConsoleTask spyTask = Mockito.spy(realTask);

    spyTask.run();

    // then
    verify(spyTask, times(3)).tryPingConsole(any(HttpRequest.class));
  }

  @Test
  void shouldRetryOnSpecificStatusCodes() throws Exception {
    final HttpResponse<String> firstMockResponse = mock(HttpResponse.class);
    final HttpResponse<String> secondMockResponse = mock(HttpResponse.class);
    final HttpResponse<String> thirdMockResponse = mock(HttpResponse.class);

    when(firstMockResponse.statusCode()).thenReturn(500); // server error
    when(secondMockResponse.statusCode()).thenReturn(429); // timeout
    when(thirdMockResponse.statusCode()).thenReturn(200); // successful request

    when(pingConfiguration.endpoint()).thenReturn("http://fake-endpoint.com");

    when(managementServices.getCamundaLicenseType()).thenReturn(LicenseType.SAAS);
    when(managementServices.isCamundaLicenseValid()).thenReturn(true);
    when(managementServices.isCommercialCamundaLicense()).thenReturn(true);
    when(managementServices.getCamundaLicenseExpiresAt()).thenReturn(null);

    when(mockClient.send(
            ArgumentMatchers.<HttpRequest>any(),
            ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenReturn(firstMockResponse)
        .thenReturn(secondMockResponse)
        .thenReturn(thirdMockResponse);

    final PingConsoleTask realTask =
        new PingConsoleTask(managementServices, pingConfiguration, mockClient);

    final PingConsoleTask spyTask = Mockito.spy(realTask);

    spyTask.run();

    // then
    verify(spyTask, times(3)).tryPingConsole(any(HttpRequest.class));
  }
}
