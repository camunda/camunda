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
 * instances. The only mutation allowed is through {@link #wrap} and {@link #reset}, which are part
 * of the {@link UnpackedObject} serialization contract.
 */
public class AuthInfo extends UnpackedObject {

  private final EnumProperty<AuthDataFormat> formatProp =
      new EnumProperty<>("format", AuthDataFormat.class, AuthDataFormat.UNKNOWN);

  private final StringProperty authDataProp = new StringProperty("authData", "").sanitized();
  private final DocumentProperty claimsProp = new DocumentProperty("claims").sanitized();
  private transient volatile Map<String, Object> cachedDecodedMap;

  public AuthInfo() {
    super(3);
    declareProperty(formatProp).declareProperty(authDataProp).declareProperty(claimsProp);
  }

  // --- Static factory methods ---

  public static AuthInfo withClaims(final Map<String, Object> claims) {
    final var auth = new AuthInfo();
    auth.formatProp.setValue(AuthDataFormat.UNKNOWN);
    auth.claimsProp.setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(claims)));
    return auth;
  }

  public static AuthInfo withClaims(final DirectBuffer claimsBuffer) {
    final var auth = new AuthInfo();
    auth.claimsProp.setValue(claimsBuffer);
    return auth;
  }

  public static AuthInfo withJwt(final String token) {
    final var auth = new AuthInfo();
    auth.formatProp.setValue(AuthDataFormat.JWT);
    auth.authDataProp.setValue(token);
    return auth;
  }

  public static AuthInfo withJwt(final String token, final Map<String, Object> claims) {
    final var auth = new AuthInfo();
    auth.formatProp.setValue(AuthDataFormat.JWT);
    auth.authDataProp.setValue(token);
    auth.claimsProp.setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(claims)));
    return auth;
  }

  public static AuthInfo preAuthorized() {
    final var auth = new AuthInfo();
    auth.formatProp.setValue(AuthDataFormat.PRE_AUTHORIZED);
    return auth;
  }

  public static AuthInfo preAuthorized(final Map<String, Object> claims) {
    final var auth = new AuthInfo();
    auth.formatProp.setValue(AuthDataFormat.PRE_AUTHORIZED);
    auth.claimsProp.setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(claims)));
    return auth;
  }

  public static AuthInfo of(final AuthInfo info) {
    if (info == null) {
      return new AuthInfo();
    }

    final var auth = new AuthInfo();
    auth.copyFrom(info);
    return auth;
  }

  // --- Getters ---

  public AuthDataFormat getFormat() {
    return formatProp.getValue();
  }

  public String getAuthData() {
    return BufferUtil.bufferAsString(authDataProp.getValue());
  }

  /**
   * Returns the raw claims from the msgpack document property. Prefer {@link #toDecodedMap()} which
   * handles both JWT and non-JWT formats and caches the result.
   */
  public Map<String, Object> getClaims() {
    return Collections.unmodifiableMap(MsgPackConverter.convertToMap(claimsProp.getValue()));
  }

  @Override
  public void reset() {
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
  @JsonIgnore
  public int getLength() {
    return super.getLength();
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    super.wrap(buffer, offset, length);
    cachedDecodedMap = null;
  }

  public DirectBuffer toDirectBuffer() {
    final var bytes = new byte[getLength()];
    final var buffer = new UnsafeBuffer(bytes);
    write(buffer, 0);

    return buffer;
  }

  /**
   * Returns the decoded claims map, lazily computed and cached on first access. For JWT format,
   * decodes the token. For other formats, deserializes from msgpack. The returned map is
   * unmodifiable.
   */
  public Map<String, Object> toDecodedMap() {
    if (cachedDecodedMap == null) {
      if (getFormat() == AuthDataFormat.JWT) {
        final String token = getAuthData();
        cachedDecodedMap = Collections.unmodifiableMap(new JwtDecoder(token).decode().getClaims());
      } else {
        cachedDecodedMap = getClaims();
      }
    }
    return cachedDecodedMap;
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
