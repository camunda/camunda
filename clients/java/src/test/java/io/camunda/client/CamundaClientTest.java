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
package io.camunda.client;

import static io.camunda.client.ClientProperties.CLOUD_REGION;
import static io.camunda.client.ClientProperties.DEFAULT_JOB_WORKER_TENANT_IDS;
import static io.camunda.client.ClientProperties.DEFAULT_REQUEST_TIMEOUT;
import static io.camunda.client.ClientProperties.DEFAULT_TENANT_ID;
import static io.camunda.client.ClientProperties.GRPC_ADDRESS;
import static io.camunda.client.ClientProperties.MAX_MESSAGE_SIZE;
import static io.camunda.client.ClientProperties.MAX_METADATA_SIZE;
import static io.camunda.client.ClientProperties.PREFER_REST_OVER_GRPC;
import static io.camunda.client.ClientProperties.REST_ADDRESS;
import static io.camunda.client.ClientProperties.STREAM_ENABLED;
import static io.camunda.client.ClientProperties.USE_DEFAULT_RETRY_POLICY;
import static io.camunda.client.ClientProperties.USE_PLAINTEXT_CONNECTION;
import static io.camunda.client.impl.CamundaClientBuilderImpl.DEFAULT_GATEWAY_ADDRESS;
import static io.camunda.client.impl.CamundaClientBuilderImpl.DEFAULT_GRPC_ADDRESS;
import static io.camunda.client.impl.CamundaClientBuilderImpl.DEFAULT_REST_ADDRESS;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.CAMUNDA_CLIENT_WORKER_STREAM_ENABLED;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.CA_CERTIFICATE_VAR;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.DEFAULT_JOB_WORKER_TENANT_IDS_VAR;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.DEFAULT_TENANT_ID_VAR;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.GRPC_ADDRESS_VAR;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.KEEP_ALIVE_VAR;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OVERRIDE_AUTHORITY_VAR;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.PLAINTEXT_CONNECTION_VAR;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.PREFER_REST_VAR;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.REST_ADDRESS_VAR;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.USE_DEFAULT_RETRY_POLICY_VAR;
import static io.camunda.client.impl.util.DataSizeUtil.ONE_KB;
import static io.camunda.client.impl.util.DataSizeUtil.ONE_MB;
import static io.camunda.zeebe.client.impl.ZeebeClientEnvironmentVariables.ZEEBE_CLIENT_WORKER_STREAM_ENABLED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import io.camunda.client.api.command.CommandWithTenantStep;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.client.impl.CamundaClientBuilderImpl;
import io.camunda.client.impl.CamundaClientCloudBuilderImpl;
import io.camunda.client.impl.NoopCredentialsProvider;
import io.camunda.client.impl.oauth.OAuthCredentialsProvider;
import io.camunda.client.impl.util.Environment;
import io.camunda.client.impl.util.EnvironmentExtension;
import io.camunda.zeebe.client.impl.ZeebeClientEnvironmentVariables;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@ExtendWith(EnvironmentExtension.class)
public final class CamundaClientTest {

  @Test
  public void shouldNotFailIfClosedTwice() {
    final CamundaClient client = CamundaClient.newClient();
    client.close();
    client.close();
  }

  @Test
  public void shouldHaveDefaultValues() {
    // given
    try (final CamundaClient client = CamundaClient.newClient()) {
      // when
      final CamundaClientConfiguration configuration = client.getConfiguration();

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
            () -> CamundaClient.newClientBuilder().caCertificatePath("/wrong/path").build())
        .hasCauseInstanceOf(FileNotFoundException.class);
  }

