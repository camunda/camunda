/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.hub.ping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.application.commons.hub.ping.PingHubRunner.HubPingConfiguration;
import io.camunda.service.ManagementServices;
import io.camunda.service.license.LicenseType;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.util.retry.RetryConfiguration;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
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
class PingHubConfigurationTest {

  private static final ManagementServices MANAGEMENT_SERVICES = mock(ManagementServices.class);
  private static final Environment ENVIRONMENT = mock(Environment.class);
  private static final ApplicationContext APPLICATION_CONTEXT = mock(ApplicationContext.class);
  private static final BrokerTopologyManager BROKER_TOPOLOGY_MANAGER =
      mock(BrokerTopologyManager.class);
  private static final ClusterConfiguration BROKER_CLUSTER_CONFIGURATION =
      mock(ClusterConfiguration.class);
  private static final M2MCredentials VALID_CREDENTIALS =
      new M2MCredentials(
          URI.create("http://auth-server.com/token"), "test-client-id", "test-client-secret");

  private final HubPingConfiguration pingConfiguration =
      new HubPingConfiguration(
          true,
          URI.create("http://fake-endpoint.com"),
          "clusterName",
          Duration.ofMillis(1000),
          new RetryConfiguration(),
          null,
          VALID_CREDENTIALS);
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
    when(BROKER_TOPOLOGY_MANAGER.getClusterConfiguration())
        .thenReturn(BROKER_CLUSTER_CONFIGURATION);
    when(BROKER_CLUSTER_CONFIGURATION.clusterId()).thenReturn(Optional.of("clusterId"));
  }

  @Test
  void endpointShouldNotBeNull() {
    // given
    final HubPingConfiguration config =
        new HubPingConfiguration(
            true,
            null,
            "clusterName",
            Duration.ofMillis(5000),
            new RetryConfiguration(),
            null,
            VALID_CREDENTIALS);

    final PingHubRunner runner =
        new PingHubRunner(
            config, MANAGEMENT_SERVICES, APPLICATION_CONTEXT, BROKER_TOPOLOGY_MANAGER);

    // when
    final var result = runner.validateConfiguration();

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isEqualTo("Ping endpoint must not be null.");
  }

  @Test
  void endpointShouldBeValid() {
    // given
    final HubPingConfiguration config =
        new HubPingConfiguration(
            true,
            URI.create("123"),
            "clusterName",
            Duration.ofMillis(5000),
            new RetryConfiguration(),
            null,
            VALID_CREDENTIALS);

    final PingHubRunner runner =
        new PingHubRunner(
            config, MANAGEMENT_SERVICES, APPLICATION_CONTEXT, BROKER_TOPOLOGY_MANAGER);

    // when
    final var result = runner.validateConfiguration();

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isEqualTo("Ping endpoint 123 must be a valid URI.");
  }

  @Test
  void clusterNameMustNotBeNullOrEmpty() {
    // given
    final HubPingConfiguration config =
        new HubPingConfiguration(
            true,
            URI.create("http://localhost:8080"),
            "",
            Duration.ofMillis(5000),
            new RetryConfiguration(),
            null,
            VALID_CREDENTIALS);

    final PingHubRunner runner =
        new PingHubRunner(
            config, MANAGEMENT_SERVICES, APPLICATION_CONTEXT, BROKER_TOPOLOGY_MANAGER);

    // when
    final var result = runner.validateConfiguration();

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isEqualTo("Cluster name must not be null or empty.");
  }

  @Test
  void pingPeriodMustBePositive() {
    // given
    final HubPingConfiguration config =
        new HubPingConfiguration(
            true,
            URI.create("http://localhost:8080"),
            "clusterName",
            Duration.ofMillis(-333),
            new RetryConfiguration(),
            null,
            VALID_CREDENTIALS);

    final PingHubRunner runner =
        new PingHubRunner(
            config, MANAGEMENT_SERVICES, APPLICATION_CONTEXT, BROKER_TOPOLOGY_MANAGER);

    // when
    final var result = runner.validateConfiguration();

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isEqualTo("Ping period must be greater than zero.");
  }

  @Test
  void numberOfMaxRetriesMustBePositive() {
    // given
    final RetryConfiguration retryConfiguration = new RetryConfiguration();
    retryConfiguration.setMaxRetries(0);

    final HubPingConfiguration config =
        new HubPingConfiguration(
            true,
            URI.create("http://localhost:8080"),
            "clusterName",
            Duration.ofMillis(5000),
            retryConfiguration,
            null,
            VALID_CREDENTIALS);

    final PingHubRunner runner =
        new PingHubRunner(
            config, MANAGEMENT_SERVICES, APPLICATION_CONTEXT, BROKER_TOPOLOGY_MANAGER);

    // when
    final var result = runner.validateConfiguration();

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isEqualTo("Number of max retries must be greater than zero.");
  }

  @Test
  void retryDelayMultiplierMustBePositive() {
    // given
    final RetryConfiguration retryConfiguration = new RetryConfiguration();
    retryConfiguration.setRetryDelayMultiplier(0.0);

    final HubPingConfiguration config =
        new HubPingConfiguration(
            true,
            URI.create("http://localhost:8080"),
            "clusterName",
            Duration.ofMillis(5000),
            retryConfiguration,
            null,
            VALID_CREDENTIALS);

    final PingHubRunner runner =
        new PingHubRunner(
            config, MANAGEMENT_SERVICES, APPLICATION_CONTEXT, BROKER_TOPOLOGY_MANAGER);

    // when
    final var result = runner.validateConfiguration();

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isEqualTo("Retry delay multiplier must be greater than zero.");
  }

  @Test
  void retryConfigurationCanBeNull() {
    // given
    final HubPingConfiguration config =
        new HubPingConfiguration(
            true,
            URI.create("http://localhost:8080"),
            "clusterName",
            Duration.ofMillis(5000),
            null,
            null,
            VALID_CREDENTIALS);

    // then
    assertThatCode(
            () ->
                new PingHubRunner(
                    config, MANAGEMENT_SERVICES, APPLICATION_CONTEXT, BROKER_TOPOLOGY_MANAGER))
        .doesNotThrowAnyException();
  }

  @Test
  void maxRetryDelayMustBePositive() {
    // given
    final RetryConfiguration retryConfiguration = new RetryConfiguration();
    retryConfiguration.setMaxRetryDelay(Duration.ofMillis(0));

    final HubPingConfiguration config =
        new HubPingConfiguration(
            true,
            URI.create("http://localhost:8080"),
            "clusterName",
            Duration.ofMillis(5000),
            retryConfiguration,
            null,
            VALID_CREDENTIALS);

    final PingHubRunner runner =
        new PingHubRunner(
            config, MANAGEMENT_SERVICES, APPLICATION_CONTEXT, BROKER_TOPOLOGY_MANAGER);

    // when
    final var result = runner.validateConfiguration();

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isEqualTo("Max retry delay must be greater than zero.");
  }

  @Test
  void minRetryDelayMustBePositive() {
    // given
    final RetryConfiguration retryConfiguration = new RetryConfiguration();
    retryConfiguration.setMinRetryDelay(Duration.ofMillis(0));

    final HubPingConfiguration config =
        new HubPingConfiguration(
            true,
            URI.create("http://localhost:8080"),
            "clusterName",
            Duration.ofMillis(5000),
            retryConfiguration,
            null,
            VALID_CREDENTIALS);

    final PingHubRunner runner =
        new PingHubRunner(
            config, MANAGEMENT_SERVICES, APPLICATION_CONTEXT, BROKER_TOPOLOGY_MANAGER);

    // when
    final var result = runner.validateConfiguration();

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isEqualTo("Min retry delay must be greater than zero.");
  }

  @Test
  void maxRetryDelayMustBeGreaterThanMinRetryDelay() {
    // given
    final RetryConfiguration retryConfiguration = new RetryConfiguration();
    retryConfiguration.setMinRetryDelay(Duration.ofMillis(1000));
    retryConfiguration.setMaxRetryDelay(Duration.ofMillis(500));

    final HubPingConfiguration config =
        new HubPingConfiguration(
            true,
            URI.create("http://localhost:8080"),
            "clusterName",
            Duration.ofMillis(5000),
            retryConfiguration,
            null,
            VALID_CREDENTIALS);

    final PingHubRunner runner =
        new PingHubRunner(
            config, MANAGEMENT_SERVICES, APPLICATION_CONTEXT, BROKER_TOPOLOGY_MANAGER);

    // when
    final var result = runner.validateConfiguration();

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft())
        .isEqualTo("Max retry delay must be greater than or equal to min retry delay.");
  }

  @Test
  void credentialsMustNotBeNull() {
    // given
    final HubPingConfiguration config =
        new HubPingConfiguration(
            true,
            URI.create("http://localhost:8080"),
            "clusterName",
            Duration.ofMillis(5000),
            new RetryConfiguration(),
            null,
            null);

    final PingHubRunner runner =
        new PingHubRunner(
            config, MANAGEMENT_SERVICES, APPLICATION_CONTEXT, BROKER_TOPOLOGY_MANAGER);

    // when
    final var result = runner.validateConfiguration();

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isEqualTo("M2M credentials must not be null.");
  }

  @Test
  void tokenEndpointMustNotBeNull() {
    // given
    final HubPingConfiguration config =
        new HubPingConfiguration(
            true,
            URI.create("http://localhost:8080"),
            "clusterName",
            Duration.ofMillis(5000),
            new RetryConfiguration(),
            null,
            new M2MCredentials(null, "clientId", "secret"));

    final PingHubRunner runner =
        new PingHubRunner(
            config, MANAGEMENT_SERVICES, APPLICATION_CONTEXT, BROKER_TOPOLOGY_MANAGER);

    // when
    final var result = runner.validateConfiguration();

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isEqualTo("M2M token endpoint must not be null.");
  }

  @Test
  void tokenEndpointMustBeValid() {
    // given
    final HubPingConfiguration config =
        new HubPingConfiguration(
            true,
            URI.create("http://localhost:8080"),
            "clusterName",
            Duration.ofMillis(5000),
            new RetryConfiguration(),
            null,
            new M2MCredentials(URI.create("not-a-valid-uri"), "clientId", "secret"));

    final PingHubRunner runner =
        new PingHubRunner(
            config, MANAGEMENT_SERVICES, APPLICATION_CONTEXT, BROKER_TOPOLOGY_MANAGER);

    // when
    final var result = runner.validateConfiguration();

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft())
        .isEqualTo("M2M token endpoint not-a-valid-uri must be a valid URI.");
  }

  @Test
  void clientIdMustNotBeNullOrEmpty() {
    // given
    final HubPingConfiguration config =
        new HubPingConfiguration(
            true,
            URI.create("http://localhost:8080"),
            "clusterName",
            Duration.ofMillis(5000),
            new RetryConfiguration(),
            null,
            new M2MCredentials(URI.create("http://auth-server.com/token"), "", "secret"));

    final PingHubRunner runner =
        new PingHubRunner(
            config, MANAGEMENT_SERVICES, APPLICATION_CONTEXT, BROKER_TOPOLOGY_MANAGER);

    // when
    final var result = runner.validateConfiguration();

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isEqualTo("M2M client ID must not be null or empty.");
  }

  @Test
  void clientSecretMustNotBeNullOrEmpty() {
    // given
    final HubPingConfiguration config =
        new HubPingConfiguration(
            true,
            URI.create("http://localhost:8080"),
            "clusterName",
            Duration.ofMillis(5000),
            new RetryConfiguration(),
            null,
            new M2MCredentials(URI.create("http://auth-server.com/token"), "clientId", ""));

    final PingHubRunner runner =
        new PingHubRunner(
            config, MANAGEMENT_SERVICES, APPLICATION_CONTEXT, BROKER_TOPOLOGY_MANAGER);

    // when
    final var result = runner.validateConfiguration();

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isEqualTo("M2M client secret must not be null or empty.");
  }

  @Test
  void shouldSucceedToStartHubPingForValidConfig() {
    // given
    final HubPingConfiguration config =
        new HubPingConfiguration(
            true,
            URI.create("http://localhost:8080"),
            "clusterName",
            Duration.ofMillis(5000),
            new RetryConfiguration(),
            null,
            VALID_CREDENTIALS);

    // then
    assertThatCode(
            () ->
                new PingHubRunner(
                    config, MANAGEMENT_SERVICES, APPLICATION_CONTEXT, BROKER_TOPOLOGY_MANAGER))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldNotThrowIfFeatureDisabled() {
    // given an invalid config
    final HubPingConfiguration config =
        new HubPingConfiguration(
            false,
            URI.create("123"),
            null,
            Duration.ofMillis(-300),
            new RetryConfiguration(),
            null,
            null);

    // then we assert that it is not throwing an exception due to the feature being disabled
    assertThatCode(
            () ->
                new PingHubRunner(
                    config, MANAGEMENT_SERVICES, APPLICATION_CONTEXT, BROKER_TOPOLOGY_MANAGER))
        .doesNotThrowAnyException();
  }

  @Test
  void doesNotRetryOnSuccess() throws Exception {
    // given
    final HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockClient.send(
            ArgumentMatchers.<HttpRequest>any(),
            ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenReturn(mockResponse);

    final M2MTokenProvider tokenProvider =
        new M2MTokenProvider(VALID_CREDENTIALS, mockClient) {
          @Override
          public synchronized String getToken() {
            return "test-token";
          }
        };

    final PingHubTask realTask =
        new PingHubTask(pingConfiguration, tokenProvider, mockClient, licensePayload);
    final PingHubTask spyTask = Mockito.spy(realTask);

    // when
    spyTask.run();

    // then it only runs once
    verify(spyTask, times(1)).tryPingHub(any(HttpRequest.class));
  }

  @Test
  void shouldRetryOnSendException() throws Exception {
    // given
    final HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockClient.send(
            ArgumentMatchers.<HttpRequest>any(),
            ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenThrow(new IOException("IO error"))
        .thenThrow(new InterruptedException("Interrupted connection error"))
        .thenReturn(mockResponse); // 3rd try succeeds

    final M2MTokenProvider tokenProvider =
        new M2MTokenProvider(VALID_CREDENTIALS, mockClient) {
          @Override
          public synchronized String getToken() {
            return "test-token";
          }
        };

    final PingHubTask realTask =
        new PingHubTask(pingConfiguration, tokenProvider, mockClient, licensePayload);
    final PingHubTask spyTask = Mockito.spy(realTask);

    // when
    spyTask.run();

    // then it retries 2 times and runs 3 times in total
    verify(spyTask, times(3)).tryPingHub(any(HttpRequest.class));
  }

  @Test
  void shouldRetryOnSpecificStatusCodes() throws Exception {
    // given
    final HttpResponse<String> firstMockResponse = mock(HttpResponse.class);
    final HttpResponse<String> secondMockResponse = mock(HttpResponse.class);
    final HttpResponse<String> thirdMockResponse = mock(HttpResponse.class);
    when(firstMockResponse.statusCode()).thenReturn(500);
    when(secondMockResponse.statusCode()).thenReturn(429);
    when(thirdMockResponse.statusCode()).thenReturn(200);
    when(mockClient.send(
            ArgumentMatchers.<HttpRequest>any(),
            ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenReturn(firstMockResponse)
        .thenReturn(secondMockResponse)
        .thenReturn(thirdMockResponse);

    final M2MTokenProvider tokenProvider =
        new M2MTokenProvider(VALID_CREDENTIALS, mockClient) {
          @Override
          public synchronized String getToken() {
            return "test-token";
          }
        };

    final PingHubTask realTask =
        new PingHubTask(pingConfiguration, tokenProvider, mockClient, licensePayload);
    final PingHubTask spyTask = Mockito.spy(realTask);

    // when
    spyTask.run();

    // then it retries 2 times and runs 3 times in total
    verify(spyTask, times(3)).tryPingHub(any(HttpRequest.class));
  }
}
