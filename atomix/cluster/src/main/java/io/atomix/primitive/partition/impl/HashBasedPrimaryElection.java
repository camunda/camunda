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
import com.google.common.hash.Hashing;
import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.primitive.partition.GroupMember;
import io.atomix.primitive.partition.MemberGroupId;
import io.atomix.primitive.partition.PartitionGroupMembership;
import io.atomix.primitive.partition.PartitionGroupMembershipEvent;
import io.atomix.primitive.partition.PartitionGroupMembershipEventListener;
import io.atomix.primitive.partition.PartitionGroupMembershipService;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PrimaryElection;
import io.atomix.primitive.partition.PrimaryElectionEvent;
import io.atomix.primitive.partition.PrimaryElectionEventListener;
import io.atomix.primitive.partition.PrimaryTerm;
import io.atomix.utils.event.AbstractListenerManager;
import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.Namespaces;
import io.atomix.utils.serializer.Serializer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Hash-based primary election. */
public class HashBasedPrimaryElection
    extends AbstractListenerManager<PrimaryElectionEvent, PrimaryElectionEventListener>
    implements PrimaryElection {
  private static final Logger LOGGER = LoggerFactory.getLogger(HashBasedPrimaryElection.class);
  private static final long BROADCAST_INTERVAL = 5000;

  private static final Serializer SERIALIZER =
      Serializer.using(
          Namespace.builder().register(Namespaces.BASIC).register(MemberId.class).build());

  private final PartitionId partitionId;
  private final ClusterMembershipService clusterMembershipService;
  private final PartitionGroupMembershipService groupMembershipService;
  private final ClusterCommunicationService communicationService;
  private final Map<MemberId, Integer> counters = Maps.newConcurrentMap();
  private final String subject;
  private final ScheduledFuture<?> broadcastFuture;
  private volatile PrimaryTerm currentTerm;
  private final ClusterMembershipEventListener clusterMembershipEventListener =
      this::handleClusterMembershipEvent;
  private final PartitionGroupMembershipEventListener groupMembershipEventListener =
      new PartitionGroupMembershipEventListener() {
        @Override
        public void event(final PartitionGroupMembershipEvent event) {
          recomputeTerm(event.membership());
        }

        @Override
        public boolean isRelevant(final PartitionGroupMembershipEvent event) {
          return event.membership().group().equals(partitionId.group());
        }
      };

  public HashBasedPrimaryElection(
      final PartitionId partitionId,
      final ClusterMembershipService clusterMembershipService,
      final PartitionGroupMembershipService groupMembershipService,
      final ClusterCommunicationService communicationService,
      final ScheduledExecutorService executor) {
    this.partitionId = partitionId;
    this.clusterMembershipService = clusterMembershipService;
    this.groupMembershipService = groupMembershipService;
    this.communicationService = communicationService;
    this.subject =
        String.format("primary-election-counter-%s-%d", partitionId.group(), partitionId.id());
    recomputeTerm(groupMembershipService.getMembership(partitionId.group()));
    groupMembershipService.addListener(groupMembershipEventListener);
    clusterMembershipService.addListener(clusterMembershipEventListener);
    communicationService.subscribe(subject, SERIALIZER::decode, this::updateCounters, executor);
    broadcastFuture =
        executor.scheduleAtFixedRate(
            this::broadcastCounters, BROADCAST_INTERVAL, BROADCAST_INTERVAL, TimeUnit.MILLISECONDS);
  }

  @Override
  public CompletableFuture<PrimaryTerm> enter(final GroupMember member) {
    return CompletableFuture.completedFuture(currentTerm);
  }

  @Override
  public CompletableFuture<PrimaryTerm> getTerm() {
    return CompletableFuture.completedFuture(currentTerm);
  }

  /** Handles a cluster membership event. */
  private void handleClusterMembershipEvent(final ClusterMembershipEvent event) {
    if (event.type() == ClusterMembershipEvent.Type.MEMBER_ADDED
        || event.type() == ClusterMembershipEvent.Type.MEMBER_REMOVED) {
      recomputeTerm(groupMembershipService.getMembership(partitionId.group()));
    }
  }

  /**
   * Returns the current term.
   *
   * @return the current term
   */
  private long currentTerm() {
    return counters.values().stream().mapToInt(v -> v).sum();
  }

  /**
   * Increments and returns the current term.
   *
   * @return the current term
   */
  private long incrementTerm() {
    counters.compute(
        clusterMembershipService.getLocalMember().id(),
        (id, value) -> value != null ? value + 1 : 1);
    broadcastCounters();
    return currentTerm();
  }

  private void updateCounters(final Map<MemberId, Integer> counters) {
    for (final Map.Entry<MemberId, Integer> entry : counters.entrySet()) {
      this.counters.compute(
          entry.getKey(),
          (key, value) -> {
            if (value == null || value < entry.getValue()) {
              return entry.getValue();
            }
            return value;
          });
    }
    updateTerm(currentTerm());
  }

  private void broadcastCounters() {
    communicationService.broadcast(subject, counters, SERIALIZER::encode);
  }

  private void updateTerm(final long term) {
    if (term > currentTerm.term()) {
      recomputeTerm(groupMembershipService.getMembership(partitionId.group()));
    }
  }

  /** Recomputes the current term. */
  private synchronized void recomputeTerm(final PartitionGroupMembership membership) {
    if (membership == null) {
      return;
    }

    // Create a list of candidates based on the availability of members in the group.
    List<GroupMember> candidates = new ArrayList<>();
    for (final MemberId memberId : membership.members()) {
      final Member member = clusterMembershipService.getMember(memberId);
      if (member != null && member.isReachable()) {
        candidates.add(new GroupMember(memberId, MemberGroupId.from(memberId.id())));
      }
    }

    // Sort the candidates by a hash of their member ID.
    candidates.sort(
        (a, b) -> {
          final int aoffset =
              Hashing.murmur3_32().hashString(a.memberId().id(), StandardCharsets.UTF_8).asInt()
                  % partitionId.id();
          final int boffset =
              Hashing.murmur3_32().hashString(b.memberId().id(), StandardCharsets.UTF_8).asInt()
                  % partitionId.id();
          return aoffset - boffset;
        });

    // Store the current term in a local variable avoid repeated volatile reads.
    final PrimaryTerm currentTerm = this.currentTerm;

    // Compute the primary from the sorted candidates list.
    final GroupMember primary = candidates.isEmpty() ? null : candidates.get(0);

    // Remove the primary from the candidates list.
    candidates =
        candidates.isEmpty() ? Collections.emptyList() : candidates.subList(1, candidates.size());

    // If the primary has changed, increment the term. Otherwise, use the current term from the
    // replicated counter.
    final long term =
        currentTerm != null
                && Objects.equals(currentTerm.primary(), primary)
                && Objects.equals(currentTerm.candidates(), candidates)
            ? currentTerm()
            : incrementTerm();

    // Create the new primary term. If the term has changed update the term and trigger an event.
    final PrimaryTerm newTerm = new PrimaryTerm(term, primary, candidates);
    if (!Objects.equals(currentTerm, newTerm)) {
      this.currentTerm = newTerm;
      LOGGER.debug(
          "{} - Recomputed term for partition {}: {}",
          clusterMembershipService.getLocalMember().id(),
          partitionId,
          newTerm);
      post(new PrimaryElectionEvent(PrimaryElectionEvent.Type.CHANGED, partitionId, newTerm));
      broadcastCounters();
    }
  }

  /** Closes the election. */
  void close() {
    broadcastFuture.cancel(false);
    groupMembershipService.removeListener(groupMembershipEventListener);
    clusterMembershipService.removeListener(clusterMembershipEventListener);
  }
}
