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
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.security.SecurityHeadersCustomizer;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

class OptimizeBearerPermissionConfigurationTest {

  @Test
  void shouldBuildAClassifierThatActuallyUsesTheConfiguredClientIdClaim() {
    // given: a non-default claim name, so a stub that just built its own OidcConfiguration
    // instead of threading the injected properties through would fail this assertion
    final var cslProperties = new CamundaSecurityLibraryProperties();
    final OidcConfiguration oidc = cslProperties.getAuthentication().getOidc();
    oidc.setClientIdClaim("custom_client_id_claim");
    final var configuration = new OptimizeBearerPermissionConfiguration();

    // when
    final OidcBearerPrincipalClassifier classifier =
        configuration.oidcBearerPrincipalClassifier(cslProperties);

    // then
    assertThat(
            classifier.requiresOptimizePermissionCheck(
                Map.of("custom_client_id_claim", "some-client")))
        .as("a client_id-shaped claim under the CONFIGURED claim name must classify as a client")
        .isFalse();
  }

  @Test
  void shouldRegisterTheBearerPermissionFilterAfterAuthorizationFilter() throws Exception {
    // given: the filter must run after Spring Security's own authorization decision, so it never
    // overrides the audience check the surrounding chain already performed
    final var configuration = new OptimizeBearerPermissionConfiguration();
    final SecurityHeadersCustomizer customizer =
        configuration.bearerPermissionFilterCustomizer(
            configuration.oidcBearerPrincipalClassifier(new CamundaSecurityLibraryProperties()),
            mock(CCSMTokenService.class));
    final HttpSecurity httpSecurity = mock(HttpSecurity.class);

    // when
    customizer.customize(httpSecurity);

    // then
    final var filterCaptor = ArgumentCaptor.forClass(OncePerRequestFilter.class);
    verify(httpSecurity).addFilterAfter(filterCaptor.capture(), eq(AuthorizationFilter.class));
    assertThat(filterCaptor.getValue()).isInstanceOf(OptimizeBearerPermissionFilter.class);
  }
}
