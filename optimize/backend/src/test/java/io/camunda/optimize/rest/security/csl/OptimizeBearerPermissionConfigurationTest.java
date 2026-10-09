/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.optimize.service.security.CCSMTokenService;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.spring.security.SecurityHeadersCustomizer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

class OptimizeBearerPermissionConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(OptimizeBearerPermissionConfiguration.class))
          .withBean(
              CamundaAuthenticationProvider.class, () -> mock(CamundaAuthenticationProvider.class))
          .withBean(CCSMTokenService.class, () -> mock(CCSMTokenService.class));

  @Test
  void shouldRegisterTheBearerPermissionFilterAfterAuthorizationFilter() throws Exception {
    // given: the filter must run after Spring Security's own authorization decision, so it never
    // overrides the audience check the surrounding chain already performed
    final var configuration = new OptimizeBearerPermissionConfiguration();
    final var customizer =
        configuration.bearerPermissionFilterCustomizer(
            mock(CamundaAuthenticationProvider.class), mock(CCSMTokenService.class));
    final HttpSecurity httpSecurity = mock(HttpSecurity.class);

    // when
    customizer.customize(httpSecurity);

    // then
    final var filterCaptor = ArgumentCaptor.forClass(OncePerRequestFilter.class);
    verify(httpSecurity).addFilterAfter(filterCaptor.capture(), eq(AuthorizationFilter.class));
    assertThat(filterCaptor.getValue()).isInstanceOf(OptimizeBearerPermissionFilter.class);
  }

  @Test
  void shouldRegisterOnCcsmWithCslEnabled() {
    // given: no profile set defaults to CCSM (see ConfigurationService#getOptimizeProfile), and
    // the csl.enabled flag defaults to true (matchIfMissing)
    runner.run(context -> assertThat(context).hasSingleBean(SecurityHeadersCustomizer.class));
  }

  @Test
  void shouldBackOffOnTheCloudProfile() {
    // given: an annotation regression here would install the bearer permission filter on CCSaaS
    // chains too, which never had this Identity check and does not go through CCSMTokenService
    runner
        .withPropertyValues("spring.profiles.active=cloud")
        .run(context -> assertThat(context).doesNotHaveBean(SecurityHeadersCustomizer.class));
  }

  @Test
  void shouldBackOffWhenCslIsDisabled() {
    // given: the legacy (pre-CSL) CCSM stack is active instead; it has no notion of this filter
    runner
        .withPropertyValues("optimize.security.csl.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(SecurityHeadersCustomizer.class));
  }
}
