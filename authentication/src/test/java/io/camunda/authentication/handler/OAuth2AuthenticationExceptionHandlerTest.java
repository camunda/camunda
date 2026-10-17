/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

class OAuth2AuthenticationExceptionHandlerTest {

  // Shaped like the description OidcAuthorizationCodeAuthenticationProvider produces when a token
  // validator rejects the ID token, including the claim values it echoes.
  private static final String CLAIM_LEAKING_DESCRIPTION =
      "An error occurred while attempting to decode the Jwt: Token claims organizations"
          + " [{id=user-org, roles=[analyst]}], expected cluster-org";
  private static final String LOGGER_NAME = OAuth2AuthenticationExceptionHandler.class.getName();

  private final OAuth2AuthenticationExceptionHandler handler =
      new OAuth2AuthenticationExceptionHandler();
  private final MockHttpServletRequest request = new MockHttpServletRequest();
  private final MockHttpServletResponse response = new MockHttpServletResponse();
  private final CapturingAppender logs = new CapturingAppender();

  @BeforeEach
  void captureHandlerLogs() {
    logs.start();
    final LoggerContext context = (LoggerContext) LogManager.getContext(false);
    final LoggerConfig loggerConfig = new LoggerConfig(LOGGER_NAME, Level.ALL, false);
    loggerConfig.addAppender(logs, null, null);
    context.getConfiguration().addLogger(LOGGER_NAME, loggerConfig);
    context.updateLoggers();
  }

  @AfterEach
  void releaseHandlerLogs() {
    final LoggerContext context = (LoggerContext) LogManager.getContext(false);
    context.getConfiguration().removeLogger(LOGGER_NAME);
    context.updateLoggers();
    logs.stop();
  }

  @Test
  void shouldRedirectToRootWhenAuthorizationRequestIsNotFound() throws Exception {
    final var exception =
        new OAuth2AuthenticationException(
            new OAuth2Error(
                OAuth2AuthenticationExceptionHandler.AUTHORIZATION_REQUEST_NOT_FOUND_ERROR_CODE));

    handler.onAuthenticationFailure(request, response, exception);

    assertThat(response.getRedirectedUrl()).isEqualTo("/");
  }

  @Test
  void shouldAnswerRejectedIdTokenWith401InsteadOfRethrowing() {
    final var exception = invalidIdToken();

    assertThatCode(() -> handler.onAuthenticationFailure(request, response, exception))
        .doesNotThrowAnyException();

    assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    assertThat(response.getErrorMessage()).isEqualTo(HttpStatus.UNAUTHORIZED.getReasonPhrase());
  }

  @Test
  void shouldAnswerNonOAuth2FailureWith401() throws Exception {
    handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad"));

    assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
  }

  @Test
  void shouldLogTheErrorCodeButNotTheClaimLeakingDescription() throws Exception {
    handler.onAuthenticationFailure(request, response, invalidIdToken());

    assertThat(loggedWarnings())
        .singleElement()
        .satisfies(
            message ->
                assertThat(message)
                    .contains("OIDC login failed with error 'invalid_id_token'")
                    .doesNotContain("user-org")
                    .doesNotContain("cluster-org"));
  }

  @Test
  void shouldNotLogAnErrorCodeThatIsNotAPlainCode() throws Exception {
    // The IdP's callback query string can set the code, so it must not forge log lines.
    final var exception =
        new OAuth2AuthenticationException(new OAuth2Error("access_denied\nforged log line"));

    handler.onAuthenticationFailure(request, response, exception);

    assertThat(loggedWarnings())
        .singleElement()
        .satisfies(
            message ->
                assertThat(message)
                    .contains("OIDC login failed with error 'unknown'")
                    .doesNotContain("forged log line"));
  }

  @Test
  void shouldLogTheExceptionTypeForNonOAuth2Failures() throws Exception {
    handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad"));

    assertThat(loggedWarnings())
        .singleElement()
        .satisfies(
            message ->
                assertThat(message)
                    .contains("OIDC login failed with error 'BadCredentialsException'"));
  }

  @Test
  void shouldNotLogTheRecoverableAuthorizationRequestNotFound() throws Exception {
    final var exception =
        new OAuth2AuthenticationException(
            new OAuth2Error(
                OAuth2AuthenticationExceptionHandler.AUTHORIZATION_REQUEST_NOT_FOUND_ERROR_CODE));

    handler.onAuthenticationFailure(request, response, exception);

    assertThat(loggedWarnings()).isEmpty();
  }

  private List<String> loggedWarnings() {
    return logs.events.stream()
        .filter(event -> event.getLevel() == Level.WARN)
        .map(event -> event.getMessage().getFormattedMessage())
        .toList();
  }

  private static OAuth2AuthenticationException invalidIdToken() {
    final var error = new OAuth2Error("invalid_id_token", CLAIM_LEAKING_DESCRIPTION, null);
    return new OAuth2AuthenticationException(error, error.toString());
  }

  /** Collects the handler's log events, so the tests assert on what is actually logged. */
  private static final class CapturingAppender extends AbstractAppender {

    private final List<LogEvent> events = new CopyOnWriteArrayList<>();

    private CapturingAppender() {
      super("oauth2-failure-handler-test", null, null, true, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(final LogEvent event) {
      events.add(event.toImmutable());
    }
  }
}
