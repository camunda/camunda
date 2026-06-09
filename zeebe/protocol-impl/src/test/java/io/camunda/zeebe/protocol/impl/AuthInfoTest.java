/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
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
    void shouldReturnEmptyWhenOfClaimsCalledWithNull() {
      assertThat(AuthInfo.ofClaims(null)).isSameAs(AuthInfo.empty());
    }

    @Test
    void shouldReturnEmptyWhenOfClaimsCalledWithEmptyMap() {
      assertThat(AuthInfo.ofClaims(Map.of())).isSameAs(AuthInfo.empty());
    }

    @Test
    void shouldReturnFrozenAuthInfoWithClaims() {
      final Map<String, Object> claims = Map.of("user", "admin", "role", "operator");

      final AuthInfo result = AuthInfo.ofClaims(claims);

      assertThat(result.isFrozen()).isTrue();
      assertThat(result.getClaims()).isEqualTo(claims);
      assertThat(result.getFormat()).isEqualTo(AuthInfo.AuthDataFormat.UNKNOWN);
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
      assertThat(copy.isFrozen()).isTrue();
    }
  }

  @Nested
  class FreezeTests {

    @Test
    void shouldRejectResetWhenFrozen() {
      final AuthInfo authInfo = new AuthInfo();
      authInfo.freeze();

      assertThatThrownBy(authInfo::reset).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectWrapWhenFrozen() {
      final AuthInfo authInfo = new AuthInfo();
      authInfo.freeze();
      final var buffer = new UnsafeBuffer(new byte[10]);

      assertThatThrownBy(() -> authInfo.wrap(buffer, 0, buffer.capacity()))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectSetFormatWhenFrozen() {
      final AuthInfo authInfo = new AuthInfo();
      authInfo.freeze();

      assertThatThrownBy(() -> authInfo.setFormat(AuthInfo.AuthDataFormat.JWT))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectSetAuthDataWhenFrozen() {
      final AuthInfo authInfo = new AuthInfo();
      authInfo.freeze();

      assertThatThrownBy(() -> authInfo.setAuthData("token"))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectSetClaimsMapWhenFrozen() {
      final AuthInfo authInfo = new AuthInfo();
      authInfo.freeze();

      assertThatThrownBy(() -> authInfo.setClaims(Map.of("k", "v")))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectSetClaimsBufferWhenFrozen() {
      final AuthInfo authInfo = new AuthInfo();
      authInfo.freeze();

      assertThatThrownBy(() -> authInfo.setClaims(new UnsafeBuffer(new byte[0])))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldAllowGettersWhenFrozen() {
      final AuthInfo authInfo = new AuthInfo();
      authInfo.setFormat(AuthInfo.AuthDataFormat.PRE_AUTHORIZED);
      authInfo.setClaims(Map.of("key", "value"));
      authInfo.freeze();

      assertThat(authInfo.getFormat()).isEqualTo(AuthInfo.AuthDataFormat.PRE_AUTHORIZED);
      assertThat(authInfo.getAuthData()).isEqualTo("");
      assertThat(authInfo.getClaims()).isEqualTo(Map.of("key", "value"));
    }

    @Test
    void shouldAllowWriteWhenFrozen() {
      final AuthInfo authInfo = new AuthInfo();
      authInfo.setClaims(Map.of("key", "value"));
      authInfo.freeze();

      final var buffer = new UnsafeBuffer(new byte[authInfo.getLength()]);
      authInfo.write(buffer, 0);

      assertThat(buffer.capacity()).isGreaterThan(0);
    }

    @Test
    void shouldCacheToDecodedMapWhenFrozen() {
      final AuthInfo authInfo = new AuthInfo();
      authInfo.setFormat(AuthInfo.AuthDataFormat.PRE_AUTHORIZED);
      authInfo.setClaims(Map.of("key", "value"));
      authInfo.freeze();

      final var first = authInfo.toDecodedMap();
      final var second = authInfo.toDecodedMap();

      assertThat(first).isSameAs(second);
    }

    @Test
    void shouldNotCacheToDecodedMapWhenNotFrozen() {
      final AuthInfo authInfo = new AuthInfo();
      authInfo.setFormat(AuthInfo.AuthDataFormat.PRE_AUTHORIZED);
      authInfo.setClaims(Map.of("key", "value"));

      final var first = authInfo.toDecodedMap();
      final var second = authInfo.toDecodedMap();

      assertThat(first).isNotSameAs(second);
      assertThat(first).isEqualTo(second);
    }

    @Test
    void shouldFreezeOnCopyViaOf() {
      final AuthInfo original = new AuthInfo();
      original.setClaims(Map.of("key", "value"));

      final AuthInfo copy = AuthInfo.of(original);

      assertThat(copy.isFrozen()).isTrue();
      assertThat(original.isFrozen()).isFalse();
    }

    @Test
    void shouldCacheLengthWhenFrozen() {
      final AuthInfo authInfo = new AuthInfo();
      authInfo.setClaims(Map.of("key", "value"));

      final int lengthBeforeFreeze = authInfo.getLength();
      authInfo.freeze();

      // calling getLength() multiple times should return the same cached value
      assertThat(authInfo.getLength()).isEqualTo(lengthBeforeFreeze);
      assertThat(authInfo.getLength()).isEqualTo(lengthBeforeFreeze);
    }

    @Test
    void shouldCacheLengthConsistentWithWrite() {
      final AuthInfo authInfo = new AuthInfo();
      authInfo.setFormat(AuthInfo.AuthDataFormat.JWT);
      authInfo.setAuthData("some-token");
      authInfo.setClaims(Map.of("a", "b", "c", "d"));
      authInfo.freeze();

      final int length = authInfo.getLength();
      final var buffer = new UnsafeBuffer(new byte[length]);
      final int written = authInfo.write(buffer, 0);
      final var bb = authInfo.toDirectBuffer();

      assertThat(bb.capacity()).isEqualTo(length);
      assertThat(written).isEqualTo(length);
    }

    @Test
    void shouldCacheLengthForEmptyFrozenAuthInfo() {
      final AuthInfo authInfo = new AuthInfo();
      authInfo.freeze();

      assertThat(authInfo.getLength()).isEqualTo(new AuthInfo().getLength());
      assertThat(authInfo.getLength()).isEqualTo(AuthInfo.empty().getLength());
    }
  }

  @Nested
  class EmptyAuthInfoTests {
    @Test
    void shouldThrowOnWrap() {
      final var empty = AuthInfo.empty();
      final var buffer = new UnsafeBuffer(new byte[0]);

      assertThatThrownBy(() -> empty.wrap(buffer, 0, 0))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldThrowOnReset() {
      final var empty = AuthInfo.empty();

      assertThatThrownBy(empty::reset).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldReturnEmptyClaims() {
      final var empty = AuthInfo.empty();

      assertThat(empty.toDecodedMap()).isEmpty();
      assertThat(empty.hasAnyClaims()).isFalse();
    }

    @Test
    void shouldSerializeIdenticallyToNewAuthInfo() {
      final var empty = AuthInfo.empty();
      final var regular = new AuthInfo();

      assertThat(empty.getLength()).isEqualTo(regular.getLength());
      assertThat(empty.toDirectBuffer()).isEqualTo(regular.toDirectBuffer());
    }

    @Test
    void shouldWriteCorrectlyUnderConcurrentAccess() throws Exception {
      // given — expected bytes from a single-threaded write
      final var empty = AuthInfo.empty();
      final var expected = new UnsafeBuffer(new byte[empty.getLength()]);
      empty.write(expected, 0);

      final int threads = 8;
      final int iterations = 1_000;
      final var errors = new AtomicInteger(0);
      final var latch = new CountDownLatch(threads);

      // when — write concurrently from multiple threads
      for (int t = 0; t < threads; t++) {
        new Thread(
                () -> {
                  try {
                    for (int i = 0; i < iterations; i++) {
                      final var buf = new UnsafeBuffer(new byte[empty.getLength()]);
                      empty.write(buf, 0);
                      if (!buf.equals(expected)) {
                        errors.incrementAndGet();
                      }
                    }
                  } finally {
                    latch.countDown();
                  }
                })
            .start();
      }

      latch.await();

      // then — no corrupted writes
      assertThat(errors.get()).isZero();
    }
  }
}
