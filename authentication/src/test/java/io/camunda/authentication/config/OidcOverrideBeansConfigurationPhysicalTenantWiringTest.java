/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.security.api.context.CamundaAuthenticationConverter;
import io.camunda.security.api.context.MembershipResolutionContextPropagator;
import io.camunda.security.api.context.OidcClaimsProvider;
import io.camunda.security.core.authz.LazyTokenClaimsConverter;
import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.converter.TokenClaimsConvertersByIssuer;
import io.camunda.security.spring.oidc.ScopedOidcClaimsProviderFactory;
import io.camunda.spring.utils.PhysicalTenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Fast unit coverage for the physical-tenant branch of {@code oidcTokenAuthenticationConverter} /
 * {@code buildPhysicalTenantConverter} (camunda/camunda#64685): with physical tenants declared only
 * in a {@link MockEnvironment}, it asserts per-tenant bearer claim resolution directly, so a
 * regression in the flat-slot vs. {@code providers.oidc.*} issuer precedence or the {@code hasText}
 * guards is caught here rather than only by the 60s+ Testcontainers ITs. The companion {@link
 * OidcOverrideBeansConfigurationTokenConverterWiringTest} runs against an empty environment and
 * never reaches this branch.
 */
final class OidcOverrideBeansConfigurationPhysicalTenantWiringTest {

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_A_ISSUER = "https://tenanta.example";
  private static final String TENANT_A_PROVIDER_ISSUER = "https://tenanta-extra.example";

  private final MembershipPort membershipPort = mock(MembershipPort.class);
  private final ScopedOidcClaimsProviderFactory scopedOidcClaimsProviderFactory =
      mock(ScopedOidcClaimsProviderFactory.class);

  // The root/cluster-default converter resolves identity from root_user/root_client only.
  private final LazyTokenClaimsConverter rootConverter =
      new LazyTokenClaimsConverter(
          "root_user",
          "root_client",
          false,
          membershipPort,
          MembershipResolutionContextPropagator.identity());

  private final OidcOverrideBeansConfiguration configuration =
      new OidcOverrideBeansConfiguration(new CamundaSecurityLibraryProperties());

  @AfterEach
  void clearRequestContext() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void shouldBuildPhysicalTenantAwareConverterWhenPhysicalTenantsAreConfigured() {
    assertThat(buildConverter(environmentWithTenantA()))
        .isInstanceOf(PhysicalTenantAwareOidcTokenAuthenticationConverter.class);
  }

  @Test
  void shouldResolveATenantTokenWithTheTenantsOwnFlatSlotClaim() {
    final var converter = buildConverter(environmentWithTenantA());
    bindRequestForTenant(TENANT_A);

    // The token carries ONLY the tenant's username-claim, from the tenant's own issuer.
    final var result = converter.convert(jwt(TENANT_A_ISSUER, "tenanta_user", "alice"));

    assertThat(result.authenticatedUsername()).isEqualTo("alice");
  }

  @Test
  void shouldResolveATenantTokenFromItsNamedProviderWithThatProvidersClaim() {
    // Precedence within a tenant: a token from the named providers.oidc.* issuer resolves with that
    // provider's own claim, not the tenant's flat-slot claim — covering the per-issuer map built in
    // buildPhysicalTenantConverter from both the flat slot and the named providers.
    final var converter = buildConverter(environmentWithTenantA());
    bindRequestForTenant(TENANT_A);

    final var result = converter.convert(jwt(TENANT_A_PROVIDER_ISSUER, "extra_user", "bob"));

    assertThat(result.authenticatedUsername()).isEqualTo("bob");
  }

  @Test
  void shouldRejectOnTheClusterSurfaceATokenCarryingOnlyATenantsClaim() {
    // The exact #64685 inverse: the token the tenant accepts is rejected on the unprefixed cluster
    // surface (unstamped request -> default/root converter), proving the per-tenant converter uses
    // the tenant's claim config and NOT the root's.
    final var converter = buildConverter(environmentWithTenantA());
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));

    // The message names the ROOT claims (root_user/root_client), proving the cluster surface used
    // the root config, not the tenant's — CSL wraps the converter's IllegalArgumentException as an
    // OAuth2AuthenticationException on the way out.
    assertThatThrownBy(() -> converter.convert(jwt(TENANT_A_ISSUER, "tenanta_user", "alice")))
        .isInstanceOf(OAuth2AuthenticationException.class)
        .hasMessageContaining("root_user")
        .hasMessageContaining("root_client");
  }

  private CamundaAuthenticationConverter<Authentication> buildConverter(final MockEnvironment env) {
    when(scopedOidcClaimsProviderFactory.buildClaimsProvider(any(), any()))
        .thenReturn(passthrough());
    return configuration.oidcTokenAuthenticationConverter(
        rootConverter,
        passthrough(),
        emptyPerIssuer(),
        membershipPort,
        MembershipResolutionContextPropagator.identity(),
        scopedOidcClaimsProviderFactory,
        env);
  }

  private static MockEnvironment environmentWithTenantA() {
    final var env = new MockEnvironment();
    // Root / cluster-default config: identity from root_user/root_client.
    env.setProperty("camunda.security.authentication.method", "oidc");
    env.setProperty("camunda.security.authentication.oidc.issuer-uri", "https://root.example");
    env.setProperty("camunda.security.authentication.oidc.client-id", "root-client");
    env.setProperty("camunda.security.authentication.oidc.username-claim", "root_user");
    env.setProperty("camunda.security.authentication.oidc.client-id-claim", "root_client");
    // Physical tenant A flat slot: its own issuer and distinct claim.
    final var flat = "camunda.physical-tenants.tenanta.security.authentication.oidc.";
    env.setProperty(flat + "issuer-uri", TENANT_A_ISSUER);
    env.setProperty(flat + "client-id", "tenanta-client");
    env.setProperty(flat + "username-claim", "tenanta_user");
    env.setProperty(flat + "client-id-claim", "tenanta_client");
    // Physical tenant A named provider: a second issuer with its own distinct claim.
    final var named =
        "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.extra.";
    env.setProperty(named + "issuer-uri", TENANT_A_PROVIDER_ISSUER);
    env.setProperty(named + "client-id", "extra-client");
    env.setProperty(named + "username-claim", "extra_user");
    env.setProperty(named + "client-id-claim", "extra_client");
    return env;
  }

  private static JwtAuthenticationToken jwt(
      final String issuer, final String claim, final String value) {
    return new JwtAuthenticationToken(
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("iss", issuer)
            .claim(claim, value)
            .build());
  }

  /** A UserInfo augmentation no-op: the resolved claims are the JWT claims unchanged. */
  private static OidcClaimsProvider passthrough() {
    return (jwtClaims, tokenValue) -> jwtClaims;
  }

  @SuppressWarnings("unchecked")
  private static ObjectProvider<TokenClaimsConvertersByIssuer> emptyPerIssuer() {
    final var provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(null);
    return provider;
  }

  private static void bindRequestForTenant(final String tenantId) {
    final var request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, tenantId);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }
}
