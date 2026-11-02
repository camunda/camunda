/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class TrailingSlashRejectionFilterTest {

  @Mock private FilterChain chain;

  private final TrailingSlashRejectionFilter filter =
      new TrailingSlashRejectionFilter(new ObjectMapper());

  @Test
  void shouldRejectEmptyTrailingPathParameter() throws Exception {
    // given
    final var request = new MockHttpServletRequest("DELETE", "/v2/groups/");
    final var response = new MockHttpServletResponse();

    // when
    filter.doFilter(request, response, chain);

    // then
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
    assertThat(response.getContentType()).isEqualTo("application/problem+json");
    verify(chain, never()).doFilter(request, response);
  }

  @ParameterizedTest(name = "{0} {1}")
  @CsvSource({
    "DELETE, /v2/groups/g1",
    "GET,    /v2/tenants/!invalid!",
    "POST,   /v2/roles/r1/users/search",
    "GET,    /operate/",
    "GET,    /tasklist/",
  })
  void shouldPassThroughPathsWithoutTrailingSlashOrOutsideApi(
      final String method, final String uri) throws Exception {
    // given
    final var request = new MockHttpServletRequest(method, uri);
    final var response = new MockHttpServletResponse();

    // when
    filter.doFilter(request, response, chain);

    // then
    verify(chain).doFilter(request, response);
  }

  @Test
  void shouldStripContextPathBeforeMatching() throws Exception {
    // given
    final var request = new MockHttpServletRequest("DELETE", "/camunda/v2/groups/");
    request.setContextPath("/camunda");
    final var response = new MockHttpServletResponse();

    // when
    filter.doFilter(request, response, chain);

    // then
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
  }
}
