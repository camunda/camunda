/*
 * Copyright 2018-present Open Networking Foundation
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
package io.atomix.core.registry;

import com.google.common.collect.Maps;
import io.atomix.cluster.discovery.NodeDiscoveryProvider;
import io.atomix.core.AtomixRegistry;
import io.atomix.core.profile.Profile;
import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.partition.PartitionGroup;
import io.atomix.primitive.protocol.PrimitiveProtocol;
import io.atomix.utils.NamedType;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/** Static Atomix registry. */
public final class SimpleRegistry implements AtomixRegistry {

  private final Map<Class<?>, Map<String, NamedType>> registrations;

  private SimpleRegistry(final Map<Class<?>, Map<String, NamedType>> registrations) {
    this.registrations = registrations;
  }

  /**
   * Returns a new static registry builder.
   *
   * @return a new static registry builder
   */
  public static Builder builder() {
    return new Builder();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends NamedType> Collection<T> getTypes(final Class<T> type) {
    final Map<String, NamedType> types = registrations.get(type);
    return types != null ? (Collection<T>) types.values() : Collections.emptyList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends NamedType> T getType(final Class<T> type, final String name) {
    final Map<String, NamedType> types = registrations.get(type);
    return types != null ? (T) types.get(name) : null;
  }

  /** Atomix registry builder. */
  public static class Builder implements io.atomix.utils.Builder<AtomixRegistry> {
    private final Map<Class<?>, Map<String, NamedType>> registrations = Maps.newHashMap();

    /**
     * Adds a profile type to the builder.
     *
     * @param profileType the profile type to add
     * @return the registry builder
     */
    public Builder addProfileType(final Profile.Type profileType) {
      registrations
          .computeIfAbsent(Profile.Type.class, t -> Maps.newHashMap())
          .put(profileType.name(), profileType);
      return this;
    }

    /**
     * Adds a node discovery provider type.
     *
     * @param discoveryProviderType the discovery provider type to add
     * @return the registry builder
     */
    public Builder addDiscoveryProviderType(
        final NodeDiscoveryProvider.Type discoveryProviderType) {
      registrations
          .computeIfAbsent(NodeDiscoveryProvider.Type.class, t -> Maps.newHashMap())
          .put(discoveryProviderType.name(), discoveryProviderType);
      return this;
    }

    /**
     * Adds a primitive type.
     *
     * @param primitiveType the primitive type to add
     * @return the registry builder
     */
    public Builder addPrimitiveType(final PrimitiveType primitiveType) {
      registrations
          .computeIfAbsent(PrimitiveType.class, t -> Maps.newHashMap())
          .put(primitiveType.name(), primitiveType);
      return this;
    }

    /**
     * Adds a protocol type.
     *
     * @param protocolType the protocol type to add
     * @return the registry builder
     */
    public Builder addProtocolType(final PrimitiveProtocol.Type protocolType) {
      registrations
          .computeIfAbsent(PrimitiveProtocol.Type.class, t -> Maps.newHashMap())
          .put(protocolType.name(), protocolType);
      return this;
    }

    /**
     * Adds a partition group type.
     *
     * @param partitionGroupType the partition group type to add
     * @return the registry builder
     */
    public Builder addPartitionGroupType(final PartitionGroup.Type partitionGroupType) {
      registrations
          .computeIfAbsent(PartitionGroup.Type.class, t -> Maps.newHashMap())
          .put(partitionGroupType.name(), partitionGroupType);
      return this;
    }

    @Override
    public AtomixRegistry build() {
      return new SimpleRegistry(registrations);
    }
  }
}
