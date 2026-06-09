/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.context.CamundaSecurityScopeProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

/**
 * Verifies that {@link PhysicalTenantSecurityConfiguration} registers the expected beans in the
 * application context.
 */
class PhysicalTenantSecurityConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(PhysicalTenantSecurityConfiguration.class);

  @Test
  void shouldRegisterScopeProviderBean() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(CamundaSecurityScopeProvider.class);
          assertThat(context.getBean(CamundaSecurityScopeProvider.class))
              .isInstanceOf(PhysicalTenantScopeProvider.class);
        });
  }

  @Test
  void shouldRegisterPreSecurityFilterBean() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(FilterRegistrationBean.class);
          final var reg = context.getBean(FilterRegistrationBean.class);
          assertThat(reg.getFilter()).isInstanceOf(PhysicalTenantPreSecurityFilter.class);
          // Must run before Spring Security's FilterChainProxy (-100)
          assertThat(reg.getOrder()).isLessThan(-100);
        });
  }

  @Test
  void shouldReturnEmptyDescriptorsWhenNoPhysicalTenantsConfigured() {
    runner.run(
        context -> {
          final var provider = context.getBean(CamundaSecurityScopeProvider.class);
          assertThat(provider.get()).isEmpty();
        });
  }

  @Test
  void shouldReturnOneDescriptorPerConfiguredTenant() {
    runner
        .withPropertyValues(
            "camunda.security.authentication.oidc.client-id=root-client",
            "camunda.security.authentication.oidc.issuer-uri=http://idp/root",
            // tenanta has at least one physical-tenants key so it gets discovered
            "camunda.physical-tenants.tenanta.security.authentication.method=oidc")
        .run(
            context -> {
              final var provider = context.getBean(CamundaSecurityScopeProvider.class);
              assertThat(provider.get()).hasSize(1);
              assertThat(provider.get().get(0).basePath()).isEqualTo("/physical-tenants/tenanta");
            });
  }
}
