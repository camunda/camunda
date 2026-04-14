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
package io.camunda.process.test.impl.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.ClientProperties;
import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * Verifies that standard {@link ClientProperties} set in a properties file are applied to the
 * Camunda client builder via {@link ContainerRuntimePropertiesUtil}.
 */
public class ClientPropertiesConfigurationTest {

  private final GitPropertiesUtil emptyGitProperties = new GitPropertiesUtil(new Properties());

  @Test
  void shouldApplyRestAddressFromClientProperties() {
    // given
    final Properties properties = buildVersionProperties();
    properties.setProperty(ClientProperties.REST_ADDRESS, "http://custom-host:8080");

    // when
    final CamundaClientConfiguration config = buildClientConfiguration(properties);

    // then
    assertThat(config.getRestAddress()).isEqualTo(URI.create("http://custom-host:8080"));
  }

  @Test
  void shouldApplyGrpcAddressFromClientProperties() {
    // given
    final Properties properties = buildVersionProperties();
    properties.setProperty(ClientProperties.GRPC_ADDRESS, "http://custom-host:26500");

    // when
    final CamundaClientConfiguration config = buildClientConfiguration(properties);

    // then
    assertThat(config.getGrpcAddress()).isEqualTo(URI.create("http://custom-host:26500"));
  }

  @Test
  void shouldApplyRequestTimeoutFromClientProperties() {
    // given
    final Properties properties = buildVersionProperties();
    properties.setProperty(ClientProperties.DEFAULT_REQUEST_TIMEOUT, "30000"); // 30 seconds in ms

    // when
    final CamundaClientConfiguration config = buildClientConfiguration(properties);

    // then
    assertThat(config.getDefaultRequestTimeout()).isEqualTo(Duration.ofSeconds(30));
  }

  @Test
  void shouldApplyRequestTimeoutOffsetFromClientProperties() {
    // given
    final Properties properties = buildVersionProperties();
    properties.setProperty(
        ClientProperties.DEFAULT_REQUEST_TIMEOUT_OFFSET, "5000"); // 5 seconds in ms

    // when
    final CamundaClientConfiguration config = buildClientConfiguration(properties);

    // then
    assertThat(config.getDefaultRequestTimeoutOffset()).isEqualTo(Duration.ofSeconds(5));
  }

  @Test
  void shouldApplyTenantIdFromClientProperties() {
    // given
    final Properties properties = buildVersionProperties();
    properties.setProperty(ClientProperties.DEFAULT_TENANT_ID, "my-tenant");

    // when
    final CamundaClientConfiguration config = buildClientConfiguration(properties);

    // then
    assertThat(config.getDefaultTenantId()).isEqualTo("my-tenant");
  }

  @Test
  void shouldApplyKeepAliveFromClientProperties() {
    // given
    final Properties properties = buildVersionProperties();
    properties.setProperty(ClientProperties.KEEP_ALIVE, "30000"); // 30 seconds in ms

    // when
    final CamundaClientConfiguration config = buildClientConfiguration(properties);

    // then
    assertThat(config.getKeepAlive()).isEqualTo(Duration.ofSeconds(30));
  }

  @Test
  void shouldApplyOverrideAuthorityFromClientProperties() {
    // given
    final Properties properties = buildVersionProperties();
    properties.setProperty(ClientProperties.OVERRIDE_AUTHORITY, "custom-authority");

    // when
    final CamundaClientConfiguration config = buildClientConfiguration(properties);

    // then
    assertThat(config.getOverrideAuthority()).isEqualTo("custom-authority");
  }

  @Test
  void shouldApplyMaxMessageSizeFromClientProperties() {
    // given
    final Properties properties = buildVersionProperties();
    properties.setProperty(ClientProperties.MAX_MESSAGE_SIZE, "10485760"); // 10 MB in bytes

    // when
    final CamundaClientConfiguration config = buildClientConfiguration(properties);

    // then
    assertThat(config.getMaxMessageSize()).isEqualTo(10_485_760L);
  }

