/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.authentication.pt.JwksTestServer;
import io.camunda.authentication.pt.PhysicalTenantAuthConfigurations;
import io.camunda.security.api.context.CamundaAuthenticationConverter;
import io.camunda.security.api.context.MembershipResolutionContextPropagator;
import io.camunda.security.core.port.in.OidcProviderConfigurationPort;
import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.converter.AdditionalJwkSetUrisByRegistrationId;
import io.camunda.security.spring.oidc.JWSKeySelectorFactory;
import io.camunda.security.spring.oidc.OidcAccessTokenDecoderFactory;
import io.camunda.security.spring.oidc.ScopedClientRegistrationFactory;
import io.camunda.security.spring.oidc.TokenValidatorFactory;
import io.camunda.spring.utils.PhysicalTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Drives the real {@code oidcUserAuthenticationConverter} bean to check that the access token of a
 * browser login is validated and sourced with the physical tenant's own OIDC settings. The access
 * token carries subject {@code alice}; the ID token carries {@code from-id-token}, so the converted
 * username shows which of the two the login used.
 */
class OidcOverrideBeansConfigurationPhysicalTenantAccessTokenTest {

  private static final String ID_TOKEN_USER = "from-id-token";
  private static final String ACCESS_TOKEN_USER = "alice";
  private static final String TENANT_OIDC_PREFIX =
      "camunda.physical-tenants.tenanta.security.authentication.oidc";

  private static JwksTestServer rootIdp;
  private static JwksTestServer tenantIdp;
  private static JwksTestServer extraIdp;
  private static JwksTestServer otherTenantIdp;

  private final OAuth2AuthorizedClientRepository authorizedClientRepository =
      mock(OAuth2AuthorizedClientRepository.class);

  @BeforeAll
  static void startIdps() throws Exception {
    rootIdp = JwksTestServer.start("root-kid");
    tenantIdp = JwksTestServer.start("tenant-kid");
    extraIdp = JwksTestServer.start("extra-kid");
    otherTenantIdp = JwksTestServer.start("other-tenant-kid");
  }

  @AfterAll
  static void stopIdps() {
    rootIdp.stop();
    tenantIdp.stop();
    extraIdp.stop();
    otherTenantIdp.stop();
  }

  @AfterEach
  void clearTenantContext() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void shouldFallBackToIdTokenWhenAccessTokenCarriesRootIssuerOnTenant() throws Exception {
    // given
    final var environment = tenantOverridingDefaultSlotIssuer();
    final var converter = converter(environment);
    bindPhysicalTenant("tenanta");
    final var token = JwksTestServer.signForIssuer(tenantIdp, rootIdp.issuerUri(), List.of());

    // when
    final var username = converter.convert(login(token, tenantIdp)).authenticatedUsername();

    // then
    assertThat(username).isEqualTo(ID_TOKEN_USER);
  }

  @Test
  void shouldNotReuseRootAccessTokenDecoderForTenantSharingTheRegistrationId() throws Exception {
    // given
    final var environment = tenantOverridingDefaultSlotIssuer();
    final var converter = converter(environment);
    final var rootToken = JwksTestServer.signForIssuer(rootIdp, rootIdp.issuerUri(), List.of());
    final var tenantToken =
        JwksTestServer.signForIssuer(tenantIdp, tenantIdp.issuerUri(), List.of());
    final var rootUsername = converter.convert(login(rootToken, rootIdp)).authenticatedUsername();

    // when
    bindPhysicalTenant("tenanta");
    final var tenantUsername =
        converter.convert(login(tenantToken, tenantIdp)).authenticatedUsername();

    // then
    assertThat(rootUsername).isEqualTo(ACCESS_TOKEN_USER);
    assertThat(tenantUsername).isEqualTo(ACCESS_TOKEN_USER);
  }

  @Test
  void shouldDecodeAccessTokenWithEachTenantsOwnIssuerWhenTenantsShareTheRegistrationId()
      throws Exception {
    // given two tenants overriding the default-slot issuer with different issuers
    final var environment = tenantOverridingDefaultSlotIssuer();
    environment.setProperty(
        "camunda.physical-tenants.tenantb.security.authentication.oidc.issuer-uri",
        otherTenantIdp.issuerUri());
    final var converter = converter(environment);
    final var tokenA = JwksTestServer.signForIssuer(tenantIdp, tenantIdp.issuerUri(), List.of());
    final var tokenB =
        JwksTestServer.signForIssuer(otherTenantIdp, otherTenantIdp.issuerUri(), List.of());
    final var tokenWithAIssuerForB =
        JwksTestServer.signForIssuer(otherTenantIdp, tenantIdp.issuerUri(), List.of());

    // when
    bindPhysicalTenant("tenanta");
    final var usernameOnA = converter.convert(login(tokenA, tenantIdp)).authenticatedUsername();
    bindPhysicalTenant("tenantb");
    final var usernameOnB =
        converter.convert(login(tokenB, otherTenantIdp)).authenticatedUsername();
    final var usernameOnBWithAIssuer =
        converter.convert(login(tokenWithAIssuerForB, otherTenantIdp)).authenticatedUsername();

    // then each tenant decodes its own access token, and rejects the other tenant's issuer
    assertThat(usernameOnA).isEqualTo(ACCESS_TOKEN_USER);
    assertThat(usernameOnB).isEqualTo(ACCESS_TOKEN_USER);
    assertThat(usernameOnBWithAIssuer).isEqualTo(ID_TOKEN_USER);
  }

