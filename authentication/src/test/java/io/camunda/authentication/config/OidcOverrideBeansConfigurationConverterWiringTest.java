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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.authentication.service.PhysicalTenantMembershipContextPropagator;
import io.camunda.security.api.context.CamundaAuthenticationConverter;
import io.camunda.security.api.context.MembershipResolutionContextPropagator;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.core.authz.LazyTokenClaimsConverter;
import io.camunda.security.core.port.in.OidcProviderConfigurationPort;
import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.oidc.OidcAccessTokenDecoderFactory;
import io.camunda.spring.utils.PhysicalTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Verifies the {@code oidcUserAuthenticationConverter} bean is wired so interactive login (issue
 * #57776) selects claim mapping by registration id and normalizes URI-valued identity claims. This
 * drives the real bean factory method (not the converter in isolation), so reverting the wiring
 * back to the plain root converter fails these tests.
 *
 * <p>Also checks that OIDC login wraps its lazy membership lists with the host's propagator, so the
 * lists still work after the request that created them has ended.
 */
@ExtendWith(MockitoExtension.class)
class OidcOverrideBeansConfigurationConverterWiringTest {

  private static final String SCOPED_PREFIX =
      "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.auth0";
  private static final String USERNAME_CLAIM = "preferred_username";

  @Mock private OAuth2AuthorizedClientRepository authorizedClientRepository;
  @Mock private OidcAccessTokenDecoderFactory accessTokenDecoderFactory;
  @Mock private HttpServletRequest request;
  @Mock private MembershipPort membershipPort;
  @Mock private OidcProviderConfigurationPort oidcProviderRepository;

  @Test
  void shouldSelectScopedConverterAndNormalizeUriClaimForScopedRegistration() {
    // given — root Keycloak uses preferred_username, scoped Auth0 uses iss (a URI-valued claim)
    final var converter = converter("preferred_username", "iss");
    final var authentication = login("auth0", Map.of("iss", URI.create("https://scoped-idp")));

    // when
    final var result = converter.convert(authentication);

    // then — the scoped iss converter runs and the URI iss is normalized to a string
    assertThat(result.authenticatedUsername()).isEqualTo("https://scoped-idp");
  }

  @Test
  void shouldNormalizeUriClaimForRootRegistrationToo() {
    // given — the root registration itself uses a URI-valued identity claim
    final var converter = converter("iss", "sub");
    final var authentication = login("oidc", Map.of("iss", URI.create("https://root-idp")));

    // when
    final var result = converter.convert(authentication);

    // then — the root iss is normalized so the default converter can resolve it
    assertThat(result.authenticatedUsername()).isEqualTo("https://root-idp");
  }

  @Test
  void shouldResolveMembershipAgainstTenantCapturedAtLoginAfterRequestScopeEnds() {
    // given a login that carries a physical tenant, converted with the real propagator
    final var loginRequest = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(loginRequest, "tenant-a");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(loginRequest));

    final Map<String, String> tenantSeenByLookup = recordTenantPerLookup();

    final var cslProperties = new CamundaSecurityLibraryProperties();
    cslProperties.getAuthentication().getOidc().setUsernameClaim(USERNAME_CLAIM);
    final var authentication =
        new OidcOverrideBeansConfiguration(cslProperties)
            .tokenClaimsConverter(
                cslProperties, membershipPort, new PhysicalTenantMembershipContextPropagator())
            .convert(Map.of(USERNAME_CLAIM, "alice"));

    // when the request has ended before any list is read, as happens when a stored session writes
    // the authentication out
    RequestContextHolder.resetRequestAttributes();

    // then every lookup uses the tenant from login time. Without the wrapping it would throw here.
    assertMembershipResolvedAgainstTenant(authentication, tenantSeenByLookup, "tenant-a");
  }

  @Test
  void shouldResolveScopedRegistrationMembershipAgainstTenantCapturedAtLogin() {
    // given a login on a physical tenant's own OIDC registration, converted with the real
    // propagator. This converter is built per registration id, separately from the root one above.
    final var loginRequest = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(loginRequest, "tenanta");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(loginRequest));

    final Map<String, String> tenantSeenByLookup = recordTenantPerLookup();
    final var authentication =
        converter(USERNAME_CLAIM, USERNAME_CLAIM, new PhysicalTenantMembershipContextPropagator())
            .convert(login("auth0", Map.of(USERNAME_CLAIM, "alice")));

    // when the request has ended before any list is read
    RequestContextHolder.resetRequestAttributes();

    // then the scoped converter's lists resolve against the tenant from login too
    assertMembershipResolvedAgainstTenant(authentication, tenantSeenByLookup, "tenanta");
  }

  @AfterEach
  void clearRequestScope() {
    // if a test fails early, the request it bound would leak into the next test
    RequestContextHolder.resetRequestAttributes();
  }

  /** Asserts all four lazy lists resolved, each lookup having observed {@code tenant}. */
  private static void assertMembershipResolvedAgainstTenant(
      final CamundaAuthentication authentication,
      final Map<String, String> tenantSeenByLookup,
      final String tenant) {
    assertThat(authentication.authenticatedMappingRuleIds()).containsExactly("mapping-rule-1");
    assertThat(authentication.authenticatedGroupIds()).containsExactly("group-1");
    assertThat(authentication.authenticatedRoleIds()).containsExactly("role-1");
    assertThat(authentication.authenticatedTenantIds()).containsExactly("tenant-1");
    assertThat(tenantSeenByLookup)
        .containsEntry("mappingRuleIds", tenant)
        .containsEntry("groupIds", tenant)
        .containsEntry("roleIds", tenant)
        .containsEntry("tenantIds", tenant);
  }

  /** Records the physical tenant each membership lookup observes, keyed by lookup name. */
  private Map<String, String> recordTenantPerLookup() {
    final Map<String, String> tenantSeenByLookup = new HashMap<>();
    when(membershipPort.mappingRuleIds(any()))
        .thenAnswer(
            invocation -> {
              tenantSeenByLookup.put("mappingRuleIds", PhysicalTenantContext.current());
              return List.of("mapping-rule-1");
            });
    when(membershipPort.groupIds(any()))
        .thenAnswer(
            invocation -> {
              tenantSeenByLookup.put("groupIds", PhysicalTenantContext.current());
              return List.of("group-1");
            });
    when(membershipPort.roleIds(any()))
        .thenAnswer(
            invocation -> {
              tenantSeenByLookup.put("roleIds", PhysicalTenantContext.current());
              return List.of("role-1");
            });
    when(membershipPort.tenantIds(any()))
        .thenAnswer(
            invocation -> {
              tenantSeenByLookup.put("tenantIds", PhysicalTenantContext.current());
              return List.of("tenant-1");
            });
    return tenantSeenByLookup;
  }

  private CamundaAuthenticationConverter<Authentication> converter(
      final String rootUsernameClaim, final String scopedUsernameClaim) {
    return converter(
        rootUsernameClaim, scopedUsernameClaim, MembershipResolutionContextPropagator.identity());
  }

  private CamundaAuthenticationConverter<Authentication> converter(
      final String rootUsernameClaim,
      final String scopedUsernameClaim,
      final MembershipResolutionContextPropagator propagator) {
    when(oidcProviderRepository.getOidcAuthenticationConfigurations()).thenReturn(Map.of());
    // authorizedClientRepository returns no client, so the converter falls back to ID-token
    // (principal) claims, which is the interactive-login path this test exercises.

    final var cslProperties = new CamundaSecurityLibraryProperties();
    cslProperties.getAuthentication().getOidc().setUsernameClaim(rootUsernameClaim);
    final var configuration = new OidcOverrideBeansConfiguration(cslProperties);

    final var defaultConverter =
        new LazyTokenClaimsConverter(
            rootUsernameClaim,
            null,
            false,
            membershipPort,
            MembershipResolutionContextPropagator.identity());

    final var environment = new MockEnvironment();
    environment.setProperty("camunda.security.authentication.method", "oidc");
    environment.setProperty(SCOPED_PREFIX + ".username-claim", scopedUsernameClaim);

    return configuration.oidcUserAuthenticationConverter(
        authorizedClientRepository,
        accessTokenDecoderFactory,
        defaultConverter,
        request,
        oidcProviderRepository,
        membershipPort,
        propagator,
        environment);
  }

  private static OAuth2AuthenticationToken login(
      final String registrationId, final Map<String, Object> attributes) {
    final var principal = mock(OidcUser.class);
    when(principal.getAttributes()).thenReturn(attributes);
    return new OAuth2AuthenticationToken(principal, List.of(), registrationId);
  }
}
