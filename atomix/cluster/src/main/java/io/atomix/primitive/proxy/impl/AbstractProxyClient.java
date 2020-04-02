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
package io.atomix.primitive.proxy.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.atomix.primitive.PrimitiveState;
import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.protocol.PrimitiveProtocol;
import io.atomix.primitive.proxy.ProxyClient;
import io.atomix.primitive.proxy.ProxySession;
import io.atomix.utils.concurrent.Futures;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.LoggerFactory;

/** Default primitive proxy. */
public abstract class AbstractProxyClient<S> implements ProxyClient<S> {
  private final String name;
  private final PrimitiveType type;
  private final PrimitiveProtocol protocol;
  private final List<PartitionId> partitionIds = new CopyOnWriteArrayList<>();
  private final Map<PartitionId, ProxySession<S>> partitions = Maps.newConcurrentMap();
  private final Set<Consumer<PrimitiveState>> stateChangeListeners = Sets.newCopyOnWriteArraySet();
  private final Map<PartitionId, PrimitiveState> states = Maps.newHashMap();
  private volatile PrimitiveState state = PrimitiveState.CLOSED;

  public AbstractProxyClient(
      final String name,
      final PrimitiveType type,
      final PrimitiveProtocol protocol,
      final Collection<ProxySession<S>> partitions) {
    this.name = checkNotNull(name, "name cannot be null");
    this.type = checkNotNull(type, "type cannot be null");
    this.protocol = checkNotNull(protocol, "protocol cannot be null");
    partitions.forEach(
        partition -> {
          this.partitionIds.add(partition.partitionId());
          this.partitions.put(partition.partitionId(), partition);
          states.put(partition.partitionId(), PrimitiveState.CLOSED);
          partition.addStateChangeListener(state -> onStateChange(partition.partitionId(), state));
        });
    Collections.sort(partitionIds);
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public PrimitiveType type() {
    return type;
  }

  @Override
  public PrimitiveProtocol protocol() {
    return protocol;
  }

  @Override
  public PrimitiveState getState() {
    return state;
  }

  @Override
  public Collection<ProxySession<S>> getPartitions() {
    return partitions.values();
  }

  @Override
  public List<PartitionId> getPartitionIds() {
    return partitionIds;
  }

  @Override
  public ProxySession<S> getPartition(final PartitionId partitionId) {
    return partitions.get(partitionId);
  }

  @Override
  public void addStateChangeListener(final Consumer<PrimitiveState> listener) {
    stateChangeListeners.add(listener);
  }

  @Override
  public void removeStateChangeListener(final Consumer<PrimitiveState> listener) {
    stateChangeListeners.remove(listener);
  }

  @Override
  public CompletableFuture<ProxyClient<S>> connect() {
    partitions.forEach(
        (partitionId, partition) -> {
          partition.addStateChangeListener(state -> onStateChange(partitionId, state));
        });
    return Futures.allOf(
            partitions.values().stream().map(ProxySession::connect).collect(Collectors.toList()))
        .thenApply(v -> this);
  }

  @Override
  public CompletableFuture<Void> close() {
    return Futures.allOf(
            partitions.values().stream().map(ProxySession::close).collect(Collectors.toList()))
        .thenApply(v -> null);
  }

  @Override
  public CompletableFuture<Void> delete() {
    return Futures.allOf(
            partitions.values().stream().map(ProxySession::delete).collect(Collectors.toList()))
        .thenApply(v -> null);
  }

  /** Handles a partition proxy state change. */
  private synchronized void onStateChange(
      final PartitionId partitionId, final PrimitiveState state) {
    states.put(partitionId, state);
    switch (state) {
      case CONNECTED:
        if (this.state != PrimitiveState.CONNECTED
            && !states.containsValue(PrimitiveState.SUSPENDED)
            && !states.containsValue(PrimitiveState.CLOSED)) {
          this.state = PrimitiveState.CONNECTED;
          stateChangeListeners.forEach(l -> l.accept(PrimitiveState.CONNECTED));
        }
        break;
      case SUSPENDED:
        if (this.state == PrimitiveState.CONNECTED) {
          this.state = PrimitiveState.SUSPENDED;
          stateChangeListeners.forEach(l -> l.accept(PrimitiveState.SUSPENDED));
        }
        break;
      case CLOSED:
        if (this.state != PrimitiveState.CLOSED) {
          this.state = PrimitiveState.CLOSED;
          stateChangeListeners.forEach(l -> l.accept(PrimitiveState.CLOSED));
        }
        break;
      default:
        LoggerFactory.getLogger(AbstractProxyClient.class)
            .warn("No handled state change {}", state);
        break;
    }
  }
}
