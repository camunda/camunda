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

import com.google.common.collect.Maps;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.primitive.partition.ManagedPartitionGroup;
import io.atomix.primitive.partition.ManagedPartitionService;
import io.atomix.primitive.partition.PartitionGroup;
import io.atomix.primitive.partition.PartitionManagementService;
import io.atomix.primitive.partition.PartitionService;
import io.atomix.utils.concurrent.Futures;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Default partition service. */
public class DefaultPartitionService implements ManagedPartitionService {
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPartitionService.class);

  private final ClusterMembershipService clusterMembershipService;
  private final ClusterCommunicationService communicationService;
  private volatile PartitionManagementService partitionManagementService;
  private final Map<String, ManagedPartitionGroup> groups = Maps.newConcurrentMap();
  private final AtomicBoolean started = new AtomicBoolean();

  @SuppressWarnings("unchecked")
  public DefaultPartitionService(
      final ClusterMembershipService membershipService,
      final ClusterCommunicationService messagingService,
      final Collection<ManagedPartitionGroup> groups) {
    clusterMembershipService = membershipService;
    communicationService = messagingService;
    groups.forEach(group -> this.groups.put(group.name(), group));
  }

  @Override
  @SuppressWarnings("unchecked")
  public PartitionGroup getPartitionGroup(final String name) {
    return groups.get(name);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Collection<PartitionGroup> getPartitionGroups() {
    return (Collection) groups.values();
  }

  @Override
  @SuppressWarnings("unchecked")
  public CompletableFuture<PartitionService> start() {
    if (started.compareAndSet(false, true)) {

      partitionManagementService =
          new DefaultPartitionManagementService(clusterMembershipService, communicationService);

      return Futures.allOf(
              groups.values().stream()
                  .map(grp -> grp.join(partitionManagementService))
                  .collect(Collectors.toList()))
          .thenApply(
              v -> {
                LOGGER.debug("Started {}", getClass());
                started.set(true);
                return this;
              });
    }
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public boolean isRunning() {
    return started.get();
  }

  @Override
  @SuppressWarnings("unchecked")
  public CompletableFuture<Void> stop() {
    final Stream<CompletableFuture<Void>> groupStream =
        groups.values().stream().map(ManagedPartitionGroup::close);
    final List<CompletableFuture<Void>> futures = groupStream.collect(Collectors.toList());

    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]))
        .exceptionally(
            throwable -> {
              LOGGER.error("Failed closing partition group(s)", throwable);
              return null;
            })
        .thenRun(
            () -> {
              LOGGER.info("Stopped");
              started.set(false);
            });
  }
}
