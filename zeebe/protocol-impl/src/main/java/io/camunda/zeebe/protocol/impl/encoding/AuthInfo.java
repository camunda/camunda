/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** */
public class AuthInfo extends UnpackedObject {

  private final DocumentProperty authDataProp = new DocumentProperty("authData");

  public AuthInfo() {
    super(1);
    declareProperty(authDataProp);
  }

  public Map<String, Object> getAuthData() {
    return MsgPackConverter.convertToMap(authDataProp.getValue());
  }

  public AuthInfo setAuthData(final DirectBuffer authData) {
    authDataProp.setValue(authData);
    return this;
  }

  public AuthInfo setAuthData(final Map<String, Object> authData) {
    authDataProp.setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(authData)));
    return this;
  }

  @JsonIgnore
  public DirectBuffer getAuthDataBuffer() {
    return authDataProp.getValue();
  }

  public void wrap(final AuthInfo authInfo) {
    authDataProp.setValue(authInfo.getAuthDataBuffer());
  }

  @Override
  public void reset() {
    authDataProp.reset();
  }

  public DirectBuffer toDirectBuffer() {
    final var bytes = new byte[getLength()];
    final var buffer = new UnsafeBuffer(bytes);
    write(buffer, 0);

    return buffer;
  }

  @Override
  public String toString() {
    return "AuthInfo{" + "authData=" + BufferUtil.bufferAsString(authDataProp.getValue()) + '}';
  }
}
