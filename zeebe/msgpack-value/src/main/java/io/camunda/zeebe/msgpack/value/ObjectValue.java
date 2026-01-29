/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.value;

import io.camunda.zeebe.msgpack.property.BaseProperty;
import io.camunda.zeebe.msgpack.property.UndeclaredProperty;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ObjectValue extends BaseValue {
  private final List<BaseProperty<? extends BaseValue>> declaredProperties;
  private final List<UndeclaredProperty> undeclaredProperties = new ArrayList<>(0);
  private final List<UndeclaredProperty> recycledProperties = new ArrayList<>(0);

  private final StringValue decodedKey = new StringValue();

  /**
   * Creates a new ObjectValue
   *
   * @param expectedDeclaredProperties a size hint for the number of declared properties. Providing
   *     the correct number helps to avoid allocations and memory copies.
   */
  public ObjectValue(final int expectedDeclaredProperties) {
    declaredProperties = new ArrayList<>(expectedDeclaredProperties);
  }

  public ObjectValue declareProperty(final BaseProperty<? extends BaseValue> prop) {
    declaredProperties.add(prop);
    return this;
  }

  @Override
  public void reset() {
    for (int i = 0; i < declaredProperties.size(); ++i) {
      final BaseProperty<? extends BaseValue> prop = declaredProperties.get(i);
      prop.reset();
    }

    for (int i = undeclaredProperties.size() - 1; i >= 0; --i) {
      final UndeclaredProperty undeclaredProperty = undeclaredProperties.remove(i);
      undeclaredProperty.reset();
      recycledProperties.add(undeclaredProperty);
    }
  }

  private UndeclaredProperty newUndeclaredProperty(final StringValue key) {
    final int recycledSize = recycledProperties.size();

    UndeclaredProperty prop = null;

    if (recycledSize > 0) {
      prop = recycledProperties.remove(recycledSize - 1);
    } else {
      prop = new UndeclaredProperty();
    }

    prop.getKey().wrap(key);
    undeclaredProperties.add(prop);

    return prop;
  }

  @Override
  public void writeJSON(final StringBuilder builder) {
    builder.append("{");

    writeJson(builder, declaredProperties, false);
    writeJson(builder, undeclaredProperties, false);

    builder.append("}");
  }

  /**
   * Caution: In case not all properties are writeable (i.e. value not set and no default), this
   * method may write some of the values and only then throw an exception. The same exception is
   * raised by {@link #getEncodedLength()}. If you call that first and it succeeds, you are safe to
   * write all the values.
   */
  @Override
  public int write(final MsgPackWriter writer) {
    final int size = declaredProperties.size() + undeclaredProperties.size();

    int written = writer.writeMapHeader(size);
    written += write(writer, declaredProperties);
    written += write(writer, undeclaredProperties);
    return written;
  }

  @Override
  public void read(final MsgPackReader reader) {
    final int mapSize = reader.readMapHeader();

    for (int i = 0; i < mapSize; ++i) {
      decodedKey.read(reader);

      BaseProperty<? extends BaseValue> prop = null;

      for (int k = 0; k < declaredProperties.size(); ++k) {
        // Cycle on all declared properties, but start iterating through them
        // by starting on i:
        // keys are serialized in the same order, so in most cases we can find the right
        // key without having to iterate on declaredProperties at all.
        final var index = (i + k) % declaredProperties.size();
        final BaseProperty<?> declaredProperty = declaredProperties.get(index);
        final StringValue declaredKey = declaredProperty.getKey();

        if (declaredKey.equals(decodedKey)) {
          prop = declaredProperty;
          break;
        }
      }

      if (prop == null) {
        prop = newUndeclaredProperty(decodedKey);
      }

      try {
        prop.read(reader);
      } catch (final Exception e) {
        throw new RuntimeException(String.format("Could not read property '%s'", prop.getKey()), e);
      }
    }

    verifyAllDeclaredPropertiesAreSet();
  }

  @Override
  public int getEncodedLength() {
    final int size = declaredProperties.size() + undeclaredProperties.size();

    int length = MsgPackWriter.getEncodedMapHeaderLength(size);
    length += getEncodedLength(declaredProperties);
    length += getEncodedLength(undeclaredProperties);

    return length;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("{");

    writeJson(builder, declaredProperties, true);
    writeJson(builder, undeclaredProperties, true);

    builder.append("}");
    return builder.toString();
  }

  private void verifyAllDeclaredPropertiesAreSet() {
    for (final BaseProperty<?> prop : declaredProperties) {
      if (!prop.hasValue()) {
        throw new RuntimeException(
            String.format("Property '%s' has no valid value", prop.getKey()));
      }
    }
  }

  private <T extends BaseProperty<?>> void writeJson(
      final StringBuilder builder, final List<T> properties, final boolean maskSanitized) {
    for (int i = 0; i < properties.size(); i++) {
      if (i > 0) {
        builder.append(",");
      }

      final BaseProperty<? extends BaseValue> prop = properties.get(i);

      if (prop.hasValue()) {
        prop.writeJSON(builder, maskSanitized);
      }
    }
  }

  private <T extends BaseProperty<?>> int write(
      final MsgPackWriter writer, final List<T> properties) {
    int written = 0;
    for (int i = 0; i < properties.size(); ++i) {
      final BaseProperty<? extends BaseValue> prop = properties.get(i);
      written += prop.write(writer);
    }
    return written;
  }

  /**
   * Hashcode of an ObjectValue object is generated based on the properties (declared, undeclared,
   * recycled).
   */
  @Override
  public int hashCode() {
    return Objects.hash(declaredProperties, undeclaredProperties, recycledProperties);
  }

  /**
   * Equality of ObjectValue objects is based on equality of properties (declared, undeclared,
   * recycled).
   */
  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof ObjectValue)) {
      return false;
    }

    final ObjectValue that = (ObjectValue) o;
    return Objects.equals(declaredProperties, that.declaredProperties)
        && Objects.equals(undeclaredProperties, that.undeclaredProperties)
        && Objects.equals(recycledProperties, that.recycledProperties);
  }

  private <T extends BaseProperty<?>> int getEncodedLength(final List<T> properties) {
    int length = 0;
    for (int i = 0; i < properties.size(); ++i) {
      final T prop = properties.get(i);
      length += prop.getEncodedLength();
    }
    return length;
  }

  public boolean isEmpty() {
    return declaredProperties.isEmpty() && undeclaredProperties.isEmpty();
  }
}
