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

  private final OAuth2AuthenticationExceptionHandler handler =
      new OAuth2AuthenticationExceptionHandler();
  private final MockHttpServletRequest request = new MockHttpServletRequest();
  private final MockHttpServletResponse response = new MockHttpServletResponse();

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
  void shouldLogTheErrorCodeRatherThanTheClaimLeakingDescription() {
    assertThat(OAuth2AuthenticationExceptionHandler.errorCode(invalidIdToken()))
        .isEqualTo("invalid_id_token");
  }

  @Test
  void shouldNotLogAnErrorCodeThatIsNotAPlainCode() {
    // The IdP's callback query string can set the code, so it must not forge log lines.
    final var exception =
        new OAuth2AuthenticationException(new OAuth2Error("access_denied\nforged log line"));

    assertThat(OAuth2AuthenticationExceptionHandler.errorCode(exception)).isEqualTo("unknown");
  }

  @Test
  void shouldLogTheExceptionTypeForNonOAuth2Failures() {
    assertThat(OAuth2AuthenticationExceptionHandler.errorCode(new BadCredentialsException("bad")))
        .isEqualTo("BadCredentialsException");
  }

  private static OAuth2AuthenticationException invalidIdToken() {
    final var error = new OAuth2Error("invalid_id_token", CLAIM_LEAKING_DESCRIPTION, null);
    return new OAuth2AuthenticationException(error, error.toString());
  }
}
