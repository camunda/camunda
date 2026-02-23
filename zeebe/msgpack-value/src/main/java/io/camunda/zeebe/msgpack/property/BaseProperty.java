/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.property;

import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.Recyclable;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.msgpack.value.BaseValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import java.util.Objects;

public abstract class BaseProperty<T extends BaseValue> implements Recyclable {
  protected final StringValue key;
  protected final T value;
  protected final T defaultValue;
  protected boolean isSet;
  protected boolean isSanitized;

  public BaseProperty(final T value) {
    this(StringValue.EMPTY_STRING, value);
  }

  public BaseProperty(final String keyString, final T value) {
    this(keyString, value, null);
  }

  public BaseProperty(final String keyString, final T value, final T defaultValue) {
    this(new StringValue(keyString), value, defaultValue);
  }

  public BaseProperty(final StringValue keyString, final T value) {
    this(keyString, value, null);
  }

  public BaseProperty(final StringValue keyString, final T value, final T defaultValue) {
    Objects.requireNonNull(keyString);
    Objects.requireNonNull(value);
    key = keyString;
    this.value = value;
    this.defaultValue = defaultValue;
  }

  public void set() {
    isSet = true;
  }

  @Override
  public void reset() {
    isSet = false;
    value.reset();
  }

  public boolean hasValue() {
    return isSet || defaultValue != null;
  }

  public StringValue getKey() {
    return key;
  }

  protected T resolveValue() {
    if (isSet) {
      return value;
    } else if (defaultValue != null) {
      return defaultValue;
    } else {
      throw new MsgpackPropertyException(
          key, "Expected a value or default value to be specified, but has nothing");
    }
  }

  public int getEncodedLength() {
    return key.getEncodedLength() + resolveValue().getEncodedLength();
  }

  public void read(final MsgPackReader reader) {
    value.read(reader);
    set();
  }

  public int write(final MsgPackWriter writer) {
    T valueToWrite = value;
    if (!isSet) {
      valueToWrite = defaultValue;
    }

    if (valueToWrite == null) {
      throw new MsgpackPropertyException(
          key, "Expected a value or default value to be set before writing, but has nothing");
    }

    int written = key.write(writer);
    written += valueToWrite.write(writer);
    return written;
  }

  public void writeJSON(final StringBuilder sb, final boolean maskSanitized) {
    key.writeJSON(sb);
    sb.append(":");
    if (hasValue()) {
      if (maskSanitized && isSanitized) {
        sb.append("\"***\"");
      } else {
        resolveValue().writeJSON(sb);
      }
    } else {
      sb.append("\"NO VALID WRITEABLE VALUE\"");
    }
  }

  /**
   * Returns true if the property should be masked when printing it out (e.g. in logs or {@link
   * #toString}).
   *
   * <p>If false, it means the value is <em>always</em> printed out as is, and is not considered
   * sensitive.
   */
  public boolean isSanitized() {
    return isSanitized;
  }

  /**
   * If set to true, will be replaced by '***' when writing to console or logs, i.e. when called via
   * {@link #toString()}, or {@link #writeJSON(StringBuilder, boolean)} with the second param set to
   * true.
   *
   * <p>The chaining portion is a bit hacky, but it allows you to declare fields as:
   *
   * <pre>{@code
   * private final IntegerProperty myProperty = new IntegerProperty("myProperty").sanitized();
   * }</pre>
   *
   * The proper way for chaining would be to add a type param to the class that would represent the
   * current type, but that would create quite a lot of refactoring, and this is likely good enough
   * in most cases.
   *
   * @return itself for chaining
   */
  public <U extends BaseValue, T extends BaseProperty<U>> T sanitized() {
    isSanitized = true;
    return (T) this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getKey(), value, defaultValue, isSet);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof BaseProperty)) {
      return false;
    }

    final BaseProperty<?> that = (BaseProperty<?>) o;
    return Objects.equals(getKey(), that.getKey())
        && Objects.equals(resolveValue(), that.resolveValue());
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append(key.toString());
    builder.append(" => ");
    builder.append(isSanitized ? "***" : value.toString());
    return builder.toString();
  }
}
