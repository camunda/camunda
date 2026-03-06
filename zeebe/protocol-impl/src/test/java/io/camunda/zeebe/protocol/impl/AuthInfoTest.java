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
import io.camunda.zeebe.protocol.impl.encoding.EmptyAuthInfo;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class AuthInfoTest {

  @RegressionTest("https://github.com/camunda/camunda/issues/35177")
  void shouldSanitizeOnToString() {
    // given
    final AuthInfo authInfo = AuthInfo.withJwt("token", Map.of("key", "value"));

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
      final String token = "token";
      final Map<String, Object> authInfoMap = Map.of("key", "value");
      final AuthInfo authInfo = AuthInfo.withJwt(token, authInfoMap);

      // when
      final var decoded = encodeDecode(authInfo);

      // then
      assertThat(decoded.getFormat()).isEqualTo(AuthInfo.AuthDataFormat.JWT);
      assertThat(decoded.getAuthData()).isEqualTo(token);
      assertThat(decoded.getClaims()).isEqualTo(authInfoMap);
    }

    @Test
    void shouldEncodeDecodeEmptyAuthInfo() {
      // given
      final AuthInfo authInfo = AuthInfo.empty();

      // when
      final var decoded = encodeDecode(authInfo);

      // then
      assertThat(decoded.getFormat()).isEqualTo(AuthInfo.AuthDataFormat.UNKNOWN);
      assertThat(decoded.getAuthData()).isEqualTo("");
      assertThat(decoded.toDecodedMap()).isEqualTo(Map.of());
    }

    private AuthInfo encodeDecode(final AuthInfo authInfo) {
      // encode
      final UnsafeBuffer buffer = new UnsafeBuffer(new byte[authInfo.getLength()]);
      authInfo.write(buffer, 0);

      // decode
      final var decoded = AuthInfo.mutable();
      decoded.wrap(buffer, 0, buffer.capacity());
      return decoded;
    }
  }

  @Nested
  class HasAnyClaimsTests {

    @Test
    void shouldReturnTrueWhenJwtFormatHasAuthData() {
      // given
      final AuthInfo authInfo = AuthInfo.withJwt("valid-token");

      // when / then
      assertThat(authInfo.hasAnyClaims()).isTrue();
    }

    @Test
    void shouldReturnFalseWhenJwtFormatHasEmptyAuthData() {
      // given
      final AuthInfo authInfo = AuthInfo.withJwt("");

      // when / then
      assertThat(authInfo.hasAnyClaims()).isFalse();
    }

    @Test
    void shouldReturnTrueWhenPreAuthorizedFormatHasClaims() {
      // given
      final AuthInfo authInfo = AuthInfo.preAuthorized(Map.of("key", "value"));

      // when / then
      assertThat(authInfo.hasAnyClaims()).isTrue();
    }

    @Test
    void shouldReturnFalseWhenPreAuthorizedFormatHasEmptyClaims() {
      // given
      final AuthInfo authInfo = AuthInfo.preAuthorized();

      // when / then
      assertThat(authInfo.hasAnyClaims()).isFalse();
    }

    @Test
    void shouldReturnFalseWhenUnknownFormatHasEmptyClaims() {
      // given
      final AuthInfo authInfo = AuthInfo.empty();

      // when / then
      assertThat(authInfo.hasAnyClaims()).isFalse();
    }

    @Test
    void shouldReturnTrueWhenUnknownFormatHasClaims() {
      // given
      final AuthInfo authInfo = AuthInfo.withClaims(Map.of("key", "value"));

      // when / then
      assertThat(authInfo.hasAnyClaims()).isTrue();
    }
  }

  @Nested
  class EmptyAuthInfoTests {
    @Test
    void shouldThrowOnWrap() {
      final var empty = EmptyAuthInfo.getInstance();
      final var buffer = new UnsafeBuffer(new byte[0]);

      assertThatThrownBy(() -> empty.wrap(buffer, 0, 0))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldThrowOnReset() {
      final var empty = EmptyAuthInfo.getInstance();

      assertThatThrownBy(empty::reset).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldReturnEmptyClaims() {
      final var empty = EmptyAuthInfo.getInstance();

      assertThat(empty.toDecodedMap()).isEmpty();
      assertThat(empty.toDecodedMap()).isEmpty();
      assertThat(empty.hasAnyClaims()).isFalse();
    }

    @Test
    void shouldSerializeIdenticallyToNewAuthInfo() {
      final var empty = EmptyAuthInfo.getInstance();
      final var regular = AuthInfo.empty();

      assertThat(empty.getLength()).isEqualTo(regular.getLength());
      assertThat(empty.toDirectBuffer()).isEqualTo(regular.toDirectBuffer());
    }

    @Test
    void shouldBeEqualToNewAuthInfo() {
      final var empty = EmptyAuthInfo.getInstance();
      final var regular = AuthInfo.empty();

      assertThat(empty).isEqualTo(regular);
    }

    @Test
    void shouldWriteCorrectlyUnderConcurrentAccess() throws Exception {
      // given — expected bytes from a single-threaded write
      final var empty = EmptyAuthInfo.getInstance();
      final var expected = new UnsafeBuffer(new byte[empty.getLength()]);
      empty.write(expected, 0);

      final int threads = 8;
      final int iterations = 1_000;
      final var errors = new java.util.concurrent.atomic.AtomicInteger(0);
      final var latch = new java.util.concurrent.CountDownLatch(threads);

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

  @Nested
  class FreezeTests {
    @Test
    void shouldThrowOnWrapAfterFreeze() {
      final var auth = AuthInfo.mutable();
      auth.freeze();
      final var buffer = new UnsafeBuffer(new byte[0]);

      assertThatThrownBy(() -> auth.wrap(buffer, 0, 0))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldThrowOnResetAfterFreeze() {
      final var auth = AuthInfo.mutable();
      auth.freeze();

      assertThatThrownBy(auth::reset).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldFreezeIdempotently() {
      final var auth = AuthInfo.mutable();
      auth.freeze();
      auth.freeze(); // second call should not throw

      assertThat(auth.isFrozen()).isTrue();
    }

    @Test
    void shouldMakeFactoryResultsImmutable() {
      final var buffer = new UnsafeBuffer(new byte[0]);

      assertThatThrownBy(() -> AuthInfo.withJwt("token").wrap(buffer, 0, 0))
          .isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(() -> AuthInfo.preAuthorized().wrap(buffer, 0, 0))
          .isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(() -> AuthInfo.withClaims(Map.of()).wrap(buffer, 0, 0))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldAllowMutationBeforeFreeze() {
      final var auth = AuthInfo.mutable();
      final var jwt = AuthInfo.withJwt("token");
      final var buffer = new UnsafeBuffer(new byte[jwt.getLength()]);
      jwt.write(buffer, 0);

      // should not throw — mutable instance can be wrapped
      auth.wrap(buffer, 0, jwt.getLength());
      assertThat(auth.getFormat()).isEqualTo(AuthInfo.AuthDataFormat.JWT);
    }
  }

  @Nested
  class OfTests {
    @Test
    void shouldReturnNullWhenOfCalledWithNull() {
      // when
      final AuthInfo result = AuthInfo.of((AuthInfo) null);

      // then
      assertThat(result).isNull();
    }

    @Test
    void shouldCopyAuthInfoWhenOfCalledWithNonNull() {
      // given
      final AuthInfo original =
          AuthInfo.withJwt("test-token", Map.of("claim1", "value1", "claim2", "value2"));

      // when
      final AuthInfo copy = AuthInfo.of(original);

      // then
      assertThat(copy).isNotNull();
      assertThat(copy).isNotSameAs(original);
      assertThat(copy.getFormat()).isEqualTo(original.getFormat());
      assertThat(copy.getAuthData()).isEqualTo(original.getAuthData());
      assertThat(copy.getClaims()).isEqualTo(original.getClaims());
    }

    @Test
    void shouldReturnEmptySingletonWhenOfBufferCalledWithEmptyAuthInfo() {
      // given — serialize an empty AuthInfo
      final var empty = AuthInfo.empty();
      final var buffer = new UnsafeBuffer(new byte[empty.getLength()]);
      empty.write(buffer, 0);

      // when
      final AuthInfo result = AuthInfo.of(buffer);

      // then — should return the empty singleton (avoids allocation)
      assertThat(result).isSameAs(AuthInfo.empty());
    }

    @Test
    void shouldDeserializePreAuthorizedFromBuffer() {
      // given — serialize a PRE_AUTHORIZED AuthInfo
      final var preAuth = AuthInfo.preAuthorized();
      final var buffer = new UnsafeBuffer(new byte[preAuth.getLength()]);
      preAuth.write(buffer, 0);

      // when
      final AuthInfo result = AuthInfo.of(buffer);

      // then — must NOT return null despite same byte length as empty
      assertThat(result).isNotNull();
      assertThat(result.getFormat()).isEqualTo(AuthInfo.AuthDataFormat.PRE_AUTHORIZED);
    }

    @Test
    void shouldDeserializePreAuthorizedWithClaimsFromBuffer() {
      // given — serialize a PRE_AUTHORIZED AuthInfo with claims
      final Map<String, Object> claims = Map.of("user", "admin", "role", "operator");
      final var preAuth = AuthInfo.preAuthorized(claims);
      final var buffer = new UnsafeBuffer(new byte[preAuth.getLength()]);
      preAuth.write(buffer, 0);

      // when
      final AuthInfo result = AuthInfo.of(buffer);

      // then
      assertThat(result).isNotNull();
      assertThat(result.getFormat()).isEqualTo(AuthInfo.AuthDataFormat.PRE_AUTHORIZED);
      assertThat(result.toDecodedMap()).isEqualTo(claims);
    }
  }
}
