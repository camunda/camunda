/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.console.ping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.application.commons.configuration.BrokerBasedConfiguration;
import io.camunda.application.commons.console.ping.PingConsoleRunner.ConsolePingConfiguration;
import io.camunda.service.ManagementServices;
import io.camunda.service.license.LicenseType;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ClusterCfg;
import io.camunda.zeebe.util.retry.RetryConfiguration;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
class PingConsoleConfigurationTest {

  private static final ManagementServices MANAGEMENT_SERVICES = mock(ManagementServices.class);
  private static final Environment ENVIRONMENT = mock(Environment.class);
  private static final ApplicationContext APPLICATION_CONTEXT = mock(ApplicationContext.class);
  private static final BrokerBasedConfiguration BROKER_BASED_CFG =
      mock(BrokerBasedConfiguration.class);
  private static final BrokerCfg BROKER_CFG = mock(BrokerCfg.class);
  private static final ClusterCfg CLUSTER_CONFIG = mock(ClusterCfg.class);
  private final ConsolePingConfiguration pingConfiguration =
      new ConsolePingConfiguration(
          true,
          URI.create("http://fake-endpoint.com"),
          "clusterName",
          Duration.ofMillis(1000),
          new RetryConfiguration(),
          null);
  private final String licensePayload =
      "{\"type\":\"SAAS\",\"valid\":true,\"expiresAt\":null,\"commercial\":true}";
  @Mock private HttpClient mockClient;

  @BeforeAll
  static void setUp() {
    when(MANAGEMENT_SERVICES.getCamundaLicenseType()).thenReturn(LicenseType.SAAS);
    when(MANAGEMENT_SERVICES.isCamundaLicenseValid()).thenReturn(true);
    when(MANAGEMENT_SERVICES.isCommercialCamundaLicense()).thenReturn(true);
    when(MANAGEMENT_SERVICES.getCamundaLicenseExpiresAt()).thenReturn(null);
    when(APPLICATION_CONTEXT.getEnvironment()).thenReturn(ENVIRONMENT);
    when(ENVIRONMENT.getActiveProfiles()).thenReturn(new String[] {"broker"});
    when(BROKER_BASED_CFG.config()).thenReturn(BROKER_CFG);
    when(BROKER_CFG.getCluster()).thenReturn(CLUSTER_CONFIG);
    when(CLUSTER_CONFIG.getClusterId()).thenReturn("clusterId");
  }

  @Test
  void endpointShouldNotBeNull() {
    // given
    final ConsolePingConfiguration consolePingConfiguration =
        new ConsolePingConfiguration(
            true, null, "clusterName", Duration.ofMillis(5000), new RetryConfiguration(), null);

    // then
    final IllegalArgumentException exception =
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(
                new PingConsoleRunner(
                        consolePingConfiguration,
                        MANAGEMENT_SERVICES,
                        APPLICATION_CONTEXT,
                        BROKER_BASED_CFG)
                    ::validateConfiguration)
            .actual();
    assertThat(exception.getMessage()).isEqualTo("Ping endpoint must not be null.");
  }

  @Test
  void endpointShouldBeValid() {
    // given
    final ConsolePingConfiguration consolePingConfiguration =
        new ConsolePingConfiguration(
            true,
            URI.create("123"),
            "clusterName",
            Duration.ofMillis(5000),
            new RetryConfiguration(),
            null);

    // then
    final IllegalArgumentException exception =
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(
                new PingConsoleRunner(
                        consolePingConfiguration,
                        MANAGEMENT_SERVICES,
                        APPLICATION_CONTEXT,
                        BROKER_BASED_CFG)
                    ::validateConfiguration)
            .actual();
    assertThat(exception.getMessage()).isEqualTo("Ping endpoint 123 must be a valid URI.");
  }

  @Test
  void clusterIdShouldNotBeNullOrEmpty() {
    // given
    when(CLUSTER_CONFIG.getClusterId()).thenReturn(null);
    final ConsolePingConfiguration consolePingConfiguration =
        new ConsolePingConfiguration(
            true,
            URI.create("http://localhost:8080"),
            "clusterName",
            Duration.ofMillis(5000),
            new RetryConfiguration(),
            null);

    // then
    final IllegalArgumentException exception =
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(
                new PingConsoleRunner(
                        consolePingConfiguration,
                        MANAGEMENT_SERVICES,
                        APPLICATION_CONTEXT,
                        BROKER_BASED_CFG)
                    ::validateConfiguration)
            .actual();
    assertThat(exception.getMessage()).isEqualTo("Cluster ID must not be null or empty.");

    // reset
    when(CLUSTER_CONFIG.getClusterId()).thenReturn("clusterId");
  }

