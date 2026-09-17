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
import io.camunda.security.core.port.in.OidcProviderConfigurationPort;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.oidc.TokenValidatorFactory;
import io.camunda.security.spring.security.SecurityHeadersCustomizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;

@ExtendWith(MockitoExtension.class)
class OptimizeCcsmSecurityConfigurationTest {

  @Mock private OidcProviderConfigurationPort oidcProviderConfigurationPort;
  @Mock private CCSMTokenService ccsmTokenService;

  @Test
  void shouldInstallTheSessionPermissionFilterAndLeaveTokenValidationToCsl() {
    // given
    // The Identity write:* gate applies to session logins only, carried by the
    // SecurityHeadersCustomizer installer. Bearer tokens stay ungated as they were on legacy CCSM,
    // so this configuration contributes neither a TokenValidatorFactory nor a JwtDecoderFactory:
    // CSL's own beans keep validating API tokens, and Spring Security's stock decoder keeps
    // validating the login id_token.
    final ApplicationContextRunner runner =
        new ApplicationContextRunner()
            // The chain beans depend on HttpSecurity and OIDC client-registration beans this
            // minimal context doesn't provide. Registering
            // LazyInitializationBeanFactoryPostProcessor as a bean marks every bean definition
            // lazy, which is enough for the context to start, the same pattern
            // CslSecurityChainSelectionTest uses.
            .withBean(
                LazyInitializationBeanFactoryPostProcessor.class,
                LazyInitializationBeanFactoryPostProcessor::new)
            .withBean(CamundaSecurityLibraryProperties.class, CamundaSecurityLibraryProperties::new)
            .withBean(OidcProviderConfigurationPort.class, () -> oidcProviderConfigurationPort)
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
