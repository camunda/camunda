/*
 * Copyright 2017-present Open Networking Foundation
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
package io.atomix.primitive;

import io.atomix.primitive.config.PrimitiveConfig;
import io.atomix.utils.AtomixRuntimeException;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Manages the creation of distributed primitive instances.
 *
 * <p>The primitives service provides various methods for constructing core and custom distributed
 * primitives. The service provides various methods for creating and operating on distributed
 * primitives. Generally, the primitive methods are separated into two types. Primitive getters
 * return multiton instances of a primitive. Primitives created via getters must be pre-configured
 * in the Atomix instance configuration. Alternatively, primitive builders can be used to create and
 * configure primitives in code:
 *
 * <pre>{@code
 * AtomicMap<String, String> map = atomix.mapBuilder("my-map")
 *   .withProtocol(MultiRaftProtocol.builder("raft")
 *     .withReadConsistency(ReadConsistency.SEQUENTIAL)
 *     .build())
 *   .build();
 *
 * }</pre>
 *
 * Custom primitives can be constructed by providing a custom {@link PrimitiveType} and using the
 * {@link #primitiveBuilder(String, PrimitiveType)} method:
 *
 * <pre>{@code
 * MyPrimitive myPrimitive = atomix.primitiveBuilder("my-primitive, MyPrimitiveType.instance())
 *   .withProtocol(MultiRaftProtocol.builder("raft")
 *     .withReadConsistency(ReadConsistency.SEQUENTIAL)
 *     .build())
 *   .build();
 *
 * }</pre>
 */
public interface PrimitiveFactory {

  /**
   * Returns a primitive type by name.
   *
   * @param typeName the primitive type name
   * @return the primitive type
   */
  PrimitiveType getPrimitiveType(String typeName);

  /**
   * Gets or creates a distributed primitive.
   *
   * <p>A new primitive of the given {@code primitiveType} will be created if no primitive instance
   * with the given {@code name} exists on this node, otherwise the existing instance will be
   * returned. The name is used to reference a distinct instance of the primitive within the
   * cluster. The returned primitive will share the same state with primitives of the same name on
   * other nodes.
   *
   * <p>When the instance is initially constructed, it will be configured with any pre-existing
   * primitive configuration defined in {@code atomix.conf}.
   *
   * <p>To get an asynchronous instance of the primitive, use the {@link SyncPrimitive#async()}
   * method:
   *
   * <pre>{@code
   * AsyncPrimitive async = atomix.getPrimitive("my-primitive").async();
   *
   * }</pre>
   *
   * @param name the primitive name
   * @param primitiveType the primitive type
   * @param <P> the primitive type
   * @return the primitive instance
   */
  default <P extends SyncPrimitive> P getPrimitive(
      final String name, final PrimitiveType<?, ?, P> primitiveType) {
    try {
      return getPrimitiveAsync(name, primitiveType).get(30, TimeUnit.SECONDS);
    } catch (final InterruptedException | ExecutionException | TimeoutException e) {
      throw new AtomixRuntimeException(e);
    }
  }

  /**
   * Gets or creates a distributed primitive.
   *
   * <p>A new primitive of the given {@code primitiveType} will be created if no primitive instance
   * with the given {@code name} exists on this node, otherwise the existing instance will be
   * returned. The name is used to reference a distinct instance of the primitive within the
   * cluster. The returned primitive will share the same state with primitives of the same name on
   * other nodes.
   *
   * <p>When the instance is initially constructed, it will be configured with any pre-existing
   * primitive configuration defined in {@code atomix.conf}.
   *
   * <p>To get an asynchronous instance of the primitive, use the {@link SyncPrimitive#async()}
   * method:
   *
   * <pre>{@code
   * AsyncPrimitive async = atomix.getPrimitive("my-primitive").async();
   *
   * }</pre>
   *
   * @param name the primitive name
   * @param primitiveType the primitive type
   * @param primitiveConfig the primitive configuration
   * @param <C> the primitive configuration type
   * @param <P> the primitive type
   * @return the primitive instance
   */
  default <C extends PrimitiveConfig<C>, P extends SyncPrimitive> P getPrimitive(
      final String name, final PrimitiveType<?, C, P> primitiveType, final C primitiveConfig) {
    try {
      return getPrimitiveAsync(name, primitiveType, primitiveConfig).get(30, TimeUnit.SECONDS);
    } catch (final InterruptedException | ExecutionException | TimeoutException e) {
      throw new AtomixRuntimeException(e);
    }
  }

