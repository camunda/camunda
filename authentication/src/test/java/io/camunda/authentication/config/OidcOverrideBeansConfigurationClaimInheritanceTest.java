/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static io.camunda.authentication.pt.PhysicalTenantTestSupport.bindPhysicalTenant;
import static io.camunda.authentication.pt.PhysicalTenantTestSupport.unifiedSecurityProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.security.api.context.CamundaAuthenticationConverter;
import io.camunda.security.api.context.MembershipResolutionContextPropagator;
import io.camunda.security.api.context.OidcClaimsProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.core.port.in.OidcProviderConfigurationPort;
import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.converter.AdditionalJwkSetUrisByRegistrationId;
import io.camunda.security.spring.oidc.OidcAccessTokenDecoderFactory;
import io.camunda.security.spring.oidc.OidcBeansConfiguration;
import io.camunda.security.spring.oidc.ScopedClientRegistrationFactory;
import io.camunda.security.spring.oidc.ScopedOidcClaimsProviderFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * REST and browser-login regression tests for the scenarios of camunda/camunda#65633: a named
 * provider uses its own username, client-id and prefer-username claims, and for each claim that it
 * does not set, the claim of the default slot (or of the physical tenant's default slot). 8.9 used
 * the default slot for every token. The converters are built by the real configuration beans.
 */
final class OidcOverrideBeansConfigurationClaimInheritanceTest {

  private static final String ROOT = "camunda.security.authentication.";
  private static final String OKTA = ROOT + "providers.oidc.okta.";
  private static final String TENANT_A =
      "camunda.physical-tenants.tenanta.security.authentication.";
  private static final String OKTA_ISSUER = "https://okta.example.com";

  private static final Map<String, Object> USER =
      Map.of("iss", OKTA_ISSUER, "sub", "4f2a", "preferred_username", "alice", "azp", "web");
  private static final Map<String, Object> M2M =
      Map.of("iss", OKTA_ISSUER, "sub", "9c1b", "azp", "my-app");

  private final MembershipPort membershipPort = mock(MembershipPort.class);
  private final MembershipResolutionContextPropagator propagator =
      MembershipResolutionContextPropagator.identity();

  @AfterEach
  void clearTenantContext() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void shouldUseTheGlobalClaimsForAProviderWithoutClaims() {
    // given scenario 1: the claims are only global, the named provider sets none
    final var environment = globalClaims();

    // when / then
    assertUser(rest(environment, USER), "alice");
    assertClient(rest(environment, M2M), "my-app");
    assertUser(login(environment, USER), "alice");
  }

  @Test
  void shouldUseTheClaimsThatTheProviderSets() {
    // given scenario 2: the provider sets its own username claim
    final var environment = globalClaims();
    environment.setProperty(OKTA + "username-claim", "upn");
    final var token = Map.<String, Object>of("iss", OKTA_ISSUER, "sub", "1a2b", "upn", "bob");

    // when / then
    assertUser(rest(environment, token), "bob");
    assertUser(login(environment, token), "bob");
  }

  @Test
  void shouldResolveEachClaimOnItsOwn() {
    // given scenario 3: the provider sets only its username claim; client-id-claim and
    // prefer-username-claim are global
    final var environment = globalClaims();
    environment.setProperty(OKTA + "username-claim", "upn");
    final var token =
        Map.<String, Object>of("iss", OKTA_ISSUER, "sub", "1a2b", "upn", "bob", "azp", "web");

    // when / then the user token is a user, because prefer-username-claim is inherited
    assertUser(rest(environment, token), "bob");
    assertClient(rest(environment, M2M), "my-app");
    assertUser(login(environment, token), "bob");
    // and the same on a tenant that is assigned the provider
    environment.setProperty(TENANT_A + "providers.assigned[0]", "okta");
    bindPhysicalTenant("tenanta");
    assertUser(rest(environment, token), "bob");
    assertClient(rest(environment, M2M), "my-app");
    assertUser(login(environment, token), "bob");
  }

  @Test
  void shouldUseAProviderClaimAlsoWhenItIsTheDefaultValue() {
    // given scenario 4: the provider sets username-claim to sub on purpose
    final var environment = globalClaims();
    environment.setProperty(OKTA + "username-claim", "sub");
    final var token =
        Map.<String, Object>of("iss", OKTA_ISSUER, "sub", "7d3e", "preferred_username", "bob");

    // when / then
    assertUser(rest(environment, token), "7d3e");
    assertUser(login(environment, token), "7d3e");
  }

  @Test
  void shouldUseTheTenantClaimForAnAssignedProviderWithoutClaims() {
    // given scenario 5: the tenant overrides the global username claim, and is assigned only the
    // named provider, which sets no claims
    final var environment = globalClaims();
    environment.setProperty(TENANT_A + "oidc.username-claim", "upn");
    environment.setProperty(TENANT_A + "providers.assigned[0]", "okta");
    final var token =
        Map.<String, Object>of(
            "iss", OKTA_ISSUER, "sub", "00u1", "preferred_username", "carol", "upn", "carol@x");

    // when / then on the tenant, the tenant's claim applies
    bindPhysicalTenant("tenanta");
    assertUser(rest(environment, token), "carol@x");
    assertUser(login(environment, token), "carol@x");
    // and outside any tenant, the global claim
    RequestContextHolder.resetRequestAttributes();
    assertUser(rest(environment, token), "carol");
    assertUser(login(environment, token), "carol");
  }

  @Test
  void shouldKeepTheClaimsOfASingleProviderInTheDefaultSlot() {
    // given scenario 6: one IdP in the default slot, no named provider
    final var environment = new MockEnvironment();
    environment.setProperty(ROOT + "method", "oidc");
    environment.setProperty(ROOT + "oidc.client-id", "camunda");
    environment.setProperty(ROOT + "oidc.issuer-uri", OKTA_ISSUER);
    environment.setProperty(ROOT + "oidc.username-claim", "preferred_username");
    environment.setProperty(ROOT + "oidc.client-id-claim", "azp");
    environment.setProperty(ROOT + "oidc.prefer-username-claim", "true");

    // when / then
    assertUser(rest(environment, USER), "alice");
    assertClient(rest(environment, M2M), "my-app");
    assertUser(login(environment, USER, "oidc"), "alice");
  }

  /** The global claims as the multi-IdP docs describe them, and a named provider without claims. */
  private static MockEnvironment globalClaims() {
    final var environment = new MockEnvironment();
    environment.setProperty(ROOT + "method", "oidc");
    environment.setProperty(ROOT + "oidc.username-claim", "preferred_username");
    environment.setProperty(ROOT + "oidc.client-id-claim", "azp");
    environment.setProperty(ROOT + "oidc.prefer-username-claim", "true");
    environment.setProperty(OKTA + "client-id", "okta-client");
    environment.setProperty(OKTA + "issuer-uri", OKTA_ISSUER);
    return environment;
  }

  /** Converts a bearer token as the REST API does, outside or inside the bound tenant. */
  private CamundaAuthentication rest(
      final MockEnvironment environment, final Map<String, Object> claims) {
    final var properties = properties(environment);
    final var configuration = new OidcOverrideBeansConfiguration(properties);
    final var rootConverter =
        configuration.tokenClaimsConverter(properties, membershipPort, propagator);
    final var perIssuer =
        new OidcBeansConfiguration()
            .tokenClaimsConvertersByIssuer(
                properties, port(properties), rootConverter, membershipPort, provider(propagator));
    final var claimsProviderFactory = mock(ScopedOidcClaimsProviderFactory.class);
    when(claimsProviderFactory.buildClaimsProvider(any(), any())).thenReturn(passthrough());
    final CamundaAuthenticationConverter<Authentication> converter =
        configuration.oidcTokenAuthenticationConverter(
            rootConverter,
            passthrough(),
            provider(perIssuer),
            membershipPort,
            propagator,
            claimsProviderFactory,
            environment);
    final var jwt = Jwt.withTokenValue("token").header("alg", "none");
    claims.forEach(jwt::claim);
    return converter.convert(new JwtAuthenticationToken(jwt.build()));
  }

  private CamundaAuthentication login(
      final MockEnvironment environment, final Map<String, Object> claims) {
    return login(environment, claims, "okta");
  }

  /** Converts a browser login as the login callback does, outside or inside the bound tenant. */
  private CamundaAuthentication login(
      final MockEnvironment environment,
      final Map<String, Object> claims,
      final String registrationId) {
    final var properties = properties(environment);
    final var configuration = new OidcOverrideBeansConfiguration(properties);
    final var converter =
        configuration.oidcUserAuthenticationConverter(
            mock(OAuth2AuthorizedClientRepository.class),
            mock(OidcAccessTokenDecoderFactory.class),
            configuration.tokenClaimsConverter(properties, membershipPort, propagator),
            mock(HttpServletRequest.class),
            port(properties),
            AdditionalJwkSetUrisByRegistrationId.empty(),
            membershipPort,
            propagator,
            environment,
            configuration.physicalTenantOidcSettings(environment, membershipPort, propagator));
    final var principal = mock(OidcUser.class);
    when(principal.getAttributes()).thenReturn(new HashMap<>(claims));
    return converter.convert(new OAuth2AuthenticationToken(principal, List.of(), registrationId));
  }

  /**
   * The root properties as the application binds them: through the unification step when physical
   * tenants are configured, else bound directly.
   */
  private static CamundaSecurityLibraryProperties properties(final MockEnvironment environment) {
    final var hasTenants =
        environment.getPropertySources().stream()
            .anyMatch(
                source ->
                    source.getSource() instanceof final Map<?, ?> map
                        && map.keySet().stream()
                            .anyMatch(
                                key -> key.toString().startsWith("camunda.physical-tenants")));
    if (hasTenants) {
      return unifiedSecurityProperties(environment);
    }
    return Binder.get(environment)
        .bind("camunda.security", Bindable.ofInstance(new CamundaSecurityLibraryProperties()))
        .orElseGet(CamundaSecurityLibraryProperties::new);
  }

  private static OidcProviderConfigurationPort port(
      final CamundaSecurityLibraryProperties properties) {
    final var port = mock(OidcProviderConfigurationPort.class);
    when(port.getOidcAuthenticationConfigurations())
        .thenReturn(new ScopedClientRegistrationFactory().flatten(properties.getAuthentication()));
    return port;
  }

  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> provider(final T value) {
    final ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(value);
    when(provider.getIfAvailable(any())).thenReturn(value);
    return provider;
  }

  private static OidcClaimsProvider passthrough() {
    return (jwtClaims, tokenValue) -> jwtClaims;
  }

  private static void assertUser(final CamundaAuthentication authentication, final String name) {
    assertThat(authentication.authenticatedUsername()).isEqualTo(name);
    assertThat(authentication.authenticatedClientId()).isNull();
  }

  private static void assertClient(final CamundaAuthentication authentication, final String id) {
    assertThat(authentication.authenticatedClientId()).isEqualTo(id);
    assertThat(authentication.authenticatedUsername()).isNull();
  }
}
