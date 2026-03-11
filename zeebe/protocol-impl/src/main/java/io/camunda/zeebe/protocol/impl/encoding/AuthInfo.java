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
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** */
public class AuthInfo extends UnpackedObject {

  private final EnumProperty<AuthDataFormat> formatProp =
      new EnumProperty<>("format", AuthDataFormat.class, AuthDataFormat.UNKNOWN);
  private final StringProperty authDataProp = new StringProperty("authData", "").sanitized();
  private final DocumentProperty claimsProp = new DocumentProperty("claims").sanitized();

  // fields  for caching
  private transient boolean frozen;
  private transient int cachedLength = -1;
  private transient Map<String, Object> cachedDecodedMap;

  public AuthInfo() {
    super(3);
    declareProperty(formatProp).declareProperty(authDataProp).declareProperty(claimsProp);
  }

  public static AuthInfo empty() {
    return EmptyAuthInfo.getInstance();
  }

  public AuthDataFormat getFormat() {
    return formatProp.getValue();
  }

  public AuthInfo setFormat(final AuthDataFormat format) {
    ensureMutable();
    formatProp.setValue(format);
    return this;
  }

  public String getAuthData() {
    return BufferUtil.bufferAsString(authDataProp.getValue());
  }

  public AuthInfo setAuthData(final String authData) {
    ensureMutable();
    authDataProp.setValue(authData);
    return this;
  }

  public Map<String, Object> getClaims() {
    return MsgPackConverter.convertToMap(claimsProp.getValue());
  }

  public AuthInfo setClaims(final DirectBuffer authInfo) {
    ensureMutable();
    claimsProp.setValue(authInfo);
    return this;
  }

  public AuthInfo setClaims(final Map<String, Object> authInfo) {
    ensureMutable();
    claimsProp.setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(authInfo)));
    return this;
  }

  @Override
  public void reset() {
    ensureMutable();
    formatProp.setValue(AuthDataFormat.UNKNOWN);
    authDataProp.setValue("");
    claimsProp.reset();
  }

  @Override
  public void read(final MsgPackReader reader) {
    ensureMutable();
    super.read(reader);
  }

  @Override
  @JsonIgnore
  public int getEncodedLength() {
    if (frozen) {
      if (cachedLength < 0) {
        cachedLength = super.getEncodedLength();
      }
      return cachedLength;
    } else {
      return super.getEncodedLength();
    }
  }

  @Override
  @JsonIgnore
  public boolean isEmpty() {
    return super.isEmpty();
  }

  @Override
  public void wrap(final DirectBuffer buff, final int offset, final int length) {
    ensureMutable();
    super.wrap(buff, offset, length);
  }

  @Override
  @JsonIgnore
  public int getLength() {
    return getEncodedLength();
  }

  /** Marks this instance as frozen. A frozen AuthInfo rejects mutation and caches decoded maps. */
  public AuthInfo freeze() {
    frozen = true;
    return this;
  }

  @JsonIgnore
  public boolean isFrozen() {
    return frozen;
  }

  private void ensureMutable() {
    if (frozen) {
      throw new UnsupportedOperationException("Cannot mutate a frozen AuthInfo");
    }
  }

  public DirectBuffer toDirectBuffer() {
    final var bytes = new byte[getLength()];
    final var buffer = new UnsafeBuffer(bytes);
    write(buffer, 0);

    return buffer;
  }

  public Map<String, Object> toDecodedMap() {
    if (frozen && cachedDecodedMap != null) {
      return cachedDecodedMap;
    }
    final Map<String, Object> result;
    if (getFormat() == AuthDataFormat.JWT) {
      result = new JwtDecoder(getAuthData()).decode().getClaims();
    } else {
      result = getClaims();
    }
    if (frozen) {
      cachedDecodedMap = result;
    }
    return result;
  }

  /**
   * @param info the AuthInfo to copy if necessary
   * @return Creates a new AuthInfo if the argument is not frozen by copying it. The returned value
   *     is always frozen
   */
  public static AuthInfo of(final AuthInfo info) {
    if (info == null) {
      return null;
    }
    if (info.isFrozen()) {
      return info;
    }

    final var auth = new AuthInfo();
    auth.copyFrom(info);
    return auth.freeze();
  }

  public static AuthInfo ofClaims(final Map<String, Object> claims) {
    if (claims == null || claims.isEmpty()) {
      return empty();
    }
    return new AuthInfo().setClaims(Map.copyOf(claims)).freeze();
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
