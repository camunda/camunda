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

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AuthorizationGrantResponseTest {

  @Nested
  class TokenExchangeGrantResponseTests {

    @Test
    void shouldBuildWithAllFields() {
      // given
      final var issuedAt = Instant.now();
      final var response =
          TokenExchangeGrantResponse.builder()
              .accessToken("access-token")
              .issuedTokenType(TokenType.ACCESS_TOKEN)
              .tokenType("Bearer")
              .expiresIn(3600)
              .scope(Set.of("read", "write"))
              .refreshToken("refresh-token")
              .issuedAt(issuedAt)
              .build();

      // then
      assertThat(response.accessToken()).isEqualTo("access-token");
      assertThat(response.issuedTokenType()).isEqualTo(TokenType.ACCESS_TOKEN);
      assertThat(response.tokenType()).isEqualTo("Bearer");
      assertThat(response.expiresIn()).isEqualTo(3600);
      assertThat(response.scope()).containsExactlyInAnyOrder("read", "write");
      assertThat(response.refreshToken()).isEqualTo("refresh-token");
      assertThat(response.issuedAt()).isEqualTo(issuedAt);
    }

    @Test
    void shouldRejectNullAccessToken() {
      // when / then
      assertThatThrownBy(() -> TokenExchangeGrantResponse.builder().build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("accessToken");
    }

    @Test
    void shouldReportExpiredToken() {
      // given
      final var response =
          TokenExchangeGrantResponse.builder()
              .accessToken("token")
              .expiresIn(0)
              .issuedAt(Instant.now().minusSeconds(10))
              .build();

      // then
      assertThat(response.isExpired()).isTrue();
    }

    @Test
    void shouldReportNonExpiredToken() {
      // given
      final var response =
          TokenExchangeGrantResponse.builder()
              .accessToken("token")
              .expiresIn(3600)
              .issuedAt(Instant.now())
              .build();

      // then
      assertThat(response.isExpired()).isFalse();
    }
  }

  @Nested
  class ClientCredentialsGrantResponseTests {

    @Test
    void shouldBuildWithAllFields() {
      // given
      final var issuedAt = Instant.now();
      final var response =
          ClientCredentialsGrantResponse.builder()
              .accessToken("access-token")
              .tokenType("Bearer")
              .expiresIn(3600)
              .scope(Set.of("read"))
              .issuedAt(issuedAt)
              .build();

      // then
      assertThat(response.accessToken()).isEqualTo("access-token");
      assertThat(response.tokenType()).isEqualTo("Bearer");
      assertThat(response.expiresIn()).isEqualTo(3600);
      assertThat(response.scope()).containsExactly("read");
      assertThat(response.issuedAt()).isEqualTo(issuedAt);
    }

    @Test
    void shouldRejectNullAccessToken() {
      // when / then
      assertThatThrownBy(() -> ClientCredentialsGrantResponse.builder().build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("accessToken");
    }

    @Test
    void shouldDefaultIssuedAtToNow() {
      // given
      final var before = Instant.now();
      final var response = ClientCredentialsGrantResponse.builder().accessToken("token").build();

      // then
      assertThat(response.issuedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void shouldReportExpiredToken() {
      // given
      final var response =
          ClientCredentialsGrantResponse.builder()
              .accessToken("token")
              .expiresIn(0)
              .issuedAt(Instant.now().minusSeconds(10))
              .build();

      // then
      assertThat(response.isExpired()).isTrue();
    }

    @Test
    void shouldReportNonExpiredToken() {
      // given
      final var response =
          ClientCredentialsGrantResponse.builder()
              .accessToken("token")
              .expiresIn(3600)
              .issuedAt(Instant.now())
              .build();

      // then
      assertThat(response.isExpired()).isFalse();
    }
  }

  @Nested
  class JwtBearerGrantResponseTests {

    @Test
    void shouldBuildWithAllFields() {
      // given
      final var issuedAt = Instant.now();
      final var response =
          JwtBearerGrantResponse.builder()
              .accessToken("access-token")
              .tokenType("Bearer")
              .expiresIn(3600)
              .scope(Set.of("read"))
              .issuedAt(issuedAt)
              .build();

      // then
      assertThat(response.accessToken()).isEqualTo("access-token");
      assertThat(response.tokenType()).isEqualTo("Bearer");
      assertThat(response.expiresIn()).isEqualTo(3600);
      assertThat(response.scope()).containsExactly("read");
      assertThat(response.issuedAt()).isEqualTo(issuedAt);
    }

    @Test
    void shouldRejectNullAccessToken() {
      // when / then
      assertThatThrownBy(() -> JwtBearerGrantResponse.builder().build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("accessToken");
    }

    @Test
    void shouldDefaultIssuedAtToNow() {
      // given
      final var before = Instant.now();
      final var response = JwtBearerGrantResponse.builder().accessToken("token").build();

      // then
      assertThat(response.issuedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void shouldReportExpiredToken() {
      // given
      final var response =
          JwtBearerGrantResponse.builder()
              .accessToken("token")
              .expiresIn(0)
              .issuedAt(Instant.now().minusSeconds(10))
              .build();

      // then
      assertThat(response.isExpired()).isTrue();
    }

    @Test
    void shouldReportNonExpiredToken() {
      // given
      final var response =
          JwtBearerGrantResponse.builder()
              .accessToken("token")
              .expiresIn(3600)
              .issuedAt(Instant.now())
              .build();

      // then
      assertThat(response.isExpired()).isFalse();
    }
  }

  @Nested
  class AuthorizationCodeGrantResponseTests {

    @Test
    void shouldBuildWithAllFields() {
      // given
      final var issuedAt = Instant.now();
      final var response =
          AuthorizationCodeGrantResponse.builder()
              .accessToken("access-token")
              .tokenType("Bearer")
              .expiresIn(3600)
              .scope(Set.of("openid", "profile"))
              .refreshToken("refresh-token")
              .idToken("id-token")
              .issuedAt(issuedAt)
              .build();

      // then
      assertThat(response.accessToken()).isEqualTo("access-token");
      assertThat(response.tokenType()).isEqualTo("Bearer");
      assertThat(response.expiresIn()).isEqualTo(3600);
      assertThat(response.scope()).containsExactlyInAnyOrder("openid", "profile");
      assertThat(response.refreshToken()).isEqualTo("refresh-token");
      assertThat(response.idToken()).isEqualTo("id-token");
      assertThat(response.issuedAt()).isEqualTo(issuedAt);
    }

    @Test
    void shouldRejectNullAccessToken() {
      // when / then
      assertThatThrownBy(() -> AuthorizationCodeGrantResponse.builder().build())
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("accessToken");
    }

    @Test
    void shouldDefaultIssuedAtToNow() {
      // given
      final var before = Instant.now();
      final var response = AuthorizationCodeGrantResponse.builder().accessToken("token").build();

      // then
      assertThat(response.issuedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void shouldDefaultToEmptyScope() {
      // given
      final var response = AuthorizationCodeGrantResponse.builder().accessToken("token").build();

      // then
      assertThat(response.scope()).isEmpty();
    }

    @Test
    void shouldReportExpiredToken() {
      // given
      final var response =
          AuthorizationCodeGrantResponse.builder()
              .accessToken("token")
              .expiresIn(0)
              .issuedAt(Instant.now().minusSeconds(10))
              .build();

      // then
      assertThat(response.isExpired()).isTrue();
    }

    @Test
    void shouldReportNonExpiredToken() {
      // given
      final var response =
          AuthorizationCodeGrantResponse.builder()
              .accessToken("token")
              .expiresIn(3600)
              .issuedAt(Instant.now())
              .build();

      // then
      assertThat(response.isExpired()).isFalse();
    }
  }
}
