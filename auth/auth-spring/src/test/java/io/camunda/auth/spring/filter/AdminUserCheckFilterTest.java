/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.auth.domain.spi.AdminUserCheckProvider;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class AdminUserCheckFilterTest {

  @Mock private AdminUserCheckProvider adminUserCheckProvider;
  @Mock private FilterChain filterChain;

  @Test
  void shouldRedirectWhenNoAdminUser() throws Exception {
    // given
    when(adminUserCheckProvider.hasAdminUser()).thenReturn(false);
    var filter = new AdminUserCheckFilter(adminUserCheckProvider);
    var request = new MockHttpServletRequest();
    request.setRequestURI("/some-page");
    var response = new MockHttpServletResponse();

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    assertThat(response.getRedirectedUrl()).isEqualTo("/admin/setup");
    verifyNoInteractions(filterChain);
  }

  @Test
  void shouldContinueChainWhenAdminExists() throws Exception {
    // given
    when(adminUserCheckProvider.hasAdminUser()).thenReturn(true);
    var filter = new AdminUserCheckFilter(adminUserCheckProvider);
    var request = new MockHttpServletRequest();
    request.setRequestURI("/some-page");
    var response = new MockHttpServletResponse();

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(filterChain).doFilter(request, response);
    assertThat(response.getRedirectedUrl()).isNull();
  }

  @Test
  void shouldSkipRedirectForSetupPath() throws Exception {
    // given
    var filter = new AdminUserCheckFilter(adminUserCheckProvider);
    var request = new MockHttpServletRequest();
    request.setRequestURI("/admin/setup");
    var response = new MockHttpServletResponse();

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(filterChain).doFilter(request, response);
    verifyNoInteractions(adminUserCheckProvider);
  }

  @Test
  void shouldSkipRedirectForAssetsPath() throws Exception {
    // given
    var filter = new AdminUserCheckFilter(adminUserCheckProvider);
    var request = new MockHttpServletRequest();
    request.setRequestURI("/admin/assets/style.css");
    var response = new MockHttpServletResponse();

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(filterChain).doFilter(request, response);
    verifyNoInteractions(adminUserCheckProvider);
  }

  @Test
  void shouldContinueChainOnException() throws Exception {
    // given
    when(adminUserCheckProvider.hasAdminUser()).thenThrow(new RuntimeException("storage down"));
    var filter = new AdminUserCheckFilter(adminUserCheckProvider);
    var request = new MockHttpServletRequest();
    request.setRequestURI("/some-page");
    var response = new MockHttpServletResponse();

    // when
    filter.doFilterInternal(request, response, filterChain);

    // then
    verify(filterChain).doFilter(request, response);
    assertThat(response.getRedirectedUrl()).isNull();
  }
}
