/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class OidcRedirectDiagnosticsFilterTest {

  private static final String CALLBACK_PATH = "/sso-callback";

  private final OidcRedirectDiagnosticsFilter filter =
      new OidcRedirectDiagnosticsFilter(CALLBACK_PATH);

  private Logger filterLogger;
  private Level originalLevel;
  private CapturingAppender appender;

  @BeforeEach
  void setUp() {
    final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    filterLogger = ctx.getLogger(OidcRedirectDiagnosticsFilter.class.getName());
    originalLevel = filterLogger.getLevel();
    appender = new CapturingAppender();
    appender.start();
    filterLogger.addAppender(appender);
    // ensure WARN events are not filtered out by the default root level
    filterLogger.setLevel(Level.WARN);
  }

  @AfterEach
  void tearDown() {
    filterLogger.removeAppender(appender);
    filterLogger.setLevel(originalLevel);
  }

  @Test
  void shouldWarnWhenAuthorizationRedirectUriDoesNotMatch() throws Exception {
    // given - an authorization request whose generated redirect_uri points at a different host
    final MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/oauth2/authorization/oidc");
    request.setServerName("localhost");
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final FilterChain chain =
        (req, res) ->
            ((MockHttpServletResponse) res)
                .setHeader(
                    HttpHeaders.LOCATION,
                    "https://idp.example.com/authorize?client_id=x&redirect_uri=https://wrong-host/sso-callback");

    // when
    filter.doFilter(request, response, chain);

    // then
    assertThat(warnMessages())
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains("redirect_uri mismatch")
                    .contains("https://wrong-host/sso-callback"));
  }

  @Test
  void shouldNotWarnWhenAuthorizationRedirectUriMatches() throws Exception {
    // given - redirect_uri matches the expected externalBaseUrl + callback path
    final MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/oauth2/authorization/oidc");
    request.setServerName("localhost");
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final FilterChain chain =
        (req, res) ->
            ((MockHttpServletResponse) res)
                .setHeader(
                    HttpHeaders.LOCATION,
                    "https://idp.example.com/authorize?client_id=x&redirect_uri=http://localhost/sso-callback");

    // when
    filter.doFilter(request, response, chain);

    // then
    assertThat(warnMessages()).noneMatch(message -> message.contains("redirect_uri mismatch"));
  }

  @Test
  void shouldComputeExpectedRedirectUriFromBracketedIpv6ForwardedHost() throws Exception {
    // given - the request comes through a reverse proxy reporting a bracketed IPv6 host + port.
    // A naive ':' split would corrupt the IPv6 literal; the expected redirect_uri in the mismatch
    // warning must preserve the full bracketed authority and its port.
    final MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/oauth2/authorization/oidc");
    request.setServerName("internal-host");
    request.addHeader("X-Forwarded-Proto", "https");
    request.addHeader("X-Forwarded-Host", "[2001:db8::1]:8443");
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final FilterChain chain =
        (req, res) ->
            ((MockHttpServletResponse) res)
                .setHeader(
                    HttpHeaders.LOCATION,
                    "https://idp.example.com/authorize?client_id=x&redirect_uri=https://wrong-host/sso-callback");

    // when
    filter.doFilter(request, response, chain);

    // then - the expected URI is derived correctly from the bracketed IPv6 authority + port
    assertThat(warnMessages())
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains("redirect_uri mismatch")
                    .contains("https://[2001:db8::1]:8443/sso-callback"));
  }

  @Test
  void shouldWarnWhenCallbackReceivesCodeWithoutSession() throws Exception {
    // given - a callback carrying an authorization code but no HTTP session
    final MockHttpServletRequest request = new MockHttpServletRequest("GET", CALLBACK_PATH);
    request.setServerName("localhost");
    request.setParameter("code", "auth-code-123");
    final MockHttpServletResponse response = new MockHttpServletResponse();

    // when
    filter.doFilter(request, response, new MockFilterChain());

    // then
    assertThat(warnMessages()).anyMatch(message -> message.contains("no valid HTTP session"));
  }

  @Test
  void shouldNotWarnWhenCallbackHasSession() throws Exception {
    // given - a callback carrying a code and a valid session
    final MockHttpServletRequest request = new MockHttpServletRequest("GET", CALLBACK_PATH);
    request.setServerName("localhost");
    request.setParameter("code", "auth-code-123");
    request.getSession(true);
    final MockHttpServletResponse response = new MockHttpServletResponse();

    // when
    filter.doFilter(request, response, new MockFilterChain());

    // then
    assertThat(warnMessages()).noneMatch(message -> message.contains("no valid HTTP session"));
  }

  private List<String> warnMessages() {
    return appender.events.stream()
        .filter(event -> event.getLevel() == Level.WARN)
        .map(event -> event.getMessage().getFormattedMessage())
        .toList();
  }

  /** Minimal in-memory Log4j2 appender that records the events it receives. */
  private static final class CapturingAppender extends AbstractAppender {

    private final List<LogEvent> events = new CopyOnWriteArrayList<>();

    private CapturingAppender() {
      super("capturing", null, null, false, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(final LogEvent event) {
      events.add(event.toImmutable());
    }
  }
}
