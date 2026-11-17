/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.msgpack.property.BaseProperty;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.msgpack.value.BaseValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import org.jspecify.annotations.NullMarked;

/**
 * Embeds an {@link AuthInfo} in a msgpack object. The property holds a reference to the immutable
 * {@link AuthInfo}, so setting and getting it never copies. Defaults to {@link AuthInfo#EMPTY}.
 */
@NullMarked
public final class AuthInfoProperty extends BaseProperty<AuthInfoProperty.AuthInfoValue> {

  public AuthInfoProperty(final String key) {
    this(new StringValue(key));
  }

  public AuthInfoProperty(final StringValue key) {
    this(key, new AuthInfoValue());
  }

  private AuthInfoProperty(final StringValue key, final AuthInfoValue value) {
    super(key, value, value);
  }

  public AuthInfo getValue() {
    return resolveValue().authInfo;
  }

  public void setValue(final AuthInfo authInfo) {
    value.authInfo = requireNonNull(authInfo, "authInfo");
    set();
  }

  static final class AuthInfoValue extends BaseValue {
    private AuthInfo authInfo = AuthInfo.EMPTY;

    @Override
    public void reset() {
      authInfo = AuthInfo.EMPTY;
    }

    @Override
    public void writeJSON(final StringBuilder builder) {
      builder.append(authInfo);
    }

    @Override
    public int write(final MsgPackWriter writer) {
      return authInfo.write(writer);
    }

    @Override
    public void read(final MsgPackReader reader) {
      authInfo = AuthInfo.read(reader);
    }

    @Override
    public int getEncodedLength() {
      return authInfo.getLength();
    }

    @Override
    public boolean equals(final Object other) {
      return other instanceof final AuthInfoValue that && authInfo.equals(that.authInfo);
    }

    @Override
    public int hashCode() {
      return authInfo.hashCode();
    }
  }
}
