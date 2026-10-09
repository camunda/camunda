/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.Either;
import io.camunda.security.core.port.in.AuthorizationCheckPort;
import io.camunda.security.spring.spi.WebAppAccessDeniedHandlerPort;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OptimizeComponentAccessFilterTest {

  private static final CamundaAuthentication KERMIT =
      CamundaAuthentication.of(builder -> builder.user("kermit"));

  private final AtomicReference<String> deniedComponent = new AtomicReference<>();

  private final WebAppAccessDeniedHandlerPort deniedHandler =
      (request, response, webApp, authentication) -> deniedComponent.set(webApp);

  @Test
  void shouldPassThroughWithoutAnAuthentication() throws Exception {
    // given
    final MockFilterChain downstream = new MockFilterChain();

    // when
    filter(() -> null, deny()).doFilter(request("/"), new MockHttpServletResponse(), downstream);

    // then
    assertThat(downstream.getRequest()).isNotNull();
    assertThat(deniedComponent.get()).isNull();
  }

  @Test
  void shouldPassThroughForAnAnonymousAuthentication() throws Exception {
    // given
    final MockFilterChain downstream = new MockFilterChain();

    // when
    filter(CamundaAuthentication::anonymous, deny())
        .doFilter(request("/"), new MockHttpServletResponse(), downstream);

    // then
    assertThat(downstream.getRequest()).isNotNull();
    assertThat(deniedComponent.get()).isNull();
  }

  @Test
  void shouldPassThroughWhenNoComponentIsClaimed() throws Exception {
    // given
    final MockFilterChain downstream = new MockFilterChain();
    final var filter =
        new OptimizeComponentAccessFilter(
            request -> Optional.empty(), deny(), deniedHandler, () -> KERMIT);

    // when
    filter.doFilter(request("/"), new MockHttpServletResponse(), downstream);

    // then
    assertThat(downstream.getRequest()).isNotNull();
    assertThat(deniedComponent.get()).isNull();
  }

  @Test
  void shouldServeTheRequestWhenTheCheckGrants() throws Exception {
    // given
    final MockFilterChain downstream = new MockFilterChain();

    // when
    filter(() -> KERMIT, grant()).doFilter(request("/"), new MockHttpServletResponse(), downstream);

    // then
    assertThat(downstream.getRequest()).isNotNull();
    assertThat(deniedComponent.get()).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/",
        "/api/report/1",
        "/api/export/csv/some-id/report.js",
        "/api/export/csv/some-id/forbidden"
      })
  void shouldCheckEveryRequestRegardlessOfItsUri(final String uri) throws Exception {
    // A caller picks the last path segment of an export, so no URI may be exempt by its shape.
    // given
    final MockFilterChain downstream = new MockFilterChain();

    // when
    filter(() -> KERMIT, deny()).doFilter(request(uri), new MockHttpServletResponse(), downstream);

    // then
    assertThat(downstream.getRequest()).isNull();
    assertThat(deniedComponent.get()).isEqualTo("optimize");
  }

  private OptimizeComponentAccessFilter filter(
      final CamundaAuthenticationProvider authenticationProvider,
      final AuthorizationCheckPort checkPort) {
    return new OptimizeComponentAccessFilter(
        request -> Optional.of("optimize"), checkPort, deniedHandler, authenticationProvider);
  }

  private static MockHttpServletRequest request(final String uri) {
    return new MockHttpServletRequest("GET", uri);
  }

  private static AuthorizationCheckPort grant() {
    return new OptimizeComponentAuthorizationAdapter(authentication -> Either.right(null));
  }

  private static AuthorizationCheckPort deny() {
    return new OptimizeComponentAuthorizationAdapter(
        authentication -> Either.left("user has no Optimize permission"));
  }
}
