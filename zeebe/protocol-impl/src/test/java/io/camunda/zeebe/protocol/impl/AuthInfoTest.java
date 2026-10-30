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
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
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
    authInfo.setFormat(AuthInfo.AuthDataFormat.PRE_AUTHORIZED);
    authInfo.setClaims(Map.of("key", "value"));

    // when
    final var authInfoString = authInfo.toString();

    // then
    assertThat(authInfoString)
        .isEqualTo(
            """
        {"format":"PRE_AUTHORIZED","claims":"***"}""");
  }

  @Test
  void shouldReadAuthInfoOfPreviousVersionsRepeatedly() {
    // given - previous versions also wrote the removed authData property
    final Map<String, Object> claims = Map.of("authorized_username", "user");
    final var previousVersion =
        new UnsafeBuffer(
            MsgPackConverter.convertToMsgPack(
                Map.of(
                    "format",
                    "PRE_AUTHORIZED",
                    "authData",
                    "",
                    "claims",
                    MsgPackConverter.convertToMsgPack(claims))));
    final var authInfo = new AuthInfo();

    // when - the same instance is reused for every record, e.g. by the record metadata
    authInfo.wrap(previousVersion);
    authInfo.wrap(previousVersion);

    // then
    assertThat(authInfo.getLength()).isEqualTo(previousVersion.capacity());
    assertThat(AuthInfo.of(authInfo).getClaims()).isEqualTo(claims);
  }

  @Nested
  class EncodeDecodeTests {
    @Test
    void shouldEncodeDecodeAuthInfo() {
      // given
      final AuthInfo authInfo = new AuthInfo();
      final Map<String, Object> authInfoMap = Map.of("key", "value");
      authInfo.setFormat(AuthInfo.AuthDataFormat.PRE_AUTHORIZED);
      authInfo.setClaims(authInfoMap);

      // when
      encodeDecode(authInfo);

      // then
      assertThat(authInfo.getFormat()).isEqualTo(AuthInfo.AuthDataFormat.PRE_AUTHORIZED);
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
      original.setFormat(AuthInfo.AuthDataFormat.PRE_AUTHORIZED);
      original.setClaims(Map.of("claim1", "value1", "claim2", "value2"));

      // when
      final AuthInfo copy = AuthInfo.of(original);

      // then
      assertThat(copy).isNotNull();
      assertThat(copy).isNotSameAs(original);
      assertThat(copy.getFormat()).isEqualTo(original.getFormat());
      assertThat(copy.getClaims()).isEqualTo(original.getClaims());
    }
  }
}
