/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.SecurityConfiguration;
import org.junit.jupiter.api.Test;

class MappingRuleMembershipPolicyTest {

  @Test
  void shouldAllowManagingMappingRulesOutsideSaaS() {
    assertThat(MappingRuleMembershipPolicy.of(configuration(null)).requireManageable().isRight())
        .isTrue();
  }

  @Test
  void shouldForbidManagingMappingRulesOnSaaS() {
    final var policy = MappingRuleMembershipPolicy.of(configuration("org-1"));

    assertThat(policy.requireManageable().isLeft()).isTrue();
    assertThat(policy.requireManageable().getLeft().getStatus()).isEqualTo(403);
  }

  private static SecurityConfiguration configuration(final String organizationId) {
    final var configuration = new SecurityConfiguration();
    configuration.getSaas().setOrganizationId(organizationId);
    return configuration;
  }
}
