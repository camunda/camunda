/*
 * Copyright 2016-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.primitive.partition.impl;

import static com.google.common.base.Throwables.throwIfUnchecked;
import static io.atomix.primitive.partition.impl.PrimaryElectorEvents.CHANGE;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import io.atomix.primitive.partition.GroupMember;
import io.atomix.primitive.partition.MemberGroupId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PrimaryElectionEvent;
import io.atomix.primitive.partition.PrimaryTerm;
import io.atomix.primitive.service.AbstractPrimitiveService;
import io.atomix.primitive.service.BackupInput;
import io.atomix.primitive.service.BackupOutput;
import io.atomix.primitive.service.Commit;
import io.atomix.primitive.service.ServiceExecutor;
import io.atomix.primitive.session.Session;
import io.atomix.utils.concurrent.Scheduled;
import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.Serializer;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Primary elector service.
 *
 * <p>This state machine orders candidates and assigns primaries based on the distribution of
 * primaries in the cluster such that primaries are evenly distributed across the cluster.
 */
public class PrimaryElectorService extends AbstractPrimitiveService {

  private static final Duration REBALANCE_DURATION = Duration.ofSeconds(15);

  private static final Serializer SERIALIZER =
      Serializer.using(
          Namespace.builder()
              .register(PrimaryElectorOperations.NAMESPACE)
              .register(PrimaryElectorEvents.NAMESPACE)
              .register(ElectionState.class)
              .register(Registration.class)
              .register(new LinkedHashMap<>().keySet().getClass())
              .build());

  private Map<PartitionId, ElectionState> elections = new HashMap<>();
  private Map<Long, Session> listeners = new LinkedHashMap<>();
  private Scheduled rebalanceTimer;

  public PrimaryElectorService() {
    super(PrimaryElectorType.instance());
  }

  @Override
  public Serializer serializer() {
    return SERIALIZER;
  }

  @Override
  protected void configure(final ServiceExecutor executor) {
    executor.register(PrimaryElectorOperations.ENTER, this::enter);
    executor.register(PrimaryElectorOperations.GET_TERM, this::getTerm);
  }

  @Override
  public void onOpen(final Session session) {
    listeners.put(session.sessionId().id(), session);
  }

  @Override
  public void onExpire(final Session session) {
    onSessionEnd(session);
  }

  @Override
  public void onClose(final Session session) {
    onSessionEnd(session);
  }

  @Override
  public void backup(final BackupOutput writer) {
    writer.writeObject(Sets.newHashSet(listeners.keySet()), SERIALIZER::encode);
    writer.writeObject(elections, SERIALIZER::encode);
    getLogger().debug("Took state machine snapshot");
  }

  @Override
  public void restore(final BackupInput reader) {
    listeners = new LinkedHashMap<>();
    for (final Long sessionId : reader.<Set<Long>>readObject(SERIALIZER::decode)) {
      listeners.put(sessionId, getSession(sessionId));
    }
    elections = reader.readObject(SERIALIZER::decode);
    elections.values().forEach(e -> e.elections = elections);
    getLogger().debug("Reinstated state machine from snapshot");
  }

  private void notifyTermChange(final PartitionId partitionId, final PrimaryTerm term) {
    listeners
        .values()
        .forEach(
            session ->
                session.publish(
                    CHANGE,
                    new PrimaryElectionEvent(
                        PrimaryElectionEvent.Type.CHANGED, partitionId, term)));
  }

  /** Schedules rebalancing of primaries. */
  private void scheduleRebalance() {
    if (rebalanceTimer != null) {
      rebalanceTimer.cancel();
    }
    rebalanceTimer = getScheduler().schedule(REBALANCE_DURATION, this::rebalance);
  }

  /** Periodically rebalances primaries. */
  private void rebalance() {
    boolean rebalanced = false;
    for (final ElectionState election : elections.values()) {
      // Count the total number of primaries for this election's primary.
      final int primaryCount = election.countPrimaries(election.primary);

      // Find the registration with the fewest number of primaries.
      int minCandidateCount = 0;
      for (final Registration candidate : election.registrations) {
        if (minCandidateCount == 0) {
          minCandidateCount = election.countPrimaries(candidate);
        } else {
          minCandidateCount = Math.min(minCandidateCount, election.countPrimaries(candidate));
        }
      }

      // If the primary count for the current primary is more than that of the candidate with the
      // fewest
      // primaries then transfer leadership to the candidate.
      if (minCandidateCount < primaryCount) {
        for (final Registration candidate : election.registrations) {
          if (election.countPrimaries(candidate) < primaryCount) {
            final PrimaryTerm oldTerm = election.term();
            elections.put(election.partitionId, election.transfer(candidate.member()));
            final PrimaryTerm newTerm = term(election.partitionId);
            if (!Objects.equals(oldTerm, newTerm)) {
              notifyTermChange(election.partitionId, newTerm);
              rebalanced = true;
            }
          }
        }
      }
    }

    // If some elections were rebalanced, reschedule another rebalance after an interval to give the
    // cluster a
    // change to recognize the primary change and replicate state first.
    if (rebalanced) {
      scheduleRebalance();
    }
  }

