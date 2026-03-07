/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.web.context.support.GenericWebApplicationContext;

/**
 * Tests for {@link SecurityFilterChainHelper} static configuration methods. Verifies CSRF token
 * repository setup and secure header configuration by building a real SecurityFilterChain and
 * exercising it with mock requests.
 */
class SecurityFilterChainHelperTest {

  private GenericWebApplicationContext applicationContext;

  @BeforeEach
  void setUp() {
    applicationContext = new GenericWebApplicationContext();
    applicationContext.refresh();
  }

  @AfterEach
  void tearDown() {
    if (applicationContext != null) {
      applicationContext.close();
    }
  }

  @Test
  void configureCsrfShouldAddCsrfFilterToChain() throws Exception {
    // given
    final HttpSecurity http = createHttpSecurity();
    SecurityFilterChainHelper.configureCsrf(
        http, "X-CSRF-TOKEN", Set.of("/actuator/**"), Set.of("/api/public"), "/login", "/logout");
    http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

    // when
    final SecurityFilterChain chain = http.build();

    // then
    final boolean hasCsrfFilter =
        chain.getFilters().stream().anyMatch(f -> f instanceof CsrfFilter);
    assertThat(hasCsrfFilter)
        .as("SecurityFilterChain should contain a CsrfFilter")
        .isTrue();
  }

  @Test
  void setupSecureHeadersShouldAddHeaderWriterFilterToChain() throws Exception {
    // given
    final HttpSecurity http = createHttpSecurity();
    SecurityFilterChainHelper.setupSecureHeaders(http);
    http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

    // when
    final SecurityFilterChain chain = http.build();

    // then
    final boolean hasHeaderFilter =
        chain.getFilters().stream().anyMatch(f -> f instanceof HeaderWriterFilter);
    assertThat(hasHeaderFilter)
        .as("SecurityFilterChain should contain a HeaderWriterFilter")
        .isTrue();
  }

  @Test
  void setupSecureHeadersShouldWriteHstsForHttpsRequests() throws Exception {
    // given
    final SecurityFilterChain chain = buildChainWithSecureHeaders();

    final var request = new MockHttpServletRequest("GET", "/test");
    request.setScheme("https");
    request.setSecure(true);
    final var response = new MockHttpServletResponse();

    // when
    new FilterChainProxy(chain).doFilter(request, response, noopFilterChain());

    // then
    final String hsts = response.getHeader("Strict-Transport-Security");
    assertThat(hsts).isNotNull();
    assertThat(hsts).contains("max-age=63072000");
    assertThat(hsts).contains("includeSubDomains");
  }

  @Test
  void setupSecureHeadersShouldWriteXssProtection() throws Exception {
    // given
    final SecurityFilterChain chain = buildChainWithSecureHeaders();

    final var request = new MockHttpServletRequest("GET", "/test");
    request.setScheme("https");
    request.setSecure(true);
    final var response = new MockHttpServletResponse();

    // when
    new FilterChainProxy(chain).doFilter(request, response, noopFilterChain());

    // then
    assertThat(response.getHeader("X-XSS-Protection")).isEqualTo("1; mode=block");
  }

  @Test
  void setupSecureHeadersShouldWriteContentTypeOptions() throws Exception {
    // given
    final SecurityFilterChain chain = buildChainWithSecureHeaders();

    final var request = new MockHttpServletRequest("GET", "/test");
    request.setScheme("https");
    request.setSecure(true);
    final var response = new MockHttpServletResponse();

    // when
    new FilterChainProxy(chain).doFilter(request, response, noopFilterChain());

    // then
    assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
  }

  @Test
  void setupSecureHeadersShouldWriteFrameOptionsSameOrigin() throws Exception {
    // given
    final SecurityFilterChain chain = buildChainWithSecureHeaders();

    final var request = new MockHttpServletRequest("GET", "/test");
    request.setScheme("https");
    request.setSecure(true);
    final var response = new MockHttpServletResponse();

    // when
    new FilterChainProxy(chain).doFilter(request, response, noopFilterChain());

    // then
    assertThat(response.getHeader("X-Frame-Options")).isEqualTo("SAMEORIGIN");
  }

  @Test
  void setupSecureHeadersShouldNotWriteHstsForPlainHttp() throws Exception {
    // given
    final SecurityFilterChain chain = buildChainWithSecureHeaders();

    final var request = new MockHttpServletRequest("GET", "/test");
    request.setScheme("http");
    request.setSecure(false);
    final var response = new MockHttpServletResponse();

    // when
    new FilterChainProxy(chain).doFilter(request, response, noopFilterChain());

    // then — HSTS should not be present for HTTP
    assertThat(response.getHeader("Strict-Transport-Security")).isNull();
    // Other headers should still be present
    assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(response.getHeader("X-Frame-Options")).isEqualTo("SAMEORIGIN");
  }

  @Test
  void csrfAndHeadersTogetherShouldProduceFunctionalFilterChain() throws Exception {
    // given
    final HttpSecurity http = createHttpSecurity();
    SecurityFilterChainHelper.configureCsrf(
        http, "XSRF-TOKEN", Set.of(), Set.of(), "/login", "/logout");
    SecurityFilterChainHelper.setupSecureHeaders(http);
    http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    final SecurityFilterChain chain = http.build();

    // then — both filters present
    final boolean hasCsrf =
        chain.getFilters().stream().anyMatch(f -> f instanceof CsrfFilter);
    final boolean hasHeaders =
        chain.getFilters().stream().anyMatch(f -> f instanceof HeaderWriterFilter);
    assertThat(hasCsrf).isTrue();
    assertThat(hasHeaders).isTrue();
  }

  // -- Helper methods --

  private SecurityFilterChain buildChainWithSecureHeaders() throws Exception {
    final HttpSecurity http = createHttpSecurity();
    SecurityFilterChainHelper.setupSecureHeaders(http);
    http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
  }

  private HttpSecurity createHttpSecurity() {
    final ObjectPostProcessor<Object> objectPostProcessor = noopObjectPostProcessor();
    final var authManagerBuilder = new AuthenticationManagerBuilder(objectPostProcessor);

    final var http =
        new HttpSecurity(objectPostProcessor, authManagerBuilder, Map.of());
    http.setSharedObject(
        org.springframework.context.ApplicationContext.class, applicationContext);
    return http;
  }

  private ObjectPostProcessor<Object> noopObjectPostProcessor() {
    return new ObjectPostProcessor<>() {
      @Override
      @SuppressWarnings("unchecked")
      public <O> O postProcess(final O object) {
        return object;
      }
    };
  }

  private FilterChain noopFilterChain() {
    return (request, response) -> {};
  }
}
