/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static io.camunda.authentication.pt.PhysicalTenantTestSupport.bindPhysicalTenant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.authentication.config.OidcOverrideBeansConfiguration;
import io.camunda.security.api.context.CamundaAuthenticationConverter;
import io.camunda.security.api.context.MembershipResolutionContextPropagator;
import io.camunda.security.core.authz.LazyTokenClaimsConverter;
import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.converter.TokenClaimsConvertersByIssuer;
import io.camunda.security.spring.oidc.JWSKeySelectorFactory;
import io.camunda.security.spring.oidc.NoopOidcClaimsProvider;
import io.camunda.security.spring.oidc.OidcAccessTokenDecoderFactory;
import io.camunda.security.spring.oidc.ScopedClientRegistrationFactory;
import io.camunda.security.spring.oidc.ScopedJwtDecoderFactory;
import io.camunda.security.spring.oidc.ScopedOidcClaimsProviderFactory;
import io.camunda.security.spring.oidc.TokenValidatorFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Drives a bearer token through the real pipeline of a physical tenant: the tenant's scoped CSL
 * decoder verifies it against an in-JVM identity provider, and the real {@code
 * oidcTokenAuthenticationConverter} bean maps it with the tenant bound. The token carries one
 * username claim per tenant besides its {@code sub} ({@code alice}), so the converted username
 * shows which claim settings mapped it.
 *
 * <p>The decoder tags each token with the principal claims of the provider that verified it
 * (camunda-security-library ADR-0038). The tenant converter must map by that tag: its issuer map is
 * keyed by the configured issuer-uri, which can differ from the token's {@code iss} by a trailing
 * slash (#61896).
 */
class PhysicalTenantBearerTokenClaimsIT {

  private static final String ROOT_PREFIX = "camunda.security.authentication";
  private static final String TENANT_A_PREFIX =
      "camunda.physical-tenants.tenanta.security.authentication";
  private static final String TENANT_B_PREFIX =
      "camunda.physical-tenants.tenantb.security.authentication";
  private static final Map<String, Object> CLAIMS = Map.of("user_a", "anna", "user_b", "bob");

  private static JwksTestServer idp;

  private final MembershipPort membershipPort = mock(MembershipPort.class);
  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

  @BeforeAll
  static void startIdp() throws Exception {
    idp = JwksTestServer.start("pt-claims-kid");
  }

  @AfterAll
  static void stopIdp() {
    idp.stop();
  }

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
    httpClient.close();
    meterRegistry.close();
  }

  /**
   * The tenant's provider is configured with the identity provider's issuer plus a trailing slash,
   * as Microsoft Entra ID setups often are. Its tokens carry the issuer without the slash, and are
   * still mapped with the provider's own username claim.
   */
  @Test
  void shouldMapATokenWithItsProvidersClaimsWhenTheIssuerUriHasATrailingSlash() throws Exception {
    // given
    final var environment = tenantsOfOneProvider(idp.issuerUri() + "/");
    final var converter = converter(environment);
    final var token = token();

    // when
    final var jwt = decoderOf("tenanta", environment).decode(token);
    bindPhysicalTenant("tenanta");
    final var username = converter.convert(new JwtAuthenticationToken(jwt)).authenticatedUsername();

    // then - the provider's username-claim, not the tenant's default "sub" (alice)
    assertThat(username).isEqualTo("anna");
  }

  /**
   * Two tenants configure the same provider of one issuer with other username claims. Each tenant
   * maps the token with the claims of the provider its own decoder verified it with.
   */
  @Test
  void shouldMapATokenWithTheClaimsOfEachTenantsOwnProvider() throws Exception {
    // given
    final var environment = tenantsOfOneProvider(idp.issuerUri() + "/");
    final var converter = converter(environment);
    final var token = token();

    // when
    final var onA = decoderOf("tenanta", environment).decode(token);
    bindPhysicalTenant("tenanta");
    final var usernameOnA =
        converter.convert(new JwtAuthenticationToken(onA)).authenticatedUsername();
    final var onB = decoderOf("tenantb", environment).decode(token);
    bindPhysicalTenant("tenantb");
    final var usernameOnB =
        converter.convert(new JwtAuthenticationToken(onB)).authenticatedUsername();

    // then
    assertThat(usernameOnA).isEqualTo("anna");
    assertThat(usernameOnB).isEqualTo("bob");
  }

  /** Root provider {@code okta}, configured per tenant with its own username claim. */
  private static MockEnvironment tenantsOfOneProvider(final String tenantIssuerUri) {
    final var environment = new MockEnvironment();
    environment.setProperty(ROOT_PREFIX + ".method", "oidc");
    environment.setProperty(ROOT_PREFIX + ".providers.oidc.okta.client-id", "okta-client");
    environment.setProperty(ROOT_PREFIX + ".providers.oidc.okta.issuer-uri", idp.issuerUri());
    environment.setProperty(TENANT_A_PREFIX + ".providers.oidc.okta.issuer-uri", tenantIssuerUri);
    environment.setProperty(TENANT_A_PREFIX + ".providers.oidc.okta.username-claim", "user_a");
    environment.setProperty(TENANT_B_PREFIX + ".providers.oidc.okta.issuer-uri", tenantIssuerUri);
    environment.setProperty(TENANT_B_PREFIX + ".providers.oidc.okta.username-claim", "user_b");
    return environment;
  }

  private static String token() throws Exception {
    return JwksTestServer.signForIssuer(idp, idp.issuerUri(), List.of(), CLAIMS);
  }

  /** The tenant's scoped decoder, as the scoped API chain of the tenant builds it. */
  private static JwtDecoder decoderOf(final String tenantId, final MockEnvironment environment) {
    final var authentication =
        PhysicalTenantAuthConfigurations.forPhysicalTenant(tenantId, environment);
    return new ScopedJwtDecoderFactory(
            new ScopedClientRegistrationFactory(),
            new OidcAccessTokenDecoderFactory(
                new JWSKeySelectorFactory(),
                new TokenValidatorFactory(Map.of(), Duration.ofSeconds(60), List.of())))
        .buildIssuerAwareDecoder(authentication, "physical tenant '" + tenantId + "'");
  }

  /** The real {@code oidcTokenAuthenticationConverter} bean for {@code environment}. */
  private CamundaAuthenticationConverter<Authentication> converter(
      final MockEnvironment environment) {
    return new OidcOverrideBeansConfiguration(new CamundaSecurityLibraryProperties())
        .oidcTokenAuthenticationConverter(
            new LazyTokenClaimsConverter("sub", null, false, membershipPort),
            new NoopOidcClaimsProvider(),
            new DefaultListableBeanFactory().getBeanProvider(TokenClaimsConvertersByIssuer.class),
            membershipPort,
            MembershipResolutionContextPropagator.identity(),
            new ScopedOidcClaimsProviderFactory(
                new ScopedClientRegistrationFactory(),
                httpClient,
                new ObjectMapper(),
                meterRegistry),
            environment);
  }
}
