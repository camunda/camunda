/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AuthorizationGrantRequestTest {

  @Nested
  class TokenExchangeGrantRequestTests {

    @Test
    void shouldBuildWithAllFields() {
      // given
      final var request =
          TokenExchangeGrantRequest.builder()
              .subjectToken("subject-token")
              .subjectTokenType(TokenType.ACCESS_TOKEN)
              .actorToken("actor-token")
              .actorTokenType(TokenType.ACCESS_TOKEN)
              .audience("my-api")
              .scopes(Set.of("read", "write"))
              .resource("https://api.example.com")
              .additionalParameters(Map.of("key", "value"))
              .build();

      // then
      assertThat(request.subjectToken()).isEqualTo("subject-token");
      assertThat(request.subjectTokenType()).isEqualTo(TokenType.ACCESS_TOKEN);
      assertThat(request.actorToken()).isEqualTo("actor-token");
      assertThat(request.actorTokenType()).isEqualTo(TokenType.ACCESS_TOKEN);
      assertThat(request.audience()).isEqualTo("my-api");
      assertThat(request.scopes()).containsExactlyInAnyOrder("read", "write");
      assertThat(request.resource()).isEqualTo("https://api.example.com");
      assertThat(request.additionalParameters()).containsEntry("key", "value");
    }

    @Test
    void shouldReturnTokenExchangeGrantType() {
      // given
      final var request = TokenExchangeGrantRequest.builder().subjectToken("token").build();

      // then
      assertThat(request.grantType()).isEqualTo(GrantType.TOKEN_EXCHANGE);
    }

    @Test
    void shouldRejectNullSubjectToken() {
      // when / then
      assertThatThrownBy(() -> TokenExchangeGrantRequest.builder().build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("subjectToken");
    }

    @Test
    void shouldDefaultToEmptyCollections() {
      // given
      final var request = TokenExchangeGrantRequest.builder().subjectToken("token").build();

      // then
      assertThat(request.scopes()).isEmpty();
      assertThat(request.additionalParameters()).isEmpty();
    }
  }

  @Nested
  class ClientCredentialsGrantRequestTests {

    @Test
    void shouldBuildWithAllFields() {
      // given
      final var request =
          ClientCredentialsGrantRequest.builder()
              .audience("my-api")
              .scopes(Set.of("read", "write"))
              .additionalParameters(Map.of("key", "value"))
              .build();

      // then
      assertThat(request.audience()).isEqualTo("my-api");
      assertThat(request.scopes()).containsExactlyInAnyOrder("read", "write");
      assertThat(request.additionalParameters()).containsEntry("key", "value");
    }

    @Test
    void shouldReturnClientCredentialsGrantType() {
      // given
      final var request = ClientCredentialsGrantRequest.builder().build();

      // then
      assertThat(request.grantType()).isEqualTo(GrantType.CLIENT_CREDENTIALS);
    }

    @Test
    void shouldDefaultToEmptyCollections() {
      // given
      final var request = ClientCredentialsGrantRequest.builder().build();

      // then
      assertThat(request.scopes()).isEmpty();
      assertThat(request.additionalParameters()).isEmpty();
    }
  }

  @Nested
  class JwtBearerGrantRequestTests {

    @Test
    void shouldBuildWithAllFields() {
      // given
      final var request =
          JwtBearerGrantRequest.builder()
              .assertion("jwt-assertion")
              .audience("my-api")
              .scopes(Set.of("read"))
              .additionalParameters(Map.of("key", "value"))
              .build();

      // then
      assertThat(request.assertion()).isEqualTo("jwt-assertion");
      assertThat(request.audience()).isEqualTo("my-api");
      assertThat(request.scopes()).containsExactly("read");
      assertThat(request.additionalParameters()).containsEntry("key", "value");
    }

    @Test
    void shouldReturnJwtBearerGrantType() {
      // given
      final var request = JwtBearerGrantRequest.builder().assertion("jwt-assertion").build();

      // then
      assertThat(request.grantType()).isEqualTo(GrantType.JWT_BEARER);
    }

    @Test
    void shouldRejectNullAssertion() {
      // when / then
      assertThatThrownBy(() -> JwtBearerGrantRequest.builder().build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("assertion");
    }

    @Test
    void shouldDefaultToEmptyCollections() {
      // given
      final var request = JwtBearerGrantRequest.builder().assertion("jwt-assertion").build();

      // then
      assertThat(request.scopes()).isEmpty();
      assertThat(request.additionalParameters()).isEmpty();
    }
  }

  @Nested
  class AuthorizationCodeGrantRequestTests {

    @Test
    void shouldBuildWithAllFields() {
      // given
      final var request =
          AuthorizationCodeGrantRequest.builder()
              .code("auth-code")
              .redirectUri("https://app.example.com/callback")
              .codeVerifier("pkce-verifier")
              .audience("my-api")
              .scopes(Set.of("openid", "profile"))
              .additionalParameters(Map.of("key", "value"))
              .build();

      // then
      assertThat(request.code()).isEqualTo("auth-code");
      assertThat(request.redirectUri()).isEqualTo("https://app.example.com/callback");
      assertThat(request.codeVerifier()).isEqualTo("pkce-verifier");
      assertThat(request.audience()).isEqualTo("my-api");
      assertThat(request.scopes()).containsExactlyInAnyOrder("openid", "profile");
      assertThat(request.additionalParameters()).containsEntry("key", "value");
    }

    @Test
    void shouldReturnAuthorizationCodeGrantType() {
      // given
      final var request =
          AuthorizationCodeGrantRequest.builder()
              .code("auth-code")
              .redirectUri("https://app.example.com/callback")
              .build();

      // then
      assertThat(request.grantType()).isEqualTo(GrantType.AUTHORIZATION_CODE);
    }

    @Test
    void shouldRejectNullCode() {
      // when / then
      assertThatThrownBy(
              () ->
                  AuthorizationCodeGrantRequest.builder()
                      .redirectUri("https://app.example.com/callback")
                      .build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("code");
    }

    @Test
    void shouldRejectNullRedirectUri() {
      // when / then
      assertThatThrownBy(() -> AuthorizationCodeGrantRequest.builder().code("auth-code").build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("redirectUri");
    }

    @Test
    void shouldDefaultToEmptyCollections() {
      // given
      final var request =
          AuthorizationCodeGrantRequest.builder()
              .code("auth-code")
              .redirectUri("https://app.example.com/callback")
              .build();

      // then
      assertThat(request.scopes()).isEmpty();
      assertThat(request.additionalParameters()).isEmpty();
    }
  }
}
