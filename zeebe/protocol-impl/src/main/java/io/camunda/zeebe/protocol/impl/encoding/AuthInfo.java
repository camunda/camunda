/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import io.camunda.zeebe.auth.impl.Authorization;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** */
public class AuthInfo extends UnpackedObject {

  private final EnumProperty<AuthDataFormat> formatProp =
      new EnumProperty<>("format", AuthDataFormat.class, AuthDataFormat.UNKNOWN);

  private final StringProperty authDataProp = new StringProperty("authData", "");

  public AuthInfo() {
    super(2);
    declareProperty(formatProp).declareProperty(authDataProp);
  }

  public AuthDataFormat getFormat() {
    return formatProp.getValue();
  }

  public AuthInfo setFormatProp(final AuthDataFormat format) {
    formatProp.setValue(format);
    return this;
  }

  public DirectBuffer getAuthDataBuffer() {
    return authDataProp.getValue();
  }

  public String getAuthData() {
    return BufferUtil.bufferAsString(authDataProp.getValue());
  }

  public AuthInfo setAuthData(final String authData) {
    authDataProp.setValue(authData);
    return this;
  }

  public void wrap(final AuthInfo authInfo) {
    formatProp.setValue(authInfo.getFormat());
    authDataProp.setValue(authInfo.getAuthData());
  }

  @Override
  public void reset() {
    formatProp.setValue(AuthDataFormat.UNKNOWN);
    authDataProp.setValue("");
  }

  public DirectBuffer toDirectBuffer() {
    final var bytes = new byte[getLength()];
    final var buffer = new UnsafeBuffer(bytes);
    write(buffer, 0);

    return buffer;
  }

  public Map<String, Object> toDecodedMap() {
    switch (getFormat()) {
      case JWT -> {
        final String jwtToken = getAuthData();
        return Authorization.jwtDecoder(jwtToken)
            .withClaim(Authorization.AUTHORIZED_TENANTS)
            .withClaim(Authorization.AUTHORIZED_ANONYMOUS_USER)
            .decode();
      }
      default -> {
        return Map.of(
            Authorization.AUTHORIZED_TENANTS, List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER));
      }
    }
  }

  @Override
  public String toString() {
    String data = getAuthData();
    data = data.isEmpty() ? "" : "." + data;
    return formatProp.getValue().toString() + data;
  }

  public enum AuthDataFormat {
    UNKNOWN((short) 0),
    JWT((short) 1);

    public final short id;

    AuthDataFormat(final short id) {
      this.id = id;
    }
  }
}
