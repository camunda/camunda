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
package io.camunda.client.spring.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.impl.CamundaClientBuilderImpl;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class MultiCamundaClientPropertiesResolverTest {

  private static StandardEnvironment environmentWith(final Map<String, Object> properties) {
    final StandardEnvironment environment = new StandardEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
    return environment;
  }

  @Test
  void shouldResolveNoClientsWhenNoneConfigured() {
    // given
    final StandardEnvironment environment = environmentWith(Map.of());

    // when
    final MultiCamundaClientProperties properties =
        MultiCamundaClientPropertiesResolver.resolve(environment);

    // then
    assertThat(properties.getClients()).isEmpty();
  }

  @Test
  void shouldOverlayPerClientOntoGlobalBase() {
    // given a shared base plus two named clients, one overriding tenant-id
    final StandardEnvironment environment =
        environmentWith(
            Map.of(
                "camunda.client.rest-address", "https://oc.example.com",
                "camunda.client.tenant-id", "global-tenant",
                "camunda.clients.finance.physical-tenant-id", "finance",
                "camunda.clients.finance.tenant-id", "finance-tenant",
                "camunda.clients.risk.physical-tenant-id", "riskproduction"));

    // when
    final MultiCamundaClientProperties properties =
        MultiCamundaClientPropertiesResolver.resolve(environment);

    // then
    assertThat(properties.getClients().keySet()).containsExactlyInAnyOrder("finance", "risk");

    final CamundaClientProperties finance = properties.getClients().get("finance");
    assertThat(finance.getPhysicalTenantId()).isEqualTo("finance");
    assertThat(finance.getTenantId()).as("per-client override wins").isEqualTo("finance-tenant");
    assertThat(finance.getRestAddress())
        .as("unset per-client property inherits the global base")
        .isEqualTo(URI.create("https://oc.example.com"));

    final CamundaClientProperties risk = properties.getClients().get("risk");
    assertThat(risk.getPhysicalTenantId()).isEqualTo("riskproduction");
    assertThat(risk.getTenantId())
        .as("inherits global when not overridden")
        .isEqualTo("global-tenant");
    assertThat(risk.getRestAddress()).isEqualTo(URI.create("https://oc.example.com"));
  }

  @Test
  void shouldFallBackToClientDefaultsWhenUnset() {
    // given a client with only a physical-tenant-id and no global base
    final StandardEnvironment environment =
        environmentWith(Map.of("camunda.clients.finance.physical-tenant-id", "finance"));

    // when
    final CamundaClientProperties finance =
        MultiCamundaClientPropertiesResolver.resolve(environment).getClients().get("finance");

    // then unset properties keep the single-client class defaults
    assertThat(finance.getPrefixPhysicalTenantPath())
        .isEqualTo(CamundaClientBuilderImpl.DEFAULT_PREFIX_PHYSICAL_TENANT_PATH);
  }

  @Test
  void shouldNormalizeBlankPhysicalTenantIdToNull() {
    // given a client whose physical-tenant-id is blank (whitespace only)
    final StandardEnvironment environment =
        environmentWith(Map.of("camunda.clients.finance.physical-tenant-id", "   "));

    // when
    final CamundaClientProperties finance =
        MultiCamundaClientPropertiesResolver.resolve(environment).getClients().get("finance");

    // then it is null, so the client treats it as "no physical-tenant targeting" and omits the
    // Camunda-Physical-Tenant gRPC header
    assertThat(finance.getPhysicalTenantId()).isNull();
  }

  @Test
  void shouldTrimPhysicalTenantId() {
    // given a physical-tenant-id padded with whitespace
    final StandardEnvironment environment =
        environmentWith(Map.of("camunda.clients.finance.physical-tenant-id", "  finance  "));

    // when
    final CamundaClientProperties finance =
        MultiCamundaClientPropertiesResolver.resolve(environment).getClients().get("finance");

    // then the stored value is trimmed
    assertThat(finance.getPhysicalTenantId()).isEqualTo("finance");
  }

  @Test
  void shouldRejectInvalidPhysicalTenantId() {
    // given a physical-tenant-id containing a dash
    final StandardEnvironment environment =
        environmentWith(Map.of("camunda.clients.bad.physical-tenant-id", "risk-production"));

    // when / then
    assertThatThrownBy(() -> MultiCamundaClientPropertiesResolver.resolve(environment))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("physical-tenant-id")
        .hasMessageContaining("risk-production");
  }
}
