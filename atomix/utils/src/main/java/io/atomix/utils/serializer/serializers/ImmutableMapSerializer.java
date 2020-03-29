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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import java.util.Map.Entry;

/** Kryo Serializer for {@link ImmutableMap}. */
public class ImmutableMapSerializer extends Serializer<ImmutableMap<?, ?>> {

  /** Creates {@link ImmutableMap} serializer instance. */
  public ImmutableMapSerializer() {
    // non-null, immutable
    super(false, true);
  }

  @Override
  public void write(final Kryo kryo, final Output output, final ImmutableMap<?, ?> object) {
    output.writeInt(object.size());
    for (final Entry<?, ?> e : object.entrySet()) {
      kryo.writeClassAndObject(output, e.getKey());
      kryo.writeClassAndObject(output, e.getValue());
    }
  }

  @Override
  public ImmutableMap<?, ?> read(
      final Kryo kryo, final Input input, final Class<ImmutableMap<?, ?>> type) {
    final int size = input.readInt();
    switch (size) {
      case 0:
        return ImmutableMap.of();
      case 1:
        return ImmutableMap.of(kryo.readClassAndObject(input), kryo.readClassAndObject(input));

      default:
        final Builder<Object, Object> builder = ImmutableMap.builder();
        for (int i = 0; i < size; ++i) {
          builder.put(kryo.readClassAndObject(input), kryo.readClassAndObject(input));
        }
        return builder.build();
    }
  }
}
