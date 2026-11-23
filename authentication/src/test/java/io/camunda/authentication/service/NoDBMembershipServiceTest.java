/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.authentication.service.MembershipService.PrincipalType;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.configuration.ProvidersConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoDBMembershipServiceTest {

  private static final String DEFAULT_ISSUER = "https://default.example.com";
  private static final String PARTNER_ISSUER = "https://partner.example.com";

  private NoDBMembershipService membershipService;

  @BeforeEach
  void setUp() {
    // given — only the additional provider has a groups claim
    final var defaultOidc = new OidcAuthenticationConfiguration();
    defaultOidc.setIssuerUri(DEFAULT_ISSUER);
    final var partnerOidc = new OidcAuthenticationConfiguration();
    partnerOidc.setIssuerUri(PARTNER_ISSUER);
    partnerOidc.setGroupsClaim("groups");
    final var providers = new ProvidersConfiguration();
    providers.setOidc(Map.of("partner", partnerOidc));

    final var securityConfiguration = new SecurityConfiguration();
    securityConfiguration.getAuthentication().setOidc(defaultOidc);
    securityConfiguration.getAuthentication().setProviders(providers);
    membershipService = new NoDBMembershipService(securityConfiguration);
  }

  @Test
  void shouldReadGroupsFromTheClaimOfTheProviderThatIssuedTheToken() {
    // when
    final var resolver =
        membershipService.newResolver(
            Map.of("sub", "demo", "iss", PARTNER_ISSUER, "groups", List.of("g1", "g2")),
            "demo",
            PrincipalType.USER);

    // then
    assertThat(resolver.groups()).containsExactly("g1", "g2");
  }

  @Test
  void shouldGiveNoGroupsForAProviderWithoutAGroupsClaim() {
    // when
    final var resolver =
        membershipService.newResolver(
            Map.of("sub", "demo", "iss", DEFAULT_ISSUER, "groups", List.of("g1")),
            "demo",
            PrincipalType.USER);

    // then
    assertThat(resolver.groups()).isEmpty();
  }
}