  @Test
  void shouldApplyMaxMetadataSizeFromClientProperties() {
    // given
    final Properties properties = buildVersionProperties();
    properties.setProperty(ClientProperties.MAX_METADATA_SIZE, "2048");

    // when
    final CamundaClientConfiguration config = buildClientConfiguration(properties);

    // then
    assertThat(config.getMaxMetadataSize()).isEqualTo(2048L);
  }

  @Test
  void shouldApplyPreferRestOverGrpcFromClientProperties() {
    // given
    final Properties properties = buildVersionProperties();
    properties.setProperty(ClientProperties.PREFER_REST_OVER_GRPC, "true");

    // when
    final CamundaClientConfiguration config = buildClientConfiguration(properties);

    // then
    assertThat(config.preferRestOverGrpc()).isTrue();
  }

  @Test
  void shouldApplyWorkerExecutionThreadsFromClientProperties() {
    // given
    final Properties properties = buildVersionProperties();
    properties.setProperty(ClientProperties.JOB_WORKER_EXECUTION_THREADS, "4");

    // when
    final CamundaClientConfiguration config = buildClientConfiguration(properties);

    // then
    assertThat(config.getNumJobWorkerExecutionThreads()).isEqualTo(4);
  }

  @Test
  void shouldApplyWorkerMaxJobsActiveFromClientProperties() {
    // given
    final Properties properties = buildVersionProperties();
    properties.setProperty(ClientProperties.JOB_WORKER_MAX_JOBS_ACTIVE, "50");

    // when
    final CamundaClientConfiguration config = buildClientConfiguration(properties);

    // then
    assertThat(config.getDefaultJobWorkerMaxJobsActive()).isEqualTo(50);
  }

  @Test
  void shouldApplyWorkerNameFromClientProperties() {
    // given
    final Properties properties = buildVersionProperties();
    properties.setProperty(ClientProperties.DEFAULT_JOB_WORKER_NAME, "my-worker");

    // when
    final CamundaClientConfiguration config = buildClientConfiguration(properties);

    // then
    assertThat(config.getDefaultJobWorkerName()).isEqualTo("my-worker");
  }

  @Test
  void shouldApplyJobTimeoutFromClientProperties() {
    // given
    final Properties properties = buildVersionProperties();
    properties.setProperty(ClientProperties.DEFAULT_JOB_TIMEOUT, "120000"); // 2 minutes in ms

    // when
    final CamundaClientConfiguration config = buildClientConfiguration(properties);

    // then
    assertThat(config.getDefaultJobTimeout()).isEqualTo(Duration.ofMinutes(2));
  }

  @Test
  void shouldApplyJobPollIntervalFromClientProperties() {
    // given
    final Properties properties = buildVersionProperties();
    properties.setProperty(ClientProperties.DEFAULT_JOB_POLL_INTERVAL, "1000"); // 1 second in ms

    // when
    final CamundaClientConfiguration config = buildClientConfiguration(properties);

    // then
    assertThat(config.getDefaultJobPollInterval()).isEqualTo(Duration.ofSeconds(1));
  }

  @Test
  void shouldApplyMessageTimeToLiveFromClientProperties() {
    // given
    final Properties properties = buildVersionProperties();
    properties.setProperty(
        ClientProperties.DEFAULT_MESSAGE_TIME_TO_LIVE, "60000"); // 1 minute in ms

    // when
    final CamundaClientConfiguration config = buildClientConfiguration(properties);

    // then
    assertThat(config.getDefaultMessageTimeToLive()).isEqualTo(Duration.ofMinutes(1));
  }

  @Test
  void shouldApplyStreamEnabledFromClientProperties() {
    // given
    final Properties properties = buildVersionProperties();
    properties.setProperty(ClientProperties.STREAM_ENABLED, "true");

    // when
    final CamundaClientConfiguration config = buildClientConfiguration(properties);

    // then
    assertThat(config.getDefaultJobWorkerStreamEnabled()).isTrue();
  }

