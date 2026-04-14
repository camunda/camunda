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
package io.camunda.process.test.impl.configuration;

import static com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.impl.CamundaClientBuilderImpl;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.jobhandling.CamundaClientExecutorService;
import io.camunda.client.spring.configuration.CredentialsProviderConfiguration;
import io.camunda.client.spring.configuration.SpringCamundaClientConfiguration;
import io.camunda.client.spring.properties.CamundaClientProperties;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import io.camunda.zeebe.client.impl.ZeebeObjectMapper;
import io.grpc.ClientInterceptor;
import java.net.URI;
import java.util.List;
import java.util.concurrent.Executors;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** Fallback values if certain beans are missing */
@Import(CredentialsProviderConfiguration.class)
public class CamundaProcessTestDefaultConfiguration {

  @Bean(name = "camundaJsonMapper")
  @ConditionalOnMissingBean
  public JsonMapper camundaJsonMapper(final ObjectMapper objectMapper) {
    return new CamundaObjectMapper(objectMapper);
  }

  @Bean(name = "zeebeJsonMapper")
  @ConditionalOnMissingBean
  public io.camunda.zeebe.client.api.JsonMapper zeebeJsonMapper(final ObjectMapper objectMapper) {
    return new ZeebeObjectMapper(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public ObjectMapper objectMapper() {
    return new ObjectMapper()
        .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true);
  }

  /**
   * Creates a {@link CamundaClientBuilderFactory} that configures the Camunda client using all
   * properties from the Spring configuration (e.g. {@code camunda.client.*}).
   *
   * <p>A dedicated executor service with {@code ownByCamundaClient=false} is used to prevent the
   * shared executor from being closed after each test closes its client.
   *
   * <p>For backwards compatibility, the remote client addresses from {@code
   * camunda.process-test.remote.client.grpcAddress} and {@code
   * camunda.process-test.remote.client.restAddress} are applied as overrides.
   *
   * <p>To use a completely custom configuration, provide your own {@link
   * CamundaClientBuilderFactory} bean.
   */
  @Bean("camundaClientBuilderFactory")
  @ConditionalOnMissingBean(CamundaClientBuilderFactory.class)
  public CamundaClientBuilderFactory camundaClientBuilderFactory(
      final CamundaClientProperties clientProperties,
      final JsonMapper jsonMapper,
      final CredentialsProvider camundaClientCredentialsProvider,
      final List<ClientInterceptor> interceptors,
      final List<AsyncExecChainHandler> chainHandlers,
      final CamundaProcessTestRuntimeConfiguration runtimeConfiguration) {

    // Use a dedicated executor with ownByCamundaClient=false to prevent closing the shared
    // executor when a test client is closed. Each test creates and closes its own CamundaClient,
    // and we reuse this executor across all clients within the test class.
    final CamundaClientExecutorService executorService =
        new CamundaClientExecutorService(
            Executors.newScheduledThreadPool(clientProperties.getExecutionThreads()), false);

    final SpringCamundaClientConfiguration configuration =
        new SpringCamundaClientConfiguration(
            clientProperties,
            jsonMapper,
            interceptors,
            chainHandlers,
            executorService,
            camundaClientCredentialsProvider);

    return () -> {
      final CamundaClientBuilder builder = configuration.toBuilder();
      // Backwards compatibility: apply remote client addresses only when explicitly configured
      // (i.e. different from the default addresses of CamundaClientProperties).
      // This matches the previously supported camunda.process-test.remote.client.* properties.
      final CamundaClientProperties remoteClientProperties =
          runtimeConfiguration.getRemote().getClient();
      final URI defaultGrpcAddress = CamundaClientBuilderImpl.DEFAULT_GRPC_ADDRESS;
      final URI remoteGrpcAddress = remoteClientProperties.getGrpcAddress();
      if (remoteGrpcAddress != null && !remoteGrpcAddress.equals(defaultGrpcAddress)) {
        builder.grpcAddress(remoteGrpcAddress);
      }
      final URI defaultRestAddress = CamundaClientBuilderImpl.DEFAULT_REST_ADDRESS;
      final URI remoteRestAddress = remoteClientProperties.getRestAddress();
      if (remoteRestAddress != null && !remoteRestAddress.equals(defaultRestAddress)) {
        builder.restAddress(remoteRestAddress);
      }
      return builder;
    };
  }
}
