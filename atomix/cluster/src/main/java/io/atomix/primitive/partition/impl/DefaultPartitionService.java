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
import io.atomix.primitive.partition.ManagedPartitionGroupMembershipService;
import io.atomix.primitive.partition.ManagedPartitionService;
import io.atomix.primitive.partition.PartitionGroup;
import io.atomix.primitive.partition.PartitionGroupMembershipEvent;
import io.atomix.primitive.partition.PartitionGroupMembershipEventListener;
import io.atomix.primitive.partition.PartitionGroupTypeRegistry;
import io.atomix.primitive.partition.PartitionManagementService;
import io.atomix.primitive.partition.PartitionService;
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
  private final ManagedPartitionGroupMembershipService groupMembershipService;
  private volatile PartitionManagementService partitionManagementService;
  private final Map<String, ManagedPartitionGroup> groups = Maps.newConcurrentMap();
  private final PartitionGroupMembershipEventListener groupMembershipEventListener =
      this::handleMembershipChange;
  private final AtomicBoolean started = new AtomicBoolean();

  @SuppressWarnings("unchecked")
  public DefaultPartitionService(
      final ClusterMembershipService membershipService,
      final ClusterCommunicationService messagingService,
      final Collection<ManagedPartitionGroup> groups,
      final PartitionGroupTypeRegistry groupTypeRegistry) {
    this.clusterMembershipService = membershipService;
    this.communicationService = messagingService;
    this.groupMembershipService =
        new DefaultPartitionGroupMembershipService(
            membershipService, messagingService, groups, groupTypeRegistry);
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

  @SuppressWarnings("unchecked")
  private void handleMembershipChange(final PartitionGroupMembershipEvent event) {
    if (partitionManagementService == null) {
      return;
    }

    if (!event.membership().system()) {
      synchronized (groups) {
        ManagedPartitionGroup group = groups.get(event.membership().group());
        if (group == null) {
          group =
              ((PartitionGroup.Type) event.membership().config().getType())
                  .newPartitionGroup(event.membership().config());
          groups.put(event.membership().group(), group);
          if (event
              .membership()
              .members()
              .contains(clusterMembershipService.getLocalMember().id())) {
            group.join(partitionManagementService);
          } else {
            group.connect(partitionManagementService);
          }
        }
      }
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public CompletableFuture<PartitionService> start() {
    groupMembershipService.addListener(groupMembershipEventListener);
    return groupMembershipService
        .start()
        .thenApply(
            v2 ->
                new DefaultPartitionManagementService(
                    clusterMembershipService, communicationService))
        .thenCompose(
            managementService -> {
              this.partitionManagementService = managementService;
              final List<CompletableFuture> futures =
                  groupMembershipService.getMemberships().stream()
                      .map(
                          membership -> {
                            ManagedPartitionGroup group;
                            synchronized (groups) {
                              group = groups.get(membership.group());
                              if (group == null) {
                                group =
                                    membership
                                        .config()
                                        .getType()
                                        .newPartitionGroup(membership.config());
                                groups.put(group.name(), group);
                              }
                            }
                            if (membership
                                .members()
                                .contains(clusterMembershipService.getLocalMember().id())) {
                              return group.join(partitionManagementService);
                            } else {
                              return group.connect(partitionManagementService);
                            }
                          })
                      .collect(Collectors.toList());
              return CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]))
                  .thenApply(
                      v -> {
                        LOGGER.info("Started");
                        started.set(true);
                        return this;
                      });
            });
  }

  @Override
  public boolean isRunning() {
    return started.get();
  }

  @Override
  @SuppressWarnings("unchecked")
  public CompletableFuture<Void> stop() {
    groupMembershipService.removeListener(groupMembershipEventListener);
    final Stream<CompletableFuture<Void>> groupStream =
        groups.values().stream().map(ManagedPartitionGroup::close);
    final List<CompletableFuture<Void>> futures = groupStream.collect(Collectors.toList());

    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]))
        .exceptionally(
            throwable -> {
              LOGGER.error("Failed closing partition group(s)", throwable);
              return null;
            })
        .thenCompose(v -> groupMembershipService.stop())
        .exceptionally(
            throwable -> {
              LOGGER.error("Failed stopping group membership service", throwable);
              return null;
            })
        .thenRun(
            () -> {
              LOGGER.info("Stopped");
              started.set(false);
            });
  }
}
