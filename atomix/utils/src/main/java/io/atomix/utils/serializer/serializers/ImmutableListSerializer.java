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
import com.google.common.collect.ImmutableList;

/** Creates {@link ImmutableList} serializer instance. */
public class ImmutableListSerializer extends Serializer<ImmutableList<?>> {

  /** Creates {@link ImmutableList} serializer instance. */
  public ImmutableListSerializer() {
    // non-null, immutable
    super(false, true);
  }

  @Override
  public void write(final Kryo kryo, final Output output, final ImmutableList<?> object) {
    output.writeInt(object.size());
    for (final Object e : object) {
      kryo.writeClassAndObject(output, e);
    }
  }

  @Override
  public ImmutableList<?> read(
      final Kryo kryo, final Input input, final Class<ImmutableList<?>> type) {
    final int size = input.readInt();
    switch (size) {
      case 0:
        return ImmutableList.of();
      case 1:
        return ImmutableList.of(kryo.readClassAndObject(input));
      default:
        final Object[] elms = new Object[size];
        for (int i = 0; i < size; ++i) {
          elms[i] = kryo.readClassAndObject(input);
        }
        return ImmutableList.copyOf(elms);
    }
  }
}
