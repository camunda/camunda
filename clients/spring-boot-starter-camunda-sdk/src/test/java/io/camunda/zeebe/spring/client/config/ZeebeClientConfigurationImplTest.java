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
package io.camunda.zeebe.spring.client.config;

import static org.assertj.core.api.Assertions.*;

import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.impl.ZeebeObjectMapper;
import io.camunda.zeebe.spring.client.configuration.ZeebeClientConfigurationImpl;
import io.camunda.zeebe.spring.client.jobhandling.ZeebeClientExecutorService;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties;
import io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties;
import io.grpc.ClientInterceptor;
import java.net.URI;
import java.util.List;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;

public class ZeebeClientConfigurationImplTest {
  private static ZeebeClientConfigurationImpl configuration(
      final ZeebeClientConfigurationProperties legacyProperties,
      final CamundaClientProperties properties,
      final JsonMapper jsonMapper,
      final List<ClientInterceptor> interceptors,
      final List<AsyncExecChainHandler> chainHandlers,
      final ZeebeClientExecutorService executorService) {
    return new ZeebeClientConfigurationImpl(
        legacyProperties, properties, jsonMapper, interceptors, chainHandlers, executorService);
  }

  private static ZeebeClientConfigurationProperties legacyProperties() {
    return new ZeebeClientConfigurationProperties(new MockEnvironment());
  }

  private static CamundaClientProperties properties() {
    return new CamundaClientProperties();
  }

  private static JsonMapper jsonMapper() {
    return new ZeebeObjectMapper();
  }

  private static ZeebeClientExecutorService executorService() {
    return ZeebeClientExecutorService.createDefault();
  }

  @Test
  void shouldCreateSingletonCredentialProvider() {
    final ZeebeClientConfigurationImpl configuration =
        configuration(
            legacyProperties(),
            properties(),
            jsonMapper(),
            List.of(),
            List.of(),
            executorService());
    final CredentialsProvider credentialsProvider1 = configuration.getCredentialsProvider();
    final CredentialsProvider credentialsProvider2 = configuration.getCredentialsProvider();
    assertThat(credentialsProvider1).isSameAs(credentialsProvider2);
  }

  @ParameterizedTest
  @CsvSource(value = {"http, true", "https, false", "grpc, true", "grpcs, false"})
  void shouldConfigurePlaintextAndGatewayAddress(final String protocol, final boolean plaintext) {
    final CamundaClientProperties properties = properties();
    properties
        .getZeebe()
        .setGrpcAddress(URI.create(String.format("%s://some-host:21500", protocol)));
    final ZeebeClientConfigurationImpl configuration =
        configuration(
            legacyProperties(), properties, jsonMapper(), List.of(), List.of(), executorService());
    assertThat(configuration.isPlaintextConnectionEnabled()).isEqualTo(plaintext);
    assertThat(configuration.getGatewayAddress()).isEqualTo("some-host:21500");
  }
}
