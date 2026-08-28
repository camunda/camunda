/*
 * Copyright 2014-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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

package io.atomix.utils.serializer.serializers;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import java.util.AbstractMap.SimpleImmutableEntry;

/**
 * Kryo Serializer for {@link SimpleImmutableEntry}.
 *
 * <p>Neither of the two obvious alternatives works here. Kryo's default {@code
 * CompatibleFieldSerializer} cannot read the private final fields of a {@code java.util} class,
 * because the JDK does not open that package for reflection. {@code JavaSerializer} does work, but
 * it hands the key and the value to {@code ObjectInputStream}, which ignores Kryo's registration
 * allowlist and so reconstructs whatever type the bytes name.
 *
 * <p>Writing key and value through {@link Kryo#writeClassAndObject} keeps both on the registered
 * type path, so an entry can only carry types the namespace already allows.
 */
public final class SimpleImmutableEntrySerializer extends Serializer<SimpleImmutableEntry<?, ?>> {

  /** Creates a {@link SimpleImmutableEntry} serializer instance. */
  public SimpleImmutableEntrySerializer() {
    // non-null, immutable
    super(false, true);
  }

  @Override
  public void write(final Kryo kryo, final Output output, final SimpleImmutableEntry<?, ?> object) {
    kryo.writeClassAndObject(output, object.getKey());
    kryo.writeClassAndObject(output, object.getValue());
  }

  @Override
  public SimpleImmutableEntry<?, ?> read(
      final Kryo kryo, final Input input, final Class<? extends SimpleImmutableEntry<?, ?>> type) {
    final Object key = kryo.readClassAndObject(input);
    final Object value = kryo.readClassAndObject(input);
    return new SimpleImmutableEntry<>(key, value);
  }
}
