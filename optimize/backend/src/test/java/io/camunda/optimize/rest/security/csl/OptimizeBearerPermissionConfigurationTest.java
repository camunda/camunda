/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.optimize.service.security.CCSMTokenService;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.security.SecurityHeadersCustomizer;
import org.junit.jupiter.api.Test;

class OptimizeBearerPermissionConfigurationTest {

  @Test
  void shouldBuildAClassifierFromTheCslPropertiesBean() {
    // given
    final var cslProperties = new CamundaSecurityLibraryProperties();
    final OidcConfiguration oidc = cslProperties.getAuthentication().getOidc();
    oidc.setUsernameClaim("preferred_username");
    oidc.setClientIdClaim("client_id");
    final var configuration = new OptimizeBearerPermissionConfiguration();

    // when
    final OidcBearerPrincipalClassifier classifier =
        configuration.oidcBearerPrincipalClassifier(cslProperties);

    // then
    assertThat(classifier).isNotNull();
  }

  @Test
  void shouldExposeANonNullSecurityHeadersCustomizer() {
    // given
    final var configuration = new OptimizeBearerPermissionConfiguration();
    final var classifier =
        configuration.oidcBearerPrincipalClassifier(new CamundaSecurityLibraryProperties());
    final var filter =
        configuration.optimizeBearerPermissionFilter(classifier, mock(CCSMTokenService.class));

    // when
    final SecurityHeadersCustomizer customizer =
        configuration.bearerPermissionFilterCustomizer(filter);

    // then
    assertThat(customizer).isNotNull();
  }
}
