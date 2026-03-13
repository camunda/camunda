/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.unit.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.camunda.gatekeeper.model.identity.CamundaAuthentication;
import io.camunda.gatekeeper.model.identity.PrincipalType;
import io.camunda.gatekeeper.spi.MembershipResolver;
import io.camunda.gatekeeper.spring.converter.UsernamePasswordAuthenticationTokenConverter;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

@ExtendWith(MockitoExtension.class)
final class UsernamePasswordAuthenticationTokenConverterTest {

  @Mock private MembershipResolver membershipResolver;
  private UsernamePasswordAuthenticationTokenConverter converter;

  @BeforeEach
  void setUp() {
    converter = new UsernamePasswordAuthenticationTokenConverter(membershipResolver);
  }

  @Test
  void shouldSupportUsernamePasswordToken() {
    // given
    final var token = new UsernamePasswordAuthenticationToken("alice", "password");

    // when / then
    assertThat(converter.supports(token)).isTrue();
  }

  @Test
  void shouldNotSupportNull() {
    assertThat(converter.supports(null)).isFalse();
  }

  @Test
  void shouldNotSupportAnonymousToken() {
    // given
    final var token =
        new AnonymousAuthenticationToken(
            "key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

    // when / then
    assertThat(converter.supports(token)).isFalse();
  }

  @Test
  void shouldConvertUsingMembershipResolver() {
    // given
    final var token = new UsernamePasswordAuthenticationToken("alice", "password");
    final var expectedAuth =
        CamundaAuthentication.of(b -> b.user("alice").role("admin").group("developers"));
    when(membershipResolver.resolveMemberships(eq(Map.of()), eq("alice"), eq(PrincipalType.USER)))
        .thenReturn(expectedAuth);

    // when
    final var result = converter.convert(token);

    // then
    assertThat(result).isEqualTo(expectedAuth);
  }
}
