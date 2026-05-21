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
package io.camunda.client.spring.configuration;

import static org.assertj.core.api.Assertions.*;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.impl.NoopCredentialsProvider;
import io.camunda.client.impl.oauth.OAuthCredentialsProvider;
import io.camunda.client.jobhandling.CamundaClientExecutorService;
import io.camunda.client.jobhandling.JobExceptionHandlerSupplier;
import io.camunda.client.spring.properties.CamundaClientAuthProperties;
import io.camunda.client.spring.properties.CamundaClientCloudProperties;
import io.camunda.client.spring.properties.CamundaClientProperties;
import io.camunda.client.spring.properties.CamundaClientProperties.ClientMode;
import io.grpc.ClientInterceptor;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

public class SpringCamundaClientConfigurationTest {
  private static SpringCamundaClientConfiguration configuration(
      final CamundaClientProperties properties,
      final JsonMapper jsonMapper,
      final List<ClientInterceptor> interceptors,
      final List<AsyncExecChainHandler> chainHandlers,
      final CamundaClientExecutorService executorService,
      final CredentialsProvider credentialsProvider,
      final JobExceptionHandlerSupplier jobExceptionHandlerSupplier) {
    return new SpringCamundaClientConfiguration(
        properties,
        jsonMapper,
        interceptors,
        chainHandlers,
        executorService,
        credentialsProvider,
        jobExceptionHandlerSupplier);
  }

  private static CamundaClientProperties properties() {
    return new CamundaClientProperties();
  }

  private static JsonMapper jsonMapper() {
    return new CamundaObjectMapper();
  }

  private static CamundaClientExecutorService executorService() {
    return CamundaClientExecutorService.createDefault();
  }

  private static CredentialsProvider credentialsProvider() {
    return new NoopCredentialsProvider();
  }

  private static JobExceptionHandlerSupplier jobExceptionHandlerSupplier() {
    return context -> null;
  }

  @Test
  void shouldCreateSingletonCredentialProvider() {
    final SpringCamundaClientConfiguration configuration =
        configuration(
            properties(),
            jsonMapper(),
            List.of(),
            List.of(),
            executorService(),
            credentialsProvider(),
            jobExceptionHandlerSupplier());
    final CredentialsProvider credentialsProvider1 = configuration.getCredentialsProvider();
    final CredentialsProvider credentialsProvider2 = configuration.getCredentialsProvider();
    assertThat(credentialsProvider1).isSameAs(credentialsProvider2);
  }

  @Test
  void shouldPrintToString() {
    final SpringCamundaClientConfiguration camundaClientConfiguration =
        new SpringCamundaClientConfiguration(properties(), null, null, null, null, null, null);
    final String toStringOutput = camundaClientConfiguration.toString();
    assertThat(toStringOutput).matches("SpringCamundaClientConfiguration\\{.*}");
  }

  @Test
  void shouldCreateBuilderWithAllSelfManagedProperties() {
    // given
    final CamundaClientProperties props = properties();
    props.setRestAddress(URI.create("http://0.0.0.0:8090"));
    props.setGrpcAddress(URI.create("http://0.0.0.0:8091"));
    props.setTenantId("my-tenant");
    props.setRequestTimeout(Duration.ofSeconds(60));
    props.setRequestTimeoutOffset(Duration.ofSeconds(10));
    props.setKeepAlive(Duration.ofSeconds(30));
    props.setMaxMessageSize(DataSize.ofMegabytes(100));
    props.setMaxMetadataSize(DataSize.ofMegabytes(10));
    props.setExecutionThreads(4);
    props.setCaCertificatePath("/path/to/cert");
    props.setOverrideAuthority("my-authority");
    props.setPreferRestOverGrpc(true);
    props.getWorker().getDefaults().setPollInterval(Duration.ofSeconds(5));
    props.getWorker().getDefaults().setTimeout(Duration.ofSeconds(120));
    props.getWorker().getDefaults().setMaxJobsActive(20);
    props.getWorker().getDefaults().setName("my-worker");
    props.getWorker().getDefaults().setStreamEnabled(true);

    final CredentialsProvider creds = credentialsProvider();

    final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);
    final CamundaClientExecutorService executor =
        new CamundaClientExecutorService(scheduledExecutor, false);

    final SpringCamundaClientConfiguration configuration =
        configuration(props, jsonMapper(), List.of(), List.of(), executor, creds, null);

    // when
    final CamundaClientBuilder builder = configuration.toBuilder();
    final CamundaClientConfiguration config = buildConfiguration(builder);

