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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.core.authz.LazyTokenClaimsConverter;
import io.camunda.security.spring.oidc.OidcAccessTokenDecoderFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

@SuppressWarnings("unchecked")
class ProviderAwareOidcUserAuthenticationConverterTest {

  @Test
  void shouldUseProviderConverterAndNormalizeUriIdentityClaim() {
    // given
    final var defaultConverter = mock(LazyTokenClaimsConverter.class);
    final var providerConverter = mock(LazyTokenClaimsConverter.class);
    final var authentication = authentication("okta", Map.of("iss", URI.create("https://idp")));
    when(providerConverter.convert(org.mockito.ArgumentMatchers.any()))
        .thenReturn(CamundaAuthentication.of(builder -> builder.user("https://idp")));
    final var converter = converter(defaultConverter, Map.of("okta", providerConverter));

    // when
    final var result = converter.convert(authentication);

    // then
    final ArgumentCaptor<Map<String, Object>> claims = ArgumentCaptor.forClass(Map.class);
    verify(providerConverter).convert(claims.capture());
    verifyNoInteractions(defaultConverter);
    assertThat(claims.getValue()).containsEntry("iss", "https://idp");
    assertThat(result.authenticatedUsername()).isEqualTo("https://idp");
  }

  @Test
  void shouldUseDefaultConverterForRootRegistration() {
    // given
    final var defaultConverter = mock(LazyTokenClaimsConverter.class);
    final var providerConverter = mock(LazyTokenClaimsConverter.class);
    final var authentication = authentication("oidc", Map.of("preferred_username", "demo"));
    when(defaultConverter.convert(org.mockito.ArgumentMatchers.any()))
        .thenReturn(CamundaAuthentication.of(builder -> builder.user("demo")));
    final var converter = converter(defaultConverter, Map.of("okta", providerConverter));

    // when
    final var result = converter.convert(authentication);

    // then
    verify(defaultConverter).convert(Map.of("preferred_username", "demo"));
    verifyNoInteractions(providerConverter);
    assertThat(result.authenticatedUsername()).isEqualTo("demo");
  }

  private static ProviderAwareOidcUserAuthenticationConverter converter(
      final LazyTokenClaimsConverter defaultConverter,
      final Map<String, LazyTokenClaimsConverter> providerConverters) {
    return new ProviderAwareOidcUserAuthenticationConverter(
        mock(OAuth2AuthorizedClientRepository.class),
        mock(OidcAccessTokenDecoderFactory.class),
        defaultConverter,
        mock(HttpServletRequest.class),
        Map.of(),
        Map.of("okta", true),
        providerConverters,
        Map.of("okta", List.of("iss", "https://camunda.com/claims/client_id")));
  }

  private static OAuth2AuthenticationToken authentication(
      final String registrationId, final Map<String, Object> attributes) {
    final var principal = mock(OidcUser.class);
    when(principal.getAttributes()).thenReturn(attributes);
    return new OAuth2AuthenticationToken(principal, List.of(), registrationId);
  }
}
