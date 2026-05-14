/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import io.camunda.authentication.context.RequestedPhysicalTenant;
import io.camunda.authentication.service.MembershipService;
import io.camunda.security.configuration.PhysicalTenantConfiguration;
import io.camunda.security.oidc.OidcClaimsProvider;
import io.camunda.security.oidc.OidcUserInfoClient;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * End-to-end OIDC chain test for the physical-tenant flow. Exercises the bearer-token pipeline on
 * {@code oidcApiSecurity} ({@code BearerTokenAuthenticationFilter} → mocked {@link JwtDecoder} →
 * {@code PhysicalTenantJwtAuthenticationConverter}) plus the {@link
 * io.camunda.authentication.filters.RequestedPhysicalTenantFilter} that resolves {@code ptId} from
 * the URL.
 *
 * <p>PT-level authorization is the controllers' responsibility and is not exercised here.
 *
 * <p>The {@link JwtDecoder} is mocked so the converter sees real {@link Jwt} instances with
 * controlled {@code iss} and {@code groups} claims. The {@link ClientRegistrationRepository} is
 * replaced with a hand-built {@link InMemoryClientRegistrationRepository} so that the chain
 * wiring's iterable check passes and {@code OidcConfiguration}'s factory doesn't perform OIDC
 * discovery against the (fake) issuer URIs.
 */
@SpringBootTest(
    classes = {
      WebSecurityConfigTestContext.class,
      WebSecurityConfig.class,
      OidcApiSecurityPhysicalTenantChainTest.LocalConfig.class
    },
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.security.authentication.unprotected-api=false",
      "camunda.security.authentication.method=oidc",
      "camunda.security.authentication.providers.oidc.default.client-id=default-client",
      "camunda.security.authentication.providers.oidc.default.issuer-uri=http://idp.test/default",
      "camunda.security.authentication.providers.oidc.default.jwk-set-uri=http://idp.test/default/jwks",
      "camunda.security.authentication.providers.oidc.default.authorization-uri=http://idp.test/default/auth",
      "camunda.security.authentication.providers.oidc.default.token-uri=http://idp.test/default/token",
      "camunda.security.authentication.providers.oidc.provider-a.client-id=provider-a-client",
      "camunda.security.authentication.providers.oidc.provider-a.issuer-uri=http://idp.test/provider-a",
      "camunda.security.authentication.providers.oidc.provider-a.jwk-set-uri=http://idp.test/provider-a/jwks",
      "camunda.security.authentication.providers.oidc.provider-a.authorization-uri=http://idp.test/provider-a/auth",
      "camunda.security.authentication.providers.oidc.provider-a.token-uri=http://idp.test/provider-a/token",
      "camunda.physical-tenants[0].id=risk-production",
      "camunda.physical-tenants[0].idps[0]=default",
      "camunda.physical-tenants[0].idps[1]=provider-a",
      "camunda.physical-tenants[1].id=audit",
      "camunda.physical-tenants[1].idps[0]=default",
    })
public class OidcApiSecurityPhysicalTenantChainTest extends AbstractWebSecurityConfigTest {

  private static final String PT_RISK_URL = "/v2/physical-tenants/risk-production/foo";
  private static final String PT_AUDIT_URL = "/v2/physical-tenants/audit/foo";
  private static final String PT_UNKNOWN_URL = "/v2/physical-tenants/unknown/foo";
  private static final String DEFAULT_ISSUER = "http://idp.test/default";
  private static final String PROVIDER_A_ISSUER = "http://idp.test/provider-a";

  @MockitoBean JwtDecoder jwtDecoder;

  // OidcConfiguration's tokenClaimsConverter/oidcUserAuthenticationConverter depend on
  // MembershipService, which is not provided by WebSecurityConfigTestContext. Mock it so the
  // context loads; the bean is never invoked from the API chain we exercise here.
  @MockitoBean MembershipService membershipService;

  // OidcClaimsProvider and OidcUserInfoClient are wired by OidcConfiguration for the userinfo /
  // claims pipeline. Not exercised by the bearer-token API chain — mock them out.
  @MockitoBean OidcClaimsProvider oidcClaimsProvider;
  @MockitoBean OidcUserInfoClient oidcUserInfoClient;