  @Test
  void shouldApplyCaCertificatePathFromClientProperties() {
    // given
    final Properties properties = buildVersionProperties();
    properties.setProperty(ClientProperties.CA_CERTIFICATE_PATH, "/path/to/cert");

    // when
    final CamundaClientConfiguration config = buildClientConfiguration(properties);

    // then
    assertThat(config.getCaCertificatePath()).isEqualTo("/path/to/cert");
  }

  @Test
  void shouldUseCloudBuilderWhenClusterIdIsSet() {
    // given
    final Properties properties = buildVersionProperties();
    properties.setProperty(ClientProperties.CLOUD_CLUSTER_ID, "my-cluster");
    properties.setProperty(ClientProperties.CLOUD_CLIENT_ID, "my-client-id");
    properties.setProperty(ClientProperties.CLOUD_CLIENT_SECRET, "my-client-secret");
    properties.setProperty(ClientProperties.CLOUD_REGION, "eu-west");

    // when
    final CamundaClientConfiguration config = buildClientConfiguration(properties);

    // then
    assertThat(config.getRestAddress())
        .isEqualTo(URI.create("https://eu-west.zeebe.camunda.io:443/my-cluster"));
    assertThat(config.getGrpcAddress())
        .isEqualTo(URI.create("https://my-cluster.eu-west.zeebe.camunda.io:443"));
  }

  @Test
  void shouldApplyRemoteClientGrpcAddressBackwardsCompatibility() {
    // given - the legacy remote.client.grpcAddress is still supported
    final Properties properties = buildVersionProperties();
    properties.setProperty("runtimeMode", "REMOTE");
    properties.setProperty("remote.client.grpcAddress", "http://remote-host:26500");

    // when
    final CamundaClientConfiguration config = buildClientConfiguration(properties);

    // then
    assertThat(config.getGrpcAddress()).isEqualTo(URI.create("http://remote-host:26500"));
  }

  @Test
  void shouldApplyRemoteClientRestAddressBackwardsCompatibility() {
    // given - the legacy remote.client.restAddress is still supported
    final Properties properties = buildVersionProperties();
    properties.setProperty("runtimeMode", "REMOTE");
    properties.setProperty("remote.client.restAddress", "http://remote-host:8080");

    // when
    final CamundaClientConfiguration config = buildClientConfiguration(properties);

    // then
    assertThat(config.getRestAddress()).isEqualTo(URI.create("http://remote-host:8080"));
  }

  @Test
  void shouldOverrideStandardClientPropertiesWithRemoteClientAddress() {
    // given - remote.client overrides the standard ClientProperties
    final Properties properties = buildVersionProperties();
    properties.setProperty(ClientProperties.REST_ADDRESS, "http://standard:8080");
    properties.setProperty("remote.client.restAddress", "http://remote-override:8080");

    // when
    final CamundaClientConfiguration config = buildClientConfiguration(properties);

    // then - remote address wins
    assertThat(config.getRestAddress()).isEqualTo(URI.create("http://remote-override:8080"));
  }

  private CamundaClientConfiguration buildClientConfiguration(final Properties properties) {
    final ContainerRuntimePropertiesUtil propertiesUtil =
        new ContainerRuntimePropertiesUtil(properties, emptyGitProperties);
    final CamundaClient client =
        propertiesUtil.getCamundaClientBuilderFactory().get().build();
    try {
      return client.getConfiguration();
    } finally {
      client.close();
    }
  }

  private static Properties buildVersionProperties() {
    // Simulate the required version properties file content
    final Properties properties = new Properties();
    properties.setProperty("camundaDockerImageVersion", "8.10.0-SNAPSHOT");
    properties.setProperty(
        "camundaDockerImageName", CamundaProcessTestRuntimeDefaults.CAMUNDA_DOCKER_IMAGE_NAME);
    properties.setProperty(
        "elasticsearch.version", CamundaProcessTestRuntimeDefaults.DEFAULT_ELASTICSEARCH_VERSION);
    return properties;
  }
}
