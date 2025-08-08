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
package io.camunda.spring.client.config;

import static org.assertj.core.api.Assertions.*;

import io.camunda.client.CredentialsProvider;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.impl.NoopCredentialsProvider;
import io.camunda.spring.client.configuration.SpringCamundaClientConfiguration;
import io.camunda.spring.client.jobhandling.CamundaClientExecutorService;
import io.camunda.spring.client.properties.CamundaClientProperties;
import io.grpc.ClientInterceptor;
import java.net.URI;
import java.util.List;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class SpringCamundaClientConfigurationTest {
  private static SpringCamundaClientConfiguration configuration(
      final CamundaClientProperties properties,
      final JsonMapper jsonMapper,
      final List<ClientInterceptor> interceptors,
      final List<AsyncExecChainHandler> chainHandlers,
      final CamundaClientExecutorService executorService,
      final CredentialsProvider credentialsProvider) {
    return new SpringCamundaClientConfiguration(
        properties, jsonMapper, interceptors, chainHandlers, executorService, credentialsProvider);
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

  @Test
  void shouldCreateSingletonCredentialProvider() {
    final SpringCamundaClientConfiguration configuration =
        configuration(
            properties(),
            jsonMapper(),
            List.of(),
            List.of(),
            executorService(),
            credentialsProvider());
    final CredentialsProvider credentialsProvider1 = configuration.getCredentialsProvider();
    final CredentialsProvider credentialsProvider2 = configuration.getCredentialsProvider();
    assertThat(credentialsProvider1).isSameAs(credentialsProvider2);
  }

  @ParameterizedTest
  @CsvSource(value = {"http, true", "https, false", "grpc, true", "grpcs, false"})
  void shouldConfigurePlaintextAndGatewayAddress(final String protocol, final boolean plaintext) {
    final CamundaClientProperties properties = properties();
    properties.setGrpcAddress(URI.create(String.format("%s://some-host:21500", protocol)));
    final SpringCamundaClientConfiguration configuration =
        configuration(
            properties,
            jsonMapper(),
            List.of(),
            List.of(),
            executorService(),
            credentialsProvider());
    assertThat(configuration.isPlaintextConnectionEnabled()).isEqualTo(plaintext);
    assertThat(configuration.getGatewayAddress()).isEqualTo("some-host:21500");
  }

  @Test
  void shouldPrintToString() {
    final SpringCamundaClientConfiguration camundaClientConfiguration =
        new SpringCamundaClientConfiguration(properties(), null, null, null, null, null);
    final String toStringOutput = camundaClientConfiguration.toString();
    assertThat(toStringOutput).matches("CamundaClientConfigurationImpl\\{.*}");
  }
}
