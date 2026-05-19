/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.authentication.service.MembershipProvider;
import io.camunda.authentication.service.MembershipService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

public class UsernamePasswordAuthenticationTokenConverterTest {

  @Mock private MembershipService membershipService;
  @Mock private MembershipProvider provider;
  private UsernamePasswordAuthenticationTokenConverter authenticationConverter;

  @BeforeEach
  void setup() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    when(membershipService.createProviderForUser("test-user")).thenReturn(provider);
    authenticationConverter = new UsernamePasswordAuthenticationTokenConverter(membershipService);
  }

  @Test
  void shouldSupport() {
    // given
    final var authentication = mock(UsernamePasswordAuthenticationToken.class);

    // when
    final var supports = authenticationConverter.supports(authentication);

    // then
    assertThat(supports).isTrue();
  }

  @Test
  void shouldNotSupport() {
    // given
    final var authentication = mock(OAuth2AuthenticationToken.class);

    // when
    final var supports = authenticationConverter.supports(authentication);

    // then
    assertThat(supports).isFalse();
  }

  @Test
  void shouldBuildAuthenticationFromProvider() {
    // given
    when(provider.groups()).thenReturn(List.of("g1"));
    when(provider.roles()).thenReturn(List.of("r1"));
    when(provider.tenants()).thenReturn(List.of("t1"));

    // when
    final var auth = authenticationConverter.convert(usernamePassword("test-user"));

    // then — username plus the three provider-backed memberships are wired through
    assertThat(auth.authenticatedUsername()).isEqualTo("test-user");
    assertThat(auth.authenticatedGroupIds()).containsExactly("g1");
    assertThat(auth.authenticatedRoleIds()).containsExactly("r1");
    assertThat(auth.authenticatedTenantIds()).containsExactly("t1");
  }

  @Test
  void shouldNotWireMappingRulesSupplierForBasicAuth() {
    // given — BASIC auth has no token claims, so mappingRules must remain empty without ever
    // calling the provider.
    // when
    final var auth = authenticationConverter.convert(usernamePassword("test-user"));

    // then
    assertThat(auth.authenticatedMappingRuleIds()).isEmpty();
    verify(provider, never()).mappingRules();
  }

  @Test
  void shouldRequestProviderFromServiceWithUsername() {
    // given
    final var authentication = usernamePassword("test-user");

    // when
    authenticationConverter.convert(authentication);

    // then — converter delegates through the USER/no-claims convenience overload (BASIC auth has
    // no notion of CLIENT principals and carries no token claims).
    verify(membershipService).createProviderForUser("test-user");
  }

  private Authentication usernamePassword(final String username) {
    return new UsernamePasswordAuthenticationToken(username, "ignored");
  }
}