  /**
   * Applies an {@link PrimaryElectorOperations.Enter} commit.
   *
   * @param commit commit entry
   * @return topic leader. If no previous leader existed this is the node that just entered the
   *     race.
   */
  protected PrimaryTerm enter(final Commit<? extends PrimaryElectorOperations.Enter> commit) {
    try {
      final PartitionId partitionId = commit.value().partitionId();
      final PrimaryTerm oldTerm = term(partitionId);
      final Registration registration =
          new Registration(commit.value().member(), commit.session().sessionId().id());
      final PrimaryTerm newTerm =
          elections
              .compute(
                  partitionId,
                  (k, v) -> {
                    if (v == null) {
                      return new ElectionState(partitionId, registration, elections);
                    } else {
                      if (!v.isDuplicate(registration)) {
                        return new ElectionState(v).addRegistration(registration);
                      } else {
                        return v;
                      }
                    }
                  })
              .term();

      if (!Objects.equals(oldTerm, newTerm)) {
        notifyTermChange(partitionId, newTerm);
        scheduleRebalance();
      }
      return newTerm;
    } catch (final Exception e) {
      getLogger().error("State machine operation failed", e);
      throwIfUnchecked(e);
      throw new RuntimeException(e);
    }
  }

  /**
   * Applies an {@link PrimaryElectorOperations.GetTerm} commit.
   *
   * @param commit GetLeadership commit
   * @return leader
   */
  protected PrimaryTerm getTerm(final Commit<? extends PrimaryElectorOperations.GetTerm> commit) {
    final PartitionId partitionId = commit.value().partitionId();
    try {
      return term(partitionId);
    } catch (final Exception e) {
      getLogger().error("State machine operation failed", e);
      throwIfUnchecked(e);
      throw new RuntimeException(e);
    }
  }

  private PrimaryTerm term(final PartitionId partitionId) {
    final ElectionState electionState = elections.get(partitionId);
    return electionState != null ? electionState.term() : null;
  }

  private void onSessionEnd(final Session session) {
    listeners.remove(session.sessionId().id());
    final Set<PartitionId> partitions = elections.keySet();
    partitions.forEach(
        partitionId -> {
          final PrimaryTerm oldTerm = term(partitionId);
          elections.compute(partitionId, (k, v) -> v.cleanup(session));
          final PrimaryTerm newTerm = term(partitionId);
          if (!Objects.equals(oldTerm, newTerm)) {
            notifyTermChange(partitionId, newTerm);
            scheduleRebalance();
          }
        });
  }

  private static class Registration {
    private final GroupMember member;
    private final long sessionId;

    Registration(final GroupMember member, final long sessionId) {
      this.member = member;
      this.sessionId = sessionId;
    }

    public GroupMember member() {
      return member;
    }

    public long sessionId() {
      return sessionId;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(getClass())
          .add("member", member)
          .add("session", sessionId)
          .toString();
    }
  }

  private static class ElectionState {
    private final PartitionId partitionId;
    private final Registration primary;
    private final long term;
    private final long termStartTime;
    private final List<Registration> registrations;
    private transient Map<PartitionId, ElectionState> elections;

    ElectionState(
        final PartitionId partitionId,
        final Registration registration,
        final Map<PartitionId, ElectionState> elections) {
      registrations = Arrays.asList(registration);
      termStartTime = System.currentTimeMillis();
      primary = registration;
      this.partitionId = partitionId;
      this.term = 1;
      this.elections = elections;
    }

    ElectionState(final ElectionState other) {
      partitionId = other.partitionId;
      registrations = Lists.newArrayList(other.registrations);
      primary = other.primary;
      term = other.term;
      termStartTime = other.termStartTime;
      elections = other.elections;
    }

    ElectionState(
        final PartitionId partitionId,
        final List<Registration> registrations,
        final Registration primary,
        final long term,
        final long termStartTime,
        final Map<PartitionId, ElectionState> elections) {
      this.partitionId = partitionId;
      this.registrations = Lists.newArrayList(registrations);
      this.primary = primary;
      this.term = term;
      this.termStartTime = termStartTime;
      this.elections = elections;
    }

