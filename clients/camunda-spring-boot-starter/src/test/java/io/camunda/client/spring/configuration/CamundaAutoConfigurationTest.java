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

import io.camunda.client.CamundaClient;
import io.camunda.client.spring.bean.CamundaClientRegistry;
import io.camunda.client.spring.properties.CamundaClientProperties;
import io.camunda.client.spring.properties.MultiCamundaClientProperties;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Integration tests wiring {@link CamundaAutoConfiguration} through the real auto-configuration
 * machinery so that its conditions ({@code camunda.client.enabled} gating and single- vs
 * multi-client selection), the resolved {@link MultiCamundaClientProperties} bean, and
 * physical-tenant-id validation are exercised end-to-end against a live Spring context.
 */
class CamundaAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CamundaAutoConfiguration.class));

  @Test
  void shouldExposeResolvedMultiClientPropertiesWhenClientsConfigured() {
    contextRunner
        .withPropertyValues(
            "camunda.client.rest-address=https://oc.example.com",
            "camunda.client.tenant-id=global-tenant",
            "camunda.clients.finance.physical-tenant-id=finance",
            "camunda.clients.finance.tenant-id=finance-tenant",
            "camunda.clients.risk.physical-tenant-id=riskproduction")
        .run(
            context -> {
              assertThat(context).hasSingleBean(MultiCamundaClientProperties.class);
              final MultiCamundaClientProperties properties =
                  context.getBean(MultiCamundaClientProperties.class);
              assertThat(properties.getClients().keySet())
                  .containsExactlyInAnyOrder("finance", "risk");

              final CamundaClientProperties finance = properties.getClients().get("finance");
              assertThat(finance.getPhysicalTenantId()).isEqualTo("finance");
              assertThat(finance.getTenantId())
                  .as("per-client override wins")
                  .isEqualTo("finance-tenant");
              assertThat(finance.getRestAddress())
                  .as("unset per-client property inherits the global base")
                  .isEqualTo(URI.create("https://oc.example.com"));

              final CamundaClientProperties risk = properties.getClients().get("risk");
              assertThat(risk.getPhysicalTenantId()).isEqualTo("riskproduction");
              assertThat(risk.getTenantId())
                  .as("inherits global when not overridden")
                  .isEqualTo("global-tenant");
            });
  }

  @Test
  void shouldRegisterOneClientBeanPerConfiguredClient() {
    contextRunner
        .withPropertyValues(
            "camunda.clients.finance.physical-tenant-id=finance",
            "camunda.clients.risk.physical-tenant-id=riskproduction")
        .run(
            context -> {
              // one CamundaClient bean per configured client, named <name>CamundaClient
              assertThat(context).hasBean("financeCamundaClient");
              assertThat(context).hasBean("riskCamundaClient");
              assertThat(context.getBean("financeCamundaClient")).isInstanceOf(CamundaClient.class);
              assertThat(context.getBeansOfType(CamundaClient.class)).hasSize(2);
            });
  }

  @Test
  void shouldExposeClientsViaRegistryByConfiguredName() {
    contextRunner
        .withPropertyValues(
            "camunda.clients.finance.physical-tenant-id=finance",
            "camunda.clients.risk.physical-tenant-id=riskproduction")
        .run(
            context -> {
              assertThat(context).hasSingleBean(CamundaClientRegistry.class);
              final CamundaClientRegistry registry = context.getBean(CamundaClientRegistry.class);
              assertThat(registry.clientNames()).containsExactlyInAnyOrder("finance", "risk");
              // registry entries are the same instances as the named beans
              assertThat(registry.get("finance"))
                  .isSameAs(context.getBean("financeCamundaClient", CamundaClient.class));
              assertThat(registry.get("risk"))
                  .isSameAs(context.getBean("riskCamundaClient", CamundaClient.class));
              assertThat(registry.all()).hasSize(2);
            });
  }

  @Test
  void shouldMarkDesignatedClientAsPrimaryBean() {
    contextRunner
        .withPropertyValues(
            "camunda.clients.finance.physical-tenant-id=finance",
            "camunda.clients.finance.primary=true",
            "camunda.clients.risk.physical-tenant-id=riskproduction")
        .run(
            context -> {
              // @Primary makes a plain CamundaClient lookup unambiguous and resolves to finance
              final CamundaClient primary = context.getBean(CamundaClient.class);
              assertThat(primary).isSameAs(context.getBean("financeCamundaClient"));
              assertThat(context.getBean(CamundaClientRegistry.class).getPrimary())
                  .isSameAs(primary);
            });
  }

  @Test
  void shouldUseRawClientNameForBeanName() {
    // a kebab-case client name is used verbatim (no camel-casing) so distinct names cannot collapse
    // onto the same bean name
    contextRunner
        .withPropertyValues("camunda.clients.my-client.physical-tenant-id=finance")
        .run(
            context -> {
              assertThat(context).hasBean("my-clientCamundaClient");
              assertThat(context.getBean(CamundaClientRegistry.class).clientNames())
                  .containsExactly("my-client");
            });
  }

  @Test
  void shouldAlwaysConfigureTheUnifiedMultiClientPath() {
    // unification: the multi-client auto-configuration is the single path and is always active
    // (no longer gated on camunda.clients.* being present)
    contextRunner
        .withPropertyValues("camunda.client.rest-address=https://oc.example.com")
        .run(
            context ->
                assertThat(context)
                    .hasSingleBean(MultiCamundaClientProperties.class)
                    .hasSingleBean(CamundaClientRegistry.class));
  }

  @Test
  void shouldNotConfigureMultiClientWhenClientDisabled() {
    contextRunner
        .withPropertyValues(
            "camunda.client.enabled=false", "camunda.clients.finance.physical-tenant-id=finance")
        .run(context -> assertThat(context).doesNotHaveBean(MultiCamundaClientProperties.class));
  }

  @Test
  void shouldFailStartupOnMixedAuthTypes() {
    contextRunner
        .withPropertyValues(
            "camunda.clients.finance.auth.method=oidc", "camunda.clients.risk.auth.method=basic")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .isInstanceOf(IllegalArgumentException.class)
                  .hasMessageContaining("authentication method");
            });
  }

  @Test
  void shouldFailStartupOnInvalidPhysicalTenantId() {
    contextRunner
        .withPropertyValues("camunda.clients.bad.physical-tenant-id=risk-production")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .isInstanceOf(IllegalArgumentException.class)
                  .hasMessageContaining("physical-tenant-id")
                  .hasMessageContaining("risk-production");
            });
  }
}
