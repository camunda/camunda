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

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.primitive.partition.ManagedPartitionGroup;
import io.atomix.primitive.partition.ManagedPartitionService;
import io.atomix.primitive.partition.PartitionManagementService;
import io.atomix.primitive.partition.PartitionService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Default partition service. */
public class DefaultPartitionService implements ManagedPartitionService {
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPartitionService.class);

  private final ClusterMembershipService clusterMembershipService;
  private final ClusterCommunicationService communicationService;
  private volatile PartitionManagementService partitionManagementService;
  private final ManagedPartitionGroup group;
  private final AtomicBoolean started = new AtomicBoolean();

  public DefaultPartitionService(
      final ClusterMembershipService membershipService,
      final ClusterCommunicationService messagingService,
      final ManagedPartitionGroup group) {
    clusterMembershipService = membershipService;
    communicationService = messagingService;
    this.group = group;
  }

  @Override
  public ManagedPartitionGroup getPartitionGroup() {
    return group;
  }

  @Override
  public CompletableFuture<PartitionService> start() {
    if (started.compareAndSet(false, true)) {

      partitionManagementService =
          new DefaultPartitionManagementService(clusterMembershipService, communicationService);

      final var startStepFuture =
          group != null
              ? group.join(partitionManagementService)
              : CompletableFuture.completedFuture(null);

      return startStepFuture.thenApply(
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
  public CompletableFuture<Void> stop() {
    final var stopStepFuture =
        group != null ? group.close() : CompletableFuture.completedFuture(null);

    return stopStepFuture
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