    try {
      // then
      assertThat(config.getRestAddress()).isEqualTo(URI.create("http://0.0.0.0:8090"));
      assertThat(config.getGrpcAddress()).isEqualTo(URI.create("http://0.0.0.0:8091"));
      assertThat(config.getDefaultTenantId()).isEqualTo("my-tenant");
      assertThat(config.getDefaultRequestTimeout()).isEqualTo(Duration.ofSeconds(60));
      assertThat(config.getDefaultRequestTimeoutOffset()).isEqualTo(Duration.ofSeconds(10));
      assertThat(config.getKeepAlive()).isEqualTo(Duration.ofSeconds(30));
      assertThat(config.getMaxMessageSize()).isEqualTo(DataSize.ofMegabytes(100).toBytes());
      assertThat(config.getMaxMetadataSize()).isEqualTo(DataSize.ofMegabytes(10).toBytes());
      assertThat(config.getNumJobWorkerExecutionThreads()).isEqualTo(4);
      assertThat(config.getCaCertificatePath()).isEqualTo("/path/to/cert");
      assertThat(config.getOverrideAuthority()).isEqualTo("my-authority");
      assertThat(config.preferRestOverGrpc()).isTrue();
      assertThat(config.getDefaultJobPollInterval()).isEqualTo(Duration.ofSeconds(5));
      assertThat(config.getDefaultJobTimeout()).isEqualTo(Duration.ofSeconds(120));
      assertThat(config.getDefaultJobWorkerMaxJobsActive()).isEqualTo(20);
      assertThat(config.getDefaultJobWorkerName()).isEqualTo("my-worker");
      assertThat(config.getDefaultJobWorkerStreamEnabled()).isTrue();
      assertThat(config.getCredentialsProvider()).isSameAs(creds);
      assertThat(config.ownsJobHandlingExecutor()).isFalse();
      assertThat(config.ownsJobWorkerSchedulingExecutor()).isFalse();
    } finally {
      scheduledExecutor.shutdown();
    }
  }

  @Test
  void shouldCreateCloudBuilderForSaaSMode() {
    // given
    final CamundaClientProperties props = properties();
    props.setMode(ClientMode.saas);

    final CamundaClientCloudProperties cloud = props.getCloud();
    cloud.setClusterId("my-cluster");
    cloud.setRegion("my-region");

    final CamundaClientAuthProperties auth = props.getAuth();
    auth.setClientId("my-client-id");
    auth.setClientSecret("my-client-secret");

    final SpringCamundaClientConfiguration configuration =
        configuration(props, jsonMapper(), List.of(), List.of(), executorService(), null, null);

    // when
    final CamundaClientBuilder builder = configuration.toBuilder();
    final CamundaClientConfiguration config = buildConfiguration(builder);

    // then
    assertThat(config.getRestAddress())
        .isEqualTo(URI.create("https://my-region.zeebe.camunda.io:443/my-cluster"));
    assertThat(config.getGrpcAddress())
        .isEqualTo(URI.create("https://my-cluster.my-region.zeebe.camunda.io:443"));
    assertThat(config.getCredentialsProvider()).isInstanceOf(OAuthCredentialsProvider.class);
  }

  @Test
  void shouldCreateCloudBuilderWhenClusterIdIsSet() {
    // given
    final CamundaClientProperties props = properties();
    // No explicit mode - auto-detect based on clusterId

    final CamundaClientCloudProperties cloud = props.getCloud();
    cloud.setClusterId("auto-detected-cluster");
    cloud.setRegion("us-east");

    final CamundaClientAuthProperties auth = props.getAuth();
    auth.setClientId("client-id");
    auth.setClientSecret("client-secret");

    final SpringCamundaClientConfiguration configuration =
        configuration(props, jsonMapper(), List.of(), List.of(), executorService(), null, null);

    // when
    final CamundaClientBuilder builder = configuration.toBuilder();
    final CamundaClientConfiguration config = buildConfiguration(builder);

    // then
    assertThat(config.getRestAddress())
        .isEqualTo(URI.create("https://us-east.zeebe.camunda.io:443/auto-detected-cluster"));
    assertThat(config.getGrpcAddress())
        .isEqualTo(URI.create("https://auto-detected-cluster.us-east.zeebe.camunda.io:443"));
  }

  private static CamundaClientConfiguration buildConfiguration(final CamundaClientBuilder builder) {
    try (final CamundaClient client = builder.build()) {
      return client.getConfiguration();
    }
  }
}
