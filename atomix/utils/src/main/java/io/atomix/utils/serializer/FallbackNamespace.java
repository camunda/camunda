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

import com.google.common.collect.ImmutableList;
import io.atomix.utils.serializer.NamespaceImpl.Builder;
import io.atomix.utils.serializer.NamespaceImpl.RegistrationBlock;
import java.nio.ByteBuffer;
import org.slf4j.Logger;

public class FallbackNamespace implements Namespace {

  private static final Logger LOG = getLogger(FallbackNamespace.class);
  private static final String DESERIALIZE_ERROR =
      "Serialized bytes contained header with version but deserialization failed (will fallback to FieldSerializer):\n";
  private static final String UNKNOWN_VERSION_ERROR =
      "Magic byte was encountered, signalling newer version of serializer, but version {} is unrecognized. Using FieldSerializer as fallback";
  private static final byte MAGIC_BYTE = (byte) 0xFF;
  private static final byte VERSION_BYTE = 0x01;
  private final NamespaceImpl legacy;
  private final NamespaceImpl compatible;

  FallbackNamespace(final NamespaceImpl legacy, final NamespaceImpl compatible) {
    this.legacy = legacy;
    this.compatible = compatible;
  }

  public FallbackNamespace(final NamespaceImpl.Builder builder) {
    final Builder copy = builder.copy();
    legacy = builder.build();
    compatible = copy.name(copy.getName() + "-compatible").setCompatible(true).build();
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
    return legacy.serialize(obj);
  }

  /**
   * Serializes given object to byte buffer using Kryo instance in pool.
   *
   * @param obj Object to serialize
   * @param buffer to write to
   */
  public void serialize(final Object obj, final ByteBuffer buffer) {
    legacy.serialize(obj, buffer);
  }

  /**
   * Deserializes given byte array to Object using Kryo instance in pool.
   *
   * @param bytes serialized bytes
   * @param <T> deserialized Object type
   * @return deserialized Object
   */
  public <T> T deserialize(final byte[] bytes) {
    final byte magicByte = bytes[1];
    final byte versionByte = bytes[0];

    if (magicByte != MAGIC_BYTE) {
      return legacy.deserialize(bytes);
    }

    if (versionByte == VERSION_BYTE) {
      try {
        return compatible.deserialize(bytes, 2);
      } catch (final Exception e) {
        LOG.debug(DESERIALIZE_ERROR, e);
      }
    } else {
      LOG.debug(UNKNOWN_VERSION_ERROR, (int) versionByte);
    }

    return legacy.deserialize(bytes);
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
      return legacy.deserialize(buffer);
    }

    if (version == VERSION_BYTE) {
      try {
        buffer.position(position + 2);
        return compatible.deserialize(buffer);
      } catch (final Exception e) {
        LOG.debug(DESERIALIZE_ERROR, e);
      }
    } else {
      LOG.debug(UNKNOWN_VERSION_ERROR, (int) version);
    }

    buffer.position(position);
    return legacy.deserialize(buffer);
  }

  @Override
  public ImmutableList<RegistrationBlock> getRegisteredBlocks() {
    return compatible.getRegisteredBlocks();
  }
}
