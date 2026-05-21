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
package io.camunda.process.test.impl.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.impl.NoopCredentialsProvider;
import io.camunda.client.impl.oauth.OAuthCredentialsProvider;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import io.camunda.process.test.impl.configuration.CamundaProcessTestAutoConfiguration;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = CamundaProcessTestAutoConfiguration.class)
public class CamundaProcessTestDefaultConfigurationTest {

  @Configuration
  static class CustomFactoryConfig {
    @Bean
    public CamundaClientBuilderFactory customCamundaClientBuilderFactory() {
      return () ->
          CamundaClient.newClientBuilder()
              .restAddress(URI.create("http://custom-factory-host:9999"))
              .grpcAddress(URI.create("http://custom-factory-host:26500"));
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.client.rest-address=http://custom-host:8080",
        "camunda.client.grpc-address=http://custom-host:26500",
        "camunda.client.request-timeout=PT30S",
        "camunda.client.tenantId=custom-tenant",
        "camunda.client.worker.defaults.max-jobs-active=50",
        "camunda.client.worker.defaults.timeout=PT2M",
        "camunda.client.message-time-to-live=PT1M",
        "camunda.client.maxMessageSize=5242880",
        "camunda.client.keepalive=PT45S"
      })
  class ShouldApplyCamundaClientProperties {

    @Autowired private CamundaClientBuilderFactory clientBuilderFactory;

    @Test
    void shouldApplyRestAddress() {
      final CamundaClientConfiguration config = buildConfiguration();
      assertThat(config.getRestAddress()).isEqualTo(URI.create("http://custom-host:8080"));
    }

    @Test
    void shouldApplyGrpcAddress() {
      final CamundaClientConfiguration config = buildConfiguration();
      assertThat(config.getGrpcAddress()).isEqualTo(URI.create("http://custom-host:26500"));
    }

    @Test
    void shouldApplyRequestTimeout() {
      final CamundaClientConfiguration config = buildConfiguration();
      assertThat(config.getDefaultRequestTimeout()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void shouldApplyTenantId() {
      final CamundaClientConfiguration config = buildConfiguration();
      assertThat(config.getDefaultTenantId()).isEqualTo("custom-tenant");
    }

    @Test
    void shouldApplyWorkerMaxJobsActive() {
      final CamundaClientConfiguration config = buildConfiguration();
      assertThat(config.getDefaultJobWorkerMaxJobsActive()).isEqualTo(50);
    }

    @Test
    void shouldApplyJobTimeout() {
      final CamundaClientConfiguration config = buildConfiguration();
      assertThat(config.getDefaultJobTimeout()).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void shouldApplyMessageTimeToLive() {
      final CamundaClientConfiguration config = buildConfiguration();
      assertThat(config.getDefaultMessageTimeToLive()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void shouldApplyMaxMessageSize() {
      final CamundaClientConfiguration config = buildConfiguration();
      assertThat(config.getMaxMessageSize()).isEqualTo(5_242_880L);
    }

    @Test
    void shouldApplyKeepAlive() {
      final CamundaClientConfiguration config = buildConfiguration();
      assertThat(config.getKeepAlive()).isEqualTo(Duration.ofSeconds(45));
    }

    @Test
    void shouldUseNoopCredentialsByDefault() {
      final CamundaClientConfiguration config = buildConfiguration();
      assertThat(config.getCredentialsProvider()).isInstanceOf(NoopCredentialsProvider.class);
    }

    private CamundaClientConfiguration buildConfiguration() {
      final CamundaClientBuilder builder = clientBuilderFactory.get();
      try (final CamundaClient client = builder.build()) {
        return client.getConfiguration();
      }
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.client.auth.method=oidc",
        "camunda.client.auth.client-id=my-client-id",
        "camunda.client.auth.client-secret=my-client-secret",
        "camunda.client.auth.token-url=https://auth.example.com/token",
        "camunda.client.auth.audience=my-audience"
      })
  class ShouldApplyAuthProperties {

    @Autowired private CamundaClientBuilderFactory clientBuilderFactory;
    @Autowired private CredentialsProvider camundaClientCredentialsProvider;

    @Test
    void shouldApplyOAuthCredentialsProvider() {
      final CamundaClientConfiguration config = buildConfiguration();
      assertThat(config.getCredentialsProvider()).isInstanceOf(OAuthCredentialsProvider.class);
    }

    @Test
    void shouldUseOAuthCredentialsProviderFromSpringContext() {
      // The Spring context creates the CredentialsProvider bean from the auth properties.
      // Verify the same provider instance is injected into the client builder.
      assertThat(camundaClientCredentialsProvider).isInstanceOf(OAuthCredentialsProvider.class);
      final CamundaClientConfiguration config = buildConfiguration();
      assertThat(config.getCredentialsProvider()).isSameAs(camundaClientCredentialsProvider);
    }

    private CamundaClientConfiguration buildConfiguration() {
      final CamundaClientBuilder builder = clientBuilderFactory.get();
      try (final CamundaClient client = builder.build()) {
        return client.getConfiguration();
      }
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.process-test.remote.client.grpc-address=http://remote-grpc:26500",
        "camunda.process-test.remote.client.rest-address=http://remote-rest:8080"
      })
  class ShouldApplyRemoteClientProperties {

    @Autowired private CamundaClientBuilderFactory clientBuilderFactory;

    @Test
    void shouldApplyRemoteGrpcAddress() {
      final CamundaClientConfiguration config = buildConfiguration();
      assertThat(config.getGrpcAddress()).isEqualTo(URI.create("http://remote-grpc:26500"));
    }

    @Test
    void shouldApplyRemoteRestAddress() {
      final CamundaClientConfiguration config = buildConfiguration();
      assertThat(config.getRestAddress()).isEqualTo(URI.create("http://remote-rest:8080"));
    }

    private CamundaClientConfiguration buildConfiguration() {
      final CamundaClientBuilder builder = clientBuilderFactory.get();
      try (final CamundaClient client = builder.build()) {
        return client.getConfiguration();
      }
    }
  }

  @Nested
  @ContextConfiguration(
      classes = {CamundaProcessTestDefaultConfigurationTest.CustomFactoryConfig.class})
  class ShouldUseCustomCamundaClientBuilderFactory {

    @Autowired
    @Qualifier("customCamundaClientBuilderFactory")
    private CamundaClientBuilderFactory clientBuilderFactory;

    @Test
    void shouldUseCustomFactory() {
      final CamundaClientConfiguration config = buildConfiguration();
      assertThat(config.getRestAddress()).isEqualTo(URI.create("http://custom-factory-host:9999"));
    }

    private CamundaClientConfiguration buildConfiguration() {
      final CamundaClientBuilder builder = clientBuilderFactory.get();
      try (final CamundaClient client = builder.build()) {
        return client.getConfiguration();
      }
    }
  }
}
