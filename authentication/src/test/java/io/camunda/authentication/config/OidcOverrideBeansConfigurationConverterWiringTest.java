/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.security.api.context.CamundaAuthenticationConverter;
import io.camunda.security.api.context.MembershipResolutionContextPropagator;
import io.camunda.security.core.authz.LazyTokenClaimsConverter;
import io.camunda.security.core.port.in.OidcProviderConfigurationPort;
import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.oidc.OidcAccessTokenDecoderFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Verifies the {@code oidcUserAuthenticationConverter} bean is wired so interactive login (issue
 * #57776) selects claim mapping by registration id and normalizes URI-valued identity claims. This
 * drives the real bean factory method (not the converter in isolation), so reverting the wiring
 * back to the plain root converter fails these tests.
 */
@ExtendWith(MockitoExtension.class)
class OidcOverrideBeansConfigurationConverterWiringTest {

  private static final String SCOPED_PREFIX =
      "camunda.physical-tenants.tenanta.security.authentication.providers.oidc.auth0";

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

  private CamundaAuthenticationConverter<Authentication> converter(
      final String rootUsernameClaim, final String scopedUsernameClaim) {
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
        MembershipResolutionContextPropagator.identity(),
        environment);
  }

  private static OAuth2AuthenticationToken login(
      final String registrationId, final Map<String, Object> attributes) {
    final var principal = mock(OidcUser.class);
    when(principal.getAttributes()).thenReturn(attributes);
    return new OAuth2AuthenticationToken(principal, List.of(), registrationId);
  }
}
