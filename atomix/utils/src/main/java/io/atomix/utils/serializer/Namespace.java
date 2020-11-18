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

import com.google.common.collect.ImmutableList;
import io.atomix.utils.serializer.NamespaceImpl.RegistrationBlock;
import java.nio.ByteBuffer;

public interface Namespace {

  /**
   * Serializes given object to byte array using Kryo instance in pool.
   *
   * <p>Note: Serialized bytes must be smaller than {@link #MAX_BUFFER_SIZE}.
   *
   * @param obj Object to serialize
   * @return serialized bytes
   */
  byte[] serialize(final Object obj);

  /**
   * Serializes given object to byte buffer using Kryo instance in pool.
   *
   * @param obj Object to serialize
   * @param buffer to write to
   */
  void serialize(final Object obj, final ByteBuffer buffer);

  /**
   * Deserializes given byte array to Object using Kryo instance in pool.
   *
   * @param bytes serialized bytes
   * @param <T> deserialized Object type
   * @return deserialized Object
   */
  <T> T deserialize(final byte[] bytes);

  /**
   * Deserializes given byte buffer to Object using Kryo instance in pool.
   *
   * @param buffer input with serialized bytes
   * @param <T> deserialized Object type
   * @return deserialized Object
   */
  <T> T deserialize(final ByteBuffer buffer);

  ImmutableList<RegistrationBlock> getRegisteredBlocks();
}
