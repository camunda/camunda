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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.security.api.context.CamundaAuthenticationConverter;
import io.camunda.security.api.context.MembershipResolutionContextPropagator;
import io.camunda.security.core.port.in.OidcProviderConfigurationPort;
import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.security.spring.converter.AdditionalJwkSetUrisByRegistrationId;
import io.camunda.security.spring.oidc.OidcAccessTokenDecoderFactory;
import io.camunda.security.spring.oidc.ScopedClientRegistrationFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Drives the real {@code oidcUserAuthenticationConverter} bean to check that a browser login maps
 * claims with the settings of the physical tenant it happens on, also when several tenants (and the
 * root) share a registration id. The login carries one username claim per configuration, so the
 * converted username shows which configuration the login used.
 */
class OidcOverrideBeansConfigurationPhysicalTenantClaimMappingTest {

  private static final String ROOT_PREFIX = "camunda.security.authentication";
  private static final String TENANT_A_PREFIX =
      "camunda.physical-tenants.tenanta.security.authentication";
  private static final String TENANT_B_PREFIX =
      "camunda.physical-tenants.tenantb.security.authentication";
  private static final Map<String, Object> ATTRIBUTES =
      Map.of("root_user", "root-user", "user_a", "alice-a", "user_b", "bob-b");

  @AfterEach
  void clearTenantContext() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void shouldMapClaimsWithEachTenantsOwnSettingsForASharedNamedProvider() {
    // given
    final var environment = rootWithSharedNamedProvider();
    environment.setProperty(TENANT_A_PREFIX + ".providers.oidc.okta.username-claim", "user_a");
    environment.setProperty(TENANT_B_PREFIX + ".providers.oidc.okta.username-claim", "user_b");
    final var converter = converter(environment);

    // when
    bindPhysicalTenant("tenanta");
    final var usernameOnTenantA = converter.convert(login("okta")).authenticatedUsername();
    bindPhysicalTenant("tenantb");
    final var usernameOnTenantB = converter.convert(login("okta")).authenticatedUsername();

    // then
    assertThat(usernameOnTenantA).isEqualTo("alice-a");
    assertThat(usernameOnTenantB).isEqualTo("bob-b");
  }

  @Test
  void shouldKeepRootClaimMappingOutsideTenantsWhenATenantOverridesTheSharedProvider() {
    // given
    final var environment = rootWithSharedNamedProvider();
    environment.setProperty(TENANT_A_PREFIX + ".providers.oidc.okta.username-claim", "user_a");
    final var converter = converter(environment);

    // when
    final var username = converter.convert(login("okta")).authenticatedUsername();

    // then
    assertThat(username).isEqualTo("root-user");
  }

  @Test
  void shouldMapClaimsWithTenantOverrideOfTheDefaultSlot() {
    // given
    final var environment = rootWithSharedNamedProvider();
    environment.setProperty(ROOT_PREFIX + ".oidc.client-id", "root-client");
    environment.setProperty(ROOT_PREFIX + ".oidc.username-claim", "root_user");
    environment.setProperty(TENANT_A_PREFIX + ".oidc.username-claim", "user_a");
    final var converter = converter(environment);

    // when
    bindPhysicalTenant("tenanta");
    final var usernameOnTenant = converter.convert(login("oidc")).authenticatedUsername();
    RequestContextHolder.resetRequestAttributes();
    final var usernameOnRoot = converter.convert(login("oidc")).authenticatedUsername();

    // then
    assertThat(usernameOnTenant).isEqualTo("alice-a");
    assertThat(usernameOnRoot).isEqualTo("root-user");
  }

  @Test
  void shouldNormalizeUriIdentityClaimWithTheTenantsOwnSettingsForASharedNamedProvider() {
    // given tenant A maps the username from iss, which Spring exposes as a URI, while tenant B maps
    // it from a plain string claim
    final var environment = rootWithSharedNamedProvider();
    environment.setProperty(TENANT_A_PREFIX + ".providers.oidc.okta.username-claim", "iss");
    environment.setProperty(TENANT_B_PREFIX + ".providers.oidc.okta.username-claim", "user_b");
    final var converter = converter(environment);
    bindPhysicalTenant("tenanta");
    final var authentication =
        login("okta", Map.of("iss", URI.create("https://tenant-a-idp"), "user_b", "bob-b"));

    // when
    final var username = converter.convert(authentication).authenticatedUsername();

    // then the URI is normalized using tenant A's identity claims, not tenant B's
    assertThat(username).isEqualTo("https://tenant-a-idp");
  }

