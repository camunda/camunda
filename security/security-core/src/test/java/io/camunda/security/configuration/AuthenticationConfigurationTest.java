/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.entity.AuthenticationMethod;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthenticationConfigurationTest {

  private static final String DEFAULT_ISSUER = "https://default.example.com/realms/camunda";
  private static final String PARTNER_ISSUER = "https://partner.example.com/";

  private final AuthenticationConfiguration authentication = new AuthenticationConfiguration();
  private final OidcAuthenticationConfiguration defaultOidc = oidc(DEFAULT_ISSUER);
  private final OidcAuthenticationConfiguration partnerOidc = oidc(PARTNER_ISSUER);

  @BeforeEach
  void setUp() {
    authentication.setOidc(defaultOidc);
    final var providers = new ProvidersConfiguration();
    providers.setOidc(Map.of("partner", partnerOidc));
    authentication.setProviders(providers);
  }

  @Test
  void shouldReadTheGroupsClaimOfTheDefaultProviderByIssuer() {
    // given
    defaultOidc.setClientId("client");
    defaultOidc.setGroupsClaim("default-groups");
    partnerOidc.setGroupsClaim("partner-groups");

    // then
    assertThat(authentication.getGroupsClaim(DEFAULT_ISSUER)).isEqualTo("default-groups");
  }

  @Test
  void shouldReadTheGroupsClaimOfAnAdditionalProviderByIssuer() {
    // given
    defaultOidc.setClientId("client");
    defaultOidc.setGroupsClaim("default-groups");
    partnerOidc.setGroupsClaim("partner-groups");

    // then
    assertThat(authentication.getGroupsClaim(PARTNER_ISSUER)).isEqualTo("partner-groups");
  }

  @Test
  void shouldIgnoreATrailingSlashOfTheIssuer() {
    // given
    defaultOidc.setClientId("client");
    partnerOidc.setGroupsClaim("partner-groups");

    // then
    assertThat(authentication.getGroupsClaim("https://partner.example.com"))
        .isEqualTo("partner-groups");
  }

  @Test
  void shouldInheritTheGroupsClaimOfTheDefaultProvider() {
    // given an additional provider without a groups claim of its own
    defaultOidc.setClientId("client");
    defaultOidc.setGroupsClaim("default-groups");

    // then
    assertThat(authentication.getGroupsClaim(PARTNER_ISSUER)).isEqualTo("default-groups");
  }

  @Test
  void shouldHaveNoGroupsClaimWhenNeitherTheProviderNorTheDefaultHasOne() {
    // given
    defaultOidc.setClientId("client");

    // then
    assertThat(authentication.getGroupsClaim(PARTNER_ISSUER)).isNull();
  }

  @Test
  void shouldFallBackToTheGroupsClaimOfTheDefaultProviderForAnUnknownIssuer() {
    // given
    defaultOidc.setClientId("client");
    defaultOidc.setGroupsClaim("default-groups");
    partnerOidc.setGroupsClaim("partner-groups");

    // then
    assertThat(authentication.getGroupsClaim("https://unknown.example.com"))
        .isEqualTo("default-groups");
  }

  @Test
  void shouldFallBackToTheGroupsClaimOfTheDefaultProviderWithoutAnIssuer() {
    // given
    defaultOidc.setClientId("client");
    defaultOidc.setGroupsClaim("default-groups");
    partnerOidc.setGroupsClaim("partner-groups");

    // then
    assertThat(authentication.getGroupsClaim(null)).isEqualTo("default-groups");
  }

  @Test
  void shouldReadTheGroupsClaimOfTheOnlyProviderWhateverTheIssuer() {
    // given a single additional provider that has no issuer URI: it verifies every token
    partnerOidc.setIssuerUri(null);
    partnerOidc.setGroupsClaim("partner-groups");

    // then
    assertThat(authentication.getGroupsClaim("https://idp.example.com"))
        .isEqualTo("partner-groups");
    assertThat(authentication.getGroupsClaim(null)).isEqualTo("partner-groups");
  }

  @Test
  void shouldReadTheGroupsClaimOfTheDefaultProviderWhenItIsTheOnlyOne() {
    // given
    authentication.setProviders(null);
    defaultOidc.setGroupsClaim("default-groups");

    // then
    assertThat(authentication.getGroupsClaim(PARTNER_ISSUER)).isEqualTo("default-groups");
  }

  private static OidcAuthenticationConfiguration oidc(final String issuerUri) {
    final var configuration = new OidcAuthenticationConfiguration();
    configuration.setIssuerUri(issuerUri);
    return configuration;
  }

  @Test
  void shouldNeedNoGroupToExistWhenOneProviderHasAGroupsClaim() {
    // given a registered default provider without a groups claim
    authentication.setMethod(AuthenticationMethod.OIDC);
    defaultOidc.setClientId("client");
    partnerOidc.setGroupsClaim("groups");

    // then
    assertThat(authentication.isExternalGroupsEnabled()).isTrue();
    assertThat(authentication.isCamundaGroupsEnabled()).isTrue();
  }

  @Test
  void shouldManageNoGroupsInCamundaWhenEveryProviderHasAGroupsClaim() {
    // given
    authentication.setMethod(AuthenticationMethod.OIDC);
    defaultOidc.setClientId("client");
    defaultOidc.setGroupsClaim("groups");
    partnerOidc.setGroupsClaim("groups");

    // then
    assertThat(authentication.isExternalGroupsEnabled()).isTrue();
    assertThat(authentication.isCamundaGroupsEnabled()).isFalse();
  }

  @Test
  void shouldIgnoreAnUnregisteredDefaultProviderWhenAdditionalProvidersExist() {
    // given: the default provider has no groups claim, but no client ID either, so it is not
    // registered and does not count
    authentication.setMethod(AuthenticationMethod.OIDC);
    partnerOidc.setGroupsClaim("groups");

    // then
    assertThat(authentication.isCamundaGroupsEnabled()).isFalse();
  }

  @Test
  void shouldUseTheDefaultProviderWhenItIsTheOnlyOne() {
    // given
    authentication.setMethod(AuthenticationMethod.OIDC);
    authentication.setProviders(null);
    defaultOidc.setGroupsClaim("groups");

    // then
    assertThat(authentication.isExternalGroupsEnabled()).isTrue();
    assertThat(authentication.isCamundaGroupsEnabled()).isFalse();
  }

  @Test
  void shouldManageAllGroupsInCamundaWithoutOidc() {
    // given
    authentication.setMethod(AuthenticationMethod.BASIC);
    defaultOidc.setGroupsClaim("groups");

    // then
    assertThat(authentication.isExternalGroupsEnabled()).isFalse();
    assertThat(authentication.isCamundaGroupsEnabled()).isTrue();
  }

  @Test
  void shouldNeedNoGroupToExistWhenAProviderInheritsAGroupsClaim() {
    // given
    authentication.setMethod(AuthenticationMethod.OIDC);
    defaultOidc.setClientId("client");
    defaultOidc.setGroupsClaim("groups");

    // then both providers read the claim, so Camunda manages no groups
    assertThat(authentication.isExternalGroupsEnabled()).isTrue();
    assertThat(authentication.isCamundaGroupsEnabled()).isFalse();
  }

  @Test
  void
      shouldManageAllGroupsInCamundaWithBasicAuthenticationAndAGroupsClaimOfAnAdditionalProvider() {
    // given
    authentication.setMethod(AuthenticationMethod.BASIC);
    partnerOidc.setGroupsClaim("groups");

    // then
    assertThat(authentication.isExternalGroupsEnabled()).isFalse();
    assertThat(authentication.isCamundaGroupsEnabled()).isTrue();
  }
}
