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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo.AuthDataFormat;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class AuthInfoTest {

  private static final Map<String, Object> ALL_CLAIMS =
      Map.of(
          AUTHORIZED_ANONYMOUS_USER,
          false,
          AUTHORIZED_USERNAME,
          "user",
          AUTHORIZED_CLIENT_ID,
          "client",
          AUTHORIZED_TENANTS,
          List.of("tenant-a", "tenant-b"),
          USER_GROUPS_CLAIMS,
          List.of("group-a", "group-b"),
          USER_TOKEN_CLAIMS,
          Map.of("sub", "user", "roles", List.of("reader", "writer"), "level", 3));

  @RegressionTest("https://github.com/camunda/camunda/issues/35177")
  void shouldSanitizeOnToString() {
    // given
    final var authInfo =
        AuthInfo.ofClaims(
                Map.of(
                    AUTHORIZED_USERNAME,
                    "secret-user",
                    USER_TOKEN_CLAIMS,
                    Map.of("email", "secret@example.com")))
            .withFormat(AuthDataFormat.PRE_AUTHORIZED);

    // when
    final var string = authInfo.toString();

    // then
    assertThat(string).contains("PRE_AUTHORIZED").doesNotContain("secret");
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

  @Nested
  final class Accessors {

    @Test
    void shouldExposeClaimsThroughTypedAccessors() {
      // when
      final var authInfo = AuthInfo.ofClaims(ALL_CLAIMS);

      // then
      assertThat(authInfo.getFormat()).isEqualTo(AuthDataFormat.UNKNOWN);
      assertThat(authInfo.isAnonymous()).isFalse();
      assertThat(authInfo.getUsername()).isEqualTo("user");
      assertThat(authInfo.getClientId()).isEqualTo("client");
      assertThat(authInfo.getTenants()).containsExactly("tenant-a", "tenant-b");
      assertThat(authInfo.getGroups()).containsExactly("group-a", "group-b");
      assertThat(authInfo.getTokenClaims())
          .isEqualTo(Map.of("sub", "user", "roles", List.of("reader", "writer"), "level", 3));
      assertThat(authInfo.toClaimsMap()).isEqualTo(ALL_CLAIMS);
      assertThat(authInfo.hasAnyClaims()).isTrue();
    }

    @Test
    void shouldReturnNullForAbsentClaims() {
      // when
      final var authInfo = AuthInfo.ofClaims(Map.of());

      // then
      assertThat(authInfo.isAnonymous()).isFalse();
      assertThat(authInfo.getUsername()).isNull();
      assertThat(authInfo.getClientId()).isNull();
      assertThat(authInfo.getTenants()).isNull();
      assertThat(authInfo.getGroups()).isNull();
      assertThat(authInfo.getTokenClaims()).isNull();
      assertThat(authInfo.toClaimsMap()).isEmpty();
      assertThat(authInfo.hasAnyClaims()).isFalse();
      assertThat(authInfo).isEqualTo(AuthInfo.EMPTY);
    }

    @Test
    void shouldBeAnonymousWhenClaimIsTrue() {
      // when
      final var authInfo = AuthInfo.ofClaims(Map.of(AUTHORIZED_ANONYMOUS_USER, true));

      // then
      assertThat(authInfo.isAnonymous()).isTrue();
      assertThat(authInfo.hasAnyClaims()).isTrue();
    }

    @Test
    void shouldIgnoreUnknownAndNullClaims() {
      // given
      final var claims = new HashMap<String, Object>();
      claims.put("is_camunda_groups_enabled", true);
      claims.put(AUTHORIZED_USERNAME, null);
      claims.put(AUTHORIZED_CLIENT_ID, "client");

      // when
      final var authInfo = AuthInfo.ofClaims(claims);

      // then
      assertThat(authInfo.toClaimsMap()).isEqualTo(Map.of(AUTHORIZED_CLIENT_ID, "client"));
    }

    @Test
    void shouldReturnTokenClaimsAsDecoded() {
      // given
      final var tokenClaims = new HashMap<String, Object>();
      tokenClaims.put("exp", 1_700_000_000L);
      tokenClaims.put("middle_name", null);
      tokenClaims.put("roles", new String[] {"reader"});

      // when
      final var authInfo = AuthInfo.ofClaims(Map.of(USER_TOKEN_CLAIMS, tokenClaims));

      // then
      assertThat(authInfo.getTokenClaims())
          .containsEntry("exp", 1_700_000_000)
          .containsEntry("middle_name", null)
          .containsEntry("roles", List.of("reader"))
          .isEqualTo(decode(encode(authInfo)).getTokenClaims());
    }

    @Test
    void shouldRejectClaimsOfUnexpectedType() {
      assertThatThrownBy(() -> AuthInfo.ofClaims(Map.of(AUTHORIZED_USERNAME, 42)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(AUTHORIZED_USERNAME);
      assertThatThrownBy(() -> AuthInfo.ofClaims(Map.of(USER_GROUPS_CLAIMS, List.of(1))))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(USER_GROUPS_CLAIMS);
      assertThatThrownBy(() -> AuthInfo.ofClaims(Map.of(USER_TOKEN_CLAIMS, "not-a-map")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(USER_TOKEN_CLAIMS);
    }

    @Test
    void shouldChangeFormatWithoutChangingClaims() {
      // given
      final var original = AuthInfo.ofClaims(ALL_CLAIMS);

      // when
      final var preAuthorized = original.withFormat(AuthDataFormat.PRE_AUTHORIZED);

      // then
      assertThat(preAuthorized.getFormat()).isEqualTo(AuthDataFormat.PRE_AUTHORIZED);
      assertThat(preAuthorized.toClaimsMap()).isEqualTo(ALL_CLAIMS);
      assertThat(decode(encode(preAuthorized)).getFormat())
          .isEqualTo(AuthDataFormat.PRE_AUTHORIZED);
      assertThat(original.getFormat()).isEqualTo(AuthDataFormat.UNKNOWN);
    }
  }

  @Nested
  final class Immutability {

    @Test
    void shouldNotReflectChangesToInputClaims() {
      // given
      final var groups = new ArrayList<>(List.of("group-a"));
      final var tokenClaims = new HashMap<String, Object>(Map.of("sub", "user"));
      final var claims = new HashMap<String, Object>();
      claims.put(USER_GROUPS_CLAIMS, groups);
      claims.put(USER_TOKEN_CLAIMS, tokenClaims);
      final var authInfo = AuthInfo.ofClaims(claims);

      // when
      groups.add("group-b");
      tokenClaims.put("sub", "someone-else");
      claims.put(AUTHORIZED_USERNAME, "someone-else");

      // then
      assertThat(authInfo.toClaimsMap())
          .isEqualTo(
              Map.of(
                  USER_GROUPS_CLAIMS,
                  List.of("group-a"),
                  USER_TOKEN_CLAIMS,
                  Map.of("sub", "user")));
    }

    @Test
    void shouldNotReflectChangesToArraysInInputTokenClaims() {
      // given
      final var roles = new String[] {"reader"};
      final var authInfo = AuthInfo.ofClaims(Map.of(USER_TOKEN_CLAIMS, Map.of("roles", roles)));

      // when
      roles[0] = "admin";

      // then
      assertThat(authInfo.getTokenClaims()).containsEntry("roles", List.of("reader"));
      assertThat(decode(encode(authInfo)).getTokenClaims()).isEqualTo(authInfo.getTokenClaims());
    }

    @Test
    void shouldNotReflectChangesToReturnedTokenClaimValues() {
      // given
      final var authInfo =
          decode(
              encode(AuthInfo.ofClaims(Map.of(USER_TOKEN_CLAIMS, Map.of("key", new byte[] {1})))));

      // when
      ((byte[]) authInfo.getTokenClaims().get("key"))[0] = 2;

      // then
      assertThat(authInfo.getTokenClaims()).containsEntry("key", new byte[] {1});
    }

    @Test
    void shouldNotAllowChangingClaims() {
      // given
      final var authInfo = AuthInfo.ofClaims(ALL_CLAIMS);

      // then
      assertThatThrownBy(() -> authInfo.toClaimsMap().put(AUTHORIZED_USERNAME, "other"))
          .isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(() -> authInfo.getGroups().add("other"))
          .isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(() -> authInfo.getTenants().add("other"))
          .isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(() -> authInfo.getTokenClaims().put("sub", "other"))
          .isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(() -> ((List<Object>) authInfo.getTokenClaims().get("roles")).add("admin"))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldNotReflectChangesToDecodedBuffer() {
      // given
      final var buffer =
          new UnsafeBuffer(BufferUtil.cloneBuffer(encode(AuthInfo.ofClaims(ALL_CLAIMS))));
      final var authInfo = decode(buffer);

      // when
      buffer.setMemory(0, buffer.capacity(), (byte) 0);

      // then
      assertThat(authInfo.toClaimsMap()).isEqualTo(ALL_CLAIMS);
      assertThat(decode(encode(authInfo)).toClaimsMap()).isEqualTo(ALL_CLAIMS);
    }

    @Test
    void shouldNotChangeWhenContainingRecordIsReused() {
      // given
      final var record = new CommandDistributionRecord();
      record.wrap(
          encode(
              new CommandDistributionRecord()
                  .setPartitionId(1)
                  .setAuthInfo(AuthInfo.ofClaims(Map.of(AUTHORIZED_USERNAME, "first")))));
      final var authInfo = record.getAuthInfo();

      // when
      record.wrap(
          encode(
              new CommandDistributionRecord()
                  .setPartitionId(1)
                  .setAuthInfo(AuthInfo.ofClaims(Map.of(AUTHORIZED_USERNAME, "second")))));

      // then
      assertThat(authInfo.getUsername()).isEqualTo("first");
      assertThat(record.getAuthInfo().getUsername()).isEqualTo("second");
    }
  }

  @Nested
  final class Encoding {

    @Test
    void shouldPreserveEmptyClaims() {
      // given
      final var authInfo =
          AuthInfo.ofClaims(
              Map.of(
                  AUTHORIZED_USERNAME,
                  "",
                  AUTHORIZED_TENANTS,
                  List.of(),
                  USER_GROUPS_CLAIMS,
                  List.of(),
                  USER_TOKEN_CLAIMS,
                  Map.of()));

      // when
      final var decoded = decode(encode(authInfo));

      // then
      assertThat(decoded.getUsername()).isEmpty();
      assertThat(decoded.getTenants()).isEmpty();
      assertThat(decoded.getGroups()).isEmpty();
      assertThat(decoded.getTokenClaims()).isEmpty();
      assertThat(decoded.getClientId()).isNull();
      assertThat(decoded.hasAnyClaims()).isTrue();
    }

    @Test
    void shouldDecodeEmptyBufferAsEmptyAuthInfo() {
      // when
      final var authInfo = AuthInfo.decode(new UnsafeBuffer(new byte[0]), 0, 0);

      // then
      assertThat(authInfo).isEqualTo(AuthInfo.EMPTY);
      assertThat(authInfo.getFormat()).isEqualTo(AuthDataFormat.UNKNOWN);
      assertThat(authInfo.hasAnyClaims()).isFalse();
    }
  }

  @Nested
  final class Equality {

    @Test
    void shouldNotEqualAuthInfoWithDifferentTokenClaims() {
      // given
      final var authInfo =
          AuthInfo.ofClaims(Map.of(USER_TOKEN_CLAIMS, Map.of("blob", new byte[] {1, 2, 3})));
      final var other =
          AuthInfo.ofClaims(Map.of(USER_TOKEN_CLAIMS, Map.of("blob", new byte[] {1, 2, 4})));
      final var withoutTokenClaims = AuthInfo.ofClaims(Map.of(AUTHORIZED_USERNAME, "user"));

      // when - then
      assertThat(authInfo).isNotEqualTo(other).isNotEqualTo(withoutTokenClaims);
      assertThat(withoutTokenClaims).isNotEqualTo(authInfo);
    }
  }

  @Nested
  final class Compatibility {

    @Test
    void shouldOnlyWriteClaimsThatArePresent() {
      // given
      final var authInfo = AuthInfo.ofClaims(Map.of(AUTHORIZED_USERNAME, "user"));
      final var legacy = new LegacyAuthInfo();

      // when
      legacy.wrap(encode(authInfo));

      // then
      assertThat(MsgPackConverter.convertToMap(legacy.claims.getValue()))
          .isEqualTo(Map.of(AUTHORIZED_USERNAME, "user"));
    }

    @Test
    void shouldWriteEmptyAuthInfoReadableByPreviousVersion() {
      // given
      final var legacy = new LegacyAuthInfo();

      // when
      legacy.wrap(encode(AuthInfo.EMPTY));

      // then
      assertThat(legacy.format.getValue()).isEqualTo(LegacyFormat.UNKNOWN);
      assertThat(MsgPackConverter.convertToMap(legacy.claims.getValue())).isEmpty();
    }

    @Test
    void shouldIgnoreUnknownClaimsWrittenByPreviousVersion() {
      // given
      final var legacy =
          new LegacyAuthInfo(
              LegacyFormat.UNKNOWN,
              Map.of("is_camunda_groups_enabled", true, AUTHORIZED_USERNAME, "user"));

      // when
      final var authInfo = decode(encode(legacy));

      // then
      assertThat(authInfo.toClaimsMap()).isEqualTo(Map.of(AUTHORIZED_USERNAME, "user"));
    }

    @Test
    void shouldDecodeUnsupportedFormatAsUnknown() {
      // given
      final var legacy = new LegacyAuthInfo(LegacyFormat.JWT, Map.of());
      legacy.authData.setValue("some.jwt.token");

      // when
      final var authInfo = decode(encode(legacy));

      // then
      assertThat(authInfo.getFormat()).isEqualTo(AuthDataFormat.UNKNOWN);
      assertThat(authInfo.hasAnyClaims()).isFalse();
    }

    @Test
    void shouldReadAuthInfoEmbeddedByPreviousVersion() {
      // given
      final var legacy = new LegacyDistributionRecord();
      legacy.partitionId.setValue(1);
      legacy.authInfo.getValue().set(LegacyFormat.PRE_AUTHORIZED, ALL_CLAIMS);
      final var record = new CommandDistributionRecord();

      // when
      record.wrap(encode(legacy));

      // then
      assertThat(record.getAuthInfo().getFormat()).isEqualTo(AuthDataFormat.PRE_AUTHORIZED);
      assertThat(record.getAuthInfo().toClaimsMap()).isEqualTo(ALL_CLAIMS);
    }

    @Test
    void shouldWriteEmbeddedAuthInfoReadableByPreviousVersion() {
      // given
      final var record =
          new CommandDistributionRecord()
              .setPartitionId(1)
              .setAuthInfo(AuthInfo.ofClaims(ALL_CLAIMS).withFormat(AuthDataFormat.PRE_AUTHORIZED));
      final var legacy = new LegacyDistributionRecord();

      // when
      legacy.wrap(encode(record));

      // then
      final var legacyAuthInfo = legacy.authInfo.getValue();
      assertThat(legacyAuthInfo.format.getValue()).isEqualTo(LegacyFormat.PRE_AUTHORIZED);
      assertThat(MsgPackConverter.convertToMap(legacyAuthInfo.claims.getValue()))
          .isEqualTo(ALL_CLAIMS);
    }
  }

  /** The formats known to previous versions, including the removed {@code JWT} format. */
  enum LegacyFormat {
    UNKNOWN,
    JWT,
    PRE_AUTHORIZED
  }

  /** Mirrors the {@code AuthInfo} of previous versions to produce and verify their encoding. */
  static final class LegacyAuthInfo extends UnpackedObject {
    final EnumProperty<LegacyFormat> format =
        new EnumProperty<>("format", LegacyFormat.class, LegacyFormat.UNKNOWN);
    final StringProperty authData = new StringProperty("authData", "");
    final DocumentProperty claims = new DocumentProperty("claims");

    LegacyAuthInfo() {
      super(3);
      declareProperty(format).declareProperty(authData).declareProperty(claims);
    }

    LegacyAuthInfo(final LegacyFormat format, final Map<String, Object> claims) {
      this();
      set(format, claims);
    }

    void set(final LegacyFormat format, final Map<String, Object> claims) {
      this.format.setValue(format);
      this.claims.setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(claims)));
    }
  }

  /** Mirrors the parts of a {@code CommandDistributionRecord} relevant to the auth info. */
  static final class LegacyDistributionRecord extends UnpackedObject {
    private final IntegerProperty partitionId = new IntegerProperty("partitionId");
    private final ObjectProperty<LegacyAuthInfo> authInfo =
        new ObjectProperty<>("authInfo", new LegacyAuthInfo());

    LegacyDistributionRecord() {
      super(2);
      declareProperty(partitionId).declareProperty(authInfo);
    }
  }
}
