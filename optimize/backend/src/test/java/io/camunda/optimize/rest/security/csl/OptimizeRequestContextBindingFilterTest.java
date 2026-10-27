/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class OptimizeRequestContextBindingFilterTest {

  private final OptimizeRequestContextBindingFilter filter =
      new OptimizeRequestContextBindingFilter();

  @AfterEach
  void resetRequestContext() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void shouldBindTheChainRequestForTheRestOfTheChain() throws Exception {
    // given
    final MockHttpServletRequest chainRequest = new MockHttpServletRequest("GET", "/");
    final AtomicReference<HttpServletRequest> bound = new AtomicReference<>();
    final FilterChain downstream =
        (request, response) ->
            bound.set(
                ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                    .getRequest());

    // when
    filter.doFilter(chainRequest, new MockHttpServletResponse(), downstream);

    // then
    assertThat(bound.get()).isSameAs(chainRequest);
  }

  @Test
  void shouldRestoreThePreviousBindingAfterTheChain() throws Exception {
    // given
    final MockHttpServletRequest outerRequest = new MockHttpServletRequest("GET", "/outer");
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(outerRequest, new MockHttpServletResponse()));

    // when
    filter.doFilter(
        new MockHttpServletRequest("GET", "/"),
        new MockHttpServletResponse(),
        new MockFilterChain());

    // then
    assertThat(
            ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest())
        .isSameAs(outerRequest);
  }
}
