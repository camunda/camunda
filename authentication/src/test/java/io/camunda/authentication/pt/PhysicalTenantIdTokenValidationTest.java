/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.authentication.config.OidcOverrideBeansConfiguration;
import io.camunda.security.api.context.MembershipResolutionContextPropagator;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.core.port.in.OidcProviderConfigurationPort;
import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.oidc.ScopedClientRegistrationFactory;
import io.camunda.security.spring.oidc.TokenValidatorFactory;
import io.camunda.spring.utils.PhysicalTenantContext;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class PhysicalTenantIdTokenValidationTest {

  private static JwksTestServer rootIdp;
  private static JwksTestServer tenantIdp;
  private static JwksTestServer otherTenantIdp;

  @BeforeAll
  static void startIdps() throws Exception {
    rootIdp = JwksTestServer.start("root-kid");
    tenantIdp = JwksTestServer.start("tenant-kid");
    otherTenantIdp = JwksTestServer.start("other-tenant-kid");
  }

  @AfterAll
  static void stopIdps() {
    rootIdp.stop();
    tenantIdp.stop();
    otherTenantIdp.stop();
  }

  @AfterEach
  void clearTenantContext() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void shouldAcceptRootIssuerTokenOutsideAnyPhysicalTenant() throws Exception {
    // given
    final var decoderFactory = decoderFactoryWithTenantOverridingDefaultSlotIssuer();

    // when
    final var decoder = decoderFactory.createDecoder(registrationFor("oidc", rootIdp));
    final var jwt =
        decoder.decode(JwksTestServer.signForIssuer(rootIdp, rootIdp.issuerUri(), List.of()));

    // then
    assertThat(jwt.getIssuer()).hasToString(rootIdp.issuerUri());
  }

  @Test
  void shouldRejectOtherIssuerTokenOutsideAnyPhysicalTenant() throws Exception {
    // given
    final var decoderFactory = decoderFactoryWithTenantOverridingDefaultSlotIssuer();
    final var decoder = decoderFactory.createDecoder(registrationFor("oidc", rootIdp));
    final var token = JwksTestServer.signForIssuer(rootIdp, tenantIdp.issuerUri(), List.of());

    // when / then
    assertThatThrownBy(() -> decoder.decode(token))
        .isInstanceOf(JwtValidationException.class)
        .hasMessageContaining("iss claim");
  }

  @Test
  void shouldAcceptTenantIssuerWhenTenantOverridesDefaultSlotIssuer() throws Exception {
    // given
    final var decoderFactory = decoderFactoryWithTenantOverridingDefaultSlotIssuer();
    bindPhysicalTenant("tenanta");

    // when
    final var decoder = decoderFactory.createDecoder(registrationFor("oidc", tenantIdp));
    final var jwt =
        decoder.decode(JwksTestServer.signForIssuer(tenantIdp, tenantIdp.issuerUri(), List.of()));

    // then
    assertThat(jwt.getIssuer()).hasToString(tenantIdp.issuerUri());
  }

  @Test
  void shouldRejectRootIssuerWhenTenantOverridesDefaultSlotIssuer() throws Exception {
    // given
    final var decoderFactory = decoderFactoryWithTenantOverridingDefaultSlotIssuer();
    bindPhysicalTenant("tenanta");
    final var decoder = decoderFactory.createDecoder(registrationFor("oidc", tenantIdp));
    final var token = JwksTestServer.signForIssuer(tenantIdp, rootIdp.issuerUri(), List.of());

    // when / then
    assertThatThrownBy(() -> decoder.decode(token))
        .isInstanceOf(JwtValidationException.class)
        .hasMessageContaining("iss claim");
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
        .isInstanceOf(JwtValidationException.class)
        .hasMessageContaining("iss claim");
    assertThatThrownBy(() -> decoderA.decode(tokenWithBIssuerForA))
        .isInstanceOf(JwtValidationException.class)
        .hasMessageContaining("iss claim");
  }

  @Test
  void shouldRejectWrongIssuerForTenantOnlyProvider() throws Exception {
    // given
    final var environment = rootOnlyEnvironment();
    final var tenantOnly =
        "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.idpa";
    environment.setProperty(tenantOnly + ".client-id", "client-a");
    environment.setProperty(tenantOnly + ".issuer-uri", tenantIdp.issuerUri());
    final var decoderFactory = decoderFactory(environment);
    bindPhysicalTenant("tenanta");
    final var decoder = decoderFactory.createDecoder(registrationFor("idpa", tenantIdp));
    final var token = JwksTestServer.signForIssuer(tenantIdp, rootIdp.issuerUri(), List.of());

    // when / then
    assertThatThrownBy(() -> decoder.decode(token))
        .isInstanceOf(JwtValidationException.class)
        .hasMessageContaining("iss claim");
  }

  @Test
  void shouldAcceptRightIssuerForTenantOnlyProvider() throws Exception {
    // given
    final var environment = rootOnlyEnvironment();
    final var tenantOnly =
        "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.idpa";
    environment.setProperty(tenantOnly + ".client-id", "client-a");
    environment.setProperty(tenantOnly + ".issuer-uri", tenantIdp.issuerUri());
    final var decoderFactory = decoderFactory(environment);
    bindPhysicalTenant("tenanta");

    // when
    final var decoder = decoderFactory.createDecoder(registrationFor("idpa", tenantIdp));
    final var jwt =
        decoder.decode(JwksTestServer.signForIssuer(tenantIdp, tenantIdp.issuerUri(), List.of()));

    // then
    assertThat(jwt.getIssuer()).hasToString(tenantIdp.issuerUri());
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

  @Test
  void shouldRejectLoginForPhysicalTenantWithoutResolvedConfiguration() {
    // given
    final var decoderFactory = decoderFactoryWithTenantOverridingDefaultSlotIssuer();
    bindPhysicalTenant("unknowntenant");

    // when / then
    assertThatThrownBy(() -> decoderFactory.createDecoder(registrationFor("oidc", rootIdp)))
        .isInstanceOf(OAuth2AuthenticationException.class);
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
    final Map<String, OidcConfiguration> rootProviders =
        new ScopedClientRegistrationFactory()
            .flatten(PhysicalTenantAuthConfigurations.forPhysicalTenant("default", environment));
    final var port = mock(OidcProviderConfigurationPort.class);
    when(port.getOidcAuthenticationConfigurations()).thenReturn(rootProviders);
    final var rootValidators =
        new TokenValidatorFactory(rootProviders, Duration.ofSeconds(60), List.of());
    final var configuration =
        new OidcOverrideBeansConfiguration(new CamundaSecurityLibraryProperties());
    final var settings =
        configuration.physicalTenantOidcSettings(
            environment,
            mock(MembershipPort.class),
            MembershipResolutionContextPropagator.identity());
    return configuration.idTokenDecoderFactory(rootValidators, port, settings);
  }

  private static void bindPhysicalTenant(final String tenantId) {
    final var request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, tenantId);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
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
