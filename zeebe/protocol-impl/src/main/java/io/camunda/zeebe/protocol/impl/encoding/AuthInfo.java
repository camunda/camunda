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
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;

/** */
public class AuthInfo extends UnpackedObject {
  private @Nullable Map<String, Object> decodedClaims;
  private @Nullable Map<String, Object> decodedJwtClaims;

  private final EnumProperty<AuthDataFormat> formatProp =
      new EnumProperty<>("format", AuthDataFormat.class, AuthDataFormat.UNKNOWN);

  private final StringProperty authDataProp = new StringProperty("authData", "").sanitized();
  private final DocumentProperty claimsProp = new DocumentProperty("claims").sanitized();

  public AuthInfo() {
    super(3);
    declareProperty(formatProp).declareProperty(authDataProp).declareProperty(claimsProp);
  }

  public AuthDataFormat getFormat() {
    return formatProp.getValue();
  }

  public AuthInfo setFormat(final AuthDataFormat format) {
    formatProp.setValue(format);
    invalidateDecodedJwtClaims();
    return this;
  }

  public String getAuthData() {
    return BufferUtil.bufferAsString(authDataProp.getValue());
  }

  public AuthInfo setAuthData(final String authData) {
    authDataProp.setValue(authData);
    invalidateDecodedJwtClaims();
    return this;
  }

  public Map<String, Object> getClaims() {
    if (decodedClaims == null) {
      decodedClaims = MsgPackConverter.convertToMap(claimsProp.getValue());
    }
    return decodedClaims;
  }

  public AuthInfo setClaims(final DirectBuffer authInfo) {
    claimsProp.setValue(authInfo);
    invalidateDecodedClaims();
    return this;
  }

  public AuthInfo setClaims(final Map<String, Object> authInfo) {
    claimsProp.setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(authInfo)));
    invalidateDecodedClaims();
    return this;
  }

  @Override
  public void reset() {
    formatProp.setValue(AuthDataFormat.UNKNOWN);
    authDataProp.setValue("");
    claimsProp.reset();
    invalidateDecodedClaims();
    invalidateDecodedJwtClaims();
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

  public DirectBuffer toDirectBuffer() {
    final var bytes = new byte[getLength()];
    final var buffer = new UnsafeBuffer(bytes);
    write(buffer, 0);

    return buffer;
  }

  public Map<String, Object> toDecodedMap() {
    if (getFormat() == AuthDataFormat.JWT) {
      if (decodedJwtClaims == null) {
        final String token = getAuthData();
        decodedJwtClaims = new JwtDecoder(token).decode().getClaims();
      }
      return decodedJwtClaims;
    }
    return getClaims();
  }

  public static AuthInfo of(final AuthInfo info) {
    if (info == null) {
      return null;
    }

    final var auth = new AuthInfo();
    auth.copyFrom(info);
    return auth;
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

  private void invalidateDecodedClaims() {
    decodedClaims = null;
  }

  private void invalidateDecodedJwtClaims() {
    decodedJwtClaims = null;
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