  @Test
  void shouldPreferIdTokenClaimsOnlyForTheTenantThatEnablesIt() throws Exception {
    // given
    final var environment = rootOnly();
    environment.setProperty(TENANT_OIDC_PREFIX + ".prefer-id-token-claims", "true");
    final var converter = converter(environment);
    final var token = JwksTestServer.signForIssuer(rootIdp, rootIdp.issuerUri(), List.of());
    final var rootUsername = converter.convert(login(token, rootIdp)).authenticatedUsername();

    // when
    bindPhysicalTenant("tenanta");
    final var tenantUsername = converter.convert(login(token, rootIdp)).authenticatedUsername();

    // then
    assertThat(rootUsername).isEqualTo(ACCESS_TOKEN_USER);
    assertThat(tenantUsername).isEqualTo(ID_TOKEN_USER);
  }

  @Test
  void shouldHonourTenantOptOutOfPreferIdTokenClaimsEnabledAtRoot() throws Exception {
    // given
    final var environment = tenantOverridingDefaultSlotIssuer();
    environment.setProperty("camunda.security.authentication.oidc.prefer-id-token-claims", "true");
    environment.setProperty(TENANT_OIDC_PREFIX + ".prefer-id-token-claims", "false");
    final var converter = converter(environment);
    bindPhysicalTenant("tenanta");
    final var token = JwksTestServer.signForIssuer(tenantIdp, tenantIdp.issuerUri(), List.of());

    // when
    final var username = converter.convert(login(token, tenantIdp)).authenticatedUsername();

    // then
    assertThat(username).isEqualTo(ACCESS_TOKEN_USER);
  }

  @Test
  void shouldAcceptAccessTokenSignedByKeyFromTenantAdditionalJwkSet() throws Exception {
    // given
    final var environment = tenantOverridingDefaultSlotIssuer();
    environment.setProperty(
        TENANT_OIDC_PREFIX + ".additional-jwk-set-uris[0]", extraIdp.issuerUri() + "/jwks");
    final var converter = converter(environment);
    bindPhysicalTenant("tenanta");
    final var token = JwksTestServer.signForIssuer(extraIdp, tenantIdp.issuerUri(), List.of());

    // when
    final var username = converter.convert(login(token, tenantIdp)).authenticatedUsername();

    // then
    assertThat(username).isEqualTo(ACCESS_TOKEN_USER);
  }

  private static MockEnvironment rootOnly() {
    final var environment = new MockEnvironment();
    environment.setProperty("camunda.security.authentication.method", "oidc");
    environment.setProperty("camunda.security.authentication.oidc.client-id", "root-client");
    environment.setProperty("camunda.security.authentication.oidc.issuer-uri", rootIdp.issuerUri());
    return environment;
  }

  private static MockEnvironment tenantOverridingDefaultSlotIssuer() {
    final var environment = rootOnly();
    environment.setProperty(TENANT_OIDC_PREFIX + ".issuer-uri", tenantIdp.issuerUri());
    return environment;
  }

  private CamundaAuthenticationConverter<Authentication> converter(
      final MockEnvironment environment) {
    final var rootProviders =
        new ScopedClientRegistrationFactory()
            .flatten(PhysicalTenantAuthConfigurations.forPhysicalTenant("default", environment));
    final var port = mock(OidcProviderConfigurationPort.class);
    when(port.getOidcAuthenticationConfigurations()).thenReturn(rootProviders);
    final var rootValidators =
        new TokenValidatorFactory(rootProviders, Duration.ofSeconds(60), List.of());
    final var accessTokenDecoderFactory =
        new OidcAccessTokenDecoderFactory(new JWSKeySelectorFactory(), rootValidators);

    final var cslProperties = new CamundaSecurityLibraryProperties();
    cslProperties.getAuthentication().getOidc().setUsernameClaim("sub");
    final var configuration = new OidcOverrideBeansConfiguration(cslProperties);
    final var membershipPort = mock(MembershipPort.class);
    final var propagator = MembershipResolutionContextPropagator.identity();
    return configuration.oidcUserAuthenticationConverter(
        authorizedClientRepository,
        accessTokenDecoderFactory,
        configuration.tokenClaimsConverter(cslProperties, membershipPort, propagator),
        mock(HttpServletRequest.class),
        port,
        AdditionalJwkSetUrisByRegistrationId.empty(),
        membershipPort,
        propagator,
        environment,
        configuration.physicalTenantOidcSettings(environment, membershipPort, propagator));
  }

  private OAuth2AuthenticationToken login(final String accessToken, final JwksTestServer jwksIdp) {
    final var registration = registration(jwksIdp);
    final var client =
        new OAuth2AuthorizedClient(
            registration,
            "alice",
            new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                accessToken,
                Instant.now(),
                Instant.now().plusSeconds(60)));
    doReturn(client).when(authorizedClientRepository).loadAuthorizedClient(any(), any(), any());
    final var principal = mock(OidcUser.class);
    when(principal.getAttributes()).thenReturn(Map.of("sub", ID_TOKEN_USER));
    return new OAuth2AuthenticationToken(principal, List.of(), "oidc");
  }

  private static ClientRegistration registration(final JwksTestServer jwksIdp) {
    return ClientRegistration.withRegistrationId("oidc")
        .clientId("client-id")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
        .authorizationUri(tenantIdp.issuerUri() + "/auth")
        .tokenUri(tenantIdp.issuerUri() + "/token")
        .jwkSetUri(jwksIdp.issuerUri() + "/jwks")
        .build();
  }

  private static void bindPhysicalTenant(final String tenantId) {
    final var request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, tenantId);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }
}
