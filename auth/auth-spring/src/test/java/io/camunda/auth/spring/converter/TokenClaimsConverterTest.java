/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.model.PrincipalType;
import io.camunda.auth.domain.spi.MembershipResolver;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenClaimsConverterTest {

  @Mock private MembershipResolver membershipResolver;

  @Test
  void shouldExtractUsernameAndDelegateToResolver() {
    // given
    final var converter =
        new TokenClaimsConverter("preferred_username", "azp", true, membershipResolver);
    final Map<String, Object> claims =
        Map.of("preferred_username", "john", "azp", "my-client", "sub", "sub-123");
    final var expectedAuth = new CamundaAuthentication.Builder().user("john").build();
    when(membershipResolver.resolveMemberships(any(), eq("john"), eq(PrincipalType.USER)))
        .thenReturn(expectedAuth);

    // when
    final CamundaAuthentication result = converter.convert(claims);

    // then
    assertThat(result).isEqualTo(expectedAuth);
    verify(membershipResolver).resolveMemberships(claims, "john", PrincipalType.USER);
  }

  @Test
  void shouldExtractClientIdWhenNoUsername() {
    // given
    final var converter =
        new TokenClaimsConverter("preferred_username", "azp", true, membershipResolver);
    final Map<String, Object> claims = Map.of("azp", "my-service");
    final var expectedAuth = new CamundaAuthentication.Builder().clientId("my-service").build();
    when(membershipResolver.resolveMemberships(any(), eq("my-service"), eq(PrincipalType.CLIENT)))
        .thenReturn(expectedAuth);

    // when
    final CamundaAuthentication result = converter.convert(claims);

    // then
    assertThat(result).isEqualTo(expectedAuth);
    verify(membershipResolver).resolveMemberships(claims, "my-service", PrincipalType.CLIENT);
  }

  @Test
  void shouldPreferUsernameClaimWhenBothPresent() {
    // given
    final var converter =
        new TokenClaimsConverter("preferred_username", "azp", true, membershipResolver);
    final Map<String, Object> claims = Map.of("preferred_username", "john", "azp", "my-client");
    final var expectedAuth = new CamundaAuthentication.Builder().user("john").build();
    when(membershipResolver.resolveMemberships(any(), eq("john"), eq(PrincipalType.USER)))
        .thenReturn(expectedAuth);

    // when
    final CamundaAuthentication result = converter.convert(claims);

    // then
    assertThat(result).isEqualTo(expectedAuth);
    verify(membershipResolver).resolveMemberships(claims, "john", PrincipalType.USER);
  }

  @Test
  void shouldFallbackToClientIdWhenPreferUsernameIsFalse() {
    // given
    final var converter =
        new TokenClaimsConverter("preferred_username", "azp", false, membershipResolver);
    final Map<String, Object> claims = Map.of("preferred_username", "john", "azp", "my-client");
    final var expectedAuth = new CamundaAuthentication.Builder().user("john").build();
    // When preferUsernameClaim=false and both are present, username is still used
    // because the fallback logic checks clientId first only when username is null
    when(membershipResolver.resolveMemberships(any(), eq("john"), eq(PrincipalType.USER)))
        .thenReturn(expectedAuth);

    // when
    final CamundaAuthentication result = converter.convert(claims);

    // then
    assertThat(result).isEqualTo(expectedAuth);
  }

  @Test
  void shouldReturnAnonymousWhenNeitherUsernameNorClientIdPresent() {
    // given
    final var converter =
        new TokenClaimsConverter("preferred_username", "azp", true, membershipResolver);
    final Map<String, Object> claims = Map.of("sub", "sub-123");

    // when
    final CamundaAuthentication result = converter.convert(claims);

    // then
    assertThat(result.anonymousUser()).isTrue();
  }

  @Test
  void shouldHandleNullClientIdClaim() {
    // given
    final var converter =
        new TokenClaimsConverter("preferred_username", null, true, membershipResolver);
    final Map<String, Object> claims = Map.of("preferred_username", "john");
    final var expectedAuth = new CamundaAuthentication.Builder().user("john").build();
    when(membershipResolver.resolveMemberships(any(), eq("john"), eq(PrincipalType.USER)))
        .thenReturn(expectedAuth);

    // when
    final CamundaAuthentication result = converter.convert(claims);

    // then
    assertThat(result).isEqualTo(expectedAuth);
  }
}
