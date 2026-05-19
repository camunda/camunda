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

import io.camunda.authentication.service.BasicMembershipService;
import io.camunda.authentication.service.MembershipResolver;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

public class UsernamePasswordAuthenticationTokenConverterTest {

  @Mock private BasicMembershipService membershipService;
  @Mock private MembershipResolver resolver;
  private UsernamePasswordAuthenticationTokenConverter authenticationConverter;

  @BeforeEach
  void setup() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    when(membershipService.newResolver("test-user")).thenReturn(resolver);
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
  void shouldBuildAuthenticationFromResolver() {
    // given
    when(resolver.groups()).thenReturn(List.of("g1"));
    when(resolver.roles()).thenReturn(List.of("r1"));
    when(resolver.tenants()).thenReturn(List.of("t1"));

    // when
    final var auth = authenticationConverter.convert(usernamePassword("test-user"));

    // then — username plus the three resolver-backed memberships are wired through
    assertThat(auth.authenticatedUsername()).isEqualTo("test-user");
    assertThat(auth.authenticatedGroupIds()).containsExactly("g1");
    assertThat(auth.authenticatedRoleIds()).containsExactly("r1");
    assertThat(auth.authenticatedTenantIds()).containsExactly("t1");
  }

  @Test
  void shouldNotWireMappingRulesSupplierForBasicAuth() {
    // given — BASIC auth has no token claims, so mappingRules must remain empty without ever
    // calling the resolver.
    // when
    final var auth = authenticationConverter.convert(usernamePassword("test-user"));

    // then
    assertThat(auth.authenticatedMappingRuleIds()).isEmpty();
    verify(resolver, never()).mappingRules();
  }

  @Test
  void shouldRequestResolverFromServiceWithUsername() {
    // given
    final var authentication = usernamePassword("test-user");

    // when
    authenticationConverter.convert(authentication);

    // then — converter delegates resolver creation to the BASIC service using the username
    verify(membershipService).newResolver("test-user");
  }

  private Authentication usernamePassword(final String username) {
    return new UsernamePasswordAuthenticationToken(username, "ignored");
  }
}