  /**
   * Gets or creates a distributed primitive asynchronously.
   *
   * <p>A new primitive of the given {@code primitiveType} will be created if no primitive instance
   * with the given {@code name} exists on this node, otherwise the existing instance will be
   * returned. The name is used to reference a distinct instance of the primitive within the
   * cluster. The returned primitive will share the same state with primitives of the same name on
   * other nodes.
   *
   * <p>When the instance is initially constructed, it will be configured with any pre-existing
   * primitive configuration defined in {@code atomix.conf}.
   *
   * @param name the primitive name
   * @param primitiveType the primitive type
   * @param <P> the primitive type
   * @return the primitive instance
   */
  <P extends SyncPrimitive> CompletableFuture<P> getPrimitiveAsync(
      String name, PrimitiveType<?, ?, P> primitiveType);

  /**
   * Gets or creates a distributed primitive asynchronously.
   *
   * <p>A new primitive of the given {@code primitiveType} will be created if no primitive instance
   * with the given {@code name} exists on this node, otherwise the existing instance will be
   * returned. The name is used to reference a distinct instance of the primitive within the
   * cluster. The returned primitive will share the same state with primitives of the same name on
   * other nodes.
   *
   * <p>When the instance is initially constructed, it will be configured with any pre-existing
   * primitive configuration defined in {@code atomix.conf}.
   *
   * @param name the primitive name
   * @param primitiveType the primitive type
   * @param primitiveConfig the primitive configuration
   * @param <C> the primitive configuration type
   * @param <P> the primitive type
   * @return a future to be completed with the primitive instance
   */
  <C extends PrimitiveConfig<C>, P extends SyncPrimitive> CompletableFuture<P> getPrimitiveAsync(
      String name, PrimitiveType<?, C, P> primitiveType, C primitiveConfig);

  /**
   * Creates a new named primitive builder of the given {@code primitiveType}.
   *
   * <p>The primitive name must be provided when constructing the builder. The name is used to
   * reference a distinct instance of the primitive within the cluster. Multiple instances of the
   * primitive with the same name will share the same state. However, the instance of the primitive
   * constructed by the returned builder will be distinct and will not share local memory (e.g.
   * cache) with any other instance on this node.
   *
   * <p>To get an asynchronous instance of the primitive, use the {@link SyncPrimitive#async()}
   * method:
   *
   * <pre>{@code
   * AsyncPrimitive async = atomix.primitiveBuilder("my-primitive", MyPrimitiveType.instance()).build().async();
   *
   * }</pre>
   *
   * @param name the primitive name
   * @param primitiveType the primitive type
   * @param <B> the primitive builder type
   * @param <P> the primitive type
   * @return the primitive builder
   */
  <B extends PrimitiveBuilder<B, C, P>, C extends PrimitiveConfig<C>, P extends SyncPrimitive>
      B primitiveBuilder(String name, PrimitiveType<B, C, P> primitiveType);

  /**
   * Returns a collection of open primitives.
   *
   * @return a collection of open primitives
   */
  Collection<PrimitiveInfo> getPrimitives();

  /**
   * Returns a collection of open primitives of the given type.
   *
   * @param primitiveType the primitive type
   * @return a collection of open primitives of the given type
   */
  Collection<PrimitiveInfo> getPrimitives(PrimitiveType primitiveType);
}
