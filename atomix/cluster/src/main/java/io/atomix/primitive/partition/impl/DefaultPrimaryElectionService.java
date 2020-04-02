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
package io.atomix.primitive.partition.impl;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.atomix.primitive.partition.impl.PrimaryElectorEvents.CHANGE;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.atomix.primitive.event.PrimitiveEvent;
import io.atomix.primitive.partition.ManagedPrimaryElection;
import io.atomix.primitive.partition.ManagedPrimaryElectionService;
import io.atomix.primitive.partition.PartitionGroup;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PrimaryElection;
import io.atomix.primitive.partition.PrimaryElectionEvent;
import io.atomix.primitive.partition.PrimaryElectionEventListener;
import io.atomix.primitive.partition.PrimaryElectionService;
import io.atomix.primitive.service.ServiceConfig;
import io.atomix.primitive.session.SessionClient;
import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.Serializer;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Default primary election service.
 *
 * <p>This implementation uses a custom primitive service for primary election. The custom primitive
 * service orders candidates based on the existing distribution of primaries such that primaries are
 * evenly spread across the cluster.
 */
public class DefaultPrimaryElectionService implements ManagedPrimaryElectionService {
  private static final String PRIMITIVE_NAME = "atomix-primary-elector";

  private static final Serializer SERIALIZER =
      Serializer.using(
          Namespace.builder()
              .register(PrimaryElectorOperations.NAMESPACE)
              .register(PrimaryElectorEvents.NAMESPACE)
              .build());

  private final PartitionGroup partitions;
  private final Set<PrimaryElectionEventListener> listeners = Sets.newCopyOnWriteArraySet();
  private final Consumer<PrimitiveEvent> eventListener =
      event -> {
        final PrimaryElectionEvent electionEvent = SERIALIZER.decode(event.value());
        listeners.forEach(l -> l.event(electionEvent));
      };
  private final Map<PartitionId, ManagedPrimaryElection> elections = Maps.newConcurrentMap();
  private final AtomicBoolean started = new AtomicBoolean();
  private SessionClient proxy;

  public DefaultPrimaryElectionService(final PartitionGroup partitionGroup) {
    this.partitions = checkNotNull(partitionGroup);
  }

  @Override
  @SuppressWarnings("unchecked")
  public PrimaryElection getElectionFor(final PartitionId partitionId) {
    return elections.computeIfAbsent(
        partitionId, id -> new DefaultPrimaryElection(partitionId, proxy, this));
  }

  @Override
  public void addListener(final PrimaryElectionEventListener listener) {
    listeners.add(checkNotNull(listener));
  }

  @Override
  public void removeListener(final PrimaryElectionEventListener listener) {
    listeners.remove(checkNotNull(listener));
  }

  @Override
  @SuppressWarnings("unchecked")
  public CompletableFuture<PrimaryElectionService> start() {
    return partitions
        .getPartitions()
        .iterator()
        .next()
        .getClient()
        .sessionBuilder(PRIMITIVE_NAME, PrimaryElectorType.instance(), new ServiceConfig())
        .build()
        .connect()
        .thenAccept(
            proxy -> {
              this.proxy = proxy;
              proxy.addEventListener(CHANGE, eventListener);
              started.set(true);
            })
        .thenApply(v -> this);
  }

  @Override
  public boolean isRunning() {
    return started.get();
  }

  @Override
  public CompletableFuture<Void> stop() {
    final SessionClient proxy = this.proxy;
    if (proxy != null) {
      return proxy
          .close()
          .whenComplete(
              (result, error) -> {
                started.set(false);
              });
    }
    started.set(false);
    return CompletableFuture.completedFuture(null);
  }
}
