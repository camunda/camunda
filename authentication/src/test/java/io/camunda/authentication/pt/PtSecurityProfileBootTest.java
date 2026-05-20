/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = PtSecurityProfileBootTest.MinimalApp.class)
@ActiveProfiles({"consolidated-auth", "pt-security"})
@TestPropertySource(properties = {"camunda.security.authentication.method=oidc"})
class PtSecurityProfileBootTest {

  @org.springframework.boot.autoconfigure.SpringBootApplication
  static class MinimalApp {}

  @Test
  void shouldNotRegisterAnySecurityFilterChainOnPtSecurityProfile(
      final ApplicationContext context) {
    // given - the pt-security profile is active alongside consolidated-auth
    // when we collect every SecurityFilterChain in the context
    final var chains = context.getBeansOfType(SecurityFilterChain.class);

    // then no chain is registered yet — PhysicalTenantSecurityConfiguration is the
    // only allowed producer and it does not exist in this task. CSL's
    // CamundaSecurityAutoConfiguration must back off because WebSecurityConfig is
    // excluded from the active profile set.
    assertThat(chains).isEmpty();
  }
}
