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
import com.esotericsoftware.kryo.Registration;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;
import com.esotericsoftware.kryo.pool.KryoCallback;
import com.esotericsoftware.kryo.pool.KryoFactory;
import com.esotericsoftware.kryo.pool.KryoPool;
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer;
import com.esotericsoftware.kryo.serializers.VersionFieldSerializer;
import com.esotericsoftware.minlog.Log;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.tuple.Pair;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.slf4j.Logger;

/** Pool of Kryo instances, with classes pre-registered. */
// @ThreadSafe
public class NamespaceImpl implements Namespace, KryoFactory, KryoPool {

  /**
   * Default buffer size used for serialization.
   *
   * @see #serialize(Object)
   */
  public static final int DEFAULT_BUFFER_SIZE = 4096;

  /** Maximum allowed buffer size. */
  public static final int MAX_BUFFER_SIZE = 100 * 1000 * 1000;

  /** ID to use if this KryoNamespace does not define registration id. */
  public static final int FLOATING_ID = -1;

  /** Smallest ID free to use for user defined registrations. */
  public static final int INITIAL_ID = 16;

  static final byte MAGIC_BYTE = (byte) 0xFF;
  static final byte VERSION_BYTE = 0x01;
  static final byte[] VERSION_HEADER = new byte[] {VERSION_BYTE, MAGIC_BYTE};
  static final String NO_NAME = "(no name)";

  private static final Logger LOGGER = getLogger(NamespaceImpl.class);

  static {
    Log.NONE();
  }

  private final KryoPool kryoPool = new KryoPool.Builder(this).softReferences().build();
  private final KryoOutputPool kryoOutputPool = new KryoOutputPool();
  private final KryoInputPool kryoInputPool = new KryoInputPool();
  private final ImmutableList<RegistrationBlock> registeredBlocks;
  private final ClassLoader classLoader;
  private final boolean compatible;
  private final boolean registrationRequired;
  private final String friendlyName;

  /**
   * Creates a Kryo instance pool.
   *
   * @param registeredTypes types to register
   * @param registrationRequired whether registration is required
   * @param compatible whether compatible serialization is enabled
   * @param friendlyName friendly name for the namespace
   */
  public NamespaceImpl(
      final List<RegistrationBlock> registeredTypes,
      final ClassLoader classLoader,
      final boolean registrationRequired,
      final boolean compatible,
      final String friendlyName) {
    registeredBlocks = ImmutableList.copyOf(registeredTypes);
    this.registrationRequired = registrationRequired;
    this.classLoader = classLoader;
    this.compatible = compatible;
    this.friendlyName = checkNotNull(friendlyName);
  }

  /**
   * Populates the Kryo pool.
   *
   * @param instances to add to the pool
   * @return this
   */
  public NamespaceImpl populate(final int instances) {
    for (int i = 0; i < instances; ++i) {
      release(create());
    }
    return this;
  }

  /**
   * Serializes given object to byte array using Kryo instance in pool.
   *
   * <p>Note: Serialized bytes must be smaller than {@link #MAX_BUFFER_SIZE}.
   *
   * @param obj Object to serialize
   * @return serialized bytes
   */
  @Override
  public byte[] serialize(final Object obj) {
    return kryoOutputPool.run(
        output ->
            kryoPool.run(
                kryo -> {
                  output.write(VERSION_HEADER);
                  kryo.writeClassAndObject(output, obj);
                  output.flush();
                  return output.getByteArrayOutputStream().toByteArray();
                }),
        DEFAULT_BUFFER_SIZE);
  }

  /**
   * Serializes given object to byte buffer using Kryo instance in pool.
   *
   * @param obj Object to serialize
   * @param buffer to write to
   */
  @Override
  public void serialize(final Object obj, final ByteBuffer buffer) {
    final Kryo kryo = borrow();
    try (final ByteBufferOutput out = new ByteBufferOutput(buffer)) {
      out.write(VERSION_HEADER);
      kryo.writeClassAndObject(out, obj);
    } finally {
      release(kryo);
    }
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
    return deserialize(bytes, 0);
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
    final Kryo kryo = borrow();
    try (final ByteBufferInput in = new ByteBufferInput(buffer)) {
      @SuppressWarnings("unchecked")
      final T obj = (T) kryo.readClassAndObject(in);
      return obj;
    } finally {
      release(kryo);
    }
  }

  @Override
  public ImmutableList<RegistrationBlock> getRegisteredBlocks() {
    return registeredBlocks;
  }

  /**
   * Deserializes given byte array to Object using Kryo instance in pool.
   *
   * @param bytes serialized bytes
   * @param offset offset in serialized bytes
   * @param <T> deserialized Object type
   * @return deserialized Object
   */
  public <T> T deserialize(final byte[] bytes, final int offset) {
    return kryoInputPool.run(
        input -> {
          input.setInputStream(new ByteArrayInputStream(bytes, offset, bytes.length - offset));
          return kryoPool.run(
              kryo -> {
                @SuppressWarnings("unchecked")
                final T obj = (T) kryo.readClassAndObject(input);
                return obj;
              });
        },
        DEFAULT_BUFFER_SIZE);
  }

  private String friendlyName() {
    return friendlyName;
  }

