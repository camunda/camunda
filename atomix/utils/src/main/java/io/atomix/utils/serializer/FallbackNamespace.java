/*
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
package io.atomix.utils.serializer;

import static org.slf4j.LoggerFactory.getLogger;

import java.nio.ByteBuffer;
import org.slf4j.Logger;

public class FallbackNamespace implements Namespace {

  private static final Logger LOG = getLogger(FallbackNamespace.class);
  private static final String DESERIALIZE_ERROR =
      "Deserialization failed with both the versioned and fallback serializers. The fallback serializer failed with:\n %s";
  private final Namespace fallback;
  private final Namespace namespace;

  public FallbackNamespace(final Namespace fallback, final Namespace namespace) {
    this.fallback = fallback;
    this.namespace = namespace;
  }

  /**
   * Serializes given object to byte array using Kryo instance in pool.
   *
   * <p>Note: Serialized bytes must be smaller than {@link NamespaceImpl#MAX_BUFFER_SIZE}.
   *
   * @param obj Object to serialize
   * @return serialized bytes
   */
  public byte[] serialize(final Object obj) {
    return namespace.serialize(obj);
  }

  /**
   * Serializes given object to byte array using Kryo instance in pool.
   *
   * @param obj Object to serialize
   * @param bufferSize maximum size of serialized bytes
   * @return serialized bytes
   */
  public byte[] serialize(final Object obj, final int bufferSize) {
    return namespace.serialize(obj, bufferSize);
  }

  /**
   * Serializes given object to byte buffer using Kryo instance in pool.
   *
   * @param obj Object to serialize
   * @param buffer to write to
   */
  public void serialize(final Object obj, final ByteBuffer buffer) {
    namespace.serialize(obj, buffer);
  }

  /**
   * Deserializes given byte array to Object using Kryo instance in pool.
   *
   * @param bytes serialized bytes
   * @param <T> deserialized Object type
   * @return deserialized Object
   */
  public <T> T deserialize(final byte[] bytes) {
    try {
      return namespace.deserialize(bytes);
    } catch (final Exception compatEx) {
      try {
        return fallback.deserialize(bytes);
      } catch (final Exception legacyEx) {
        // rethrow most relevant exception and log the second one
        LOG.warn(String.format(DESERIALIZE_ERROR, legacyEx));
        throw compatEx;
      }
    }
  }

  /**
   * Deserializes given byte buffer to Object using Kryo instance in pool.
   *
   * @param buffer input with serialized bytes
   * @param <T> deserialized Object type
   * @return deserialized Object
   */
  public <T> T deserialize(final ByteBuffer buffer) {
    final int position = buffer.position();
    final int limit = buffer.limit();

    try {
      return namespace.deserialize(buffer);
    } catch (final Exception compatEx) {
      try {
        buffer.position(position).limit(limit);
        return fallback.deserialize(buffer);
      } catch (final Exception legacyEx) {
        // rethrow most relevant exception and log the second one
        LOG.warn(String.format(DESERIALIZE_ERROR, legacyEx));
        throw compatEx;
      }
    }
  }
}
