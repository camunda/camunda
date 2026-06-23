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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.impl.CamundaClientEnvironmentVariables;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.impl.NoopCredentialsProvider;
import io.camunda.client.impl.oauth.OAuthCredentialsProvider;
import io.camunda.client.impl.util.Environment;
import io.camunda.client.spring.configuration.CamundaClientAllAutoConfiguration;
import io.camunda.client.spring.configuration.MetricsDefaultConfiguration;
import io.camunda.client.spring.testsupport.CamundaSpringProcessTestContext;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import io.camunda.process.test.impl.configuration.CamundaProcessTestAutoConfiguration;
import io.camunda.process.test.impl.configuration.CamundaProcessTestDefaultConfiguration;
import io.camunda.process.test.impl.configuration.CamundaProcessTestRuntimeConfiguration;
import io.camunda.process.test.impl.configuration.LegacyCamundaProcessTestRuntimeConfiguration;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = CamundaProcessTestDefaultConfigurationTest.TestConfig.class)
@EnableConfigurationProperties({
  CamundaProcessTestRuntimeConfiguration.class,
  LegacyCamundaProcessTestRuntimeConfiguration.class
})
public class CamundaProcessTestDefaultConfigurationTest {

  private static CamundaClientConfiguration buildConfiguration(
      final CamundaClientBuilderFactory clientBuilderFactory) {
    final CamundaClientBuilder builder = clientBuilderFactory.get();
    try (final CamundaClient client = builder.build()) {
      return client.getConfiguration();
    }
  }

