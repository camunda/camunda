/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.unit.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.camunda.gatekeeper.auth.OidcPrincipalLoader;
import io.camunda.gatekeeper.model.identity.CamundaAuthentication;
import io.camunda.gatekeeper.model.identity.PrincipalType;
import io.camunda.gatekeeper.spi.MembershipResolver;
import io.camunda.gatekeeper.spring.converter.TokenClaimsConverter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

@ExtendWith(MockitoExtension.class)
final class TokenClaimsConverterTest {

  @Mock private MembershipResolver membershipResolver;

  @Test
  void shouldConvertWithUsernameWhenPreferUsernameClaim() {
    // given
    final var oidcPrincipalLoader = new OidcPrincipalLoader("sub", "azp");
    final var converter =
        new TokenClaimsConverter(oidcPrincipalLoader, "sub", "azp", true, membershipResolver);
    final var claims = Map.<String, Object>of("sub", "alice", "azp", "my-client");
    final var expectedAuth = CamundaAuthentication.of(b -> b.user("alice").role("admin"));
    when(membershipResolver.resolveMemberships(claims, "alice", PrincipalType.USER))
        .thenReturn(expectedAuth);

    // when
    final var result = converter.convert(claims);

    // then
    assertThat(result).isEqualTo(expectedAuth);
  }

  @Test
  void shouldConvertWithClientIdWhenNotPreferringUsername() {
    // given
    final var oidcPrincipalLoader = new OidcPrincipalLoader("sub", "azp");
    final var converter =
        new TokenClaimsConverter(oidcPrincipalLoader, "sub", "azp", false, membershipResolver);
    final var claims = Map.<String, Object>of("sub", "alice", "azp", "my-client");
    final var expectedAuth = CamundaAuthentication.of(b -> b.clientId("my-client").role("service"));
    when(membershipResolver.resolveMemberships(claims, "my-client", PrincipalType.CLIENT))
        .thenReturn(expectedAuth);

    // when
    final var result = converter.convert(claims);

    // then
    assertThat(result).isEqualTo(expectedAuth);
  }

  @Test
  void shouldFallbackToUsernameWhenClientIdIsNull() {
    // given
    final var oidcPrincipalLoader = new OidcPrincipalLoader("sub", "azp");
    final var converter =
        new TokenClaimsConverter(oidcPrincipalLoader, "sub", "azp", false, membershipResolver);
    final var claims = Map.<String, Object>of("sub", "alice");
    final var expectedAuth = CamundaAuthentication.of(b -> b.user("alice"));
    when(membershipResolver.resolveMemberships(claims, "alice", PrincipalType.USER))
        .thenReturn(expectedAuth);

    // when
    final var result = converter.convert(claims);

    // then
    assertThat(result).isEqualTo(expectedAuth);
  }

  @Test
  void shouldThrowWhenNeitherUsernameNorClientIdFound() {
    // given
    final var oidcPrincipalLoader = new OidcPrincipalLoader("sub", "azp");
    final var converter =
        new TokenClaimsConverter(oidcPrincipalLoader, "sub", "azp", false, membershipResolver);
    final var claims = Map.<String, Object>of("iss", "http://issuer");

    // when / then
    assertThatThrownBy(() -> converter.convert(claims))
        .isInstanceOf(OAuth2AuthenticationException.class);
  }
}
