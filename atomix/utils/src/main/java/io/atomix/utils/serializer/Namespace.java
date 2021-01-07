/*
 * Copyright 2014-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.utils.serializer;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.slf4j.LoggerFactory.getLogger;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.util.Pool;
import com.esotericsoftware.minlog.Log;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;

/** Pool of Kryo instances, with classes pre-registered. */
public class Namespace {

  /** ID to use if this KryoNamespace does not define registration id. */
  static final int FLOATING_ID = -1;

  static final String NO_NAME = "(no name)";
  private static final Logger LOGGER = getLogger(Namespace.class);

  /** Default buffer size used for serialization (@see #serialize(Object)). */
  private static final int DEFAULT_BUFFER_SIZE = 4096;

  private static final int MAX_OUTPUT_BUFFER_SIZE = 768 * 1024;

  private static final int MAX_POOLED_BUFFER_SIZE = 512 * 1024;

  /** Smallest ID free to use for user defined registrations. */
  private static final int INITIAL_ID = 16;

  static {
    Log.NONE();
  }

  private final Pool<Kryo> kryoPool;
  private final Pool<ByteArrayOutput> outputPool =
      new Pool<>(true, true) {
        @Override
        protected ByteArrayOutput create() {
          return new ByteArrayOutput(
              DEFAULT_BUFFER_SIZE,
              MAX_OUTPUT_BUFFER_SIZE,
              new BufferAwareByteArrayOutputStream(DEFAULT_BUFFER_SIZE));
        }

        @Override
        public void free(final ByteArrayOutput output) {
          if (output.getByteArrayOutputStream().getBufferSize() < MAX_POOLED_BUFFER_SIZE) {
            output.getByteArrayOutputStream().reset();
            output.reset();
            super.free(output);
          }
        }
      };
  private final Pool<Input> inputPool =
      new Pool<>(true, true) {
        @Override
        protected Input create() {
          return new Input(DEFAULT_BUFFER_SIZE);
        }

        @Override
        public void free(final Input input) {
          if (input.getBuffer().length < MAX_POOLED_BUFFER_SIZE) {
            input.reset();
            input.setInputStream(null);
            super.free(input);
          }
        }
      };

  private final ImmutableList<RegistrationBlock> registeredBlocks;
  private final String friendlyName;

  /**
   * Creates a Kryo instance pool.
   *
   * @param registeredTypes types to register
   * @param friendlyName friendly name for the namespace
   */
  public Namespace(final List<RegistrationBlock> registeredTypes, final String friendlyName) {
    this.registeredBlocks = ImmutableList.copyOf(registeredTypes);
    this.friendlyName = checkNotNull(friendlyName);

    final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    kryoPool = new CompatibleKryoPool(friendlyName, classLoader, registeredTypes);
    kryoPool.free(kryoPool.obtain());
  }

  /**
   * Serializes given object to byte array using Kryo instance in pool.
   *
   * @param obj Object to serialize
   * @return serialized bytes
   */
  public byte[] serialize(final Object obj) {
    final ByteArrayOutput output = outputPool.obtain();
    try {
      final Kryo kryo = kryoPool.obtain();
      try {
        kryo.writeClassAndObject(output, obj);
      } finally {
        kryoPool.free(kryo);
      }
      output.flush();
      return output.getByteArrayOutputStream().toByteArray();
    } finally {
      outputPool.free(output);
    }
  }

  /**
   * Serializes given object to byte buffer using Kryo instance in pool.
   *
   * @param obj Object to serialize
   * @param buffer to write to
   */
  public void serialize(final Object obj, final ByteBuffer buffer) {
    final Kryo kryo = kryoPool.obtain();
    try (final ByteBufferOutput output = new ByteBufferOutput(buffer)) {
      kryo.writeClassAndObject(output, obj);
    } finally {
      kryoPool.free(kryo);
    }
  }

  /**
   * Deserializes given byte array to Object using Kryo instance in pool.
   *
   * @param bytes serialized bytes
   * @param <T> deserialized Object type
   * @return deserialized Object
   */
  @SuppressWarnings("unchecked")
  public <T> T deserialize(final byte[] bytes) {
    final Input input = inputPool.obtain();
    try {
      final Kryo kryo = kryoPool.obtain();

      try {
        input.setInputStream(new ByteArrayInputStream(bytes));
        return (T) kryo.readClassAndObject(input);
      } finally {
        kryoPool.free(kryo);
      }
    } finally {
      inputPool.free(input);
    }
  }

  /**
   * Deserializes given byte buffer to Object using Kryo instance in pool.
   *
   * @param buffer input with serialized bytes
   * @param <T> deserialized Object type
   * @return deserialized Object
   */
  @SuppressWarnings("unchecked")
  public <T> T deserialize(final ByteBuffer buffer) {
    final Kryo kryo = kryoPool.obtain();
    try (final ByteBufferInput input = new ByteBufferInput(buffer)) {
      return (T) kryo.readClassAndObject(input);
    } finally {
      kryoPool.free(kryo);
    }
  }

