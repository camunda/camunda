/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.client;

import static io.camunda.zeebe.client.ClientProperties.CLOUD_REGION;
import static io.camunda.zeebe.client.ClientProperties.DEFAULT_JOB_WORKER_TENANT_IDS;
import static io.camunda.zeebe.client.ClientProperties.DEFAULT_REQUEST_TIMEOUT;
import static io.camunda.zeebe.client.ClientProperties.DEFAULT_TENANT_ID;
import static io.camunda.zeebe.client.ClientProperties.GRPC_ADDRESS;
import static io.camunda.zeebe.client.ClientProperties.MAX_MESSAGE_SIZE;
import static io.camunda.zeebe.client.ClientProperties.MAX_METADATA_SIZE;
import static io.camunda.zeebe.client.ClientProperties.PREFER_REST_OVER_GRPC;
import static io.camunda.zeebe.client.ClientProperties.REST_ADDRESS;
import static io.camunda.zeebe.client.ClientProperties.STREAM_ENABLED;
import static io.camunda.zeebe.client.ClientProperties.USE_DEFAULT_RETRY_POLICY;
import static io.camunda.zeebe.client.ClientProperties.USE_PLAINTEXT_CONNECTION;
import static io.camunda.zeebe.client.impl.ZeebeClientBuilderImpl.DEFAULT_GATEWAY_ADDRESS;
import static io.camunda.zeebe.client.impl.ZeebeClientBuilderImpl.DEFAULT_GRPC_ADDRESS;
import static io.camunda.zeebe.client.impl.ZeebeClientBuilderImpl.DEFAULT_REST_ADDRESS;
import static io.camunda.zeebe.client.impl.ZeebeClientEnvironmentVariables.CA_CERTIFICATE_VAR;
import static io.camunda.zeebe.client.impl.ZeebeClientEnvironmentVariables.DEFAULT_JOB_WORKER_TENANT_IDS_VAR;
import static io.camunda.zeebe.client.impl.ZeebeClientEnvironmentVariables.DEFAULT_TENANT_ID_VAR;
import static io.camunda.zeebe.client.impl.ZeebeClientEnvironmentVariables.GRPC_ADDRESS_VAR;
import static io.camunda.zeebe.client.impl.ZeebeClientEnvironmentVariables.KEEP_ALIVE_VAR;
import static io.camunda.zeebe.client.impl.ZeebeClientEnvironmentVariables.OVERRIDE_AUTHORITY_VAR;
import static io.camunda.zeebe.client.impl.ZeebeClientEnvironmentVariables.PLAINTEXT_CONNECTION_VAR;
import static io.camunda.zeebe.client.impl.ZeebeClientEnvironmentVariables.PREFER_REST_VAR;
import static io.camunda.zeebe.client.impl.ZeebeClientEnvironmentVariables.REST_ADDRESS_VAR;
import static io.camunda.zeebe.client.impl.ZeebeClientEnvironmentVariables.USE_DEFAULT_RETRY_POLICY_VAR;
import static io.camunda.zeebe.client.impl.ZeebeClientEnvironmentVariables.ZEEBE_CLIENT_WORKER_STREAM_ENABLED;
import static io.camunda.zeebe.client.impl.util.DataSizeUtil.ONE_KB;
import static io.camunda.zeebe.client.impl.util.DataSizeUtil.ONE_MB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.client.api.command.CommandWithTenantStep;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.client.impl.NoopCredentialsProvider;
import io.camunda.zeebe.client.impl.ZeebeClientBuilderImpl;
import io.camunda.zeebe.client.impl.ZeebeClientCloudBuilderImpl;
import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProvider;
import io.camunda.zeebe.client.impl.util.Environment;
import io.camunda.zeebe.client.impl.util.EnvironmentRule;
import io.camunda.zeebe.client.util.ClientTest;
import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public final class ZeebeClientTest extends ClientTest {
  @Rule public final EnvironmentRule environmentRule = new EnvironmentRule();
  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test
  public void shouldNotFailIfClosedTwice() {
    client.close();
    client.close();
  }

  @Test
  public void shouldHaveDefaultValues() {
    // given
    try (final ZeebeClient client = ZeebeClient.newClient()) {
      // when
      final ZeebeClientConfiguration configuration = client.getConfiguration();

      // then
      assertThat(configuration.getGatewayAddress()).isEqualTo(DEFAULT_GATEWAY_ADDRESS);
      assertThat(configuration.getGrpcAddress()).isEqualTo(DEFAULT_GRPC_ADDRESS);
      assertThat(configuration.getRestAddress()).isEqualTo(DEFAULT_REST_ADDRESS);
      assertThat(configuration.getDefaultJobWorkerMaxJobsActive()).isEqualTo(32);
      assertThat(configuration.getNumJobWorkerExecutionThreads()).isEqualTo(1);
      assertThat(configuration.getDefaultJobWorkerName()).isEqualTo("default");
      assertThat(configuration.getDefaultJobTimeout()).isEqualTo(Duration.ofMinutes(5));
      assertThat(configuration.getDefaultJobPollInterval()).isEqualTo(Duration.ofMillis(100));
      assertThat(configuration.getDefaultMessageTimeToLive()).isEqualTo(Duration.ofHours(1));
      assertThat(configuration.getDefaultRequestTimeout()).isEqualTo(Duration.ofSeconds(10));
      assertThat(configuration.getMaxMessageSize()).isEqualTo(5 * 1024 * 1024);
      assertThat(configuration.getMaxMetadataSize()).isEqualTo(16 * 1024);
      assertThat(configuration.getOverrideAuthority()).isNull();
      assertThat(configuration.getDefaultTenantId())
          .isEqualTo(CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER);
      assertThat(configuration.getDefaultJobWorkerStreamEnabled()).isFalse();
      assertThat(configuration.getDefaultJobWorkerTenantIds())
          .containsExactly(CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER);
      assertThat(configuration.preferRestOverGrpc()).isFalse();
    }
  }

  @Test
  public void shouldFailIfCertificateDoesNotExist() {
    assertThatThrownBy(
            () -> ZeebeClient.newClientBuilder().caCertificatePath("/wrong/path").build())
        .hasCauseInstanceOf(FileNotFoundException.class);
  }

  @Test
  public void shouldFailWithEmptyCertificatePath() {
    assertThatThrownBy(() -> ZeebeClient.newClientBuilder().caCertificatePath("").build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldHaveTlsEnabledByDefault() {
    assertThat(new ZeebeClientBuilderImpl().isPlaintextConnectionEnabled()).isFalse();
  }

  @Test
  public void shouldUseInsecureWithEnvVar() {
    // given
    Environment.system().put(PLAINTEXT_CONNECTION_VAR, "true");
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();

    // when
    builder.build();

    // then
    assertThat(builder.isPlaintextConnectionEnabled()).isTrue();
  }

  @Test
  public void shouldOverridePropertyWithEnvVariable() {
    // given
    Environment.system().put(PLAINTEXT_CONNECTION_VAR, "false");
    final Properties properties = new Properties();
    properties.putIfAbsent(USE_PLAINTEXT_CONNECTION, "true");
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.withProperties(properties);

    // when
    builder.build();

    // then
    assertThat(builder.isPlaintextConnectionEnabled()).isFalse();
  }

  @Test
  public void shouldNotOverridePropertyWithEnvVariableIfOverridingIsDisabled() {
    // given
    Environment.system().put(PLAINTEXT_CONNECTION_VAR, "false");
    final Properties properties = new Properties();
    properties.putIfAbsent(USE_PLAINTEXT_CONNECTION, "true");
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.applyEnvironmentVariableOverrides(false);
    builder.withProperties(properties);

    // when
    builder.build();

    // then
    assertThat(builder.isPlaintextConnectionEnabled()).isTrue();
  }

  @Test
  public void shouldEnableStreamingWithProperty() {
    // given
    final Properties properties = new Properties();
    properties.putIfAbsent(STREAM_ENABLED, "true");
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.withProperties(properties);

    // when
    builder.build();

    // then
    assertThat(builder.getDefaultJobWorkerStreamEnabled()).isTrue();
  }

  @Test
  public void shouldEnableStreamingWithEnvironmentVariableWhenApplied() {
    // given
    Environment.system().put(ZEEBE_CLIENT_WORKER_STREAM_ENABLED, "true");

    final ZeebeClientBuilderImpl builder1 = new ZeebeClientBuilderImpl();
    final ZeebeClientBuilderImpl builder2 = new ZeebeClientBuilderImpl();
    builder1.applyEnvironmentVariableOverrides(false);
    builder2.applyEnvironmentVariableOverrides(true);

    // when
    builder1.build();
    builder2.build();
    assertThat(builder1.getDefaultJobWorkerStreamEnabled()).isFalse();
    assertThat(builder2.getDefaultJobWorkerStreamEnabled()).isTrue();
  }

  @Test
  public void environmentVariableShouldOverrideProperty() {
    // given
    Environment.system().put(ZEEBE_CLIENT_WORKER_STREAM_ENABLED, "true");
    final Properties properties = new Properties();
    properties.putIfAbsent(STREAM_ENABLED, "false");

    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.withProperties(properties).applyEnvironmentVariableOverrides(true);

    // when
    builder.build();
    assertThat(builder.getDefaultJobWorkerStreamEnabled()).isTrue();
  }

  @Test
  public void shouldCaCertificateWithEnvVar() {
    // given
    final String certPath = getClass().getClassLoader().getResource("ca.cert.pem").getPath();
    Environment.system().put(CA_CERTIFICATE_VAR, certPath);
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();

    // when
    builder.build();

    // then
    assertThat(builder.getCaCertificatePath()).isEqualTo(certPath);
  }

  @Test
  public void shouldSetKeepAlive() {
    // given
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.keepAlive(Duration.ofMinutes(2));

    // when
    builder.build();

    // then
    assertThat(builder.getKeepAlive()).isEqualTo(Duration.ofMinutes(2));
  }

  @Test
  public void shouldOverrideKeepAliveWithEnvVar() {
    // given
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.keepAlive(Duration.ofMinutes(2));
    Environment.system().put(KEEP_ALIVE_VAR, "15000");

    // when
    builder.build();

    // then
    assertThat(builder.getKeepAlive()).isEqualTo(Duration.ofSeconds(15));
  }

  @Test
  public void shouldSetAuthority() {
    // given
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.overrideAuthority("virtualhost");

    // when
    builder.build();

    // then
    assertThat(builder.getOverrideAuthority()).isEqualTo("virtualhost");
  }

  @Test
  public void shouldOverrideAuthorityWithEnvVar() {
    // given
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.overrideAuthority("localhost");
    Environment.system().put(OVERRIDE_AUTHORITY_VAR, "virtualhost");

    // when
    builder.build();

    // then
    assertThat(builder.getOverrideAuthority()).isEqualTo("virtualhost");
  }

  @Test
  public void shouldSetMaxMessageSize() {
    // given
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.maxMessageSize(10 * 1024 * 1024);

    // when
    builder.build();

    // then
    assertThat(builder.getMaxMessageSize()).isEqualTo(10 * 1024 * 1024);
  }

  @Test
  public void shouldSetMaxMetadataSize() {
    // given
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.maxMetadataSize(10 * 1024);

    // when
    builder.build();

    // then
    assertThat(builder.getMaxMetadataSize()).isEqualTo(10 * 1024);
  }

  @Test
  public void shouldSetMaxMessageSizeWithProperty() {
    // given
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();

    final Properties properties = new Properties();
    properties.setProperty(MAX_MESSAGE_SIZE, "10MB");
    builder.withProperties(properties);
    // when
    builder.build();

    // then
    assertThat(builder.getMaxMessageSize()).isEqualTo(10 * ONE_MB);
  }

  @Test
  public void shouldSetMaxMetadataSizeWithProperty() {
    // given
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();

    final Properties properties = new Properties();
    properties.setProperty(MAX_METADATA_SIZE, "10KB");
    builder.withProperties(properties);
    // when
    builder.build();

    // then
    assertThat(builder.getMaxMetadataSize()).isEqualTo(10 * ONE_KB);
  }

  @Test
  public void shouldRejectUnsupportedTimeUnitWithEnvVar() {
    // when/then
    Environment.system().put(KEEP_ALIVE_VAR, "30d");
    assertThatThrownBy(() -> new ZeebeClientBuilderImpl().build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldRejectNegativeTime() {
    // when/then
    assertThatThrownBy(() -> new ZeebeClientBuilderImpl().keepAlive(Duration.ofSeconds(-2)).build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldRejectNegativeTimeAsEnvVar() {
    // when/then
    Environment.system().put(KEEP_ALIVE_VAR, "-2s");
    assertThatThrownBy(() -> new ZeebeClientBuilderImpl().build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldCloudBuilderBuildProperClient() {
    // given
    final String clusterId = "clusterId";
    final String region = "asdf-123";

    try (final ZeebeClient client =
        ZeebeClient.newCloudClientBuilder()
            .withClusterId(clusterId)
            .withClientId("clientId")
            .withClientSecret("clientSecret")
            .withRegion(region)
            .build()) {
      // when
      final ZeebeClientConfiguration clientConfiguration = client.getConfiguration();
      // then
      assertThat(clientConfiguration.getCredentialsProvider())
          .isInstanceOf(OAuthCredentialsProvider.class);
      assertThat(clientConfiguration.getGrpcAddress())
          .hasHost(String.format("%s.%s.zeebe.camunda.io", clusterId, region))
          .hasPort(443)
          .hasScheme("https");
    }
  }

  @Test
  public void shouldCloudBuilderBuildProperClientWithDefaultRegion() {
    // given
    final String clusterId = "clusterId";
    try (final ZeebeClient client =
        ZeebeClient.newCloudClientBuilder()
            .withClusterId(clusterId)
            .withClientId("clientId")
            .withClientSecret("clientSecret")
            .build()) {
      // when
      final ZeebeClientConfiguration clientConfiguration = client.getConfiguration();
      // then
      assertThat(clientConfiguration.getCredentialsProvider())
          .isInstanceOf(OAuthCredentialsProvider.class);
      assertThat(clientConfiguration.getGrpcAddress())
          .hasHost(String.format("%s.bru-2.zeebe.camunda.io", clusterId))
          .hasPort(443)
          .hasScheme("https");
    }
  }

  @Test
  public void shouldOverrideCloudProperties() {
    // given
    final String gatewayAddress = "localhost:10000";
    final NoopCredentialsProvider credentialsProvider = new NoopCredentialsProvider();
    try (final ZeebeClient client =
        ZeebeClient.newCloudClientBuilder()
            .withClusterId("clusterId")
            .withClientId("clientId")
            .withClientSecret("clientSecret")
            .gatewayAddress(gatewayAddress)
            .credentialsProvider(credentialsProvider)
            .build()) {
      final ZeebeClientConfiguration configuration = client.getConfiguration();
      assertThat(configuration.getGatewayAddress()).isEqualTo(gatewayAddress);
      assertThat(configuration.getCredentialsProvider()).isEqualTo(credentialsProvider);
    }
  }

  @Test
  public void shouldCloudBuilderBuildProperClientWithRegionPropertyProvided() {
    // given
    final String region = "asdf-123";
    final Properties properties = new Properties();
    properties.putIfAbsent(CLOUD_REGION, region);
    try (final ZeebeClient client =
        ZeebeClient.newCloudClientBuilder()
            .withClusterId("clusterId")
            .withClientId("clientId")
            .withClientSecret("clientSecret")
            .withProperties(properties)
            .build()) {
      // when
      final ZeebeClientConfiguration clientConfiguration = client.getConfiguration();
      // then
      assertThat(clientConfiguration.getCredentialsProvider())
          .isInstanceOf(OAuthCredentialsProvider.class);
      assertThat(clientConfiguration.getGrpcAddress())
          .hasHost(String.format("clusterId.%s.zeebe.camunda.io", region))
          .hasPort(443)
          .hasScheme("https");
    }
  }

  @Test
  public void shouldCloudBuilderBuildProperClientWithRegionPropertyNotProvided() {
    // given
    final String defaultRegion = "bru-2";
    try (final ZeebeClient client =
        ZeebeClient.newCloudClientBuilder()
            .withClusterId("clusterId")
            .withClientId("clientId")
            .withClientSecret("clientSecret")
            .build()) {
      // when
      final ZeebeClientConfiguration clientConfiguration = client.getConfiguration();
      // then
      assertThat(clientConfiguration.getCredentialsProvider())
          .isInstanceOf(OAuthCredentialsProvider.class);
      assertThat(clientConfiguration.getGrpcAddress())
          .hasHost(String.format("clusterId.%s.zeebe.camunda.io", defaultRegion))
          .hasPort(443)
          .hasScheme("https");
    }
  }

  @Test
  public void shouldCloseOwnedExecutorOnClose() {
    // given
    final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    try (final ZeebeClient client =
        ZeebeClient.newClientBuilder().jobWorkerExecutor(executor, true).build()) {
      // when
      client.close();

      // then
      assertThat(executor.isShutdown()).isTrue();
    }
  }

  @Test
  public void shouldNotCloseNotOwnedExecutor() {
    // given
    final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    try (final ZeebeClient client =
        ZeebeClient.newClientBuilder().jobWorkerExecutor(executor, false).build()) {
      // when
      client.close();

      // then
      assertThat(executor.isShutdown()).isFalse();
    }

    executor.shutdownNow();
  }

  @Test
  public void shouldUseCustomExecutorWithJobWorker() {
    // given
    final ScheduledThreadPoolExecutor executor = spy(new ScheduledThreadPoolExecutor(1));
    final Duration pollInterval = Duration.ZERO;
    try (final ZeebeClient client =
            ZeebeClient.newClientBuilder().jobWorkerExecutor(executor).build();
        final JobWorker ignored =
            client
                .newWorker()
                .jobType("type")
                .handler((c, j) -> {})
                .pollInterval(pollInterval)
                .open()) {
      // when - then
      verify(executor)
          .schedule(any(Runnable.class), eq(pollInterval.toMillis()), eq(TimeUnit.MILLISECONDS));
    }
  }

  @Test
  public void shouldSetRestAddressFromSetterWithClientBuilder() throws URISyntaxException {
    // given
    final URI restAddress = new URI("http://localhost:9090");
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.restAddress(restAddress);

    // when
    builder.build();

    // then
    assertThat(builder.getRestAddress()).isEqualTo(restAddress);
  }

  @Test
  public void shouldSetRestAddressPortFromPropertyWithClientBuilder() throws URISyntaxException {
    // given
    final URI restAddress = new URI("http://localhost:9090");
    final Properties properties = new Properties();
    properties.setProperty(REST_ADDRESS, restAddress.toString());
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.withProperties(properties);

    // when
    builder.build();

    // then
    assertThat(builder.getRestAddress()).isEqualTo(restAddress);
  }

  @Test
  public void shouldSetRestAddressPortFromEnvVarWithClientBuilder() throws URISyntaxException {
    // given
    final URI restAddress = new URI("http://localhost:9090");
    Environment.system().put(REST_ADDRESS_VAR, restAddress.toString());

    // when
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.build();

    // then
    assertThat(builder.getRestAddress()).isEqualTo(restAddress);
  }

  @Test
  public void shouldSetGrpcAddressFromSetterWithClientBuilder() throws URISyntaxException {
    // given
    final URI grpcAddress = new URI("https://localhost:9090");
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.grpcAddress(grpcAddress);

    // when
    builder.build();

    // then
    assertThat(builder.getGrpcAddress()).isEqualTo(grpcAddress);
  }

  @Test
  public void shouldSetGrpcAddressFromPropertyWithClientBuilder() throws URISyntaxException {
    // given
    final URI grpcAddress = new URI("https://localhost:9090");
    final Properties properties = new Properties();
    properties.setProperty(GRPC_ADDRESS, grpcAddress.toString());
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.withProperties(properties);

    // when
    builder.build();

    // then
    assertThat(builder.getGrpcAddress()).isEqualTo(grpcAddress);
  }

  @Test
  public void shouldSetGrpcAddressFromEnvVarWithClientBuilder() throws URISyntaxException {
    // given
    final URI grpcAddress = new URI("https://localhost:9090");
    Environment.system().put(GRPC_ADDRESS_VAR, grpcAddress.toString());

    // when
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.build();

    // then
    assertThat(builder.getGrpcAddress()).isEqualTo(grpcAddress);
  }

  @Test
  public void shouldSetPreferRestFromSetterWithClientBuilder() {
    // given
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();

    // when
    builder.preferRestOverGrpc(false);

    // then
    try (final ZeebeClient client = builder.build()) {
      assertThat(client.getConfiguration().preferRestOverGrpc()).isFalse();
    }
  }

  @Test
  public void shouldSetPreferRestFromPropertyWithClientBuilder() {
    // given
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    final Properties properties = new Properties();
    properties.setProperty(PREFER_REST_OVER_GRPC, "false");

    // when
    builder.withProperties(properties);

    // then
    try (final ZeebeClient client = builder.build()) {
      assertThat(client.getConfiguration().preferRestOverGrpc()).isFalse();
    }
  }

  @Test
  public void shouldSetPreferRestFromEnvVarWithClientBuilder() {
    // given
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    Environment.system().put(PREFER_REST_VAR, "false");

    // when
    builder.preferRestOverGrpc(true);

    // then
    try (final ZeebeClient client = builder.build()) {
      assertThat(client.getConfiguration().preferRestOverGrpc()).isFalse();
    }
  }

  @Test
  public void shouldSetGrpcAddressFromGatewayAddressIfUnderfined() throws URISyntaxException {
    // given
    final String gatewayAddress = "localhost:26500";
    final Properties properties = new Properties();
    properties.setProperty(ClientProperties.GATEWAY_ADDRESS, gatewayAddress);
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.withProperties(properties);

    // when
    builder.build();

    // then
    assertThat(builder.getGrpcAddress().toString()).contains(gatewayAddress);
  }

  @Test
  public void shouldUseDefaultTenantId() {
    // given
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();

    // when
    builder.build();

    // then
    assertThat(builder.getDefaultTenantId())
        .isEqualTo(CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  public void shouldSetDefaultTenantIdFromSetterWithClientBuilder() {
    // given
    final String overrideTenant = "override-tenant";
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.defaultTenantId(overrideTenant);

    // when
    builder.build();

    // then
    assertThat(builder.getDefaultTenantId()).isEqualTo(overrideTenant);
  }

  @Test
  public void shouldSetDefaultTenantIdFromPropertyWithClientBuilder() {
    // given
    final String tenantId = "test-tenant";
    final Properties properties = new Properties();
    properties.setProperty(DEFAULT_TENANT_ID, tenantId);
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.withProperties(properties);

    // when
    builder.build();

    // then
    assertThat(builder.getDefaultTenantId()).isEqualTo(tenantId);
  }

  @Test
  public void shouldSetDefaultTenantIdFromEnvVarWithClientBuilder() {
    // given
    final String overrideTenant = "override-tenant";
    Environment.system().put(DEFAULT_TENANT_ID_VAR, overrideTenant);

    // when
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.build();

    // then
    assertThat(builder.getDefaultTenantId()).isEqualTo(overrideTenant);
  }

  @Test
  public void shouldSetFinalDefaultTenantIdFromEnvVarWithClientBuilder() {
    // given
    final String propertyTenantId = "test-tenant";
    final Properties properties = new Properties();
    properties.setProperty(DEFAULT_TENANT_ID, propertyTenantId);
    final String envVarTenantId = "override-tenant";
    Environment.system().put(DEFAULT_TENANT_ID_VAR, envVarTenantId);
    final String setterTenantId = "setter-tenant";
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.defaultTenantId(setterTenantId);

    // when
    builder.build();

    // then
    assertThat(builder.getDefaultTenantId()).isEqualTo(envVarTenantId);
  }

  @Test
  public void shouldNotSetDefaultTenantIdFromPropertyWithCloudClientBuilder() {
    // given
    final String tenantId = "test-tenant";
    final ZeebeClientCloudBuilderImpl builder = new ZeebeClientCloudBuilderImpl();
    final Properties properties = new Properties();
    properties.setProperty(DEFAULT_TENANT_ID, tenantId);
    builder.withProperties(properties);

    // when
    final ZeebeClient client =
        builder
            .withClusterId("clusterId")
            .withClientId("clientId")
            .withClientSecret("clientSecret")
            .build();

    // then
    // todo(#14106): verify that tenant id is set in the request
    assertThat(client.getConfiguration().getDefaultTenantId()).isEqualTo("");
  }

  @Test
  public void shouldNotSetDefaultTenantIdFromSetterWithCloudClientBuilder() {
    // given
    final String tenantId = "test-tenant";
    final ZeebeClientCloudBuilderImpl builder = new ZeebeClientCloudBuilderImpl();

    // when
    final ZeebeClientCloudBuilderImpl builderWithTenantId =
        (ZeebeClientCloudBuilderImpl) builder.defaultTenantId(tenantId);

    // then
    // todo(#14106): verify that tenant id is set in the builder
    assertThat(builderWithTenantId)
        .describedAs(
            "This method has no effect on the cloud client builder while under development")
        .isEqualTo(builder);
  }

  @Test
  public void shouldUseDefaultJobWorkerTenantIds() {
    // given
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();

    // when
    builder.build();

    // then
    assertThat(builder.getDefaultJobWorkerTenantIds())
        .containsExactly(CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  public void shouldSetDefaultJobWorkerTenantIdsFromSetterWithClientBuilder() {
    // given
    final String overrideTenant = "override-tenant";
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.defaultJobWorkerTenantIds(Arrays.asList(overrideTenant));

    // when
    builder.build();

    // then
    assertThat(builder.getDefaultJobWorkerTenantIds()).containsExactly(overrideTenant);
  }

  @Test
  public void shouldSetDefaultJobWorkerTenantIdsFromPropertyWithClientBuilder() {
    // given
    final List<String> tenantIdList = Arrays.asList("test-tenant-1", "test-tenant-2");
    final Properties properties = new Properties();
    properties.setProperty(DEFAULT_JOB_WORKER_TENANT_IDS, String.join(",", tenantIdList));
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.withProperties(properties);

    // when
    builder.build();

    // then
    assertThat(builder.getDefaultJobWorkerTenantIds()).containsExactlyElementsOf(tenantIdList);
  }

  @Test
  public void shouldSetDefaultJobWorkerTenantIdsFromEnvVarWithClientBuilder() {
    // given
    final List<String> tenantIdList = Arrays.asList("test-tenant-1", "test-tenant-2");
    Environment.system().put(DEFAULT_JOB_WORKER_TENANT_IDS_VAR, String.join(",", tenantIdList));

    // when
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.build();

    // then
    assertThat(builder.getDefaultJobWorkerTenantIds()).containsExactlyElementsOf(tenantIdList);
  }

  @Test
  public void shouldSetFinalDefaultJobWorkerTenantIdsFromEnvVarWithClientBuilder() {
    // given
    final String propertyTenantId = "test-tenant";
    final Properties properties = new Properties();
    properties.setProperty(DEFAULT_JOB_WORKER_TENANT_IDS, propertyTenantId);
    final List<String> tenantIdList = Arrays.asList("test-tenant-1", "test-tenant-2");
    Environment.system().put(DEFAULT_JOB_WORKER_TENANT_IDS_VAR, String.join(",", tenantIdList));
    final String setterTenantId = "setter-tenant";
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.defaultJobWorkerTenantIds(Arrays.asList(setterTenantId));

    // when
    builder.build();

    // then
    assertThat(builder.getDefaultJobWorkerTenantIds()).containsExactlyElementsOf(tenantIdList);
  }

  @Test
  public void shouldNotSetDefaultJobWorkerTenantIdsFromPropertyWithCloudClientBuilder() {
    // given
    final ZeebeClientCloudBuilderImpl builder = new ZeebeClientCloudBuilderImpl();
    final Properties properties = new Properties();
    final List<String> tenantIdList = Arrays.asList("test-tenant-1", "test-tenant-2");
    properties.setProperty(DEFAULT_JOB_WORKER_TENANT_IDS, String.join(",", tenantIdList));
    builder.withProperties(properties);

    // when
    final ZeebeClient client =
        builder
            .withClusterId("clusterId")
            .withClientId("clientId")
            .withClientSecret("clientSecret")
            .build();

    // then
    // todo(#14106): verify that tenant ids are set in the request
    assertThat(client.getConfiguration().getDefaultJobWorkerTenantIds()).isEmpty();
  }

  @Test
  public void shouldNotSetDefaultJobWorkerTenantIdsFromSetterWithCloudClientBuilder() {
    // given
    final ZeebeClientCloudBuilderImpl builder = new ZeebeClientCloudBuilderImpl();
    final List<String> tenantIdList = Arrays.asList("test-tenant-1", "test-tenant-2");

    // when
    final ZeebeClientCloudBuilderImpl builderWithTenantId =
        (ZeebeClientCloudBuilderImpl) builder.defaultJobWorkerTenantIds(tenantIdList);

    // then
    // todo(#14106): verify that tenant id is set in the builder
    assertThat(builderWithTenantId)
        .describedAs(
            "This method has no effect on the cloud client builder while under development")
        .isEqualTo(builder);
  }

  @Test
  public void shouldUseDefaultRetryPolicy() {
    // given
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.useDefaultRetryPolicy(true);

    // when
    builder.build();

    // then
    assertThat(builder.useDefaultRetryPolicy()).isTrue();
  }

  @Test
  public void shouldOverrideDefaultRetryPolicyWithEnvVar() {
    // given
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.useDefaultRetryPolicy(true);
    Environment.system().put(USE_DEFAULT_RETRY_POLICY_VAR, "false");

    // when
    builder.build();

    // then
    assertThat(builder.useDefaultRetryPolicy()).isFalse();
  }

  @Test
  public void shouldOverrideDefaultRetryPolicyWithProperty() {
    // given
    final Properties properties = new Properties();
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.useDefaultRetryPolicy(true);
    properties.setProperty(USE_DEFAULT_RETRY_POLICY, "false");
    builder.withProperties(properties);

    // when
    builder.build();

    // then
    assertThat(builder.useDefaultRetryPolicy()).isFalse();
  }

  @Test
  public void shouldSetTimeoutInMillis() {
    // given
    final Properties properties = new Properties();
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    properties.setProperty(DEFAULT_REQUEST_TIMEOUT, "1000");
    builder.withProperties(properties);

    // when
    builder.build();

    // then
    assertThat(builder.getDefaultRequestTimeout()).isEqualTo(Duration.ofSeconds(1));
  }
}
