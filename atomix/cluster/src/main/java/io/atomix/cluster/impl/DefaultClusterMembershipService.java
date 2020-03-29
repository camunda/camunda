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
package io.atomix.cluster.impl;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.slf4j.LoggerFactory.getLogger;

import io.atomix.cluster.BootstrapService;
import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.ManagedClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.discovery.ManagedNodeDiscoveryService;
import io.atomix.cluster.protocol.GroupMembershipEvent;
import io.atomix.cluster.protocol.GroupMembershipEventListener;
import io.atomix.cluster.protocol.GroupMembershipProtocol;
import io.atomix.utils.Version;
import io.atomix.utils.event.AbstractListenerManager;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;

/** Default cluster implementation. */
public class DefaultClusterMembershipService
    extends AbstractListenerManager<ClusterMembershipEvent, ClusterMembershipEventListener>
    implements ManagedClusterMembershipService {

  private static final Logger LOGGER = getLogger(DefaultClusterMembershipService.class);

  private static final String HEARTBEAT_MESSAGE = "atomix-cluster-membership";

  private final ManagedNodeDiscoveryService discoveryService;
  private final BootstrapService bootstrapService;
  private final GroupMembershipProtocol protocol;

  private final AtomicBoolean started = new AtomicBoolean();
  private final StatefulMember localMember;
  private final GroupMembershipEventListener membershipEventListener = this::handleMembershipEvent;

  public DefaultClusterMembershipService(
      final Member localMember,
      final Version version,
      final ManagedNodeDiscoveryService discoveryService,
      final BootstrapService bootstrapService,
      final GroupMembershipProtocol protocol) {
    this.discoveryService = checkNotNull(discoveryService, "discoveryService cannot be null");
    this.bootstrapService = checkNotNull(bootstrapService, "bootstrapService cannot be null");
    this.protocol = checkNotNull(protocol, "protocol cannot be null");
    this.localMember =
        new StatefulMember(
            localMember.id(),
            localMember.address(),
            localMember.zone(),
            localMember.rack(),
            localMember.host(),
            localMember.properties(),
            version);
  }

  @Override
  public Member getLocalMember() {
    return localMember;
  }

  @Override
  public Set<Member> getMembers() {
    return protocol.getMembers();
  }

  @Override
  public Member getMember(final MemberId memberId) {
    return protocol.getMember(memberId);
  }

  /** Handles a group membership event. */
  private void handleMembershipEvent(final GroupMembershipEvent event) {
    post(
        new ClusterMembershipEvent(
            ClusterMembershipEvent.Type.valueOf(event.type().name()), event.member()));
  }

  @Override
  public CompletableFuture<ClusterMembershipService> start() {
    if (started.compareAndSet(false, true)) {
      protocol.addListener(membershipEventListener);
      return discoveryService
          .start()
          .thenCompose(
              v -> {
                localMember.setActive(true);
                localMember.setReachable(true);
                return protocol.join(bootstrapService, discoveryService, localMember);
              })
          .thenApply(
              v -> {
                LOGGER.info("Started");
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
    if (started.compareAndSet(true, false)) {
      return protocol
          .leave(localMember)
          .thenCompose(v -> discoveryService.stop())
          .thenRun(
              () -> {
                localMember.setActive(false);
                localMember.setReachable(false);
                bootstrapService.getMessagingService().unregisterHandler(HEARTBEAT_MESSAGE);
                protocol.removeListener(membershipEventListener);
                LOGGER.info("Stopped");
              });
    }
    return CompletableFuture.completedFuture(null);
  }
}
