/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static io.camunda.authentication.pt.PhysicalTenantTestSupport.bindPhysicalTenant;
import static io.camunda.authentication.pt.PhysicalTenantTestSupport.unifiedSecurityProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.authentication.config.OidcOverrideBeansConfiguration;
import io.camunda.security.api.context.MembershipResolutionContextPropagator;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.core.port.in.OidcProviderConfigurationPort;
import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.security.spring.oidc.ScopedClientRegistrationFactory;
import io.camunda.security.spring.oidc.TokenValidatorFactory;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.web.context.request.RequestContextHolder;

class PhysicalTenantIdTokenValidationTest {

  private static JwksTestServer rootIdp;
  private static JwksTestServer tenantIdp;
  private static JwksTestServer otherTenantIdp;
  private static JwksTestServer ecIdp;

  @BeforeAll
  static void startIdps() throws Exception {
    rootIdp = JwksTestServer.start("root-kid");
    tenantIdp = JwksTestServer.start("tenant-kid");
    otherTenantIdp = JwksTestServer.start("other-tenant-kid");
    ecIdp = JwksTestServer.startEc("ec-kid");
  }

  @AfterAll
  static void stopIdps() {
    rootIdp.stop();
    tenantIdp.stop();
    otherTenantIdp.stop();
    ecIdp.stop();
  }

  @AfterEach
  void clearTenantContext() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void shouldKeepValidatingWithRootIssuerOutsideAnyPhysicalTenant() throws Exception {
    // given
    final var decoderFactory = decoderFactoryWithTenantOverridingDefaultSlotIssuer();
    final var decoder = decoderFactory.createDecoder(registrationFor("oidc", rootIdp));
    final var otherIssuerToken =
        JwksTestServer.signForIssuer(rootIdp, tenantIdp.issuerUri(), List.of());

    // when
    final var jwt =
        decoder.decode(JwksTestServer.signForIssuer(rootIdp, rootIdp.issuerUri(), List.of()));

    // then
    assertThat(jwt.getIssuer()).hasToString(rootIdp.issuerUri());
    assertThatThrownBy(() -> decoder.decode(otherIssuerToken))
        .isInstanceOf(JwtValidationException.class);
  }

  @Test
  void shouldValidateEachTenantWithItsOwnIssuerWhenTenantsShareARegistrationId() throws Exception {
    // given two tenants overriding the default-slot issuer with different issuers
    final var environment = rootOnlyEnvironment();
    environment.setProperty(
        "camunda.physical-tenants.tenanta.security.authentication.oidc.issuer-uri",
        tenantIdp.issuerUri());
    environment.setProperty(
        "camunda.physical-tenants.tenantb.security.authentication.oidc.issuer-uri",
        otherTenantIdp.issuerUri());
    final var decoderFactory = decoderFactory(environment);

    // when
    bindPhysicalTenant("tenanta");
    final var decoderA = decoderFactory.createDecoder(registrationFor("oidc", tenantIdp));
    bindPhysicalTenant("tenantb");
    final var decoderB = decoderFactory.createDecoder(registrationFor("oidc", otherTenantIdp));

    // then each tenant accepts its own issuer and rejects the other tenant's
    final var tokenA = JwksTestServer.signForIssuer(tenantIdp, tenantIdp.issuerUri(), List.of());
    final var tokenB =
        JwksTestServer.signForIssuer(otherTenantIdp, otherTenantIdp.issuerUri(), List.of());
    assertThat(decoderA.decode(tokenA).getIssuer()).hasToString(tenantIdp.issuerUri());
    assertThat(decoderB.decode(tokenB).getIssuer()).hasToString(otherTenantIdp.issuerUri());
    final var tokenWithAIssuerForB =
        JwksTestServer.signForIssuer(otherTenantIdp, tenantIdp.issuerUri(), List.of());
    final var tokenWithBIssuerForA =
        JwksTestServer.signForIssuer(tenantIdp, otherTenantIdp.issuerUri(), List.of());
    assertThatThrownBy(() -> decoderB.decode(tokenWithAIssuerForB))
        .isInstanceOf(JwtValidationException.class);
    assertThatThrownBy(() -> decoderA.decode(tokenWithBIssuerForA))
        .isInstanceOf(JwtValidationException.class);
    final var tokenWithRootIssuerForA =
        JwksTestServer.signForIssuer(tenantIdp, rootIdp.issuerUri(), List.of());
    assertThatThrownBy(() -> decoderA.decode(tokenWithRootIssuerForA))
        .isInstanceOf(JwtValidationException.class);
  }

  @Test
  void shouldApplyTheDefaultTenantOverlayToTheRootAndTheDefaultTenant() throws Exception {
    // given the default tenant overlays the issuer of the default slot
    final var environment = rootOnlyEnvironment();
    environment.setProperty(
        "camunda.physical-tenants.default.security.authentication.oidc.issuer-uri",
        tenantIdp.issuerUri());
    final var decoderFactory = decoderFactory(environment);
    final var overlayIssuerToken =
        JwksTestServer.signForIssuer(tenantIdp, tenantIdp.issuerUri(), List.of());
    final var rootIssuerToken =
        JwksTestServer.signForIssuer(tenantIdp, rootIdp.issuerUri(), List.of());

    // when
    final var rootDecoder = decoderFactory.createDecoder(registrationFor("oidc", tenantIdp));
    bindPhysicalTenant("default");
    final var defaultTenantDecoder =
        decoderFactory.createDecoder(registrationFor("oidc", tenantIdp));

    // then outside any tenant and on the default tenant, the overlay issuer replaces the root one
    for (final var decoder : List.of(rootDecoder, defaultTenantDecoder)) {
      assertThat(decoder.decode(overlayIssuerToken).getIssuer()).hasToString(tenantIdp.issuerUri());
      assertThatThrownBy(() -> decoder.decode(rootIssuerToken))
          .isInstanceOf(JwtValidationException.class);
    }
  }

