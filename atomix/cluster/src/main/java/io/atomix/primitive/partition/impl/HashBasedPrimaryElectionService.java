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
package io.atomix.primitive.partition.impl;

import com.google.common.collect.Maps;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.primitive.partition.ManagedPrimaryElectionService;
import io.atomix.primitive.partition.PartitionGroupMembershipService;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PrimaryElection;
import io.atomix.primitive.partition.PrimaryElectionEvent;
import io.atomix.primitive.partition.PrimaryElectionEventListener;
import io.atomix.primitive.partition.PrimaryElectionService;
import io.atomix.utils.concurrent.Threads;
import io.atomix.utils.event.AbstractListenerManager;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Hash-based primary election service. */
public class HashBasedPrimaryElectionService
    extends AbstractListenerManager<PrimaryElectionEvent, PrimaryElectionEventListener>
    implements ManagedPrimaryElectionService {

  private final Logger log = LoggerFactory.getLogger(getClass());
  private final ClusterMembershipService clusterMembershipService;
  private final PartitionGroupMembershipService groupMembershipService;
  private final ClusterCommunicationService messagingService;
  private final Map<PartitionId, HashBasedPrimaryElection> elections = Maps.newConcurrentMap();
  private final PrimaryElectionEventListener primaryElectionListener = this::post;
  private final ScheduledExecutorService executor;
  private final AtomicBoolean started = new AtomicBoolean();

  public HashBasedPrimaryElectionService(
      final ClusterMembershipService clusterMembershipService,
      final PartitionGroupMembershipService groupMembershipService,
      final ClusterCommunicationService messagingService) {
    this.clusterMembershipService = clusterMembershipService;
    this.groupMembershipService = groupMembershipService;
    this.messagingService = messagingService;
    this.executor =
        Executors.newSingleThreadScheduledExecutor(
            Threads.namedThreads("primary-election-%d", log));
  }

  @Override
  public PrimaryElection getElectionFor(final PartitionId partitionId) {
    return elections.computeIfAbsent(
        partitionId,
        id -> {
          final HashBasedPrimaryElection election =
              new HashBasedPrimaryElection(
                  partitionId,
                  clusterMembershipService,
                  groupMembershipService,
                  messagingService,
                  executor);
          election.addListener(primaryElectionListener);
          return election;
        });
  }

  @Override
  public CompletableFuture<PrimaryElectionService> start() {
    started.set(true);
    log.info("Started");
    return CompletableFuture.completedFuture(this);
  }

  @Override
  public boolean isRunning() {
    return started.get();
  }

  @Override
  public CompletableFuture<Void> stop() {
    if (started.compareAndSet(true, false)) {
      elections.values().forEach(election -> election.close());
    }
    executor.shutdownNow();
    return CompletableFuture.completedFuture(null);
  }
}