  @Configuration
  static class CustomJsonMapperConfig {
    @Bean
    JsonMapper jsonMapper() {
      final ObjectMapper objectMapper =
          new ObjectMapper()
              .configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true)
              .registerModule(new JavaTimeModule());
      return new CamundaObjectMapper(objectMapper);
    }
  }

  @Configuration
  @Import(CamundaProcessTestDefaultConfiguration.class)
  @ImportAutoConfiguration({
    CamundaClientAllAutoConfiguration.class,
    MetricsDefaultConfiguration.class
  })
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

  private static class BlueprintTest {
    private Long id;
    private LocalDateTime createdDate;
    private LocalDateTime modifiedDate;
    private String businessKey;

    BlueprintTest() {}

    BlueprintTest(
        final Long id,
        final LocalDateTime createdDate,
        final LocalDateTime modifiedDate,
        final String businessKey) {
      this.id = id;
      this.createdDate = createdDate;
      this.modifiedDate = modifiedDate;
      this.businessKey = businessKey;
    }

    public Long getId() {
      return id;
    }

    public void setId(final Long id) {
      this.id = id;
    }

    public LocalDateTime getCreatedDate() {
      return createdDate;
    }

    public void setCreatedDate(final LocalDateTime createdDate) {
      this.createdDate = createdDate;
    }

    public LocalDateTime getModifiedDate() {
      return modifiedDate;
    }

    public void setModifiedDate(final LocalDateTime modifiedDate) {
      this.modifiedDate = modifiedDate;
    }

    public String getBusinessKey() {
      return businessKey;
    }

    public void setBusinessKey(final String businessKey) {
      this.businessKey = businessKey;
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
    void shouldDisableEnvironmentVariableOverridesByDefault() {
      final Environment environment = Environment.system();
      environment.put(CamundaClientEnvironmentVariables.REST_ADDRESS_VAR, "http://env-host:8080");

      try {
        final CamundaClientConfiguration config = buildConfiguration();
        assertThat(config.getRestAddress()).isEqualTo(URI.create("http://custom-host:8080"));
      } finally {
        environment.remove(CamundaClientEnvironmentVariables.REST_ADDRESS_VAR);
      }
    }

    @Test
    void shouldUseNoopCredentialsByDefault() {
      final CamundaClientConfiguration config = buildConfiguration();
      assertThat(config.getCredentialsProvider()).isInstanceOf(NoopCredentialsProvider.class);
    }

    private CamundaClientConfiguration buildConfiguration() {
      return CamundaProcessTestDefaultConfigurationTest.buildConfiguration(clientBuilderFactory);
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
      return CamundaProcessTestDefaultConfigurationTest.buildConfiguration(clientBuilderFactory);
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
      return CamundaProcessTestDefaultConfigurationTest.buildConfiguration(clientBuilderFactory);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.process-test.connectors-enabled=true",
        "camunda.process-test.camunda-docker-image-version=8.8.0-new",
        "camunda.process-test.camunda-docker-image-name=camunda/camunda-new",
        "io.camunda.process.test.camunda-docker-image-name=camunda/camunda-legacy",
      })
  class ShouldApplyRuntimeConfigurationWithNewPrefix {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;

    @Test
    void shouldReadConfigurationWithNewPrefix() {
      assertThat(configuration.isConnectorsEnabled()).isTrue();
      assertThat(configuration.getCamundaDockerImageVersion()).isEqualTo("8.8.0-new");
      assertThat(configuration.getCamundaDockerImageName()).isEqualTo("camunda/camunda-new");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "io.camunda.process.test.connectors-enabled=true",
        "io.camunda.process.test.camunda-docker-image-version=8.8.0-legacy",
        "io.camunda.process.test.camunda-docker-image-name=camunda/camunda-legacy"
      })
  class ShouldApplyRuntimeConfigurationWithLegacyPrefix {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;

    @Test
    void shouldUseLegacyConfiguration() {
      assertThat(configuration.isConnectorsEnabled()).isTrue();
      assertThat(configuration.getCamundaDockerImageVersion()).isEqualTo("8.8.0-legacy");
      assertThat(configuration.getCamundaDockerImageName()).isEqualTo("camunda/camunda-legacy");
    }
  }

  @Nested
  @ContextConfiguration(
      classes = {
        CamundaProcessTestAutoConfiguration.class,
        CamundaProcessTestDefaultConfigurationTest.CustomJsonMapperConfig.class
      })
  class ShouldUseCustomJsonMapper {

    @Autowired private CamundaClientBuilderFactory clientBuilderFactory;
    @Autowired private JsonMapper jsonMapper;

    @Test
    void shouldApplyCustomJsonMapper() {
      final CamundaClientConfiguration config = buildConfiguration();
      final LocalDateTime createdDate = LocalDateTime.now();
      final LocalDateTime modifiedDate = createdDate.plusMinutes(1);
      final Map<String, Object> variables =
          Map.of("bpt2", new BlueprintTest(1L, createdDate, modifiedDate, "testBusinessKey"));

      final Map<String, Object> deserializedVariables =
          config.getJsonMapper().fromJsonAsMap(config.getJsonMapper().toJson(variables));
      final BlueprintTest blueprint =
          config.getJsonMapper().transform(deserializedVariables.get("bpt2"), BlueprintTest.class);

      assertThat(config.getJsonMapper()).isSameAs(jsonMapper);
      assertThat(blueprint.getBusinessKey()).isEqualTo("testBusinessKey");
      assertThat(blueprint.getId()).isEqualTo(1L);
      assertThat(blueprint.getCreatedDate()).isEqualTo(createdDate);
      assertThat(blueprint.getModifiedDate()).isEqualTo(modifiedDate);
    }

    private CamundaClientConfiguration buildConfiguration() {
      return CamundaProcessTestDefaultConfigurationTest.buildConfiguration(clientBuilderFactory);
    }
  }

  @Nested
  @ContextConfiguration(
      classes = {
        CamundaProcessTestDefaultConfigurationTest.TestConfig.class,
        CamundaProcessTestDefaultConfigurationTest.CustomFactoryConfig.class
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
      return CamundaProcessTestDefaultConfigurationTest.buildConfiguration(clientBuilderFactory);
    }
  }
}
