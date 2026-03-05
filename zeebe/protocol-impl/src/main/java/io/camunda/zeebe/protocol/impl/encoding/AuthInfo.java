/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.auth.JwtDecoder;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collections;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * AuthInfo is treated as immutable after construction. Use the static factory methods to create
 * instances. Call {@link #freeze()} to make an instance permanently immutable — after freezing,
 * {@link #wrap} and {@link #reset} will throw {@link UnsupportedOperationException}. All factory
 * methods except {@link #mutable()} return frozen instances.
 */
public class AuthInfo extends UnpackedObject {

  private final EnumProperty<AuthDataFormat> formatProp =
      new EnumProperty<>("format", AuthDataFormat.class, AuthDataFormat.UNKNOWN);

  private final StringProperty authDataProp = new StringProperty("authData", "").sanitized();
  private final DocumentProperty claimsProp = new DocumentProperty("claims").sanitized();
  private transient volatile Map<String, Object> cachedDecodedMap;
  private transient volatile boolean frozen;

  protected AuthInfo() {
    super(3);
    declareProperty(formatProp).declareProperty(authDataProp).declareProperty(claimsProp);
  }

  /**
   * Returns the shared immutable empty AuthInfo singleton. This is the default "no authorization"
   * sentinel.
   */
  public static AuthInfo empty() {
    return EmptyAuthInfo.getInstance();
  }

  /**
   * Creates a new mutable, unfrozen AuthInfo. Intended for the msgpack {@link
   * io.camunda.zeebe.msgpack.property.ObjectProperty} framework (which requires a mutable template
   * instance for deserialization).
   */
  public static AuthInfo mutable() {
    return new AuthInfo();
  }

  /**
   * Permanently freezes this instance. After freezing, {@link #wrap} and {@link #reset} will throw
   * {@link UnsupportedOperationException}. This operation is idempotent and cannot be undone.
   *
   * @return this instance, for chaining
   */
  public AuthInfo freeze() {
    frozen = true;
    return this;
  }

  @JsonIgnore
  public boolean isFrozen() {
    return frozen;
  }

  // --- Static factory methods ---

  public static AuthInfo withClaims(final Map<String, Object> claims) {
    final var auth = new AuthInfo();
    auth.formatProp.setValue(AuthDataFormat.UNKNOWN);
    auth.claimsProp.setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(claims)));
    auth.cachedDecodedMap = Collections.unmodifiableMap(claims);
    return auth.freeze();
  }

  public static AuthInfo withClaims(final DirectBuffer claimsBuffer) {
    final var auth = new AuthInfo();
    auth.claimsProp.setValue(claimsBuffer);
    return auth.freeze();
  }

  public static AuthInfo withJwt(final String token) {
    final var auth = new AuthInfo();
    auth.formatProp.setValue(AuthDataFormat.JWT);
    auth.authDataProp.setValue(token);
    return auth.freeze();
  }

  public static AuthInfo withJwt(final String token, final Map<String, Object> claims) {
    final var auth = new AuthInfo();
    auth.formatProp.setValue(AuthDataFormat.JWT);
    auth.authDataProp.setValue(token);
    auth.claimsProp.setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(claims)));
    auth.cachedDecodedMap = Collections.unmodifiableMap(claims);
    return auth.freeze();
  }

  public static AuthInfo preAuthorized() {
    final var auth = new AuthInfo();
    auth.formatProp.setValue(AuthDataFormat.PRE_AUTHORIZED);
    auth.cachedDecodedMap = Map.of();
    return auth.freeze();
  }

  public static AuthInfo preAuthorized(final Map<String, Object> claims) {
    final var auth = new AuthInfo();
    auth.formatProp.setValue(AuthDataFormat.PRE_AUTHORIZED);
    auth.claimsProp.setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(claims)));
    auth.cachedDecodedMap = Collections.unmodifiableMap(claims);
    return auth.freeze();
  }

  /**
   * Creates a new AuthInfo by deserializing from the given buffer. Returns the empty singleton if
   * the buffer matches it, avoiding allocation. The buffer contents are copied so the returned
   * instance does not retain a reference to the caller's buffer (which may be reused, e.g. in the
   * processing state machine).
   */
  public static AuthInfo of(final DirectBuffer buffer) {
    if (buffer.capacity() == EmptyAuthInfo.getInstance().getLength()
        && buffer.equals(EmptyAuthInfo.getInstance().toDirectBuffer())) {
      return empty();
    }

    // Copy the buffer so deserialized properties don't reference the caller's buffer.
    final var copy = new byte[buffer.capacity()];
    buffer.getBytes(0, copy);

    final var auth = new AuthInfo();
    auth.wrap(new UnsafeBuffer(copy));
    return auth.freeze();
  }

  public static AuthInfo of(final AuthInfo info) {
    if (info == null) {
      return null;
    }

    final var auth = new AuthInfo();
    auth.copyFrom(info);
    return auth.freeze();
  }

  // --- Getters ---

  public AuthDataFormat getFormat() {
    return formatProp.getValue();
  }

  public String getAuthData() {
    return BufferUtil.bufferAsString(authDataProp.getValue());
  }

  @Override
  public void reset() {
    if (frozen) {
      throw new UnsupportedOperationException("Cannot reset a frozen AuthInfo");
    }
    formatProp.setValue(AuthDataFormat.UNKNOWN);
    authDataProp.setValue("");
    claimsProp.reset();
    cachedDecodedMap = null;
  }

  @Override
  @JsonIgnore
  public int getEncodedLength() {
    return super.getEncodedLength();
  }

  @Override
  @JsonIgnore
  public boolean isEmpty() {
    return super.isEmpty();
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    if (frozen) {
      throw new UnsupportedOperationException("Cannot wrap a frozen AuthInfo");
    }
    super.wrap(buffer, offset, length);
    cachedDecodedMap = null;
  }

  @Override
  @JsonIgnore
  public int getLength() {
    return super.getLength();
  }

  public DirectBuffer toDirectBuffer() {
    final var bytes = new byte[getLength()];
    final var buffer = new UnsafeBuffer(bytes);
    write(buffer, 0);

    return buffer;
  }

  /**
   * Returns the stored claims from the msgpack document property. The returned map is unmodifiable.
   */
  public Map<String, Object> getClaims() {
    return Collections.unmodifiableMap(MsgPackConverter.convertToMap(claimsProp.getValue()));
  }

  /**
   * Returns the decoded claims map. For frozen instances the result is lazily computed and cached.
   * For mutable instances the result is always recomputed (since the underlying data may change).
   * For JWT format, decodes the token. For other formats, deserializes from msgpack. The returned
   * map is unmodifiable.
   */
  public Map<String, Object> toDecodedMap() {
    if (cachedDecodedMap != null) {
      return cachedDecodedMap;
    }

    final Map<String, Object> result;
    if (getFormat() == AuthDataFormat.JWT) {
      result = Collections.unmodifiableMap(new JwtDecoder(getAuthData()).decode().getClaims());
    } else {
      result = getClaims();
    }

    if (frozen) {
      cachedDecodedMap = result;
    }
    return result;
  }

  public boolean hasAnyClaims() {
    switch (getFormat()) {
      case JWT:
        return authDataProp.getValue() != null && authDataProp.getValue().capacity() > 0;
      default:
        return claimsProp.getValue() != null
            && !DocumentValue.EMPTY_DOCUMENT.equals(claimsProp.getValue());
    }
  }

  public enum AuthDataFormat {
    UNKNOWN((short) 0),
    JWT((short) 1),
    PRE_AUTHORIZED((short) 2);

    public final short id;

    AuthDataFormat(final short id) {
      this.id = id;
    }
  }
}
