/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl;

import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_ANONYMOUS_USER;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_CLIENT_ID;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_TENANTS;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_USERNAME;
import static io.camunda.zeebe.auth.Authorization.USER_GROUPS_CLAIMS;
import static io.camunda.zeebe.auth.Authorization.USER_TOKEN_CLAIMS;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.protocol.impl.AuthInfoTest.LegacyAuthInfo;
import io.camunda.zeebe.protocol.impl.AuthInfoTest.LegacyFormat;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo.AuthDataFormat;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;
import net.jqwik.api.Tuple.Tuple2;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Checks the encoding and equality of {@link AuthInfo} for arbitrary claims. These live in their
 * own class, as jqwik properties are not run inside JUnit {@code @Nested} classes.
 */
final class AuthInfoPropertyTest {

  private static final int MAX_DEPTH = 2;

  @Property
  void shouldRoundTripThroughEncoding(@ForAll("authInfos") final AuthInfo authInfo) {
    // when
    final var encoded = encode(authInfo);
    final var decoded = decode(encoded);

    // then
    assertThat(decoded).isEqualTo(authInfo);
    assertThat(decoded.hashCode()).isEqualTo(authInfo.hashCode());
    assertThat(encode(decoded)).isEqualTo(encoded);
  }

  @Property
  void shouldRoundTripWhenEmbeddedInRecord(@ForAll("authInfos") final AuthInfo authInfo) {
    // given
    final var record = new CommandDistributionRecord().setPartitionId(1).setAuthInfo(authInfo);

    // when
    final var decoded = new CommandDistributionRecord();
    decoded.wrap(encode(record));

    // then
    assertThat(decoded.getAuthInfo()).isEqualTo(authInfo);
    assertThat(decoded.getAuthInfo().hashCode()).isEqualTo(authInfo.hashCode());
  }

  @Property
  void shouldBeReadableByPreviousVersion(@ForAll("authInfos") final AuthInfo authInfo) {
    // given
    final var legacy = new LegacyAuthInfo();

    // when
    legacy.wrap(encode(authInfo));

    // then
    assertThat(legacy.format.getValue().name()).isEqualTo(authInfo.getFormat().name());
    assertThat(BufferUtil.bufferAsString(legacy.authData.getValue())).isEmpty();
    assertThat(MsgPackConverter.convertToMap(legacy.claims.getValue()))
        .usingRecursiveComparison()
        .isEqualTo(authInfo.toClaimsMap());
  }

  @Property
  void shouldReadAuthInfoWrittenByPreviousVersion(@ForAll("authInfos") final AuthInfo authInfo) {
    // given
    final var legacy =
        new LegacyAuthInfo(
            LegacyFormat.valueOf(authInfo.getFormat().name()),
            new LinkedHashMap<>(authInfo.toClaimsMap()));

    // when
    final var decoded = decode(encode(legacy));

    // then
    assertThat(decoded.getFormat()).isEqualTo(authInfo.getFormat());
    assertThat(decoded.toClaimsMap()).usingRecursiveComparison().isEqualTo(authInfo.toClaimsMap());
  }

  @Property
  void shouldEqualItselfAndItsDecodedCopyWithBinaryTokenClaims(
      @ForAll("authInfosWithBinaryTokenClaims") final AuthInfo authInfo) {
    // given
    final int hashCode = authInfo.hashCode();

    // when
    final var decoded = decode(encode(authInfo));

    // then
    assertThat(authInfo).isEqualTo(authInfo);
    assertThat(authInfo.hashCode()).isEqualTo(hashCode);
    assertThat(decoded).isEqualTo(authInfo);
    assertThat(authInfo).isEqualTo(decoded);
    assertThat(decoded.hashCode()).isEqualTo(hashCode);
  }

