/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

@ExtendWith(MockitoExtension.class)
class IssuerAwareJWSKeySelectorTest {

  private static final String JWK_SET_URI = "https://idp.example.com/jwks";

  @Mock private JWSKeySelectorFactory jwsKeySelectorFactory;
  @Mock private JWSKeySelector<SecurityContext> keySelector;

  /**
   * The additional JWK Set URIs are keyed by the configured issuer-uri, and the token carries the
   * provider's issuer. They can differ by a trailing slash, see camunda/camunda#61896; the URIs
   * must still apply.
   */
  @Test
  void shouldUseTheAdditionalJwkSetUrisOfAnIssuerConfiguredWithATrailingSlash() throws Exception {
    // given - discovery resolved the provider's issuer, without the configured trailing slash
    final var additionalUris = List.of("https://keys.example.com/jwks");
    when(jwsKeySelectorFactory.createJWSKeySelector(any(), any())).thenReturn(keySelector);
    final var selector =
        new IssuerAwareJWSKeySelector(
            List.of(registration("https://idp.example.com")),
            jwsKeySelectorFactory,
            Map.of("https://idp.example.com/", additionalUris));

    // when
    selector.selectKeys(
        new JWSHeader(JWSAlgorithm.RS256),
        new JWTClaimsSet.Builder().issuer("https://idp.example.com").build(),
        null);

    // then
    verify(jwsKeySelectorFactory).createJWSKeySelector(JWK_SET_URI, additionalUris);
  }

  @Test
  void shouldPreferTheAdditionalJwkSetUrisOfTheExactIssuer() throws Exception {
    // given
    final var exactUris = List.of("https://keys.example.com/exact");
    when(jwsKeySelectorFactory.createJWSKeySelector(any(), any())).thenReturn(keySelector);
    final var selector =
        new IssuerAwareJWSKeySelector(
            List.of(registration("https://idp.example.com")),
            jwsKeySelectorFactory,
            Map.of(
                "https://idp.example.com",
                exactUris,
                "https://idp.example.com/",
                List.of("https://keys.example.com/other")));

    // when
    selector.selectKeys(
        new JWSHeader(JWSAlgorithm.RS256),
        new JWTClaimsSet.Builder().issuer("https://idp.example.com").build(),
        null);

    // then
    verify(jwsKeySelectorFactory).createJWSKeySelector(JWK_SET_URI, exactUris);
  }

  private static ClientRegistration registration(final String issuer) {
    return ClientRegistration.withRegistrationId("idp")
        .clientId("client")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/sso-callback")
        .authorizationUri("https://idp.example.com/auth")
        .tokenUri("https://idp.example.com/token")
        .jwkSetUri(JWK_SET_URI)
        .issuerUri(issuer)
        .build();
  }
}
