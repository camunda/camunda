/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.auth.JwtDecoder;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** */
public class AuthInfo extends UnpackedObject {

  private static final List<String> ACTOR_CLAIMS =
      List.of(
          Authorization.AUTHORIZED_ANONYMOUS_USER,
          Authorization.AUTHORIZED_USERNAME,
          Authorization.AUTHORIZED_CLIENT_ID);

  private final EnumProperty<AuthDataFormat> formatProp =
      new EnumProperty<>("format", AuthDataFormat.class, AuthDataFormat.UNKNOWN);

  private final StringProperty authDataProp = new StringProperty("authData", "").sanitized();
  private final DocumentProperty claimsProp = new DocumentProperty("claims").sanitized();

  public AuthInfo() {
    super(3);
    declareProperty(formatProp).declareProperty(authDataProp).declareProperty(claimsProp);
  }

  /**
   * Returns an auth info with the same format and only the actor claims: the anonymous flag, the
   * username and the client id.
   */
  public AuthInfo withActorClaimsOnly() {
    if (getFormat() == AuthDataFormat.JWT) {
      // The claims are inside the token, so they cannot be reduced without the token.
      return this;
    }
    final var claims = getClaims();
    final var actorClaims = new HashMap<String, Object>();
    for (final var key : ACTOR_CLAIMS) {
      if (claims.containsKey(key)) {
        actorClaims.put(key, claims.get(key));
      }
    }
    return new AuthInfo().setFormat(getFormat()).setClaims(actorClaims);
  }

  public AuthDataFormat getFormat() {
    return formatProp.getValue();
  }

  public AuthInfo setFormat(final AuthDataFormat format) {
    formatProp.setValue(format);
    return this;
  }

  public String getAuthData() {
    return BufferUtil.bufferAsString(authDataProp.getValue());
  }

  public AuthInfo setAuthData(final String authData) {
    authDataProp.setValue(authData);
    return this;
  }

  public Map<String, Object> getClaims() {
    return MsgPackConverter.convertToMap(claimsProp.getValue());
  }

  public AuthInfo setClaims(final DirectBuffer authInfo) {
    claimsProp.setValue(authInfo);
    return this;
  }

  public AuthInfo setClaims(final Map<String, Object> authInfo) {
    claimsProp.setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(authInfo)));
    return this;
  }

  @Override
  public void reset() {
    formatProp.setValue(AuthDataFormat.UNKNOWN);
    authDataProp.setValue("");
    claimsProp.reset();
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
      final String token = getAuthData();
      return new JwtDecoder(token).decode().getClaims();
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