  @Property
  void shouldHaveEqualHashCodesWhenEqual(@ForAll("pairs") final Tuple2<AuthInfo, AuthInfo> pair) {
    // given
    final var a = pair.get1();
    final var b = pair.get2();

    // then
    assertThat(a).isEqualTo(a);
    assertThat(b).isEqualTo(b);
    assertThat(a.equals(b)).isEqualTo(b.equals(a));
    if (a.equals(b)) {
      assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
  }

  @Provide
  Arbitrary<Tuple2<AuthInfo, AuthInfo>> pairs() {
    return authInfos()
        .flatMap(
            a ->
                Arbitraries.of(true, false)
                    .flatMap(
                        copy ->
                            copy
                                ? Arbitraries.just(Tuple.of(a, decode(encode(a))))
                                : authInfos().map(b -> Tuple.of(a, b))));
  }

  @Provide
  Arbitrary<AuthInfo> authInfos() {
    return authInfos(tokenClaims().injectNull(0.5));
  }

  @Provide
  Arbitrary<AuthInfo> authInfosWithBinaryTokenClaims() {
    return authInfos(
        Combinators.combine(tokenClaims(), strings(), binary())
            .as(
                (claims, key, blob) -> {
                  final var withBlob = new LinkedHashMap<>(claims);
                  withBlob.put(key, blob);
                  return withBlob;
                }));
  }

  private static Arbitrary<AuthInfo> authInfos(final Arbitrary<Map<String, Object>> tokenClaims) {
    return Combinators.combine(
            Arbitraries.of(AuthDataFormat.class),
            Arbitraries.of(true, false).injectNull(0.5),
            strings().injectNull(0.5),
            strings().injectNull(0.5),
            strings().list().ofMaxSize(4).injectNull(0.5),
            strings().list().ofMaxSize(4).injectNull(0.5),
            tokenClaims)
        .as(AuthInfoPropertyTest::authInfo);
  }

  private static AuthInfo authInfo(
      final AuthDataFormat format,
      final Boolean anonymous,
      final String username,
      final String clientId,
      final List<String> tenants,
      final List<String> groups,
      final Map<String, Object> tokenClaims) {
    // absent claims are null, which ofClaims ignores
    final var claims = new LinkedHashMap<String, Object>();
    claims.put(AUTHORIZED_ANONYMOUS_USER, anonymous);
    claims.put(AUTHORIZED_USERNAME, username);
    claims.put(AUTHORIZED_CLIENT_ID, clientId);
    claims.put(AUTHORIZED_TENANTS, tenants);
    claims.put(USER_GROUPS_CLAIMS, groups);
    claims.put(USER_TOKEN_CLAIMS, tokenClaims);
    return AuthInfo.ofClaims(claims).withFormat(format);
  }

  private static Arbitrary<String> strings() {
    return Arbitraries.integers()
        .between(0, Character.MAX_CODE_POINT)
        .filter(
            codePoint -> codePoint < Character.MIN_SURROGATE || codePoint > Character.MAX_SURROGATE)
        .list()
        .ofMaxSize(8)
        .map(
            codePoints -> {
              final var builder = new StringBuilder();
              codePoints.forEach(builder::appendCodePoint);
              return builder.toString();
            });
  }

  private static Arbitrary<byte[]> binary() {
    return Arbitraries.bytes().array(byte[].class).ofMaxSize(8);
  }

  private static Arbitrary<Map<String, Object>> tokenClaims() {
    return map(MAX_DEPTH);
  }

  private static Arbitrary<Map<String, Object>> map(final int depth) {
    return Arbitraries.maps(strings(), value(depth)).ofMaxSize(4).map(LinkedHashMap::new);
  }

  private static Arbitrary<Object> value(final int depth) {
    final List<Arbitrary<Object>> options = new ArrayList<>();
    options.add(strings().map(v -> v));
    options.add(Arbitraries.longs().map(v -> v));
    options.add(Arbitraries.of(true, false).map(v -> v));
    options.add(Arbitraries.doubles().map(v -> v));
    options.add(binary().map(v -> v));
    if (depth > 0) {
      options.add(value(depth - 1).list().ofMaxSize(3).map(v -> v));
      options.add(map(depth - 1).map(v -> v));
    }
    return Arbitraries.oneOf(options);
  }

  private static DirectBuffer encode(final AuthInfo authInfo) {
    final var buffer = new UnsafeBuffer(new byte[authInfo.getLength()]);
    authInfo.write(buffer, 0);
    return buffer;
  }

  private static DirectBuffer encode(final UnpackedObject object) {
    final var buffer = new UnsafeBuffer(new byte[object.getLength()]);
    object.write(buffer, 0);
    return buffer;
  }

  private static AuthInfo decode(final DirectBuffer buffer) {
    return AuthInfo.decode(buffer, 0, buffer.capacity());
  }
}
