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
import io.camunda.client.impl.NoopCredentialsProvider;
import io.camunda.client.impl.oauth.OAuthCredentialsProvider;
import io.camunda.client.spring.configuration.CamundaClientAllAutoConfiguration;
import io.camunda.client.spring.configuration.MetricsDefaultConfiguration;
import io.camunda.client.spring.testsupport.CamundaSpringProcessTestContext;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import io.camunda.process.test.impl.configuration.CamundaProcessTestDefaultConfiguration;
import io.camunda.process.test.impl.configuration.CamundaProcessTestRuntimeConfiguration;
import io.camunda.process.test.impl.configuration.LegacyCamundaProcessTestRuntimeConfiguration;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

public class CamundaProcessTestDefaultConfigurationTest {

  @Nested
  @SpringBootTest(
      classes = {
        CamundaProcessTestDefaultConfigurationTest.TestConfig.class,
        CamundaSpringProcessTestContext.class
      })
  @EnableConfigurationProperties({
    CamundaProcessTestRuntimeConfiguration.class,
    LegacyCamundaProcessTestRuntimeConfiguration.class
  })
  @TestPropertySource(
      properties = {
        "camunda.client.rest-address=http://custom-host:8080",
        "camunda.client.grpc-address=http://custom-host:26500",
        "camunda.client.request-timeout=PT30S",
        "camunda.client.tenantId=custom-tenant"
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
  @SpringBootTest(
      classes = {
        CamundaProcessTestDefaultConfigurationTest.TestConfig.class,
        CamundaSpringProcessTestContext.class
      })
  @EnableConfigurationProperties({
    CamundaProcessTestRuntimeConfiguration.class,
    LegacyCamundaProcessTestRuntimeConfiguration.class
  })
  @TestPropertySource(
      properties = {
        "camunda.client.auth.method=oidc",
        "camunda.client.auth.client-id=my-client-id",
        "camunda.client.auth.client-secret=my-client-secret",
        "camunda.client.auth.token-url=https://auth.example.com/token"
      })
  class ShouldApplyAuthProperties {

    @Autowired private CamundaClientBuilderFactory clientBuilderFactory;

    @Test
    void shouldApplyOAuthCredentials() {
      final CamundaClientConfiguration config = buildConfiguration();
      assertThat(config.getCredentialsProvider()).isInstanceOf(OAuthCredentialsProvider.class);
    }

    private CamundaClientConfiguration buildConfiguration() {
      final CamundaClientBuilder builder = clientBuilderFactory.get();
      try (final CamundaClient client = builder.build()) {
        return client.getConfiguration();
      }
    }
  }

  @Nested
  @SpringBootTest(
      classes = {
        CamundaProcessTestDefaultConfigurationTest.TestConfig.class,
        CamundaProcessTestDefaultConfigurationTest.CustomFactoryConfig.class,
        CamundaSpringProcessTestContext.class
      })
  @EnableConfigurationProperties({
    CamundaProcessTestRuntimeConfiguration.class,
    LegacyCamundaProcessTestRuntimeConfiguration.class
  })
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

  @Configuration
  @Import(CamundaProcessTestDefaultConfiguration.class)
  @ImportAutoConfiguration({CamundaClientAllAutoConfiguration.class, MetricsDefaultConfiguration.class})
  static class TestConfig {
    @Bean
    public CamundaSpringProcessTestContext enableTestContext() {
      return new CamundaSpringProcessTestContext();
    }
  }

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
}
