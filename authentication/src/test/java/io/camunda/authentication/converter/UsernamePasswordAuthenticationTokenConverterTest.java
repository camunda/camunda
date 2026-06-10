/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.authentication.service.MembershipService;
import io.camunda.authentication.service.MembershipService.PrincipalType;
import io.camunda.security.auth.CamundaAuthentication;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

public class UsernamePasswordAuthenticationTokenConverterTest {

  @Mock private MembershipService membershipService;
  private UsernamePasswordAuthenticationTokenConverter authenticationConverter;

  @BeforeEach
  void setup() throws Exception {
    MockitoAnnotations.openMocks(this).close();
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
  void shouldDelegateToMembershipServiceWithEmptyClaimsAndUserPrincipal() {
    // given
    final var expected = CamundaAuthentication.of(b -> b.user("test-user"));
    when(membershipService.resolveMemberships(any(), any(), any())).thenReturn(expected);
    final var authentication = getUsernamePasswordAuthentication("test-user", "test-password");

    // when
    final var camundaAuthentication = authenticationConverter.convert(authentication);

    // then — converter passes the username through with empty claims as a USER principal,
    // and returns whatever the service produced.
    verify(membershipService).resolveMemberships(Map.of(), "test-user", PrincipalType.USER);
    assertThat(camundaAuthentication).isSameAs(expected);
  }

  private Authentication getUsernamePasswordAuthentication(
      final String username, final String password) {
    return new UsernamePasswordAuthenticationToken(username, password);
  }
}
