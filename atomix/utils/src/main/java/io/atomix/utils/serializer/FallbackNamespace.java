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

import static io.atomix.utils.serializer.NamespaceImpl.MAGIC_BYTE;
import static io.atomix.utils.serializer.NamespaceImpl.VERSION_BYTE;
import static io.atomix.utils.serializer.NamespaceImpl.VERSION_HEADER;
import static org.slf4j.LoggerFactory.getLogger;

import com.google.common.collect.ImmutableList;
import io.atomix.utils.serializer.NamespaceImpl.Builder;
import io.atomix.utils.serializer.NamespaceImpl.RegistrationBlock;
import java.nio.ByteBuffer;
import org.slf4j.Logger;

public class FallbackNamespace implements Namespace {

  private static final Logger LOG = getLogger(FallbackNamespace.class);
  private static final String DESERIALIZE_ERROR =
      "Serialized bytes contained header with version but deserialization failed (will fallback to FieldSerializer): ";
  private static final String UNKNOWN_VERSION_ERROR =
      "Magic byte was encountered, signalling newer version of serializer, but version {} is unrecognized. Using FieldSerializer as fallback";
  private final NamespaceImpl fallback;
  private final NamespaceImpl namespace;

  FallbackNamespace(final NamespaceImpl fallback, final NamespaceImpl namespace) {
    this.fallback = fallback;
    this.namespace = namespace;
  }

  public FallbackNamespace(final NamespaceImpl.Builder builder) {
    final Builder copy = builder.copy();
    fallback = builder.build();
    namespace = copy.name(copy.getName() + "-compatible").setCompatible(true).build();
  }

  /**
   * Serializes given object to byte array using Kryo instance in pool.
   *
   * <p>Note: Serialized bytes must be smaller than {@link NamespaceImpl#MAX_BUFFER_SIZE}.
   *
   * @param obj Object to serialize
   * @return serialized bytes
   */
  @Override
  public byte[] serialize(final Object obj) {
    return namespace.serialize(obj);
  }

  /**
   * Serializes given object to byte buffer using Kryo instance in pool.
   *
   * @param obj Object to serialize
   * @param buffer to write to
   */
  @Override
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
  @Override
  public <T> T deserialize(final byte[] bytes) {
    final byte magicByte = bytes[1];
    final byte versionByte = bytes[0];

    if (magicByte != MAGIC_BYTE) {
      return fallback.deserialize(bytes);
    }

    if (versionByte == VERSION_BYTE) {
      try {
        return namespace.deserialize(bytes, VERSION_HEADER.length);
      } catch (final Exception e) {
        LOG.warn(DESERIALIZE_ERROR, e);
      }
    } else {
      LOG.debug(UNKNOWN_VERSION_ERROR, (int) versionByte);
    }

    return fallback.deserialize(bytes);
  }

  /**
   * Deserializes given byte buffer to Object using Kryo instance in pool.
   *
   * @param buffer input with serialized bytes
   * @param <T> deserialized Object type
   * @return deserialized Object
   */
  @Override
  public <T> T deserialize(final ByteBuffer buffer) {
    final int position = buffer.position();
    final byte version = buffer.get(position);
    final byte magicByte = buffer.get(position + 1);

    if (magicByte != MAGIC_BYTE) {
      return fallback.deserialize(buffer);
    }

    if (version == VERSION_BYTE) {
      try {
        buffer.position(position + VERSION_HEADER.length);
        return namespace.deserialize(buffer);
      } catch (final Exception e) {
        LOG.warn(DESERIALIZE_ERROR, e);
      }
    } else {
      LOG.debug(UNKNOWN_VERSION_ERROR, (int) version);
    }

    buffer.position(position);
    return fallback.deserialize(buffer);
  }

  @Override
  public ImmutableList<RegistrationBlock> getRegisteredBlocks() {
    return namespace.getRegisteredBlocks();
  }
}