    ElectionState cleanup(final Session session) {
      final Optional<Registration> registration =
          registrations.stream().filter(r -> r.sessionId() == session.sessionId().id()).findFirst();
      if (registration.isPresent()) {
        final List<Registration> updatedRegistrations =
            registrations.stream()
                .filter(r -> r.sessionId() != session.sessionId().id())
                .collect(Collectors.toList());
        if (primary.sessionId() == session.sessionId().id()) {
          if (!updatedRegistrations.isEmpty()) {
            return new ElectionState(
                partitionId,
                updatedRegistrations,
                updatedRegistrations.get(0),
                term + 1,
                System.currentTimeMillis(),
                elections);
          } else {
            return new ElectionState(
                partitionId, updatedRegistrations, null, term, termStartTime, elections);
          }
        } else {
          return new ElectionState(
              partitionId, updatedRegistrations, primary, term, termStartTime, elections);
        }
      } else {
        return this;
      }
    }

    boolean isDuplicate(final Registration registration) {
      return registrations.stream().anyMatch(r -> r.sessionId() == registration.sessionId());
    }

    PrimaryTerm term() {
      return new PrimaryTerm(term, primary(), candidates());
    }

    GroupMember primary() {
      if (primary == null) {
        return null;
      } else {
        return primary.member();
      }
    }

    List<GroupMember> candidates() {
      return registrations.stream()
          .map(registration -> registration.member())
          .collect(Collectors.toList());
    }

    ElectionState addRegistration(final Registration registration) {
      if (!registrations.stream().anyMatch(r -> r.sessionId() == registration.sessionId())) {
        final List<Registration> updatedRegistrations = new LinkedList<>(registrations);

        boolean added = false;
        final int registrationCount = countPrimaries(registration);
        for (int i = 0; i < registrations.size(); i++) {
          if (countPrimaries(registrations.get(i)) > registrationCount) {
            updatedRegistrations.add(i, registration);
            added = true;
            break;
          }
        }

        if (!added) {
          updatedRegistrations.add(registration);
        }

        final List<Registration> sortedRegistrations = sortRegistrations(updatedRegistrations);

        final Registration firstRegistration = sortedRegistrations.get(0);
        Registration leader = this.primary;
        long term = this.term;
        long termStartTime = this.termStartTime;
        if (leader == null || !leader.equals(firstRegistration)) {
          leader = firstRegistration;
          term = this.term + 1;
          termStartTime = System.currentTimeMillis();
        }
        return new ElectionState(
            partitionId, sortedRegistrations, leader, term, termStartTime, elections);
      }
      return this;
    }

    List<Registration> sortRegistrations(final List<Registration> registrations) {
      // Count the number of distinct groups in the registrations list.
      final int groupCount =
          (int) registrations.stream().map(r -> r.member().groupId()).distinct().count();

      final Set<MemberGroupId> groups = new HashSet<>();
      final List<Registration> sortedRegistrations = new LinkedList<>();

      // Loop until all registrations have been sorted.
      while (!registrations.isEmpty()) {
        // Clear the list of consumed groups.
        groups.clear();

        // For each registration, check if it can be added to the registrations list.
        final Iterator<Registration> iterator = registrations.iterator();
        while (iterator.hasNext()) {
          final Registration registration = iterator.next();

          // If the registration's group has not been added to the list, add the registration.
          if (groups.add(registration.member().groupId())) {
            sortedRegistrations.add(registration);
            iterator.remove();

            // If an instance of a registration from each group has been added, reset the list of
            // registrations.
            if (groups.size() == groupCount) {
              groups.clear();
            }
          }
        }
      }
      return sortedRegistrations;
    }

    int countPrimaries(final Registration registration) {
      if (registration == null) {
        return 0;
      }

      return (int)
          elections.entrySet().stream()
              .filter(entry -> !entry.getKey().equals(partitionId))
              .filter(entry -> entry.getValue().primary != null)
              .filter(
                  entry -> {
                    // Get the topic leader's identifier and a list of session identifiers.
                    // Then return true if the leader's identifier matches any of the session's
                    // candidates.
                    final GroupMember leaderId = entry.getValue().primary();
                    final List<GroupMember> sessionCandidates =
                        entry.getValue().registrations.stream()
                            .filter(r -> r.sessionId == registration.sessionId)
                            .map(Registration::member)
                            .collect(Collectors.toList());
                    return sessionCandidates.stream()
                        .anyMatch(candidate -> Objects.equals(candidate, leaderId));
                  })
              .count();
    }

    ElectionState transfer(final GroupMember member) {
      final Registration newLeader =
          registrations.stream()
              .filter(r -> Objects.equals(r.member(), member))
              .findFirst()
              .orElse(null);
      if (newLeader != null) {
        return new ElectionState(
            partitionId, registrations, newLeader, term + 1, System.currentTimeMillis(), elections);
      } else {
        return this;
      }
    }
  }
}
