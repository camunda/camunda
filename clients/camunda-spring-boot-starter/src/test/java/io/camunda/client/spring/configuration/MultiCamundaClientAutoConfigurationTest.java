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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.spring.bean.CamundaClientRegistry;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

public class MultiCamundaClientAutoConfigurationTest {

  @Nested
  @SpringBootTest(
      classes = MultiCamundaClientAutoConfiguration.class,
      properties = {
        "camunda.clients.production.primary=true",
        "camunda.clients.production.rest-address=http://localhost:8080",
        "camunda.clients.staging.rest-address=http://localhost:8081"
      })
  class MultiClientConfiguration {

    @Autowired CamundaClient primaryClient;

    @Autowired
    @Qualifier("productionCamundaClient")
    CamundaClient productionClient;

    @Autowired
    @Qualifier("stagingCamundaClient")
    CamundaClient stagingClient;

    @Autowired CamundaClientRegistry registry;

    @Test
    void shouldCreateMultipleClients() {
      assertThat(productionClient).isNotNull();
      assertThat(stagingClient).isNotNull();
    }

    @Test
    void shouldInjectPrimaryClientWithoutQualifier() {
      assertThat(primaryClient).isNotNull();
      assertThat(primaryClient).isSameAs(productionClient);
    }

    @Test
    void shouldProvideRegistry() {
      assertThat(registry).isNotNull();
      assertThat(registry.size()).isEqualTo(2);
      assertThat(registry.getClientNames()).containsExactlyInAnyOrder("production", "staging");
    }

    @Test
    void shouldGetClientByNameFromRegistry() {
      final CamundaClient prodClient = registry.getClient("production");
      assertThat(prodClient).isSameAs(productionClient);

      final CamundaClient stageClient = registry.getClient("staging");
      assertThat(stageClient).isSameAs(stagingClient);
    }

    @Test
    void shouldThrowExceptionForUnknownClient() {
      assertThatThrownBy(() -> registry.getClient("unknown"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("No CamundaClient found with name 'unknown'");
    }

    @Test
    void shouldFindClientOptionally() {
      assertThat(registry.findClient("production")).isPresent();
      assertThat(registry.findClient("unknown")).isEmpty();
    }
  }

  @Nested
  @SpringBootTest(
      classes = MultiCamundaClientAutoConfiguration.class,
      properties = {
        "camunda.clients.enabled-client.rest-address=http://localhost:8080",
        "camunda.clients.disabled-client.enabled=false",
        "camunda.clients.disabled-client.rest-address=http://localhost:8081"
      })
  class DisabledClientConfiguration {

    @Autowired CamundaClientRegistry registry;

    @Autowired(required = false)
    @Qualifier("enabledClientCamundaClient")
    CamundaClient enabledClient;

    @Autowired(required = false)
    @Qualifier("disabledClientCamundaClient")
    CamundaClient disabledClient;

    @Test
    void shouldNotCreateDisabledClient() {
      assertThat(enabledClient).isNotNull();
      assertThat(disabledClient).isNull();
    }

    @Test
    void shouldOnlyHaveEnabledClientInRegistry() {
      assertThat(registry.size()).isEqualTo(1);
      assertThat(registry.hasClient("enabled-client")).isTrue();
      assertThat(registry.hasClient("disabled-client")).isFalse();
    }
  }

  @Nested
  @SpringBootTest(
      classes = MultiCamundaClientAutoConfiguration.class,
      properties = {
        "camunda.clients.client-one.primary=true",
        "camunda.clients.client-one.rest-address=http://localhost:8080",
        "camunda.clients.client-two.rest-address=http://localhost:8081"
      })
  class MultipleClientsWithPrimaryConfiguration {

    @Autowired CamundaClient primaryClient;

    @Autowired
    @Qualifier("clientOneCamundaClient")
    CamundaClient clientOne;

    @Autowired
    @Qualifier("clientTwoCamundaClient")
    CamundaClient clientTwo;

    @Autowired CamundaClientRegistry registry;

    @Test
    void shouldCreateBothClients() {
      assertThat(clientOne).isNotNull();
      assertThat(clientTwo).isNotNull();
    }

    @Test
    void shouldInjectPrimaryWithoutQualifier() {
      // Primary client can be injected without qualifier
      assertThat(primaryClient).isSameAs(clientOne);
    }

    @Test
    void shouldHaveDifferentClientInstances() {
      // Both clients exist and are different
      assertThat(clientOne).isNotSameAs(clientTwo);
      assertThat(registry.size()).isEqualTo(2);
    }
  }

  @Nested
  @SpringBootTest(
      classes = MultiCamundaClientAutoConfiguration.class,
      properties = {
        "camunda.clients.my-client.rest-address=http://localhost:8080",
        "camunda.clients.my-client.grpc-address=http://localhost:26500",
        "camunda.clients.my-client.tenant-id=my-tenant",
        "camunda.clients.my-client.request-timeout=30s",
        "camunda.clients.my-client.prefer-rest-over-grpc=true"
      })
  class ClientWithCustomProperties {

    @Autowired
    @Qualifier("myClientCamundaClient")
    CamundaClient myClient;

    @Test
    void shouldCreateClientWithCustomProperties() {
      assertThat(myClient).isNotNull();
      final var config = myClient.getConfiguration();
      assertThat(config.getRestAddress().toString()).isEqualTo("http://localhost:8080");
      assertThat(config.getGrpcAddress().toString()).isEqualTo("http://localhost:26500");
      assertThat(config.getDefaultTenantId()).isEqualTo("my-tenant");
      assertThat(config.getDefaultRequestTimeout().getSeconds()).isEqualTo(30);
      assertThat(config.preferRestOverGrpc()).isTrue();
    }
  }
}
