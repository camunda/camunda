/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

@ExtendWith(MockitoExtension.class)
class IssuerAwareJWSKeySelectorTest {

  private static final List<String> ADDITIONAL_URIS = List.of("https://keys.example.com/jwks");

  @Mock private JWSKeySelectorFactory jwsKeySelectorFactory;
  @Mock private JWSKeySelector<SecurityContext> keySelector;

  @BeforeEach
  void setUp() {
    when(jwsKeySelectorFactory.createJWSKeySelector(any(), any())).thenReturn(keySelector);
  }

  /**
   * Two providers whose issuers differ only by a trailing slash, and only one of them configures
   * additional keys. The other provider's tokens must never be verified with those keys: whoever
   * holds them could otherwise sign a token that claims the first provider's issuer.
   */
  @Test
  void shouldNotGiveATokenTheAdditionalKeysOfALookAlikeIssuer() throws Exception {
    // given
    final var selector = lookAlikeProviders();

    // when
    selectKeysFor(selector, "https://idp.example.com/realm");

    // then
    verify(jwsKeySelectorFactory).createJWSKeySelector(eq(jwkSetUri("without-keys")), isNull());
  }

  @Test
  void shouldGiveATokenTheAdditionalKeysOfTheProviderItIsRoutedTo() throws Exception {
    // given
    final var selector = lookAlikeProviders();

    // when
    selectKeysFor(selector, "https://idp.example.com/realm/");

    // then
    verify(jwsKeySelectorFactory)
        .createJWSKeySelector(eq(jwkSetUri("with-keys")), eq(ADDITIONAL_URIS));
  }

  /**
   * camunda/camunda#61896: the provider is configured with a trailing slash its tokens don't carry.
   * Discovery resolves the registration to the provider's issuer, and the additional keys are found
   * by registration, so they still apply.
   */
  @Test
  void shouldKeepTheAdditionalKeysOfARegistrationResolvedToTheProvidersIssuer() throws Exception {
    // given - registered as "https://idp.example.com" although configured with a trailing slash
    final var selector =
        new IssuerAwareJWSKeySelector(
            List.of(registration("entra", "https://idp.example.com")),
            jwsKeySelectorFactory,
            Map.of("entra", ADDITIONAL_URIS));

    // when
    selectKeysFor(selector, "https://idp.example.com");

    // then
    verify(jwsKeySelectorFactory).createJWSKeySelector(eq(jwkSetUri("entra")), eq(ADDITIONAL_URIS));
  }

  private IssuerAwareJWSKeySelector lookAlikeProviders() {
    return new IssuerAwareJWSKeySelector(
        List.of(
            registration("without-keys", "https://idp.example.com/realm"),
            registration("with-keys", "https://idp.example.com/realm/")),
        jwsKeySelectorFactory,
        Map.of("with-keys", ADDITIONAL_URIS));
  }

  private static void selectKeysFor(final IssuerAwareJWSKeySelector selector, final String issuer)
      throws Exception {
    selector.selectKeys(
        new JWSHeader(JWSAlgorithm.RS256), new JWTClaimsSet.Builder().issuer(issuer).build(), null);
  }

  private static String jwkSetUri(final String registrationId) {
    return "https://idp.example.com/" + registrationId + "/jwks";
  }

  private static ClientRegistration registration(final String registrationId, final String issuer) {
    return ClientRegistration.withRegistrationId(registrationId)
        .clientId("client")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/sso-callback")
        .authorizationUri("https://idp.example.com/auth")
        .tokenUri("https://idp.example.com/token")
        .jwkSetUri(jwkSetUri(registrationId))
        .issuerUri(issuer)
        .build();
  }
}
