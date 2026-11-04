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
import org.springframework.mock.env.MockEnvironment;

class MappingRuleMembershipPolicyTest {

  @Test
  void shouldAllowManagingMappingRulesOutsideSaaS() {
    final var policy = MappingRuleMembershipPolicy.of(properties(null), new MockEnvironment());

    assertThat(policy.requireManageable(null).isRight()).isTrue();
  }

  @Test
  void shouldForbidManagingMappingRulesOnSaaSWithoutAnAdditionalIdp() {
    final var policy = MappingRuleMembershipPolicy.of(properties("org-1"), new MockEnvironment());

    assertThat(policy.requireManageable(null).isLeft()).isTrue();
    assertThat(policy.requireManageable(null).getLeft().getStatus()).isEqualTo(403);
  }

  @Test
  void shouldAllowManagingMappingRulesOnSaaSWithAnAdditionalIdp() {
    final var policy =
        MappingRuleMembershipPolicy.of(
            withAdditionalIdp(properties("org-1")), new MockEnvironment());

    assertThat(policy.requireManageable(null).isRight()).isTrue();
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

    final var policy = MappingRuleMembershipPolicy.of(properties, new MockEnvironment());

    assertThat(policy.requireManageable(null).isLeft()).isTrue();
  }

  @Test
  void shouldAllowManagingMappingRulesOnlyForTheTenantWithAnAdditionalIdp() {
    // given only tenantb configures an additional IdP
    final var environment =
        new MockEnvironment()
            .withProperty("camunda.security.authentication.method", "oidc")
            .withProperty(
                "camunda.physical-tenants.tenantb.security.authentication.providers.oidc.byoidp"
                    + ".client-id",
                "byoidp-client");

    // when
    final var policy = MappingRuleMembershipPolicy.of(properties("org-1"), environment);

    // then tenantb can manage its mapping rules, and the default tenant can't
    assertThat(policy.requireManageable("tenantb").isRight()).isTrue();
    assertThat(policy.requireManageable("default").isLeft()).isTrue();
    assertThat(policy.requireManageable(null).isLeft()).isTrue();
  }

  @Test
  void shouldForbidManagingMappingRulesForATenantWithoutTheClusterAdditionalIdp() {
    // given the cluster has an additional IdP, and tenantb assigns only the default provider
    final var environment =
        new MockEnvironment()
            .withProperty("camunda.security.authentication.method", "oidc")
            .withProperty(
                "camunda.security.authentication.providers.oidc.byoidp.client-id", "byoidp-client")
            .withProperty(
                "camunda.physical-tenants.tenantb.security.authentication.providers.assigned[0]",
                OidcConfiguration.DEFAULT_REGISTRATION_ID);

    // when
    final var policy =
        MappingRuleMembershipPolicy.of(withAdditionalIdp(properties("org-1")), environment);

    // then
    assertThat(policy.requireManageable("default").isRight()).isTrue();
    assertThat(policy.requireManageable("tenantb").isLeft()).isTrue();
  }

  @Test
  void shouldUseTheClusterConfigurationForATenantThatIsNotConfigured() {
    final var policy =
        MappingRuleMembershipPolicy.of(
            withAdditionalIdp(properties("org-1")), new MockEnvironment());

    assertThat(policy.isAdditionalIdpConfigured("unknown")).isTrue();
  }

  private static CamundaSecurityLibraryProperties properties(final String organizationId) {
    final var properties = new CamundaSecurityLibraryProperties();
    properties.getSaas().setOrganizationId(organizationId);
    return properties;
  }

  private static CamundaSecurityLibraryProperties withAdditionalIdp(
      final CamundaSecurityLibraryProperties properties) {
    properties
        .getAuthentication()
        .getProviders()
        .getOidc()
        .put("byoidp", OidcConfiguration.builder().clientId("byoidp-client").build());
    return properties;
  }
}
