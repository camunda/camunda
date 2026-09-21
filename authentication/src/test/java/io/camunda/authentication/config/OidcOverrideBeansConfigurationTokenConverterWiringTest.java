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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.security.api.context.OidcClaimsProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.core.authz.LazyTokenClaimsConverter;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.converter.TokenClaimsConvertersByIssuer;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Verifies the {@code oidcTokenAuthenticationConverter} bean (issue #61920) passes the per-issuer
 * {@link TokenClaimsConvertersByIssuer} through to the real converter, so a bearer token from a
 * non-default provider (e.g. a BYOIDP additional IdP) is converted using that provider's own {@code
 * usernameClaim}/{@code clientIdClaim}/{@code preferUsernameClaim}, not the primary provider's.
 * This drives the real bean factory method, so reverting the wiring back to the plain two-argument
 * constructor fails these tests.
 */
@ExtendWith(MockitoExtension.class)
class OidcOverrideBeansConfigurationTokenConverterWiringTest {

  private static final String ENTRA_ISSUER = "https://entra.example.com";

  @Mock private LazyTokenClaimsConverter defaultConverter;
  @Mock private LazyTokenClaimsConverter entraConverter;
  @Mock private OidcClaimsProvider claimsProvider;

  private final OidcOverrideBeansConfiguration configuration =
      new OidcOverrideBeansConfiguration(new CamundaSecurityLibraryProperties());

  @Test
  void shouldUseIssuerSpecificConverterWhenTheProviderIsWired() {
    // given a per-issuer map wired for the additional provider's issuer
    final var converter =
        configuration.oidcTokenAuthenticationConverter(
            defaultConverter, claimsProvider, converterMapProvider(ENTRA_ISSUER, entraConverter));
    final var jwt = jwtWithIssuer(ENTRA_ISSUER);
    when(claimsProvider.claimsFor(jwt.getClaims(), jwt.getTokenValue()))
        .thenReturn(jwt.getClaims());
    final var expected = CamundaAuthentication.of(b -> b.user("alice"));
    when(entraConverter.convert(jwt.getClaims())).thenReturn(expected);

    // when converting a token from that provider
    final var result = converter.convert(new JwtAuthenticationToken(jwt));

    // then the additional provider's own converter resolved it, not the primary one
    assertThat(result).isSameAs(expected);
    verifyNoInteractions(defaultConverter);
  }

  @Test
  void shouldResolveEachIssuersOwnConverterIndependentlyFromATwoEntryMap() {
    // given a per-issuer map holding two distinct providers, each with its own converter
    final var auth0Issuer = "https://auth0.example.com";
    final var auth0Converter = mock(LazyTokenClaimsConverter.class);
    final var converter =
        configuration.oidcTokenAuthenticationConverter(
            defaultConverter,
            claimsProvider,
            converterMapProvider(
                Map.of(ENTRA_ISSUER, entraConverter, auth0Issuer, auth0Converter)));

    final var entraJwt = jwtWithIssuer(ENTRA_ISSUER);
    when(claimsProvider.claimsFor(entraJwt.getClaims(), entraJwt.getTokenValue()))
        .thenReturn(entraJwt.getClaims());
    final var entraExpected = CamundaAuthentication.of(b -> b.user("entra-alice"));
    when(entraConverter.convert(entraJwt.getClaims())).thenReturn(entraExpected);

    final var auth0Jwt = jwtWithIssuer(auth0Issuer);
    when(claimsProvider.claimsFor(auth0Jwt.getClaims(), auth0Jwt.getTokenValue()))
        .thenReturn(auth0Jwt.getClaims());
    final var auth0Expected = CamundaAuthentication.of(b -> b.user("auth0-bob"));
    when(auth0Converter.convert(auth0Jwt.getClaims())).thenReturn(auth0Expected);

    // when converting a token from each provider
    final var entraResult = converter.convert(new JwtAuthenticationToken(entraJwt));
    final var auth0Result = converter.convert(new JwtAuthenticationToken(auth0Jwt));

    // then each resolves to its own converter, not the other's or the map's only other entry
    assertThat(entraResult).isSameAs(entraExpected);
    assertThat(auth0Result).isSameAs(auth0Expected);
  }

  @Test
  void shouldFallBackToDefaultConverterForAnUnmappedIssuer() {
    // given a per-issuer map that does not cover the token's issuer
    final var converter =
        configuration.oidcTokenAuthenticationConverter(
            defaultConverter, claimsProvider, converterMapProvider(ENTRA_ISSUER, entraConverter));
    final var jwt = jwtWithIssuer("https://auth0.example.com");
    when(claimsProvider.claimsFor(jwt.getClaims(), jwt.getTokenValue()))
        .thenReturn(jwt.getClaims());
    final var expected = CamundaAuthentication.of(b -> b.user("alice"));
    when(defaultConverter.convert(jwt.getClaims())).thenReturn(expected);

    // when converting a token from an unrecognized issuer
    final var result = converter.convert(new JwtAuthenticationToken(jwt));

    // then it falls back to the primary provider's converter, unchanged from before the fix
    assertThat(result).isSameAs(expected);
    verifyNoInteractions(entraConverter);
  }

  @Test
  void shouldFallBackToDefaultConverterWhenNoPerIssuerMapIsAvailable() {
    // given no TokenClaimsConvertersByIssuer bean at all (e.g. MembershipPort absent), matching
    // this class's own two-argument constructor call before this fix
    final var converter =
        configuration.oidcTokenAuthenticationConverter(
            defaultConverter, claimsProvider, emptyConverterMapProvider());
    final var jwt = jwtWithIssuer(ENTRA_ISSUER);
    when(claimsProvider.claimsFor(jwt.getClaims(), jwt.getTokenValue()))
        .thenReturn(jwt.getClaims());
    final var expected = CamundaAuthentication.of(b -> b.user("alice"));
    when(defaultConverter.convert(jwt.getClaims())).thenReturn(expected);

    // when converting any token
    final var result = converter.convert(new JwtAuthenticationToken(jwt));

    // then behaviour is unchanged: the single default converter still runs
    assertThat(result).isSameAs(expected);
  }

  private static Jwt jwtWithIssuer(final String issuer) {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .claim("iss", issuer)
        .claim("sub", "alice")
        .build();
  }

  private static ObjectProvider<TokenClaimsConvertersByIssuer> converterMapProvider(
      final String issuer, final LazyTokenClaimsConverter converter) {
    return converterMapProvider(Map.of(issuer, converter));
  }

  private static ObjectProvider<TokenClaimsConvertersByIssuer> converterMapProvider(
      final Map<String, LazyTokenClaimsConverter> byIssuer) {
    return providerReturning(new TokenClaimsConvertersByIssuer(byIssuer));
  }

  private static ObjectProvider<TokenClaimsConvertersByIssuer> emptyConverterMapProvider() {
    return providerReturning(null);
  }

  @SuppressWarnings("unchecked")
  private static ObjectProvider<TokenClaimsConvertersByIssuer> providerReturning(
      final TokenClaimsConvertersByIssuer value) {
    final var provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(value);
    return provider;
  }
}
