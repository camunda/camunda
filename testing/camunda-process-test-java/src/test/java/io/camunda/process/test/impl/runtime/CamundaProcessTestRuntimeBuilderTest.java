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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.impl.CamundaClientBuilderImpl;
import io.camunda.process.test.api.CamundaProcessTestRuntimeMode;
import io.camunda.process.test.api.runtime.CamundaProcessTestContainerProvider;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.containers.GenericContainer;

@ExtendWith(MockitoExtension.class)
public class CamundaProcessTestRuntimeBuilderTest {

  private CamundaProcessTestRuntimeBuilder runtimeBuilder;

  @Spy private CamundaClientBuilder clientBuilder = CamundaClient.newClientBuilder();

  @Mock private CamundaProcessTestContainerProvider containerProvider;

  @SuppressWarnings("rawtypes")
  @Mock(answer = Answers.RETURNS_SELF)
  private GenericContainer customContainer;

  @BeforeEach
  public void setup() {
    runtimeBuilder =
        new CamundaProcessTestRuntimeBuilder().withCamundaClientBuilderFactory(() -> clientBuilder);
  }

  @Test
  void shouldOverrideCamundaClientBuilderConfiguration() {
    // given
    final String tenantId = "customTenant";
    final Duration requestTimeout = Duration.ofHours(1);

    runtimeBuilder.withCamundaClientBuilderOverrides(
        clientBuilder -> {
          clientBuilder.defaultRequestTimeout(requestTimeout).defaultTenantId(tenantId);
        });

    // when
    final CamundaClientBuilderImpl client =
        (CamundaClientBuilderImpl) runtimeBuilder.getConfiguredCamundaClientBuilderFactory().get();

    // then
    verify(clientBuilder).defaultRequestTimeout(requestTimeout);
    verify(clientBuilder).defaultTenantId(tenantId);

    assertThat(client.getDefaultRequestTimeout()).isEqualTo(requestTimeout);
    assertThat(client.getDefaultTenantId()).isEqualTo(tenantId);
  }

  @Test
  void shouldLoadContainerProvidersViaServiceLoaderByDefault() {
    // given: the DummyContainerProvider is registered in the service loader file
    runtimeBuilder.withRuntimeMode(CamundaProcessTestRuntimeMode.MANAGED);

    // when
    runtimeBuilder.build();

    // then
    assertThat(runtimeBuilder.isContainerProvidersServiceLoaderEnabled()).isTrue();

    assertThat(runtimeBuilder.getContainerProviders())
        .hasSize(1)
        .first()
        .isInstanceOf(DummyContainerProvider.class);
  }

  @Test
  void shouldDisableLoadContainerProvider() {
    // given
    runtimeBuilder.withContainerProvidersServiceLoaderEnabled(false);

    // when
    runtimeBuilder.build();

    // then
    assertThat(runtimeBuilder.isContainerProvidersServiceLoaderEnabled()).isFalse();
    assertThat(runtimeBuilder.getContainerProviders()).isEmpty();
  }

  @Test
  void shouldAddCustomContainersFromProviders() {
    // given
    //noinspection unchecked
    when(containerProvider.createContainer(any())).thenReturn(customContainer);

    runtimeBuilder.withContainerProvider(containerProvider);

    // when
    runtimeBuilder.build();

    // then
    assertThat(runtimeBuilder.getContainerProviders())
        .hasSize(2)
        .contains(containerProvider)
        .hasAtLeastOneElementOfType(DummyContainerProvider.class);
  }

  @Test
  void shouldLoadContainerProvidersViaServiceLoaderForSharedRuntime() {
    // given: the DummyContainerProvider is registered in the service loader file
    runtimeBuilder.withRuntimeMode(CamundaProcessTestRuntimeMode.SHARED);

    // when
    runtimeBuilder.build();

    // then
    assertThat(runtimeBuilder.isContainerProvidersServiceLoaderEnabled()).isTrue();

    assertThat(runtimeBuilder.getContainerProviders())
        .hasSize(1)
        .first()
        .isInstanceOf(DummyContainerProvider.class);
  }
}
