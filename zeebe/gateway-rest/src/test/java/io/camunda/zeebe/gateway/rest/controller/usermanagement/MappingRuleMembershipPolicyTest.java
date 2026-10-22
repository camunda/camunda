/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import org.junit.jupiter.api.Test;

class MappingRuleMembershipPolicyTest {

  @Test
  void shouldAllowManagingMappingRulesOutsideSaaS() {
    assertThat(MappingRuleMembershipPolicy.of(properties(null)).requireManageable().isRight())
        .isTrue();
  }

  @Test
  void shouldForbidManagingMappingRulesOnSaaSWithoutAnAdditionalIdp() {
    final var policy = MappingRuleMembershipPolicy.of(properties("org-1"));

    assertThat(policy.requireManageable().isLeft()).isTrue();
    assertThat(policy.requireManageable().getLeft().getStatus()).isEqualTo(403);
  }

  @Test
  void shouldAllowManagingMappingRulesOnSaaSWithAnAdditionalIdp() {
    final var properties = properties("org-1");
    properties
        .getAuthentication()
        .getProviders()
        .getOidc()
        .put("byoidp", OidcConfiguration.builder().clientId("byoidp-client").build());

    assertThat(MappingRuleMembershipPolicy.of(properties).requireManageable().isRight()).isTrue();
  }

  /** A named provider without a client ID is no additional IdP. */
  @Test
  void shouldForbidManagingMappingRulesOnSaaSWithANamedProviderWithoutClientId() {
    final var properties = properties("org-1");
    properties
        .getAuthentication()
        .getProviders()
        .getOidc()
        .put("incomplete", OidcConfiguration.builder().build());

    assertThat(MappingRuleMembershipPolicy.of(properties).requireManageable().isLeft()).isTrue();
  }

  private static CamundaSecurityLibraryProperties properties(final String organizationId) {
    final var properties = new CamundaSecurityLibraryProperties();
    properties.getSaas().setOrganizationId(organizationId);
    return properties;
  }
}