  @Test
  public void shouldFailWithEmptyCertificatePath() {
    assertThatThrownBy(() -> CamundaClient.newClientBuilder().caCertificatePath("").build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldHaveTlsEnabledByDefault() {
    assertThat(new CamundaClientBuilderImpl().isPlaintextConnectionEnabled()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        PLAINTEXT_CONNECTION_VAR,
        ZeebeClientEnvironmentVariables.PLAINTEXT_CONNECTION_VAR
      })
  public void shouldUseInsecureWithEnvVar(final String envVarName) {
    // given
    Environment.system().put(envVarName, "true");
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();

    // when
    builder.build();

    // then
    assertThat(builder.isPlaintextConnectionEnabled()).isTrue();
  }

  @ParameterizedTest
  @CsvSource({
    PLAINTEXT_CONNECTION_VAR + "," + USE_PLAINTEXT_CONNECTION,
    ZeebeClientEnvironmentVariables.PLAINTEXT_CONNECTION_VAR + "," + USE_PLAINTEXT_CONNECTION,
    PLAINTEXT_CONNECTION_VAR
        + ","
        + io.camunda.zeebe.client.ClientProperties.USE_PLAINTEXT_CONNECTION,
    ZeebeClientEnvironmentVariables.PLAINTEXT_CONNECTION_VAR
        + ","
        + io.camunda.zeebe.client.ClientProperties.USE_PLAINTEXT_CONNECTION
  })
  public void shouldOverridePropertyWithEnvVariable(
      final String envName, final String propertyName) {
    // given
    Environment.system().put(envName, "false");
    final Properties properties = new Properties();
    properties.putIfAbsent(propertyName, "true");
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.withProperties(properties);

    // when
    builder.build();

    // then
    assertThat(builder.isPlaintextConnectionEnabled()).isFalse();
  }

  @ParameterizedTest
  @CsvSource({
    PLAINTEXT_CONNECTION_VAR + "," + USE_PLAINTEXT_CONNECTION,
    ZeebeClientEnvironmentVariables.PLAINTEXT_CONNECTION_VAR + "," + USE_PLAINTEXT_CONNECTION,
    PLAINTEXT_CONNECTION_VAR
        + ","
        + io.camunda.zeebe.client.ClientProperties.USE_PLAINTEXT_CONNECTION,
    ZeebeClientEnvironmentVariables.PLAINTEXT_CONNECTION_VAR
        + ","
        + io.camunda.zeebe.client.ClientProperties.USE_PLAINTEXT_CONNECTION
  })
  public void shouldNotOverridePropertyWithEnvVariableIfOverridingIsDisabled(
      final String envName, final String propertyName) {
    // given
    Environment.system().put(envName, "false");
    final Properties properties = new Properties();
    properties.putIfAbsent(propertyName, "true");
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.applyEnvironmentVariableOverrides(false);
    builder.withProperties(properties);

    // when
    builder.build();

    // then
    assertThat(builder.isPlaintextConnectionEnabled()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {STREAM_ENABLED, io.camunda.zeebe.client.ClientProperties.STREAM_ENABLED})
  public void shouldEnableStreamingWithProperty(final String propertyName) {
    // given
    final Properties properties = new Properties();
    properties.putIfAbsent(propertyName, "true");
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.withProperties(properties);

    // when
    builder.build();

    // then
    assertThat(builder.getDefaultJobWorkerStreamEnabled()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {CAMUNDA_CLIENT_WORKER_STREAM_ENABLED, ZEEBE_CLIENT_WORKER_STREAM_ENABLED})
  public void shouldEnableStreamingWithEnvironmentVariableWhenApplied(final String envName) {
    // given
    Environment.system().put(envName, "true");

    final CamundaClientBuilderImpl builder1 = new CamundaClientBuilderImpl();
    final CamundaClientBuilderImpl builder2 = new CamundaClientBuilderImpl();
    builder1.applyEnvironmentVariableOverrides(false);
    builder2.applyEnvironmentVariableOverrides(true);

    // when
    builder1.build();
    builder2.build();
    assertThat(builder1.getDefaultJobWorkerStreamEnabled()).isFalse();
    assertThat(builder2.getDefaultJobWorkerStreamEnabled()).isTrue();
  }

  @ParameterizedTest
  @CsvSource({
    CAMUNDA_CLIENT_WORKER_STREAM_ENABLED + "," + STREAM_ENABLED,
    ZEEBE_CLIENT_WORKER_STREAM_ENABLED + "," + STREAM_ENABLED,
    CAMUNDA_CLIENT_WORKER_STREAM_ENABLED
        + ","
        + io.camunda.zeebe.client.ClientProperties.STREAM_ENABLED,
    ZEEBE_CLIENT_WORKER_STREAM_ENABLED
        + ","
        + io.camunda.zeebe.client.ClientProperties.STREAM_ENABLED
  })
  public void environmentVariableShouldOverrideProperty(
      final String envName, final String propertyName) {
    // given
    Environment.system().put(envName, "true");
    final Properties properties = new Properties();
    properties.putIfAbsent(propertyName, "false");

    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.withProperties(properties).applyEnvironmentVariableOverrides(true);

    // when
    builder.build();
    assertThat(builder.getDefaultJobWorkerStreamEnabled()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {CA_CERTIFICATE_VAR, ZeebeClientEnvironmentVariables.CA_CERTIFICATE_VAR})
  public void shouldCaCertificateWithEnvVar(final String envName) {
    // given
    final String certPath = getClass().getClassLoader().getResource("ca.cert.pem").getPath();
    Environment.system().put(envName, certPath);
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();

    // when
    builder.build();

    // then
    assertThat(builder.getCaCertificatePath()).isEqualTo(certPath);
  }

  @Test
  public void shouldSetKeepAlive() {
    // given
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.keepAlive(Duration.ofMinutes(2));

    // when
    builder.build();

    // then
    assertThat(builder.getKeepAlive()).isEqualTo(Duration.ofMinutes(2));
  }

  @ParameterizedTest
  @ValueSource(strings = {KEEP_ALIVE_VAR, ZeebeClientEnvironmentVariables.KEEP_ALIVE_VAR})
  public void shouldOverrideKeepAliveWithEnvVar(final String envName) {
    // given
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
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
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.overrideAuthority("virtualhost");

    // when
    builder.build();

    // then
    assertThat(builder.getOverrideAuthority()).isEqualTo("virtualhost");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {OVERRIDE_AUTHORITY_VAR, ZeebeClientEnvironmentVariables.OVERRIDE_AUTHORITY_VAR})
  public void shouldOverrideAuthorityWithEnvVar(final String envName) {
    // given
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.overrideAuthority("localhost");
    Environment.system().put(envName, "virtualhost");

    // when
    builder.build();

    // then
    assertThat(builder.getOverrideAuthority()).isEqualTo("virtualhost");
  }

  @Test
  public void shouldSetMaxMessageSize() {
    // given
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.maxMessageSize(10 * 1024 * 1024);

    // when
    builder.build();

    // then
    assertThat(builder.getMaxMessageSize()).isEqualTo(10 * 1024 * 1024);
  }

  @Test
  public void shouldSetMaxMetadataSize() {
    // given
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.maxMetadataSize(10 * 1024);

    // when
    builder.build();

    // then
    assertThat(builder.getMaxMetadataSize()).isEqualTo(10 * 1024);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {MAX_MESSAGE_SIZE, io.camunda.zeebe.client.ClientProperties.MAX_MESSAGE_SIZE})
  public void shouldSetMaxMessageSizeWithProperty(final String propertyName) {
    // given
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();

    final Properties properties = new Properties();
    properties.setProperty(propertyName, "10MB");
    builder.withProperties(properties);
    // when
    builder.build();

    // then
    assertThat(builder.getMaxMessageSize()).isEqualTo(10 * ONE_MB);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {MAX_METADATA_SIZE, io.camunda.zeebe.client.ClientProperties.MAX_METADATA_SIZE})
  public void shouldSetMaxMetadataSizeWithProperty(final String propertyName) {
    // given
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();

    final Properties properties = new Properties();
    properties.setProperty(propertyName, "10KB");
    builder.withProperties(properties);
    // when
    builder.build();

    // then
    assertThat(builder.getMaxMetadataSize()).isEqualTo(10 * ONE_KB);
  }

  @ParameterizedTest
  @ValueSource(strings = {KEEP_ALIVE_VAR, ZeebeClientEnvironmentVariables.KEEP_ALIVE_VAR})
  public void shouldRejectUnsupportedTimeUnitWithEnvVar(final String envName) {
    // when/then
    Environment.system().put(envName, "30d");
    assertThatThrownBy(() -> new CamundaClientBuilderImpl().build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldRejectNegativeTime() {
    // when/then
    assertThatThrownBy(
            () -> new CamundaClientBuilderImpl().keepAlive(Duration.ofSeconds(-2)).build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {KEEP_ALIVE_VAR, ZeebeClientEnvironmentVariables.KEEP_ALIVE_VAR})
  public void shouldRejectNegativeTimeAsEnvVar(final String envName) {
    // when/then
    Environment.system().put(envName, "-2s");
    assertThatThrownBy(() -> new CamundaClientBuilderImpl().build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldCloudBuilderBuildProperClient() {
    // given
    final String clusterId = "clusterId";
    final String region = "asdf-123";

    try (final CamundaClient client =
        CamundaClient.newCloudClientBuilder()
            .withClusterId(clusterId)
            .withClientId("clientId")
            .withClientSecret("clientSecret")
            .withRegion(region)
            .build()) {
      // when
      final CamundaClientConfiguration clientConfiguration = client.getConfiguration();
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
    try (final CamundaClient client =
        CamundaClient.newCloudClientBuilder()
            .withClusterId(clusterId)
            .withClientId("clientId")
            .withClientSecret("clientSecret")
            .build()) {
      // when
      final CamundaClientConfiguration clientConfiguration = client.getConfiguration();
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
    try (final CamundaClient client =
        CamundaClient.newCloudClientBuilder()
            .withClusterId("clusterId")
            .withClientId("clientId")
            .withClientSecret("clientSecret")
            .gatewayAddress(gatewayAddress)
            .credentialsProvider(credentialsProvider)
            .build()) {
      final CamundaClientConfiguration configuration = client.getConfiguration();
      assertThat(configuration.getGatewayAddress()).isEqualTo(gatewayAddress);
      assertThat(configuration.getCredentialsProvider()).isEqualTo(credentialsProvider);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {CLOUD_REGION, io.camunda.zeebe.client.ClientProperties.CLOUD_REGION})
  public void shouldCloudBuilderBuildProperClientWithRegionPropertyProvided(
      final String propertyName) {
    // given
    final String region = "asdf-123";
    final Properties properties = new Properties();
    properties.putIfAbsent(propertyName, region);
    try (final CamundaClient client =
        CamundaClient.newCloudClientBuilder()
            .withClusterId("clusterId")
            .withClientId("clientId")
            .withClientSecret("clientSecret")
            .withProperties(properties)
            .build()) {
      // when
      final CamundaClientConfiguration clientConfiguration = client.getConfiguration();
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
    try (final CamundaClient client =
        CamundaClient.newCloudClientBuilder()
            .withClusterId("clusterId")
            .withClientId("clientId")
            .withClientSecret("clientSecret")
            .build()) {
      // when
      final CamundaClientConfiguration clientConfiguration = client.getConfiguration();
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
    try (final CamundaClient client =
        CamundaClient.newClientBuilder().jobWorkerExecutor(executor, true).build()) {
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
    try (final CamundaClient client =
        CamundaClient.newClientBuilder().jobWorkerExecutor(executor, false).build()) {
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
    try (final CamundaClient client =
            CamundaClient.newClientBuilder().jobWorkerExecutor(executor).build();
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
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.restAddress(restAddress);

    // when
    builder.build();

    // then
    assertThat(builder.getRestAddress()).isEqualTo(restAddress);
  }

  @ParameterizedTest
  @ValueSource(strings = {REST_ADDRESS, io.camunda.zeebe.client.ClientProperties.REST_ADDRESS})
  public void shouldSetRestAddressPortFromPropertyWithClientBuilder(final String propertyName)
      throws URISyntaxException {
    // given
    final URI restAddress = new URI("http://localhost:9090");
    final Properties properties = new Properties();
    properties.setProperty(propertyName, restAddress.toString());
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.withProperties(properties);

    // when
    builder.build();

    // then
    assertThat(builder.getRestAddress()).isEqualTo(restAddress);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "localhost",
        "localhost:9090",
        "localhost:9090/context",
        "/some-path/some-other-path",
      })
  public void shouldThrowExceptionWhenRestAddressIsNotAbsoluteFromSetterWithClientBuilder(
      final String uri) throws URISyntaxException {
    // given
    final URI restAddress = new URI(uri);
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();

    // when/then
    assertThatThrownBy(() -> builder.restAddress(restAddress))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("'restAddress' must be an absolute URI");
  }

  @ParameterizedTest
  @ValueSource(strings = {REST_ADDRESS, io.camunda.zeebe.client.ClientProperties.REST_ADDRESS})
  public void shouldThrowExceptionWhenRestAddressIsNotAbsoluteFromPropertyWithClientBuilder(
      final String propertyName) throws URISyntaxException {
    // given
    final URI restAddress = new URI("localhost:9090");
    final Properties properties = new Properties();
    properties.setProperty(propertyName, restAddress.toString());
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();

    // when/then
    assertThatThrownBy(() -> builder.restAddress(restAddress))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("'restAddress' must be an absolute URI");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "localhost",
        "localhost:9090",
        "localhost:9090/context",
        "/some-path/some-other-path",
      })
  public void shouldThrowExceptionWhenGrpcAddressIsNotAbsoluteFromSetterWithClientBuilder(
      final String uri) throws URISyntaxException {
    // given
    final URI grpcAddress = new URI(uri);
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();

    // when/then
    assertThatThrownBy(() -> builder.grpcAddress(grpcAddress))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("'grpcAddress' must be an absolute URI");
  }

  @ParameterizedTest
  @ValueSource(strings = {GRPC_ADDRESS, io.camunda.zeebe.client.ClientProperties.GRPC_ADDRESS})
  public void shouldThrowExceptionWhenGrpcAddressIsNotAbsoluteFromPropertyWithClientBuilder(
      final String propertyName) throws URISyntaxException {
    // given
    final URI grpcAddress = new URI("localhost:9090");
    final Properties properties = new Properties();
    properties.setProperty(propertyName, grpcAddress.toString());
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();

    // when/then
    assertThatThrownBy(() -> builder.grpcAddress(grpcAddress))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("'grpcAddress' must be an absolute URI");
  }

  @ParameterizedTest
  @ValueSource(strings = {REST_ADDRESS_VAR, ZeebeClientEnvironmentVariables.REST_ADDRESS_VAR})
  public void shouldSetRestAddressPortFromEnvVarWithClientBuilder(final String envName)
      throws URISyntaxException {
    // given
    final URI restAddress = new URI("http://localhost:9090");
    Environment.system().put(envName, restAddress.toString());

    // when
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.build();

    // then
    assertThat(builder.getRestAddress()).isEqualTo(restAddress);
  }

  @Test
  public void shouldSetGrpcAddressFromSetterWithClientBuilder() throws URISyntaxException {
    // given
    final URI grpcAddress = new URI("https://localhost:9090");
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.grpcAddress(grpcAddress);

    // when
    builder.build();

    // then
    assertThat(builder.getGrpcAddress()).isEqualTo(grpcAddress);
  }

  @ParameterizedTest
  @ValueSource(strings = {GRPC_ADDRESS, io.camunda.zeebe.client.ClientProperties.GRPC_ADDRESS})
  public void shouldSetGrpcAddressFromPropertyWithClientBuilder(final String propertyName)
      throws URISyntaxException {
    // given
    final URI grpcAddress = new URI("https://localhost:9090");
    final Properties properties = new Properties();
    properties.setProperty(propertyName, grpcAddress.toString());
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.withProperties(properties);

    // when
    builder.build();

    // then
    assertThat(builder.getGrpcAddress()).isEqualTo(grpcAddress);
  }

  @ParameterizedTest
  @ValueSource(strings = {GRPC_ADDRESS_VAR, ZeebeClientEnvironmentVariables.GRPC_ADDRESS_VAR})
  public void shouldSetGrpcAddressFromEnvVarWithClientBuilder(final String envName)
      throws URISyntaxException {
    // given
    final URI grpcAddress = new URI("https://localhost:9090");
    Environment.system().put(envName, grpcAddress.toString());

    // when
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.build();

    // then
    assertThat(builder.getGrpcAddress()).isEqualTo(grpcAddress);
  }

  @Test
  public void shouldSetPreferRestFromSetterWithClientBuilder() {
    // given
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();

    // when
    builder.preferRestOverGrpc(false);

    // then
    try (final CamundaClient client = builder.build()) {
      assertThat(client.getConfiguration().preferRestOverGrpc()).isFalse();
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        PREFER_REST_OVER_GRPC,
        io.camunda.zeebe.client.ClientProperties.PREFER_REST_OVER_GRPC
      })
  public void shouldSetPreferRestFromPropertyWithClientBuilder(final String propertyName) {
    // given
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    final Properties properties = new Properties();
    properties.setProperty(propertyName, "false");

    // when
    builder.withProperties(properties);

    // then
    try (final CamundaClient client = builder.build()) {
      assertThat(client.getConfiguration().preferRestOverGrpc()).isFalse();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {PREFER_REST_VAR, ZeebeClientEnvironmentVariables.PREFER_REST_VAR})
  public void shouldSetPreferRestFromEnvVarWithClientBuilder(final String envName) {
    // given
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    Environment.system().put(envName, "false");

    // when
    builder.preferRestOverGrpc(true);

    // then
    try (final CamundaClient client = builder.build()) {
      assertThat(client.getConfiguration().preferRestOverGrpc()).isFalse();
    }
  }

  @Test
  public void shouldSetGrpcAddressFromGatewayAddressIfUnderfined() throws URISyntaxException {
    // given
    final String gatewayAddress = "localhost:26500";
    final Properties properties = new Properties();
    properties.setProperty(
        io.camunda.zeebe.client.ClientProperties.GATEWAY_ADDRESS, gatewayAddress);
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.withProperties(properties);

    // when
    builder.build();

    // then
    assertThat(builder.getGrpcAddress().toString()).contains(gatewayAddress);
  }

  @Test
  public void shouldUseDefaultTenantId() {
    // given
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();

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
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.defaultTenantId(overrideTenant);

    // when
    builder.build();

    // then
    assertThat(builder.getDefaultTenantId()).isEqualTo(overrideTenant);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {DEFAULT_TENANT_ID, io.camunda.zeebe.client.ClientProperties.DEFAULT_TENANT_ID})
  public void shouldSetDefaultTenantIdFromPropertyWithClientBuilder(final String propertyName) {
    // given
    final String tenantId = "test-tenant";
    final Properties properties = new Properties();
    properties.setProperty(propertyName, tenantId);
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.withProperties(properties);

    // when
    builder.build();

    // then
    assertThat(builder.getDefaultTenantId()).isEqualTo(tenantId);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {DEFAULT_TENANT_ID_VAR, ZeebeClientEnvironmentVariables.DEFAULT_TENANT_ID_VAR})
  public void shouldSetDefaultTenantIdFromEnvVarWithClientBuilder(final String envName) {
    // given
    final String overrideTenant = "override-tenant";
    Environment.system().put(envName, overrideTenant);

    // when
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.build();

    // then
    assertThat(builder.getDefaultTenantId()).isEqualTo(overrideTenant);
  }

  @ParameterizedTest
  @CsvSource({
    DEFAULT_TENANT_ID_VAR + "," + DEFAULT_TENANT_ID,
    ZeebeClientEnvironmentVariables.DEFAULT_TENANT_ID_VAR + "," + DEFAULT_TENANT_ID,
    DEFAULT_TENANT_ID_VAR + "," + io.camunda.zeebe.client.ClientProperties.DEFAULT_TENANT_ID,
    ZeebeClientEnvironmentVariables.DEFAULT_TENANT_ID_VAR
        + ","
        + io.camunda.zeebe.client.ClientProperties.DEFAULT_TENANT_ID
  })
  public void shouldSetFinalDefaultTenantIdFromEnvVarWithClientBuilder(
      final String envName, final String propertyName) {
    // given
    final String propertyTenantId = "test-tenant";
    final Properties properties = new Properties();
    properties.setProperty(propertyName, propertyTenantId);
    final String envVarTenantId = "override-tenant";
    Environment.system().put(envName, envVarTenantId);
    final String setterTenantId = "setter-tenant";
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.defaultTenantId(setterTenantId);

    // when
    builder.build();

    // then
    assertThat(builder.getDefaultTenantId()).isEqualTo(envVarTenantId);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {DEFAULT_TENANT_ID, io.camunda.zeebe.client.ClientProperties.DEFAULT_TENANT_ID})
  public void shouldNotSetDefaultTenantIdFromPropertyWithCloudClientBuilder(
      final String propertyName) {
    // given
    final String tenantId = "test-tenant";
    final CamundaClientCloudBuilderImpl builder = new CamundaClientCloudBuilderImpl();
    final Properties properties = new Properties();
    properties.setProperty(propertyName, tenantId);
    builder.withProperties(properties);

    // when
    final CamundaClient client =
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
    final CamundaClientCloudBuilderImpl builder = new CamundaClientCloudBuilderImpl();

    // when
    final CamundaClientCloudBuilderImpl builderWithTenantId =
        (CamundaClientCloudBuilderImpl) builder.defaultTenantId(tenantId);

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
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();

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
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.defaultJobWorkerTenantIds(Arrays.asList(overrideTenant));

    // when
    builder.build();

    // then
    assertThat(builder.getDefaultJobWorkerTenantIds()).containsExactly(overrideTenant);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        DEFAULT_JOB_WORKER_TENANT_IDS,
        io.camunda.zeebe.client.ClientProperties.DEFAULT_JOB_WORKER_TENANT_IDS
      })
  public void shouldSetDefaultJobWorkerTenantIdsFromPropertyWithClientBuilder(
      final String propertyName) {
    // given
    final List<String> tenantIdList = Arrays.asList("test-tenant-1", "test-tenant-2");
    final Properties properties = new Properties();
    properties.setProperty(propertyName, String.join(",", tenantIdList));
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.withProperties(properties);

    // when
    builder.build();

    // then
    assertThat(builder.getDefaultJobWorkerTenantIds()).containsExactlyElementsOf(tenantIdList);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        DEFAULT_JOB_WORKER_TENANT_IDS_VAR,
        ZeebeClientEnvironmentVariables.DEFAULT_JOB_WORKER_TENANT_IDS_VAR
      })
  public void shouldSetDefaultJobWorkerTenantIdsFromEnvVarWithClientBuilder(final String envName) {
    // given
    final List<String> tenantIdList = Arrays.asList("test-tenant-1", "test-tenant-2");
    Environment.system().put(envName, String.join(",", tenantIdList));

    // when
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.build();

    // then
    assertThat(builder.getDefaultJobWorkerTenantIds()).containsExactlyElementsOf(tenantIdList);
  }

  @ParameterizedTest
  @CsvSource({
    DEFAULT_JOB_WORKER_TENANT_IDS_VAR + "," + DEFAULT_JOB_WORKER_TENANT_IDS,
    ZeebeClientEnvironmentVariables.DEFAULT_JOB_WORKER_TENANT_IDS_VAR
        + ","
        + DEFAULT_JOB_WORKER_TENANT_IDS,
    DEFAULT_JOB_WORKER_TENANT_IDS_VAR
        + ","
        + io.camunda.zeebe.client.ClientProperties.DEFAULT_JOB_WORKER_TENANT_IDS,
    ZeebeClientEnvironmentVariables.DEFAULT_JOB_WORKER_TENANT_IDS_VAR
        + ","
        + io.camunda.zeebe.client.ClientProperties.DEFAULT_JOB_WORKER_TENANT_IDS
  })
  public void shouldSetFinalDefaultJobWorkerTenantIdsFromEnvVarWithClientBuilder(
      final String envName, final String propertyName) {
    // given
    final String propertyTenantId = "test-tenant";
    final Properties properties = new Properties();
    properties.setProperty(propertyName, propertyTenantId);
    final List<String> tenantIdList = Arrays.asList("test-tenant-1", "test-tenant-2");
    Environment.system().put(envName, String.join(",", tenantIdList));
    final String setterTenantId = "setter-tenant";
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.defaultJobWorkerTenantIds(Arrays.asList(setterTenantId));

    // when
    builder.build();

    // then
    assertThat(builder.getDefaultJobWorkerTenantIds()).containsExactlyElementsOf(tenantIdList);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        DEFAULT_JOB_WORKER_TENANT_IDS,
        io.camunda.zeebe.client.ClientProperties.DEFAULT_JOB_WORKER_TENANT_IDS
      })
  public void shouldNotSetDefaultJobWorkerTenantIdsFromPropertyWithCloudClientBuilder(
      final String propertyName) {
    // given
    final CamundaClientCloudBuilderImpl builder = new CamundaClientCloudBuilderImpl();
    final Properties properties = new Properties();
    final List<String> tenantIdList = Arrays.asList("test-tenant-1", "test-tenant-2");
    properties.setProperty(propertyName, String.join(",", tenantIdList));
    builder.withProperties(properties);

    // when
    final CamundaClient client =
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
    final CamundaClientCloudBuilderImpl builder = new CamundaClientCloudBuilderImpl();
    final List<String> tenantIdList = Arrays.asList("test-tenant-1", "test-tenant-2");

    // when
    final CamundaClientCloudBuilderImpl builderWithTenantId =
        (CamundaClientCloudBuilderImpl) builder.defaultJobWorkerTenantIds(tenantIdList);

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
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.useDefaultRetryPolicy(true);

    // when
    builder.build();

    // then
    assertThat(builder.useDefaultRetryPolicy()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        USE_DEFAULT_RETRY_POLICY_VAR,
        ZeebeClientEnvironmentVariables.USE_DEFAULT_RETRY_POLICY_VAR
      })
  public void shouldOverrideDefaultRetryPolicyWithEnvVar(final String envName) {
    // given
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.useDefaultRetryPolicy(true);
    Environment.system().put(envName, "false");

    // when
    builder.build();

    // then
    assertThat(builder.useDefaultRetryPolicy()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        USE_DEFAULT_RETRY_POLICY,
        io.camunda.zeebe.client.ClientProperties.USE_DEFAULT_RETRY_POLICY
      })
  public void shouldOverrideDefaultRetryPolicyWithProperty(final String propertyName) {
    // given
    final Properties properties = new Properties();
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    builder.useDefaultRetryPolicy(true);
    properties.setProperty(propertyName, "false");
    builder.withProperties(properties);

    // when
    builder.build();

    // then
    assertThat(builder.useDefaultRetryPolicy()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        DEFAULT_REQUEST_TIMEOUT,
        io.camunda.zeebe.client.ClientProperties.DEFAULT_REQUEST_TIMEOUT
      })
  public void shouldSetTimeoutInMillis(final String propertyName) {
    // given
    final Properties properties = new Properties();
    final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();
    properties.setProperty(propertyName, "1000");
    builder.withProperties(properties);

    // when
    builder.build();

    // then
    assertThat(builder.getDefaultRequestTimeout()).isEqualTo(Duration.ofSeconds(1));
  }
}