  @Test
  void clusterNameMustNotBeNullOrEmpty() {
    // given
    final ConsolePingConfiguration consolePingConfiguration =
        new ConsolePingConfiguration(
            true,
            URI.create("http://localhost:8080"),
            "",
            Duration.ofMillis(5000),
            new RetryConfiguration(),
            null);

    // then
    final IllegalArgumentException exception =
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(
                new PingConsoleRunner(
                        consolePingConfiguration,
                        MANAGEMENT_SERVICES,
                        APPLICATION_CONTEXT,
                        BROKER_BASED_CFG)
                    ::validateConfiguration)
            .actual();
    assertThat(exception.getMessage()).isEqualTo("Cluster name must not be null or empty.");
  }

  @Test
  void pingPeriodMustBePositive() {
    // given
    final ConsolePingConfiguration consolePingConfiguration =
        new ConsolePingConfiguration(
            true,
            URI.create("http://localhost:8080"),
            "clusterName",
            Duration.ofMillis(-333),
            new RetryConfiguration(),
            null);

    // then
    final IllegalArgumentException exception =
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(
                new PingConsoleRunner(
                        consolePingConfiguration,
                        MANAGEMENT_SERVICES,
                        APPLICATION_CONTEXT,
                        BROKER_BASED_CFG)
                    ::validateConfiguration)
            .actual();
    assertThat(exception.getMessage()).isEqualTo("Ping period must be greater than zero.");
  }

  @Test
  void numberOfMaxRetriesMustBePositive() {
    // given
    final RetryConfiguration retryConfiguration = new RetryConfiguration();
    retryConfiguration.setMaxRetries(0);

    final ConsolePingConfiguration consolePingConfiguration =
        new ConsolePingConfiguration(
            true,
            URI.create("http://localhost:8080"),
            "clusterName",
            Duration.ofMillis(5000),
            retryConfiguration,
            null);

    // then
    final IllegalArgumentException exception =
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(
                new PingConsoleRunner(
                        consolePingConfiguration,
                        MANAGEMENT_SERVICES,
                        APPLICATION_CONTEXT,
                        BROKER_BASED_CFG)
                    ::validateConfiguration)
            .actual();
    assertThat(exception.getMessage())
        .isEqualTo("Number of max retries must be greater than zero.");
  }

  @Test
  void retryDelayMultiplierMustBePositive() {
    // given
    final RetryConfiguration retryConfiguration = new RetryConfiguration();
    retryConfiguration.setRetryDelayMultiplier(0.0);

    final ConsolePingConfiguration consolePingConfiguration =
        new ConsolePingConfiguration(
            true,
            URI.create("http://localhost:8080"),
            "clusterName",
            Duration.ofMillis(5000),
            retryConfiguration,
            null);

    // then
    final IllegalArgumentException exception =
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(
                new PingConsoleRunner(
                        consolePingConfiguration,
                        MANAGEMENT_SERVICES,
                        APPLICATION_CONTEXT,
                        BROKER_BASED_CFG)
                    ::validateConfiguration)
            .actual();
    assertThat(exception.getMessage())
        .isEqualTo("Retry delay multiplier must be greater than zero.");
  }

  @Test
  void retryConfigurationCanBeNull() {
    // given
    final ConsolePingConfiguration consolePingConfiguration =
        new ConsolePingConfiguration(
            true,
            URI.create("http://localhost:8080"),
            "clusterName",
            Duration.ofMillis(5000),
            null,
            null);

    // then
    assertThatCode(
            () ->
                new PingConsoleRunner(
                    consolePingConfiguration,
                    MANAGEMENT_SERVICES,
                    APPLICATION_CONTEXT,
                    BROKER_BASED_CFG))
        .doesNotThrowAnyException();
  }

  @Test
  void maxRetryDelayMustBePositive() {
    // given
    final RetryConfiguration retryConfiguration = new RetryConfiguration();
    retryConfiguration.setMaxRetryDelay(Duration.ofMillis(0));

    final ConsolePingConfiguration consolePingConfiguration =
        new ConsolePingConfiguration(
            true,
            URI.create("http://localhost:8080"),
            "clusterName",
            Duration.ofMillis(5000),
            retryConfiguration,
            null);

    // then
    final IllegalArgumentException exception =
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(
                new PingConsoleRunner(
                        consolePingConfiguration,
                        MANAGEMENT_SERVICES,
                        APPLICATION_CONTEXT,
                        BROKER_BASED_CFG)
                    ::validateConfiguration)
            .actual();
    assertThat(exception.getMessage()).isEqualTo("Max retry delay must be greater than zero.");
  }

  @Test
  void minRetryDelayMustBePositive() {
    // given
    final RetryConfiguration retryConfiguration = new RetryConfiguration();
    retryConfiguration.setMinRetryDelay(Duration.ofMillis(0));

    final ConsolePingConfiguration consolePingConfiguration =
        new ConsolePingConfiguration(
            true,
            URI.create("http://localhost:8080"),
            "clusterName",
            Duration.ofMillis(5000),
            retryConfiguration,
            null);

    // then
    final IllegalArgumentException exception =
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(
                new PingConsoleRunner(
                        consolePingConfiguration,
                        MANAGEMENT_SERVICES,
                        APPLICATION_CONTEXT,
                        BROKER_BASED_CFG)
                    ::validateConfiguration)
            .actual();
    assertThat(exception.getMessage()).isEqualTo("Min retry delay must be greater than zero.");
  }

