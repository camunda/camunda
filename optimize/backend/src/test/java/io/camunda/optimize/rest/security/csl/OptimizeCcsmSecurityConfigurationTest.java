/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.service.security.CCSMTokenService;
import io.camunda.security.spring.oidc.TokenValidatorFactory;
import io.camunda.security.spring.security.SecurityHeadersCustomizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;

@ExtendWith(MockitoExtension.class)
class OptimizeCcsmSecurityConfigurationTest {

  @Mock private CCSMTokenService ccsmTokenService;

  @Test
  void shouldInstallTheSessionPermissionFilterAndLeaveTokenValidationToCsl() {
    // given
    final ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withBean(CCSMTokenService.class, () -> ccsmTokenService)
            .withUserConfiguration(OptimizeCcsmSecurityConfiguration.class);

    // when / then
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(SecurityHeadersCustomizer.class);
          assertThat(context).getBeanNames(TokenValidatorFactory.class).isEmpty();
          assertThat(context).getBeanNames(JwtDecoderFactory.class).isEmpty();
        });
  }
}
