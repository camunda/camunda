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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

class OptimizeBearerPermissionConfigurationTest {

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
}
