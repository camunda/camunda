/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.msgpack.value;

import io.zeebe.msgpack.property.BaseProperty;
import io.zeebe.msgpack.property.UndeclaredProperty;
import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ObjectValue extends BaseValue {
  private final List<BaseProperty<? extends BaseValue>> declaredProperties = new ArrayList<>();
  private final List<UndeclaredProperty> undeclaredProperties = new ArrayList<>();
  private final List<UndeclaredProperty> recycledProperties = new ArrayList<>();

  private final StringValue decodedKey = new StringValue();

  public ObjectValue declareProperty(BaseProperty<? extends BaseValue> prop) {
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

  private UndeclaredProperty newUndeclaredProperty(StringValue key) {
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
  public void writeJSON(StringBuilder builder) {
    builder.append("{");

    writeJson(builder, declaredProperties);
    writeJson(builder, undeclaredProperties);

    builder.append("}");
  }

  protected <T extends BaseProperty<?>> void writeJson(StringBuilder builder, List<T> properties) {
    for (int i = 0; i < properties.size(); i++) {
      if (i > 0) {
        builder.append(",");
      }

      final BaseProperty<? extends BaseValue> prop = properties.get(i);

      if (prop.hasValue()) {
        prop.writeJSON(builder);
      }
    }
  }

  @Override
  public void read(MsgPackReader reader) {
    final int mapSize = reader.readMapHeader();

    for (int i = 0; i < mapSize; ++i) {
      decodedKey.read(reader);

      BaseProperty<? extends BaseValue> prop = null;

      for (int k = 0; k < declaredProperties.size(); ++k) {
        final BaseProperty<?> declaredProperty = declaredProperties.get(k);
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
      } catch (Exception e) {
        throw new RuntimeException(String.format("Could not read property '%s'", prop.getKey()), e);
      }
    }

    // verify that all required properties are set
    for (int p = 0; p < declaredProperties.size(); p++) {
      final BaseProperty<?> prop = declaredProperties.get(p);
      if (!prop.hasValue()) {
        throw new RuntimeException(
            String.format("Property '%s' has no valid value", prop.getKey()));
      }
    }
  }

  /**
   * Caution: In case not all properties are writeable (i.e. value not set and no default), this
   * method may write some of the values and only then throw an exception. The same exception is
   * raised by {@link #getEncodedLength()}. If you call that first and it succeeds, you are safe to
   * write all the values.
   */
  @Override
  public void write(MsgPackWriter writer) {
    final int size = declaredProperties.size() + undeclaredProperties.size();

    writer.writeMapHeader(size);
    write(writer, declaredProperties);
    write(writer, undeclaredProperties);
  }

  protected <T extends BaseProperty<?>> void write(MsgPackWriter writer, List<T> properties) {
    for (int i = 0; i < properties.size(); ++i) {
      final BaseProperty<? extends BaseValue> prop = properties.get(i);
      prop.write(writer);
    }
  }

  @Override
  public int getEncodedLength() {
    final int size = declaredProperties.size() + undeclaredProperties.size();

    int length = MsgPackWriter.getEncodedMapHeaderLenght(size);
    length += getEncodedLength(declaredProperties);
    length += getEncodedLength(undeclaredProperties);

    return length;
  }

  @Override
  public boolean equals(Object o) {
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

  @Override
  public int hashCode() {
    return Objects.hash(declaredProperties, undeclaredProperties, recycledProperties);
  }

  protected <T extends BaseProperty<?>> int getEncodedLength(List<T> properties) {
    int length = 0;
    for (int i = 0; i < properties.size(); ++i) {
      final T prop = properties.get(i);
      length += prop.getEncodedLength();
    }
    return length;
  }
}