  @Test
  void maxRetryDelayMustBeGreaterThanMinRetryDelay() {
    // given
    final RetryConfiguration retryConfiguration = new RetryConfiguration();
    retryConfiguration.setMinRetryDelay(Duration.ofMillis(1000));
    retryConfiguration.setMaxRetryDelay(Duration.ofMillis(500));

    final ConsolePingConfiguration consolePingConfiguration =
        new ConsolePingConfiguration(
            true,
            URI.create("http://localhost:8080"),
            "clusterName",
            Duration.ofMillis(5000),
            retryConfiguration,
            null);

    // then
    final IllegalArgumentException exception =
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(
                new PingConsoleRunner(
                        consolePingConfiguration,
                        MANAGEMENT_SERVICES,
                        APPLICATION_CONTEXT,
                        BROKER_BASED_CFG)
                    ::validateConfiguration)
            .actual();
    assertThat(exception.getMessage())
        .isEqualTo("Max retry delay must be greater than or equal to min retry delay.");
  }

  @Test
  void shouldSucceedToStartConsolePingForValidConfig() {
    // given
    final ConsolePingConfiguration consolePingConfiguration =
        new ConsolePingConfiguration(
            true,
            URI.create("http://localhost:8080"),
            "clusterName",
            Duration.ofMillis(5000),
            new RetryConfiguration(),
            null);
    // then
    assertThatCode(
            () ->
                new PingConsoleRunner(
                    consolePingConfiguration,
                    MANAGEMENT_SERVICES,
                    APPLICATION_CONTEXT,
                    BROKER_BASED_CFG))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldNotThrowIfFeatureDisabled() {
    // given an invalid config
    final ConsolePingConfiguration consolePingConfiguration =
        new ConsolePingConfiguration(
            false,
            URI.create("123"),
            null,
            Duration.ofMillis(-300),
            new RetryConfiguration(),
            null);

    // then we assert that it is not throwing an exception due to the feature being disabled
    assertThatCode(
            () ->
                new PingConsoleRunner(
                    consolePingConfiguration,
                    MANAGEMENT_SERVICES,
                    APPLICATION_CONTEXT,
                    BROKER_BASED_CFG))
        .doesNotThrowAnyException();
  }

  @Test
  void doesNotRetryOnSuccess() throws Exception {
    // given
    final HttpResponse<String> mockResponse = mock(HttpResponse.class);

    //  when simulate a successful response
    when(mockResponse.statusCode()).thenReturn(200);

    when(mockClient.send(
            ArgumentMatchers.<HttpRequest>any(),
            ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenReturn(mockResponse);

    final PingConsoleTask realTask =
        new PingConsoleTask(pingConfiguration, mockClient, licensePayload);

    final PingConsoleTask spyTask = Mockito.spy(realTask);

    spyTask.run();

    // then it only runs once
    verify(spyTask, times(1)).tryPingConsole(any(HttpRequest.class));
  }

  @Test
  void shouldRetryOnSendException() throws Exception {
    // given
    final HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(200);

    // when we simulate 2 failures and 1 success
    when(mockClient.send(
            ArgumentMatchers.<HttpRequest>any(),
            ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenThrow(new IOException("IO error"))
        .thenThrow(new InterruptedException("Interrupted connection error"))
        .thenReturn(mockResponse); // 3rd try succeeds

    final PingConsoleTask realTask =
        new PingConsoleTask(pingConfiguration, mockClient, licensePayload);

    final PingConsoleTask spyTask = Mockito.spy(realTask);

    spyTask.run();

    // then it retries 2 times and runs 3 times in total
    verify(spyTask, times(3)).tryPingConsole(any(HttpRequest.class));
  }

  @Test
  void shouldRetryOnSpecificStatusCodes() throws Exception {
    // given
    final HttpResponse<String> firstMockResponse = mock(HttpResponse.class);
    final HttpResponse<String> secondMockResponse = mock(HttpResponse.class);
    final HttpResponse<String> thirdMockResponse = mock(HttpResponse.class);

    // when we simulate 2 failed responses (server error and timeout) followed by 1 success:
    when(firstMockResponse.statusCode()).thenReturn(500);
    when(secondMockResponse.statusCode()).thenReturn(429);
    when(thirdMockResponse.statusCode()).thenReturn(200);

    when(mockClient.send(
            ArgumentMatchers.<HttpRequest>any(),
            ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenReturn(firstMockResponse)
        .thenReturn(secondMockResponse)
        .thenReturn(thirdMockResponse);

    final PingConsoleTask realTask =
        new PingConsoleTask(pingConfiguration, mockClient, licensePayload);

    final PingConsoleTask spyTask = Mockito.spy(realTask);

    spyTask.run();

    // then it retries 2 times and runs 3 times in total
    verify(spyTask, times(3)).tryPingConsole(any(HttpRequest.class));
  }
}