  @Test
  void shouldUseTenantIdTokenAlgorithmForASharedRegistrationIdWhileTheRootKeepsRs256()
      throws Exception {
    // given a tenant that sets ES256 for the default slot, and a token signed with an EC key
    final var environment = rootOnlyEnvironment();
    environment.setProperty(
        "camunda.physical-tenants.tenanta.security.authentication.oidc.id-token-algorithm",
        "ES256");
    final var decoderFactory = decoderFactory(environment);
    final var rootDecoder = decoderFactory.createDecoder(registrationFor("oidc", ecIdp));
    bindPhysicalTenant("tenanta");
    final var tenantDecoder = decoderFactory.createDecoder(registrationFor("oidc", ecIdp));
    final var token = JwksTestServer.signForIssuer(ecIdp, rootIdp.issuerUri(), List.of());

    // when
    final var jwt = tenantDecoder.decode(token);

    // then the tenant accepts the token, while the root, which keeps RS256, rejects the same token
    assertThat(jwt.getIssuer()).hasToString(rootIdp.issuerUri());
    assertThatThrownBy(() -> rootDecoder.decode(token))
        .isInstanceOf(JwtException.class)
        .hasMessageContaining("algorithm");
  }

  @Test
  void shouldCheckIssuerForTenantOnlyProvider() throws Exception {
    // given
    final var environment = rootOnlyEnvironment();
    final var tenantOnly =
        "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.idpa";
    environment.setProperty(tenantOnly + ".client-id", "client-a");
    environment.setProperty(tenantOnly + ".issuer-uri", tenantIdp.issuerUri());
    final var decoderFactory = decoderFactory(environment);
    bindPhysicalTenant("tenanta");
    final var decoder = decoderFactory.createDecoder(registrationFor("idpa", tenantIdp));
    final var wrongIssuerToken =
        JwksTestServer.signForIssuer(tenantIdp, rootIdp.issuerUri(), List.of());

    // when
    final var jwt =
        decoder.decode(JwksTestServer.signForIssuer(tenantIdp, tenantIdp.issuerUri(), List.of()));

    // then
    assertThat(jwt.getIssuer()).hasToString(tenantIdp.issuerUri());
    assertThatThrownBy(() -> decoder.decode(wrongIssuerToken))
        .isInstanceOf(JwtValidationException.class);
  }

  @Test
  void shouldNotCheckIssuerForTenantProviderWithoutConfiguredIssuer() throws Exception {
    // given a tenant provider declared without issuer-uri, as a root provider can be
    final var environment = rootOnlyEnvironment();
    final var tenantOnly =
        "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.manual";
    environment.setProperty(tenantOnly + ".client-id", "client-manual");
    final var decoderFactory = decoderFactory(environment);
    bindPhysicalTenant("tenanta");

    // when
    final var decoder = decoderFactory.createDecoder(registrationFor("manual", tenantIdp));
    final var jwt =
        decoder.decode(JwksTestServer.signForIssuer(tenantIdp, rootIdp.issuerUri(), List.of()));

    // then login behaves as for a root provider without issuer-uri: signature is checked, iss is
    // not
    assertThat(jwt.getIssuer()).hasToString(rootIdp.issuerUri());
  }

  private static JwtDecoderFactory<ClientRegistration>
      decoderFactoryWithTenantOverridingDefaultSlotIssuer() {
    final var environment = rootOnlyEnvironment();
    environment.setProperty(
        "camunda.physical-tenants.tenanta.security.authentication.oidc.issuer-uri",
        tenantIdp.issuerUri());
    return decoderFactory(environment);
  }

  private static MockEnvironment rootOnlyEnvironment() {
    final var environment = new MockEnvironment();
    environment.setProperty("camunda.security.authentication.method", "oidc");
    environment.setProperty("camunda.security.authentication.oidc.client-id", "root-client");
    environment.setProperty("camunda.security.authentication.oidc.issuer-uri", rootIdp.issuerUri());
    return environment;
  }

  private static JwtDecoderFactory<ClientRegistration> decoderFactory(
      final MockEnvironment environment) {
    final var cslProperties = unifiedSecurityProperties(environment);
    final Map<String, OidcConfiguration> rootProviders =
        new ScopedClientRegistrationFactory().flatten(cslProperties.getAuthentication());
    final var port = mock(OidcProviderConfigurationPort.class);
    when(port.getOidcAuthenticationConfigurations()).thenReturn(rootProviders);
    final var rootValidators =
        new TokenValidatorFactory(rootProviders, Duration.ofSeconds(60), List.of());
    final var configuration = new OidcOverrideBeansConfiguration(cslProperties);
    final var settings =
        configuration.physicalTenantOidcSettings(
            environment,
            mock(MembershipPort.class),
            MembershipResolutionContextPropagator.identity());
    return configuration.idTokenDecoderFactory(rootValidators, port, settings);
  }

  private static ClientRegistration registrationFor(
      final String registrationId, final JwksTestServer idp) {
    return ClientRegistration.withRegistrationId(registrationId)
        .clientId("client-id")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
        .authorizationUri(idp.issuerUri() + "/auth")
        .tokenUri(idp.issuerUri() + "/token")
        .jwkSetUri(idp.issuerUri() + "/jwks")
        .build();
  }
}
