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
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

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
              // The explicit tenant plus the default alias (the root declares a usable oidc slot).
              assertThat(provider.get())
                  .extracting(
                      io.camunda.security.api.model.config.ScopedSecurityDescriptor::basePath)
                  .containsExactlyInAnyOrder(
                      "/physical-tenants/tenanta", "/physical-tenants/default");
            });
  }

  // -------------------------------------------------------------------------
  // Cluster-auth unification BeanPostProcessor (#54730): keeps /v2 == /physical-tenants/default
  // -------------------------------------------------------------------------

  @Test
  void shouldUnifyClusterAuthWithDefaultTenantWhenPhysicalTenantsConfigured() {
    // given an OIDC cluster, a configured physical tenant (PT mode active), and a default selection
    final MockEnvironment env = new MockEnvironment();
    env.setProperty("camunda.security.authentication.method", "oidc");
    env.setProperty("camunda.security.authentication.oidc.client-id", "root-client");
    env.setProperty("camunda.security.authentication.oidc.issuer-uri", "http://idp/root");
    env.setProperty("camunda.security.authentication.providers.oidc.a.client-id", "client-a");
    env.setProperty("camunda.security.authentication.providers.oidc.a.issuer-uri", "http://idp/a");
    env.setProperty(
        "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[0]", "a");
    env.setProperty(
        "camunda.physical-tenants.default.security.authentication.providers.assigned[0]", "a");
    final CamundaSecurityLibraryProperties props = new CamundaSecurityLibraryProperties();

    // when the BPP post-processes the CSL properties bean
    final Object result =
        bpp(env).postProcessAfterInitialization(props, "camundaSecurityLibraryProperties");

    // then the cluster authentication mirrors the narrowed default tenant: default slot dropped,
    // only the assigned named provider "a" remains
    assertThat(result).isSameAs(props);
    assertThat(props.getAuthentication().getOidc().getClientId()).isNull();
    assertThat(props.getAuthentication().getProviders().getOidc()).containsOnlyKeys("a");
  }

  @Test
  void shouldLeaveClusterAuthUntouchedWhenNoPhysicalTenantsConfigured() {
    // given no physical tenants configured
    final MockEnvironment env = new MockEnvironment();
    env.setProperty("camunda.security.authentication.method", "oidc");
    env.setProperty("camunda.security.authentication.oidc.client-id", "root-client");
    final CamundaSecurityLibraryProperties props = new CamundaSecurityLibraryProperties();
    final var original = props.getAuthentication();

    // when
    bpp(env).postProcessAfterInitialization(props, "camundaSecurityLibraryProperties");

    // then the bean is left exactly as CSL bound it
    assertThat(props.getAuthentication()).isSameAs(original);
  }

  @Test
  void shouldIgnoreBeansThatAreNotSecurityProperties() {
    final MockEnvironment env = new MockEnvironment();
    env.setProperty(
        "camunda.physical-tenants.tenanta.security.authentication.providers.assigned[0]", "a");
    final Object other = new Object();

    assertThat(bpp(env).postProcessAfterInitialization(other, "someOtherBean")).isSameAs(other);
  }

  private static BeanPostProcessor bpp(final MockEnvironment env) {
    return PhysicalTenantSecurityConfiguration.physicalTenantClusterAuthUnification(env);
  }
}