  @Test
  void shouldApplyTheDefaultTenantOverlayClaimToTheRootAndTheDefaultTenant() {
    // given the default tenant overlays the username claim of the default slot
    final var environment = rootWithSharedNamedProvider();
    environment.setProperty(ROOT_PREFIX + ".oidc.client-id", "root-client");
    environment.setProperty(ROOT_PREFIX + ".oidc.username-claim", "root_user");
    environment.setProperty(
        "camunda.physical-tenants.default.security.authentication.oidc.username-claim",
        "user_default");
    final var converter = converter(environment);
    final var attributes =
        Map.<String, Object>of("root_user", "root-user", "user_default", "default-user");

    // when
    final var usernameOutsideTenants =
        converter.convert(login("oidc", attributes)).authenticatedUsername();
    bindPhysicalTenant("default");
    final var usernameOnDefaultTenant =
        converter.convert(login("oidc", attributes)).authenticatedUsername();

    // then the overlay claim applies outside any tenant and on the default tenant
    assertThat(usernameOutsideTenants).isEqualTo("default-user");
    assertThat(usernameOnDefaultTenant).isEqualTo("default-user");
  }

  @Test
  void shouldRejectLoginForRegistrationNotResolvedForThePhysicalTenant() {
    // given
    final var environment = rootWithSharedNamedProvider();
    environment.setProperty(TENANT_A_PREFIX + ".providers.oidc.okta.username-claim", "user_a");
    final var converter = converter(environment);
    bindPhysicalTenant("tenanta");
    final var authentication = login("notconfigured");

    // when / then
    assertThatThrownBy(() -> converter.convert(authentication))
        .isInstanceOf(OAuth2AuthenticationException.class);
  }

  private static MockEnvironment rootWithSharedNamedProvider() {
    final var environment = new MockEnvironment();
    environment.setProperty(ROOT_PREFIX + ".method", "oidc");
    environment.setProperty(ROOT_PREFIX + ".providers.oidc.okta.client-id", "okta-client");
    environment.setProperty(ROOT_PREFIX + ".providers.oidc.okta.username-claim", "root_user");
    return environment;
  }

  private static CamundaAuthenticationConverter<Authentication> converter(
      final MockEnvironment environment) {
    final var cslProperties = unifiedSecurityProperties(environment);
    final var rootProviders =
        new ScopedClientRegistrationFactory().flatten(cslProperties.getAuthentication());
    final var port = mock(OidcProviderConfigurationPort.class);
    when(port.getOidcAuthenticationConfigurations()).thenReturn(rootProviders);
    final var membershipPort = mock(MembershipPort.class);
    final var propagator = MembershipResolutionContextPropagator.identity();
    final var configuration = new OidcOverrideBeansConfiguration(cslProperties);
    return configuration.oidcUserAuthenticationConverter(
        mock(OAuth2AuthorizedClientRepository.class),
        mock(OidcAccessTokenDecoderFactory.class),
        configuration.tokenClaimsConverter(cslProperties, membershipPort, propagator),
        mock(HttpServletRequest.class),
        port,
        AdditionalJwkSetUrisByRegistrationId.empty(),
        membershipPort,
        propagator,
        environment,
        configuration.physicalTenantOidcSettings(environment, membershipPort, propagator));
  }

  private static OAuth2AuthenticationToken login(final String registrationId) {
    return login(registrationId, ATTRIBUTES);
  }

  private static OAuth2AuthenticationToken login(
      final String registrationId, final Map<String, Object> attributes) {
    final var principal = mock(OidcUser.class);
    when(principal.getAttributes()).thenReturn(attributes);
    return new OAuth2AuthenticationToken(principal, List.of(), registrationId);
  }
}
