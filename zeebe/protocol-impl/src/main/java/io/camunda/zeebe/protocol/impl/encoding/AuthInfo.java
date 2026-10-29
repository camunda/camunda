/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_ANONYMOUS_USER;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_CLIENT_ID;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_TENANTS;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_USERNAME;
import static io.camunda.zeebe.auth.Authorization.USER_GROUPS_CLAIMS;
import static io.camunda.zeebe.auth.Authorization.USER_TOKEN_CLAIMS;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import io.camunda.zeebe.msgpack.spec.MsgPackCodes;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The authorization claims of the principal that issued a command.
 *
 * <p>Instances are immutable, including all returned collections, and can be shared freely, e.g.
 * between a command and its follow-up records, without copying.
 *
 * <p>The encoding is the same as in previous versions: a msgpack map {@code {"format": <name>,
 * "claims": <msgpack map, wrapped as binary>}}. Only claims that are present are written, since
 * consumers treat absent claims differently from empty ones: for example, absent groups are
 * resolved from state. On read, unknown claims and properties (such as the legacy {@code authData})
 * are ignored, and unknown formats are read as {@link AuthDataFormat#UNKNOWN}.
 *
 * <p>The token claims are kept in their msgpack encoding, as they may contain values of any type
 * and are only needed to resolve the principal's memberships. See {@link #getTokenClaims()}.
 */
@NullMarked
@JsonIncludeProperties({"format", "claims"})
public final class AuthInfo implements BufferWriter {

  /** With {@link AuthDataFormat#UNKNOWN} format and no claims. */
  public static final AuthInfo EMPTY =
      new AuthInfo(AuthDataFormat.UNKNOWN, null, null, null, null, null, null, null);

  private static final String FORMAT_KEY = "format";
  private static final String CLAIMS_KEY = "claims";

  private static final DirectBuffer FORMAT_KEY_BUFFER = BufferUtil.wrapString(FORMAT_KEY);
  private static final DirectBuffer CLAIMS_KEY_BUFFER = BufferUtil.wrapString(CLAIMS_KEY);
  private static final DirectBuffer ANONYMOUS_KEY_BUFFER =
      BufferUtil.wrapString(AUTHORIZED_ANONYMOUS_USER);
  private static final DirectBuffer USERNAME_KEY_BUFFER =
      BufferUtil.wrapString(AUTHORIZED_USERNAME);
  private static final DirectBuffer CLIENT_ID_KEY_BUFFER =
      BufferUtil.wrapString(AUTHORIZED_CLIENT_ID);
  private static final DirectBuffer TENANTS_KEY_BUFFER = BufferUtil.wrapString(AUTHORIZED_TENANTS);
  private static final DirectBuffer GROUPS_KEY_BUFFER = BufferUtil.wrapString(USER_GROUPS_CLAIMS);
  private static final DirectBuffer TOKEN_CLAIMS_KEY_BUFFER =
      BufferUtil.wrapString(USER_TOKEN_CLAIMS);

  private final AuthDataFormat format;
  private final @Nullable Boolean anonymous;
  private final @Nullable String username;
  private final @Nullable String clientId;
  private final @Nullable List<String> tenants;
  private final @Nullable List<String> groups;
  private final @Nullable DirectBuffer tokenClaims;

  /**
   * The msgpack encoding: the copied source bytes of decoded instances, otherwise encoded on first
   * use, as some instances are never written (e.g. when only used for an authorization check).
   */
  private volatile @Nullable DirectBuffer encoded;

  private AuthInfo(
      final AuthDataFormat format,
      final @Nullable Boolean anonymous,
      final @Nullable String username,
      final @Nullable String clientId,
      final @Nullable List<String> tenants,
      final @Nullable List<String> groups,
      final @Nullable DirectBuffer tokenClaims,
      final @Nullable DirectBuffer encoded) {
    this.format = format;
    this.anonymous = anonymous;
    this.username = username;
    this.clientId = clientId;
    this.tenants = tenants;
    this.groups = groups;
    this.tokenClaims = tokenClaims;
    this.encoded = encoded;
  }

  /**
   * Creates an auth info with format {@link AuthDataFormat#UNKNOWN} from the given claims, keyed by
   * the names defined in {@link io.camunda.zeebe.auth.Authorization}. Unknown claims and claims
   * with a {@code null} value are ignored. The claims are copied, so later changes to them have no
   * effect.
   *
   * @throws IllegalArgumentException if a claim has an unexpected type
   */
  public static AuthInfo ofClaims(final Map<String, ?> claims) {
    final var authInfo =
        new AuthInfo(
            AuthDataFormat.UNKNOWN,
            claim(claims, AUTHORIZED_ANONYMOUS_USER, Boolean.class),
            claim(claims, AUTHORIZED_USERNAME, String.class),
            claim(claims, AUTHORIZED_CLIENT_ID, String.class),
            stringsClaim(claims, AUTHORIZED_TENANTS),
            stringsClaim(claims, USER_GROUPS_CLAIMS),
            tokenClaims(claims),
            null);
    return authInfo.hasAnyClaims() ? authInfo : EMPTY;
  }

  /**
   * Decodes an auth info from its msgpack encoding, copying the bytes: later changes to the buffer
   * have no effect. An empty range, which is how SBE messages encode an absent authorization,
   * decodes to {@link #EMPTY}, although {@code EMPTY} itself is written as a non-empty msgpack map.
   */
  public static AuthInfo decode(final DirectBuffer buffer, final int offset, final int length) {
    if (length == 0) {
      return EMPTY;
    }

    final var bytes = new byte[length];
    buffer.getBytes(offset, bytes);
    return decode(new UnsafeBuffer(bytes));
  }

  /** Decodes an auth info embedded in another msgpack value, e.g. as property of an object. */
  public static AuthInfo read(final MsgPackReader reader) {
    final int offset = reader.getOffset();
    reader.skipValue();
    return decode(reader.getBuffer(), offset, reader.getOffset() - offset);
  }

  /** Returns an auth info with the same claims, but the given format. */
  public AuthInfo withFormat(final AuthDataFormat format) {
    if (format == this.format) {
      return this;
    }

    return new AuthInfo(format, anonymous, username, clientId, tenants, groups, tokenClaims, null);
  }

  public AuthDataFormat getFormat() {
    return format;
  }

  /** Returns true if the command was issued by an anonymous user, i.e. without authentication. */
  public boolean isAnonymous() {
    return Boolean.TRUE.equals(anonymous);
  }

  /** Returns the username of the authenticated user, or null if not authenticated as a user. */
  public @Nullable String getUsername() {
    return username;
  }

  /** Returns the id of the authenticated client, or null if not authenticated as a client. */
  public @Nullable String getClientId() {
    return clientId;
  }

  /** Returns the tenants the principal is authorized for, or null if not provided. */
  public @Nullable List<String> getTenants() {
    return tenants;
  }

  /**
   * Returns the groups of the principal as extracted from its token, or null if not provided, in
   * which case the groups have to be resolved from state.
   */
  public @Nullable List<String> getGroups() {
    return groups;
  }

  /**
   * Returns the claims of the principal's token, or null if not provided. They're decoded from
   * their msgpack encoding on every call, so values have the decoded types, e.g. {@code Integer}
   * for a small {@code Long} and {@code List} for an array.
   */
  @SuppressWarnings("unchecked")
  public @Nullable Map<String, Object> getTokenClaims() {
    return tokenClaims == null
        ? null
        : (Map<String, Object>) freeze(MsgPackConverter.convertToMap(tokenClaims));
  }

  /**
   * Converts the present claims to a map keyed by the names in {@link
   * io.camunda.zeebe.auth.Authorization}. Only meant for consumers that require claims as a map,
   * i.e. {@link io.camunda.zeebe.protocol.record.Record#getAuthorizations()} and the security
   * library; use the typed accessors otherwise.
   */
  @JsonGetter("claims")
  public Map<String, Object> toClaimsMap() {
    final var claims = new LinkedHashMap<String, Object>();
    putIfPresent(claims, AUTHORIZED_ANONYMOUS_USER, anonymous);
    putIfPresent(claims, AUTHORIZED_USERNAME, username);
    putIfPresent(claims, AUTHORIZED_CLIENT_ID, clientId);
    putIfPresent(claims, AUTHORIZED_TENANTS, tenants);
    putIfPresent(claims, USER_GROUPS_CLAIMS, groups);
    putIfPresent(claims, USER_TOKEN_CLAIMS, getTokenClaims());
    return Collections.unmodifiableMap(claims);
  }

  public boolean hasAnyClaims() {
    return presentClaims() > 0;
  }

  @Override
  public int getLength() {
    return encoded().capacity();
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    final var bytes = encoded();
    buffer.putBytes(offset, bytes, 0, bytes.capacity());
    return bytes.capacity();
  }

  /** Writes the auth info embedded in another msgpack value, e.g. as property of an object. */
  public int write(final MsgPackWriter writer) {
    return writer.writeRaw(encoded());
  }

  @Override
  public boolean equals(final Object other) {
    return other instanceof final AuthInfo that
        && format == that.format
        && Objects.equals(anonymous, that.anonymous)
        && Objects.equals(username, that.username)
        && Objects.equals(clientId, that.clientId)
        && Objects.equals(tenants, that.tenants)
        && Objects.equals(groups, that.groups)
        && Objects.equals(getTokenClaims(), that.getTokenClaims());
  }

  @Override
  public int hashCode() {
    return Objects.hash(format, anonymous, username, clientId, tenants, groups, getTokenClaims());
  }

  /** Masks the claims, as they may contain sensitive data. */
  @Override
  public String toString() {
    return "{\"format\":\"" + format + "\",\"claims\":\"***\"}";
  }

  private int presentClaims() {
    int present = 0;
    for (final var claim :
        new Object[] {anonymous, username, clientId, tenants, groups, tokenClaims}) {
      present += claim != null ? 1 : 0;
    }
    return present;
  }

  private DirectBuffer encoded() {
    var bytes = encoded;
    if (bytes == null) {
      // racing threads encode the same bytes, so it doesn't matter which one wins
      bytes = encode();
      encoded = bytes;
    }
    return bytes;
  }

  private DirectBuffer encode() {
    final var writer = new MsgPackWriter();

    final var claimsBuffer = new ExpandableArrayBuffer();
    writer.wrap(claimsBuffer, 0);
    writer.writeMapHeader(presentClaims());
    if (anonymous != null) {
      writer.writeString(ANONYMOUS_KEY_BUFFER);
      writer.writeBoolean(anonymous);
    }
    writeStringClaim(writer, USERNAME_KEY_BUFFER, username);
    writeStringClaim(writer, CLIENT_ID_KEY_BUFFER, clientId);
    writeStringsClaim(writer, TENANTS_KEY_BUFFER, tenants);
    writeStringsClaim(writer, GROUPS_KEY_BUFFER, groups);
    if (tokenClaims != null) {
      writer.writeString(TOKEN_CLAIMS_KEY_BUFFER);
      writer.writeRaw(tokenClaims);
    }
    final int claimsLength = writer.getOffset();

    final var buffer = new ExpandableArrayBuffer();
    writer.wrap(buffer, 0);
    writer.writeMapHeader(2);
    writer.writeString(FORMAT_KEY_BUFFER);
    writer.writeString(format.encodedName);
    writer.writeString(CLAIMS_KEY_BUFFER);
    writer.writeBinary(claimsBuffer, 0, claimsLength);
    return new UnsafeBuffer(Arrays.copyOf(buffer.byteArray(), writer.getOffset()));
  }

  private static void writeStringClaim(
      final MsgPackWriter writer, final DirectBuffer key, final @Nullable String value) {
    if (value != null) {
      writer.writeString(key);
      writeString(writer, value);
    }
  }

  private static void writeStringsClaim(
      final MsgPackWriter writer, final DirectBuffer key, final @Nullable List<String> values) {
    if (values != null) {
      writer.writeString(key);
      writer.writeArrayHeader(values.size());
      values.forEach(value -> writeString(writer, value));
    }
  }

  private static void writeString(final MsgPackWriter writer, final String string) {
    writer.writeString(BufferUtil.wrapString(string));
  }

  private static void putIfPresent(
      final Map<String, Object> claims, final String key, final @Nullable Object value) {
    if (value != null) {
      claims.put(key, value);
    }
  }

  private static <T> @Nullable T claim(
      final Map<String, ?> claims, final String key, final Class<T> type) {
    final var value = claims.get(key);
    return value == null ? null : cast(key, value, type);
  }

  private static @Nullable List<String> stringsClaim(
      final Map<String, ?> claims, final String key) {
    final var values = claim(claims, key, Collection.class);
    if (values == null) {
      return null;
    }

    final var strings = new ArrayList<String>(values.size());
    for (final var value : values) {
      strings.add(cast(key, value, String.class));
    }
    return Collections.unmodifiableList(strings);
  }

  private static @Nullable DirectBuffer tokenClaims(final Map<String, ?> claims) {
    final var tokenClaims = claim(claims, USER_TOKEN_CLAIMS, Map.class);
    return tokenClaims == null
        ? null
        : new UnsafeBuffer(MsgPackConverter.convertToMsgPack(tokenClaims));
  }

  private static <T> T cast(final String key, final @Nullable Object value, final Class<T> type) {
    if (!type.isInstance(value)) {
      throw new IllegalArgumentException(
          "Expected claim '%s' to be of type %s, but was '%s'"
              .formatted(key, type.getSimpleName(), value));
    }
    return type.cast(value);
  }

  /** Makes the freshly decoded value deeply unmodifiable. */
  @SuppressWarnings("unchecked")
  private static @Nullable Object freeze(final @Nullable Object value) {
    if (value instanceof final Map<?, ?> map) {
      ((Map<Object, Object>) map).replaceAll((key, nested) -> freeze(nested));
      return Collections.unmodifiableMap(map);
    } else if (value instanceof final List<?> list) {
      ((List<Object>) list).replaceAll(AuthInfo::freeze);
      return Collections.unmodifiableList(list);
    }
    return value;
  }

  /** Decodes the given buffer, which must be owned by the returned instance. */
  private static AuthInfo decode(final DirectBuffer encoded) {
    try {
      final var reader = new MsgPackReader().wrap(encoded, 0, encoded.capacity());
      var format = AuthDataFormat.UNKNOWN;
      Boolean anonymous = null;
      String username = null;
      String clientId = null;
      List<String> tenants = null;
      List<String> groups = null;
      DirectBuffer tokenClaims = null;

      final int properties = reader.readMapHeader();
      for (int i = 0; i < properties; i++) {
        switch (readString(reader)) {
          case FORMAT_KEY -> format = AuthDataFormat.ofName(readString(reader));
          case CLAIMS_KEY -> {
            final int claimsLength = reader.readBinaryLength();
            final var claimsReader =
                new MsgPackReader().wrap(reader.getBuffer(), reader.getOffset(), claimsLength);
            reader.skipBytes(claimsLength);

            final int claims = isNil(claimsReader, claimsLength) ? 0 : claimsReader.readMapHeader();
            for (int j = 0; j < claims; j++) {
              final var key = readString(claimsReader);
              if (isNil(claimsReader, claimsLength)) {
                claimsReader.skipValue();
                continue;
              }

              switch (key) {
                case AUTHORIZED_ANONYMOUS_USER -> anonymous = claimsReader.readBoolean();
                case AUTHORIZED_USERNAME -> username = readString(claimsReader);
                case AUTHORIZED_CLIENT_ID -> clientId = readString(claimsReader);
                case AUTHORIZED_TENANTS -> tenants = readStrings(claimsReader);
                case USER_GROUPS_CLAIMS -> groups = readStrings(claimsReader);
                case USER_TOKEN_CLAIMS -> tokenClaims = readMap(claimsReader);
                default -> claimsReader.skipValue();
              }
            }
          }
          default -> reader.skipValue();
        }
      }

      return new AuthInfo(
          format, anonymous, username, clientId, tenants, groups, tokenClaims, encoded);
    } catch (final RuntimeException e) {
      throw new IllegalArgumentException("Expected to decode auth info, but it is malformed", e);
    }
  }

  private static boolean isNil(final MsgPackReader reader, final int length) {
    return length == 0
        || reader.getOffset() >= length
        || reader.getBuffer().getByte(reader.getOffset()) == MsgPackCodes.NIL;
  }

  private static String readString(final MsgPackReader reader) {
    final int length = reader.readStringLength();
    final var string = reader.getBuffer().getStringWithoutLengthUtf8(reader.getOffset(), length);
    reader.skipBytes(length);
    return string;
  }

  private static List<String> readStrings(final MsgPackReader reader) {
    final int size = reader.readArrayHeader();
    final var strings = new ArrayList<String>(size);
    for (int i = 0; i < size; i++) {
      strings.add(readString(reader));
    }
    return Collections.unmodifiableList(strings);
  }

  /** Returns a view of the map at the reader's offset, whose buffer must never change. */
  private static DirectBuffer readMap(final MsgPackReader reader) {
    final int offset = reader.getOffset();
    if (!MsgPackCodes.isMap(reader.getBuffer().getByte(offset))) {
      throw new IllegalArgumentException("Expected a map at offset " + offset);
    }
    reader.skipValue();
    return new UnsafeBuffer(reader.getBuffer(), offset, reader.getOffset() - offset);
  }

  public enum AuthDataFormat {
    UNKNOWN((short) 0),
    PRE_AUTHORIZED((short) 2);

    public final short id;
    private final DirectBuffer encodedName;

    AuthDataFormat(final short id) {
      this.id = id;
      encodedName = BufferUtil.wrapString(name());
    }

    private static AuthDataFormat ofName(final String name) {
      for (final var format : values()) {
        if (format.name().equals(name)) {
          return format;
        }
      }
      return UNKNOWN;
    }
  }
}
