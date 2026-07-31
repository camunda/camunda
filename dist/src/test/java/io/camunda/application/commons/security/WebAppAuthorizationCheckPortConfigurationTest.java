/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.security.core.authz.AuthorizationService;
import io.camunda.security.core.authz.LazyTokenClaimsConverter;
import io.camunda.security.core.port.in.AuthorizationCheckPort;
import io.camunda.security.core.port.out.AuthorizationScopeRepositoryPort;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.authz.AuthorizationCheckerConfiguration;
import io.camunda.security.spring.authz.AuthorizationConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

/**
 * Proves that the host-supplied {@link TenantAwareAuthorizationCheckPort} wins the
 * {@code @ConditionalOnMissingBean(AuthorizationCheckPort.class)} race against CSL's default {@link
 * AuthorizationService} bean. Registers the dist configurations via {@code withUserConfiguration}
 * and CSL's competing configurations via {@code AutoConfigurations.of(...)}, mirroring the real
 * ordering: {@code @ComponentScan}-discovered host configurations register before
 * {@code @ImportAutoConfiguration}-imported library configurations.
 */
class WebAppAuthorizationCheckPortConfigurationTest {

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withBean(
              AuthorizationScopeRepositoryPort.class,
              () -> mock(AuthorizationScopeRepositoryPort.class))
          .withBean(LazyTokenClaimsConverter.class, () -> mock(LazyTokenClaimsConverter.class))
          .withBean(CamundaSecurityLibraryProperties.class, CamundaSecurityLibraryProperties::new)
          .withConfiguration(
              AutoConfigurations.of(
                  AuthorizationCheckerConfiguration.class, AuthorizationConfiguration.class));

  @Test
  void shouldWireTenantAwarePortWinningConditionalOnMissingBeanRace() {
    // given: the host configurations are registered as user configuration, ahead of CSL's
    // auto-configuration-imported default

    // when / then
    runner
        .withUserConfiguration(
            AuthorizationCheckerProviderConfiguration.class,
            WebAppAuthorizationCheckPortConfiguration.class)
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(AuthorizationCheckPort.class);
              assertThat(ctx.getBean(AuthorizationCheckPort.class))
                  .isInstanceOf(TenantAwareAuthorizationCheckPort.class);
            });
  }

  @Test
  void shouldFallBackToDefaultAuthorizationServiceWhenHostPortAbsent() {
    // given: no host override registered, only CSL's own configurations

    // when / then: CSL's default AuthorizationCheckPort backs off to nothing else, so its own
    // AuthorizationService is exactly the bean that would otherwise ship the default behavior --
    // confirming the win in the other test comes from bean ordering, not from CSL's bean being
    // uncreatable
    runner.run(
        ctx -> {
          assertThat(ctx).hasSingleBean(AuthorizationCheckPort.class);
          assertThat(ctx.getBean(AuthorizationCheckPort.class))
              .isInstanceOf(AuthorizationService.class);
        });
  }
}