  public ImmutableList<RegistrationBlock> getRegisteredBlocks() {
    return registeredBlocks;
  }

  @Override
  public String toString() {
    if (!friendlyName.equals(NO_NAME)) {
      return MoreObjects.toStringHelper(getClass())
          .omitNullValues()
          .add("friendlyName", friendlyName)
          // omit lengthy detail, when there's a name
          .toString();
    }
    return MoreObjects.toStringHelper(getClass())
        .add("registeredBlocks", registeredBlocks)
        .toString();
  }

  /** KryoNamespace builder. */
  // @NotThreadSafe
  public static final class Builder {
    private int blockHeadId = INITIAL_ID;
    private List<Pair<Class<?>[], Serializer<?>>> types = new ArrayList<>();
    private final List<RegistrationBlock> blocks = new ArrayList<>();
    private String name = NO_NAME;

    /**
     * Builds a {@link Namespace} instance.
     *
     * @return KryoNamespace
     */
    public Namespace build() {
      if (!types.isEmpty()) {
        blocks.add(new RegistrationBlock(blockHeadId, types));
      }
      return new Namespace(blocks, name);
    }

    public Builder name(final String name) {
      this.name = name;
      return this;
    }

    public String getName() {
      return name;
    }

    /**
     * Sets the next Kryo registration Id for following register entries.
     *
     * @param id Kryo registration Id
     * @return this
     * @see Kryo#register(Class, Serializer, int)
     */
    public Builder nextId(final int id) {
      if (!types.isEmpty()) {
        if (id != FLOATING_ID && id < blockHeadId + types.size() && LOGGER.isWarnEnabled()) {
          LOGGER.warn(
              "requested nextId {} could potentially overlap "
                  + "with existing registrations {}+{} ",
              id,
              blockHeadId,
              types.size(),
              new RuntimeException());
        }

        blocks.add(new RegistrationBlock(blockHeadId, types));
        types = new ArrayList<>();
      }
      blockHeadId = id;
      return this;
    }

    /**
     * Registers classes to be serialized using Kryo default serializer.
     *
     * @param expectedTypes list of classes
     * @return this
     */
    public Builder register(final Class<?>... expectedTypes) {
      for (final Class<?> clazz : expectedTypes) {
        types.add(Pair.of(new Class<?>[] {clazz}, null));
      }
      return this;
    }

    /**
     * Registers serializer for the given set of classes.
     *
     * <p>When multiple classes are registered with an explicitly provided serializer, the namespace
     * guarantees all instances will be serialized with the same type ID.
     *
     * @param classes list of classes to register
     * @param serializer serializer to use for the class
     * @return this
     */
    public Builder register(final Serializer<?> serializer, final Class<?>... classes) {
      for (final Class<?> clazz : classes) {
        types.add(Pair.of(new Class[] {clazz}, checkNotNull(serializer)));
      }
      return this;
    }

    private void register(final RegistrationBlock block) {
      if (block.begin() != FLOATING_ID) {
        // flush pending types
        nextId(block.begin());
        blocks.add(block);
        nextId(block.begin() + block.types().size());
      } else {
        // flush pending types
        final int addedBlockBegin = blockHeadId + types.size();
        nextId(addedBlockBegin);
        blocks.add(new RegistrationBlock(addedBlockBegin, block.types()));
        nextId(addedBlockBegin + block.types().size());
      }
    }

    /**
     * Registers all the class registered to given KryoNamespace.
     *
     * @param ns KryoNamespace
     * @return this
     */
    public Builder register(final Namespace ns) {
      if (blocks.containsAll(ns.getRegisteredBlocks())) {
        // Everything was already registered.
        LOGGER.debug("Ignoring {}, already registered.", ns);
        return this;
      }

      for (final RegistrationBlock block : ns.getRegisteredBlocks()) {
        register(block);
      }
      return this;
    }
  }

  static final class RegistrationBlock {
    private final int begin;
    private final ImmutableList<Pair<Class<?>[], Serializer<?>>> types;

    RegistrationBlock(final int begin, final List<Pair<Class<?>[], Serializer<?>>> types) {
      this.begin = begin;
      this.types = ImmutableList.copyOf(types);
    }

    public int begin() {
      return begin;
    }

    public ImmutableList<Pair<Class<?>[], Serializer<?>>> types() {
      return types;
    }

    @Override
    public int hashCode() {
      return types.hashCode();
    }

    // Only the registered types are used for equality.
    @Override
    public boolean equals(final Object obj) {
      if (this == obj) {
        return true;
      }

      if (obj instanceof RegistrationBlock) {
        final RegistrationBlock that = (RegistrationBlock) obj;
        return Objects.equals(types, that.types);
      }
      return false;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(getClass())
          .add("begin", begin)
          .add("types", types)
          .toString();
    }
  }
}
