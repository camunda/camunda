/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.active;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class InactiveUserFilterTest {

  private final CamundaAuthenticationProvider provider = mock(CamundaAuthenticationProvider.class);
  private final ActiveUserChecker checker = mock(ActiveUserChecker.class);
  private final FilterChain chain = mock(FilterChain.class);
  private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v2/topology");
  private final MockHttpServletResponse response = new MockHttpServletResponse();
  private final InactiveUserFilter filter = new InactiveUserFilter(provider, checker);

  @BeforeEach
  void setUp() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("jane", "n/a", "ROLE_USER"));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private static CamundaAuthentication user(final String username) {
    return CamundaAuthentication.of(b -> b.user(username));
  }

  @Test
  void shouldRejectWithForbiddenWhenUserIsInactive() throws Exception {
    // given
    when(provider.getCamundaAuthentication()).thenReturn(user("jane"));
    when(checker.isActive("default", "jane")).thenReturn(false);

    // when
    filter.doFilter(request, response, chain);

    // then
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentType()).startsWith("application/problem+json");
    assertThat(response.getContentAsString()).contains("\"status\":403", "/v2/topology");
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void shouldPassWhenUserIsActive() throws Exception {
    // given
    when(provider.getCamundaAuthentication()).thenReturn(user("jane"));
    when(checker.isActive("default", "jane")).thenReturn(true);

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(chain).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void shouldPassWithoutLookupWhenAuthenticationHasNoUsername() throws Exception {
    // given
    when(provider.getCamundaAuthentication())
        .thenReturn(CamundaAuthentication.of(b -> b.clientId("client")));

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(chain).doFilter(request, response);
    verify(checker, never()).isActive(any(), any());
  }

  @Test
  void shouldPassWithoutLookupWhenRequestIsUnauthenticated() throws Exception {
    // given
    SecurityContextHolder.clearContext();

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(chain).doFilter(request, response);
    verify(provider, never()).getCamundaAuthentication();
    verify(checker, never()).isActive(any(), any());
  }

  @Test
  void shouldPassWithoutLookupWhenAuthenticationIsAnonymous() throws Exception {
    // given
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(chain).doFilter(request, response);
    verify(checker, never()).isActive(any(), any());
  }

  @Test
  void shouldPassWhenAuthenticationCannotBeResolved() throws Exception {
    // given
    when(provider.getCamundaAuthentication()).thenThrow(new IllegalStateException("boom"));

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(chain).doFilter(request, response);
    verify(checker, never()).isActive(any(), any());
  }
}
