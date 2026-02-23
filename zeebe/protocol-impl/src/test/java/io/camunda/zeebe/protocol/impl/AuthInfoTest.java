/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class AuthInfoTest {

  @RegressionTest("https://github.com/camunda/camunda/issues/35177")
  void shouldSanitizeOnToString() {
    // given
    final AuthInfo authInfo = new AuthInfo();
    final String token = "token";
    authInfo.setFormat(AuthInfo.AuthDataFormat.JWT);
    authInfo.setAuthData(token);
    authInfo.setClaims(Map.of("key", "value"));

    // when
    final var authInfoString = authInfo.toString();

    // then
    assertThat(authInfoString)
        .isEqualTo(
            """
        {"format":"JWT","authData":"***","claims":"***"}""");
  }

  @Nested
  class EncodeDecodeTests {
    @Test
    void shouldEncodeDecodeAuthInfo() {
      // given
      final AuthInfo authInfo = new AuthInfo();
      final String token = "token";
      final Map<String, Object> authInfoMap = Map.of("key", "value");
      authInfo.setFormat(AuthInfo.AuthDataFormat.JWT);
      authInfo.setAuthData(token);
      authInfo.setClaims(authInfoMap);

      // when
      encodeDecode(authInfo);

      // then
      assertThat(authInfo.getFormat()).isEqualTo(AuthInfo.AuthDataFormat.JWT);
      assertThat(authInfo.getAuthData()).isEqualTo(token);
      assertThat(authInfo.getClaims()).isEqualTo(authInfoMap);
    }

    @Test
    void shouldEncodeDecodeEmptyAuthInfo() {
      // given
      final AuthInfo authInfo = new AuthInfo();

      // when
      encodeDecode(authInfo);

      // then
      assertThat(authInfo.getFormat()).isEqualTo(AuthInfo.AuthDataFormat.UNKNOWN);
      assertThat(authInfo.getAuthData()).isEqualTo("");
      assertThat(authInfo.getClaims()).isEqualTo(Map.of());
    }

    private void encodeDecode(final AuthInfo authInfo) {
      // encode
      final UnsafeBuffer buffer = new UnsafeBuffer(new byte[authInfo.getLength()]);
      authInfo.write(buffer, 0);

      // decode
      authInfo.reset();
      authInfo.wrap(buffer, 0, buffer.capacity());
    }
  }

  @Nested
  class HasAnyClaimsTests {

    @Test
    void shouldReturnTrueWhenJwtFormatHasAuthData() {
      // given
      final AuthInfo authInfo = new AuthInfo();
      authInfo.setFormat(AuthInfo.AuthDataFormat.JWT);
      authInfo.setAuthData("valid-token");

      // when / then
      assertThat(authInfo.hasAnyClaims()).isTrue();
    }

    @Test
    void shouldReturnFalseWhenJwtFormatHasEmptyAuthData() {
      // given
      final AuthInfo authInfo = new AuthInfo();
      authInfo.setFormat(AuthInfo.AuthDataFormat.JWT);
      authInfo.setAuthData("");

      // when / then
      assertThat(authInfo.hasAnyClaims()).isFalse();
    }

    @Test
    void shouldReturnTrueWhenPreAuthorizedFormatHasClaims() {
      // given
      final AuthInfo authInfo = new AuthInfo();
      authInfo.setFormat(AuthInfo.AuthDataFormat.PRE_AUTHORIZED);
      authInfo.setClaims(Map.of("key", "value"));

      // when / then
      assertThat(authInfo.hasAnyClaims()).isTrue();
    }

    @Test
    void shouldReturnFalseWhenPreAuthorizedFormatHasEmptyClaims() {
      // given
      final AuthInfo authInfo = new AuthInfo();
      authInfo.setFormat(AuthInfo.AuthDataFormat.PRE_AUTHORIZED);
      authInfo.setClaims(Map.of());

      // when / then
      assertThat(authInfo.hasAnyClaims()).isFalse();
    }

    @Test
    void shouldReturnFalseWhenUnknownFormatHasEmptyClaims() {
      // given
      final AuthInfo authInfo = new AuthInfo();
      authInfo.setFormat(AuthInfo.AuthDataFormat.UNKNOWN);
      authInfo.setClaims(Map.of());

      // when / then
      assertThat(authInfo.hasAnyClaims()).isFalse();
    }

    @Test
    void shouldReturnTrueWhenUnknownFormatHasClaims() {
      // given
      final AuthInfo authInfo = new AuthInfo();
      authInfo.setFormat(AuthInfo.AuthDataFormat.UNKNOWN);
      authInfo.setClaims(Map.of("key", "value"));

      // when / then
      assertThat(authInfo.hasAnyClaims()).isTrue();
    }
  }

  @Nested
  class OfTests {
    @Test
    void shouldReturnNullWhenOfCalledWithNull() {
      // when
      final AuthInfo result = AuthInfo.of(null);

      // then
      assertThat(result).isNull();
    }

    @Test
    void shouldCopyAuthInfoWhenOfCalledWithNonNull() {
      // given
      final AuthInfo original = new AuthInfo();
      original.setFormat(AuthInfo.AuthDataFormat.JWT);
      original.setAuthData("test-token");
      original.setClaims(Map.of("claim1", "value1", "claim2", "value2"));

      // when
      final AuthInfo copy = AuthInfo.of(original);

      // then
      assertThat(copy).isNotNull();
      assertThat(copy).isNotSameAs(original);
      assertThat(copy.getFormat()).isEqualTo(original.getFormat());
      assertThat(copy.getAuthData()).isEqualTo(original.getAuthData());
      assertThat(copy.getClaims()).isEqualTo(original.getClaims());
    }
  }
}