  @Test
  void shouldReturn401OnPhysicalTenantUrlWithoutBearerToken() {
    final MvcTestResult result =
        mockMvcTester.get().uri("https://localhost" + PT_RISK_URL).exchange();

    assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void shouldReturn401WhenBearerTokenIssuerIsNotInPtAllowList() {
    // given — provider-a is NOT in audit's idps; token claims pt:audit
    when(jwtDecoder.decode("cross-pt"))
        .thenReturn(jwt(PROVIDER_A_ISSUER, "groups", List.of("pt:audit")));

    // when
    final MvcTestResult result =
        mockMvcTester
            .get()
            .headers(bearer("cross-pt"))
            .uri("https://localhost" + PT_AUDIT_URL)
            .exchange();

    // then — converter throws OAuth2AuthenticationException, surfaced as 401. This is the cross-PT
    // token-replay defence pinned end-to-end and is the only PT-related auth-time decision left
    // on the chain.
    assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void shouldReachControllerAndExposeResolvedPtWhenAuthIsValidAndPtIsKnown() {
    // given — token from an allow-listed issuer claiming pt:risk-production
    when(jwtDecoder.decode("good-token"))
        .thenReturn(jwt(DEFAULT_ISSUER, "groups", List.of("pt:risk-production")));

    // when
    final MvcTestResult result =
        mockMvcTester
            .get()
            .headers(bearer("good-token"))
            .uri("https://localhost" + PT_RISK_URL)
            .exchange();

    // then — auth passes, filter resolves PT and attaches the attribute, controller reads it back
    assertThat(result).hasStatusOk();
    assertThat(result).hasBodyTextEqualTo("PT risk-production resolved=risk-production");
  }

  @Test
  void shouldReturn404WhenPhysicalTenantUrlReferencesUnknownPt() {
    // given — valid token, but the URL targets a PT that is not configured
    when(jwtDecoder.decode("good-token"))
        .thenReturn(jwt(DEFAULT_ISSUER, "groups", List.of("pt:risk-production")));

    // when
    final MvcTestResult result =
        mockMvcTester
            .get()
            .headers(bearer("good-token"))
            .uri("https://localhost" + PT_UNKNOWN_URL)
            .exchange();

    // then — filter rejects before any controller runs
    assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void shouldNotRegressUnprotectedV2Endpoint() {
    // when — no token; /v2/license is in UNPROTECTED_API_PATHS
    final MvcTestResult result = mockMvcTester.get().uri("https://localhost/v2/license").exchange();

    // then — no auth required (404 only because no controller is registered; the gate did NOT
    // fire, which is what matters)
    assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
  }

  private static HttpHeaders bearer(final String token) {
    final HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    return headers;
  }

  private static Jwt jwt(final String issuer, final String claimName, final Object claimValue) {
    final Map<String, Object> headers = Map.of("alg", "RS256");
    final Map<String, Object> claims = new HashMap<>();
    claims.put("iss", issuer);
    claims.put("sub", "alice");
    claims.put(claimName, claimValue);
    return new Jwt("token-value", Instant.now(), Instant.now().plusSeconds(300), headers, claims);
  }

  @Configuration
  static class LocalConfig {

    /**
     * Replaces {@code OidcConfiguration#clientRegistrationRepository}, whose factory does OIDC
     * discovery (network call) against the configured issuer URIs and fails in tests. The chain
     * wiring iterates this bean, so it has to be a real {@link
     * InMemoryClientRegistrationRepository} rather than a mock.
     */
    @Bean
    @Primary
    public ClientRegistrationRepository clientRegistrationRepository() {
      return new InMemoryClientRegistrationRepository(
          stubRegistration("default", "http://idp.test/default"),
          stubRegistration("provider-a", "http://idp.test/provider-a"));
    }

    /**
     * Hand-built PT list so the converter has a tenants list to look up. In real deployments this
     * bean comes from {@code CamundaPhysicalTenantsConfiguration} in the {@code dist} module, which
     * is not on this test's classpath.
     */
    @Bean
    public List<PhysicalTenantConfiguration> physicalTenantConfigurations() {
      return List.of(
          buildTenant("risk-production", List.of("default", "provider-a")),
          buildTenant("audit", List.of("default")));
    }

    private static PhysicalTenantConfiguration buildTenant(
        final String id, final List<String> idps) {
      final var tenant = new PhysicalTenantConfiguration();
      tenant.setId(id);
      tenant.setIdps(idps);
      return tenant;
    }

    private static ClientRegistration stubRegistration(final String id, final String issuerUri) {
      return ClientRegistration.withRegistrationId(id)
          .clientId(id + "-client")
          .clientSecret("test-secret")
          .clientName(id)
          .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
          .redirectUri("/sso-callback")
          .scope("openid")
          .authorizationUri(issuerUri + "/auth")
          .tokenUri(issuerUri + "/token")
          .jwkSetUri(issuerUri + "/jwks")
          .issuerUri(issuerUri)
          .build();
    }

    @Bean
    PhysicalTenantTestController physicalTenantTestController() {
      return new PhysicalTenantTestController();
    }
  }

  @RestController
  static class PhysicalTenantTestController {
    @GetMapping("/v2/physical-tenants/{ptId}/foo")
    String dummyPhysicalTenantEndpoint(@PathVariable("ptId") final String ptId) {
      // also exposes the value the filter attached, so the test can verify the attribute
      // round-trip without coupling to a real authz check
      return "PT " + ptId + " resolved=" + RequestedPhysicalTenant.id().orElse("UNSET");
    }
  }
}