  /**
   * Creates a Kryo instance.
   *
   * @return Kryo instance
   */
  @Override
  public Kryo create() {
    LOGGER.trace("Creating Kryo instance for {}", this);
    final Kryo kryo = new Kryo();
    kryo.setClassLoader(classLoader);
    kryo.setRegistrationRequired(registrationRequired);

    // If compatible serialization is enabled, override the default serializer.
    if (compatible) {
      kryo.setDefaultSerializer(VersionFieldSerializer::new);
    }

    // TODO rethink whether we want to use StdInstantiatorStrategy
    kryo.setInstantiatorStrategy(
        new Kryo.DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));

    for (final RegistrationBlock block : registeredBlocks) {
      int id = block.begin();
      if (id == FLOATING_ID) {
        id = kryo.getNextRegistrationId();
      }
      for (final Pair<Class<?>[], Serializer<?>> entry : block.types()) {
        register(kryo, entry.getLeft(), entry.getRight(), id++);
      }
    }
    return kryo;
  }

  /**
   * Register {@code type} and {@code serializer} to {@code kryo} instance.
   *
   * @param kryo Kryo instance
   * @param types types to register
   * @param serializer Specific serializer to register or null to use default.
   * @param id type registration id to use
   */
  private void register(
      final Kryo kryo, final Class<?>[] types, final Serializer<?> serializer, final int id) {
    final Registration existing = kryo.getRegistration(id);
    if (existing != null) {
      boolean matches = false;
      for (final Class<?> type : types) {
        if (existing.getType() == type) {
          matches = true;
          break;
        }
      }

      if (!matches) {
        LOGGER.error(
            "{}: Failed to register {} as {}, {} was already registered.",
            friendlyName(),
            types,
            id,
            existing.getType());

        throw new IllegalStateException(
            String.format(
                "Failed to register %s as %s, %s was already registered.",
                Arrays.toString(types), id, existing.getType()));
      }
      // falling through to register call for now.
      // Consider skipping, if there's reasonable
      // way to compare serializer equivalence.
    }

    for (final Class<?> type : types) {
      Registration r = null;
      if (serializer == null) {
        r = kryo.register(type, id);
      } else if (type.isInterface()) {
        kryo.addDefaultSerializer(type, serializer);
      } else {
        r = kryo.register(type, serializer, id);
      }
      if (r != null) {
        if (r.getId() != id) {
          LOGGER.debug(
              "{}: {} already registered as {}. Skipping {}.",
              friendlyName(),
              r.getType(),
              r.getId(),
              id);
        }
        LOGGER.trace("{} registered as {}", r.getType(), r.getId());
      }
    }
  }

  @Override
  public Kryo borrow() {
    return kryoPool.borrow();
  }

  @Override
  public void release(final Kryo kryo) {
    kryoPool.release(kryo);
  }

  @Override
  public <T> T run(final KryoCallback<T> callback) {
    return kryoPool.run(callback);
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
    private ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    private boolean registrationRequired = true;
    private boolean compatible = false;
    private String name = NO_NAME;

    /**
     * Builds a {@link Namespace} instance.
     *
     * @return KryoNamespace
     */
    public NamespaceImpl build() {
      if (!types.isEmpty()) {
        blocks.add(new RegistrationBlock(blockHeadId, types));
      }
      return new NamespaceImpl(blocks, classLoader, registrationRequired, compatible, name)
          .populate(1);
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
        if (id != FLOATING_ID && id < blockHeadId + types.size()) {

          if (LOGGER.isWarnEnabled()) {
            LOGGER.warn(
                "requested nextId {} could potentially overlap "
                    + "with existing registrations {}+{} ",
                id,
                blockHeadId,
                types.size(),
                new RuntimeException());
          }
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
      types.add(Pair.of(classes, checkNotNull(serializer)));
      return this;
    }

    private Builder register(final RegistrationBlock block) {
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
      return this;
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

    /**
     * Sets the namespace class loader.
     *
     * @param classLoader the namespace class loader
     * @return the namespace builder
     */
    public Builder setClassLoader(final ClassLoader classLoader) {
      this.classLoader = classLoader;
      return this;
    }

    /**
     * Sets whether backwards/forwards compatible versioned serialization is enabled.
     *
     * <p>When compatible serialization is enabled, the {@link CompatibleFieldSerializer} will be
     * set as the default serializer for types that do not otherwise explicitly specify a
     * serializer.
     *
     * @param compatible whether versioned serialization is enabled
     * @return this
     */
    public Builder setCompatible(final boolean compatible) {
      this.compatible = compatible;
      return this;
    }

    /**
     * Sets the registrationRequired flag.
     *
     * @param registrationRequired Kryo's registrationRequired flag
     * @return this
     * @see Kryo#setRegistrationRequired(boolean)
     */
    public Builder setRegistrationRequired(final boolean registrationRequired) {
      this.registrationRequired = registrationRequired;
      return this;
    }

    /**
     * Creates a copy of the builder.
     *
     * @return copy of this builder
     */
    public Builder copy() {
      final Builder copy = new Builder();
      copy.blockHeadId = blockHeadId;
      copy.blocks.addAll(blocks);
      copy.classLoader = classLoader;
      copy.compatible = compatible;
      copy.types.addAll(types);
      copy.registrationRequired = registrationRequired;
      copy.name = name;

      return copy;
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
