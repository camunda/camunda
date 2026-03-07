/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import java.security.Key;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

@ExtendWith(MockitoExtension.class)
class IssuerAwareJWSKeySelectorTest {

  private static final String ISSUER_URI = "https://issuer.example.com";
  private static final String JWK_SET_URI = "https://example.com/.well-known/jwks.json";

  @Mock private JWSKeySelectorFactory jwsKeySelectorFactory;
  @Mock private JWSHeader jwsHeader;
  @Mock private SecurityContext securityContext;

  @Test
  void shouldSelectKeysForKnownIssuer() throws Exception {
    // given
    final ClientRegistration registration = createClientRegistration(ISSUER_URI);
    @SuppressWarnings("unchecked")
    final JWSKeySelector<SecurityContext> mockSelector = mock(JWSKeySelector.class);
    final Key mockKey = mock(Key.class);
    when(jwsKeySelectorFactory.createJWSKeySelector(eq(JWK_SET_URI), any()))
        .thenReturn(mockSelector);
    final List<Key> expectedKeys = List.of(mockKey);
    doReturn(expectedKeys).when(mockSelector).selectJWSKeys(jwsHeader, securityContext);

    final var selector =
        new IssuerAwareJWSKeySelector(List.of(registration), jwsKeySelectorFactory);
    final JWTClaimsSet claimsSet = new JWTClaimsSet.Builder().issuer(ISSUER_URI).build();

    // when
    final var keys = selector.selectKeys(jwsHeader, claimsSet, securityContext);

    // then
    assertThat(keys).hasSize(1);
    assertThat(keys.iterator().next()).isEqualTo(mockKey);
  }

  @Test
  void shouldCacheKeySelectorPerIssuer() throws Exception {
    // given
    final ClientRegistration registration = createClientRegistration(ISSUER_URI);
    @SuppressWarnings("unchecked")
    final JWSKeySelector<SecurityContext> mockSelector = mock(JWSKeySelector.class);
    when(jwsKeySelectorFactory.createJWSKeySelector(eq(JWK_SET_URI), any()))
        .thenReturn(mockSelector);
    doReturn(List.of()).when(mockSelector).selectJWSKeys(jwsHeader, securityContext);

    final var selector =
        new IssuerAwareJWSKeySelector(List.of(registration), jwsKeySelectorFactory);
    final JWTClaimsSet claimsSet = new JWTClaimsSet.Builder().issuer(ISSUER_URI).build();

    // when
    selector.selectKeys(jwsHeader, claimsSet, securityContext);
    selector.selectKeys(jwsHeader, claimsSet, securityContext);

    // then
    verify(jwsKeySelectorFactory, times(1)).createJWSKeySelector(eq(JWK_SET_URI), any());
  }

  @Test
  void shouldThrowForNullIssuer() {
    // given
    final ClientRegistration registration = createClientRegistration(ISSUER_URI);
    final var selector =
        new IssuerAwareJWSKeySelector(List.of(registration), jwsKeySelectorFactory);
    final JWTClaimsSet claimsSet = new JWTClaimsSet.Builder().build();

    // when/then
    assertThatThrownBy(() -> selector.selectKeys(jwsHeader, claimsSet, securityContext))
        .isInstanceOf(KeySourceException.class)
        .hasMessageContaining("Missing or empty 'iss' (issuer) claim");
  }

  @Test
  void shouldThrowForBlankIssuer() {
    // given
    final ClientRegistration registration = createClientRegistration(ISSUER_URI);
    final var selector =
        new IssuerAwareJWSKeySelector(List.of(registration), jwsKeySelectorFactory);
    final JWTClaimsSet claimsSet = new JWTClaimsSet.Builder().issuer("   ").build();

    // when/then
    assertThatThrownBy(() -> selector.selectKeys(jwsHeader, claimsSet, securityContext))
        .isInstanceOf(KeySourceException.class)
        .hasMessageContaining("Missing or empty 'iss' (issuer) claim");
  }

  @Test
  void shouldThrowForUnknownIssuer() {
    // given
    final ClientRegistration registration = createClientRegistration(ISSUER_URI);
    final var selector =
        new IssuerAwareJWSKeySelector(List.of(registration), jwsKeySelectorFactory);
    final JWTClaimsSet claimsSet =
        new JWTClaimsSet.Builder().issuer("https://unknown.example.com").build();

    // when/then
    assertThatThrownBy(() -> selector.selectKeys(jwsHeader, claimsSet, securityContext))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown issuer");
  }

  private ClientRegistration createClientRegistration(final String issuerUri) {
    return ClientRegistration.withRegistrationId("test-reg")
        .clientId("client-id")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("https://example.com/callback")
        .authorizationUri("https://example.com/authorize")
        .tokenUri("https://example.com/token")
        .jwkSetUri(JWK_SET_URI)
        .issuerUri(issuerUri)
        .build();
  }
}
