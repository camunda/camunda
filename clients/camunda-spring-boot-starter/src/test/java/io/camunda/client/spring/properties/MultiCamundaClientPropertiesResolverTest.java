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
  void shouldProjectSingleDefaultClientWhenNoneConfigured() {
    // given no explicit camunda.clients.<name>.* entries (single-client / unconfigured app)
    final StandardEnvironment environment =
        environmentWith(Map.of("camunda.client.rest-address", "https://oc.example.com"));

    // when
    final MultiCamundaClientProperties properties =
        MultiCamundaClientPropertiesResolver.resolve(environment);

    // then it is projected onto exactly one client named 'default', seeded from camunda.client.*
    assertThat(properties.getClients().keySet()).containsExactly("default");
    assertThat(properties.getPrimaryClientName()).contains("default");
    assertThat(properties.getClients().get("default").getRestAddress().toString())
        .isEqualTo("https://oc.example.com");
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

  @Test
  void shouldTreatSingleClientAsPrimary() {
    // given a single client and no explicit primary flag
    final StandardEnvironment environment =
        environmentWith(Map.of("camunda.clients.finance.physical-tenant-id", "finance"));

    // when
    final MultiCamundaClientProperties properties =
        MultiCamundaClientPropertiesResolver.resolve(environment);

    // then it is the primary implicitly
    assertThat(properties.getPrimaryClientName()).contains("finance");
  }

  @Test
  void shouldResolveExplicitlyFlaggedPrimary() {
    // given two clients with one flagged primary
    final StandardEnvironment environment =
        environmentWith(
            Map.of(
                "camunda.clients.finance.physical-tenant-id", "finance",
                "camunda.clients.risk.physical-tenant-id", "riskproduction",
                "camunda.clients.risk.primary", "true"));

    // when
    final MultiCamundaClientProperties properties =
        MultiCamundaClientPropertiesResolver.resolve(environment);

    // then
    assertThat(properties.getPrimaryClientName()).contains("risk");
  }

  @Test
  void shouldHaveNoPrimaryWhenMultipleClientsAndNoneFlagged() {
    // given two clients and no primary flag
    final StandardEnvironment environment =
        environmentWith(
            Map.of(
                "camunda.clients.finance.physical-tenant-id", "finance",
                "camunda.clients.risk.physical-tenant-id", "riskproduction"));

    // when
    final MultiCamundaClientProperties properties =
        MultiCamundaClientPropertiesResolver.resolve(environment);

    // then no primary is designated
    assertThat(properties.getPrimaryClientName()).isEmpty();
  }

  @Test
  void shouldRejectMoreThanOnePrimary() {
    // given two clients both flagged primary
    final StandardEnvironment environment =
        environmentWith(
            Map.of(
                "camunda.clients.finance.primary", "true",
                "camunda.clients.risk.primary", "true"));

    // when / then
    assertThatThrownBy(() -> MultiCamundaClientPropertiesResolver.resolve(environment))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("primary");
  }

  @Test
  void shouldAllowSameAuthTypeWithDistinctCredentials() {
    // given two clients sharing the basic auth type but with distinct credentials
    final StandardEnvironment environment =
        environmentWith(
            Map.of(
                "camunda.client.auth.method", "basic",
                "camunda.clients.finance.auth.username", "financeuser",
                "camunda.clients.finance.auth.password", "financepw",
                "camunda.clients.risk.auth.username", "riskuser",
                "camunda.clients.risk.auth.password", "riskpw"));

    // when
    final MultiCamundaClientProperties properties =
        MultiCamundaClientPropertiesResolver.resolve(environment);

    // then both inherit the same method, with their own identities
    assertThat(properties.getClients().get("finance").getAuth().getUsername())
        .isEqualTo("financeuser");
    assertThat(properties.getClients().get("risk").getAuth().getUsername()).isEqualTo("riskuser");
  }

  @Test
  void shouldAllowExplicitNoneAndUnsetAuthMethodTogether() {
    // given one client with an explicit 'none' method and another leaving it unset - both mean no
    // auth (NoopCredentialsProvider), so they must not be treated as mixed auth types
    final StandardEnvironment environment =
        environmentWith(
            Map.of(
                "camunda.clients.finance.auth.method", "none",
                "camunda.clients.risk.grpc-address", "http://localhost:26500"));

    // when
    final MultiCamundaClientProperties properties =
        MultiCamundaClientPropertiesResolver.resolve(environment);

    // then both clients are resolved without a mixed-auth-type rejection
    assertThat(properties.getClients()).containsKeys("finance", "risk");
  }

  @Test
  void shouldRejectMixedAuthTypes() {
    // given two clients declaring different auth methods
    final StandardEnvironment environment =
        environmentWith(
            Map.of(
                "camunda.clients.finance.auth.method", "oidc",
                "camunda.clients.risk.auth.method", "basic"));

    // when / then
    assertThatThrownBy(() -> MultiCamundaClientPropertiesResolver.resolve(environment))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("authentication method")
        .hasMessageContaining("oidc")
        .hasMessageContaining("basic");
  }
}
