/*
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
package io.atomix.raft;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.partition.RaftElectionConfig;
import io.atomix.raft.partition.RaftPartitionConfig;
import io.atomix.raft.partition.systempartition.SystemPartitionMembership;
import io.atomix.raft.partition.systempartition.SystemPartitionMembership.BrokerState;
import io.atomix.raft.partition.systempartition.SystemPartitionMembership.ClusterView;
import io.atomix.raft.partition.systempartition.SystemPartitionMembership.ElectionPriorities;
import io.atomix.raft.partition.systempartition.SystemPartitionMembership.LeaderDecision;
import io.atomix.raft.partition.systempartition.SystemPartitionMembership.LeaderInput;
import io.atomix.raft.partition.systempartition.SystemPartitionRecovery;
import io.atomix.raft.partition.systempartition.SystemPartitionRecovery.MemberReport;
import io.atomix.raft.partition.systempartition.SystemPartitionRecovery.RecoveryDecision;
import io.atomix.raft.partition.systempartition.VoterPolicy;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.raft.protocol.TestRaftProtocolFactory;
import io.atomix.raft.roles.LeaderRole;
import io.atomix.raft.snapshot.TestSnapshotStore;
import io.atomix.raft.storage.RaftStorage;
import io.atomix.raft.storage.system.Configuration;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An in-process system partition (design revision 7): one Raft server per broker, with a small
 * in-memory stand-in of the dynamic configuration. It plays the broker side of the design: the
 * start table (bootstrap, restart with the stored configuration, start without data), the fenced
 * listener, the membership component of the leader ({@link SystemPartitionMembership#decide}),
 * {@code MemberLeave}, phase 0 and "force system partition voters" ({@link
 * SystemPartitionRecovery}), so that scenario tests can check the Raft configuration and the quorum
 * at each step.
 *
 * <p>The membership component runs only when a test calls {@link #reconcileOnce()}, so that a test
 * can also check that nothing changes. A fenced broker deletes its data and starts again without
 * data in the background, as a broker does.
 */
final class SystemPartitionCluster implements AutoCloseable {

  static final Duration ELECTION_TIMEOUT = Duration.ofMillis(500);
  private static final Logger LOG = LoggerFactory.getLogger(SystemPartitionCluster.class);

  final DynamicConfiguration dynamic = new DynamicConfiguration();
  private final Path root;
  private final VoterPolicy policy;
  private final TestRaftProtocolFactory protocolFactory = new TestRaftProtocolFactory();
  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final Map<MemberId, Broker> brokers = new ConcurrentHashMap<>();
  private final AtomicLong incarnations = new AtomicLong(100);
  private final AtomicLong positions = new AtomicLong();
  private final Map<Long, Long> committedEntries = new TreeMap<>();
  private final ScheduledExecutorService background = Executors.newSingleThreadScheduledExecutor();
  private volatile boolean closed;

  SystemPartitionCluster(final Path root, final VoterPolicy policy) {
    this.root = root;
    this.policy = policy;
  }

  // ---------------------------------------------------------------------------------------------
  // Start of the system partition (5.4)
  // ---------------------------------------------------------------------------------------------

  /** Row 3 of the start table: all static brokers bootstrap the initial configuration (5.4.1). */
  void bootstrap(final Collection<MemberId> staticMembers) {
    final var initial = SystemPartitionMembership.initialConfiguration(policy, staticMembers);
    staticMembers.forEach(member -> dynamic.setState(member, BrokerState.ACTIVE));
    staticMembers.forEach(member -> brokers.computeIfAbsent(member, Broker::new));
    final var priorities = SystemPartitionMembership.priorities(configuration(initial), policy);
    final var futures =
        staticMembers.stream()
            .map(
                member ->
                    createServer(member, priorities, 0)
                        .bootstrap(initial)
                        .orTimeout(30, TimeUnit.SECONDS))
            .toArray(CompletableFuture[]::new);
    CompletableFuture.allOf(futures).join();
  }

  /**
   * Row 1 of the start table: the broker starts with the configuration that it stored on disk, with
   * the member ids and types of that configuration. Its priorities come from that configuration,
   * without a leader. A fenced broker is notified and starts again without data in the background.
   */
  void restart(final MemberId member) {
    restartAll(List.of(member));
  }

  void restartAll(final Collection<MemberId> members) {
    final var futures = new ArrayList<CompletableFuture<?>>();
    for (final var member : members) {
      final var stored = storedConfiguration(member);
      if (stored.isEmpty()) {
        startWithoutData(member);
        continue;
      }
      final var priorities = SystemPartitionMembership.priorities(stored.get(), policy);
      final var incarnation = stored.get().incarnationOf(member).orElse(0);
      final var server = createServer(member, priorities, incarnation);
      final var ids = stored.get().allMembers().stream().map(RaftMember::memberId).toList();
      // A fenced broker's start does not complete; its fenced listener takes over
      final var fenced = new CompletableFuture<Void>();
      server.addFencedListener(i -> fenced.complete(null));
      futures.add(
          CompletableFuture.anyOf(server.bootstrap(ids), fenced)
              .orTimeout(30, TimeUnit.SECONDS)
              .exceptionally(
                  error -> {
                    LOG.info("Start of {} failed", member, error);
                    return null;
                  }));
    }
    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
  }

  /**
   * Row 2 of the start table and 5.4.3: the broker deletes its data, generates a new incarnation,
   * publishes it and starts without a configuration. It waits until the leader adds it.
   */
  void startWithoutData(final MemberId member) {
    final var broker = brokers.computeIfAbsent(member, Broker::new);
    stopServer(broker);
    deleteData(member);
    final long incarnation = incarnations.incrementAndGet();
    createServer(member, null, incarnation)
        .startWithoutConfiguration(incarnation)
        .orTimeout(30, TimeUnit.SECONDS)
        .join();
    LOG.info("{} started without data with incarnation {}", member, incarnation);
  }

  /**
   * {@code MemberJoin} (5.6.1): the broker is the target of a pending {@code MemberJoin}, starts
   * without data and becomes {@code JOINING}. The applier does not change the system partition.
   */
  void memberJoin(final MemberId member) {
    dynamic.addPendingJoin(member);
    if (!isRunning(member)) {
      startWithoutData(member);
    }
    dynamic.setState(member, BrokerState.JOINING);
  }

  // ---------------------------------------------------------------------------------------------
  // The membership component of the leader (5.5)
  // ---------------------------------------------------------------------------------------------

  /**
   * Runs the rules of the leader once: decides with {@link SystemPartitionMembership#decide}, sends
   * the fenced notices, and sends the change or transfers the leadership.
   *
   * @return true if the leader made a change
   */
  boolean reconcileOnce() {
    final var leader = leader();
    if (leader.isEmpty()) {
      return false;
    }
    final var server = leader.get().server;
    final var decision = decide(leader.get().id, server);
    if (decision.isEmpty()) {
      return false;
    }
    LOG.info("Leader {} decided: {}", leader.get().id, decision.get());
    decision.get().fencedNotices().forEach(member -> server.sendFencedNotice(member).join());
    if (decision.get().transferLeadership()) {
      transferLeadership(leader.get().id);
      return false;
    }
    if (decision.get().change() == null) {
      return false;
    }
    try {
      final var response = server.changeMembers(decision.get().change()).get(10, TimeUnit.SECONDS);
      if (response.status() != Status.OK) {
        LOG.info("Change rejected: {}", response.error());
      }
      return response.status() == Status.OK;
    } catch (final Exception e) {
      LOG.info("Change failed", e);
      return false;
    }
  }

  private Optional<LeaderDecision> decide(final MemberId leaderId, final RaftServer server) {
    try {
      final var configuration = server.getConfiguration().get(5, TimeUnit.SECONDS);
      final var replication = server.getReplicationStatus().get(5, TimeUnit.SECONDS);
      if (configuration == null || replication.isEmpty()) {
        return Optional.empty();
      }
      final boolean committed =
          configuration.force() || replication.get().commitIndex() >= configuration.index();
      return Optional.of(
          SystemPartitionMembership.decide(
              new LeaderInput(
                  leaderId,
                  configuration,
                  committed,
                  dynamic.view(),
                  publishedIncarnations(),
                  replication.get(),
                  policy,
                  System.currentTimeMillis(),
                  ELECTION_TIMEOUT.toMillis(),
                  16 * 1024 * 1024)));
    } catch (final Exception e) {
      LOG.info("Leader {} could not decide", leaderId, e);
      return Optional.empty();
    }
  }

  /** The leader transfers its leadership to another voter before it is removed (R6). */
  private void transferLeadership(final MemberId leader) {
    final var configuration = server(leader).getConfiguration().join();
    configuration.newMembers().stream()
        .filter(member -> member.getType() == Type.ACTIVE)
        .map(RaftMember::memberId)
        .filter(member -> !member.equals(leader))
        .filter(this::isRunning)
        .findFirst()
        .ifPresent(
            target -> {
              try {
                server(target).anoint().get(10, TimeUnit.SECONDS);
              } catch (final Exception e) {
                LOG.info("Transfer of the leadership to {} failed", target, e);
              }
            });
  }

  /** The incarnations that the running brokers publish (5.4.4). */
  Map<MemberId, Long> publishedIncarnations() {
    final var published = new LinkedHashMap<MemberId, Long>();
    runningBrokers().forEach(broker -> published.put(broker.id, broker.incarnation));
    return published;
  }

  /** Calls {@link #reconcileOnce()} until the leader has the expected members and types. */
  void awaitReconciled(final Map<MemberId, Type> expected) {
    Awaitility.await("the configuration becomes " + expected)
        .atMost(Duration.ofSeconds(60))
        .until(
            () -> {
              reconcileOnce();
              return leader()
                  .flatMap(broker -> committedView(broker.server))
                  .map(SystemPartitionCluster::typesOf)
                  .filter(expected::equals)
                  .isPresent();
            });
  }

  // ---------------------------------------------------------------------------------------------
  // Flows (5.6)
  // ---------------------------------------------------------------------------------------------

  /**
   * {@code MemberLeave} (5.6.2): the broker is {@code LEAVING}; the leader removes it and promotes
   * a replacement in one change. The applier waits until the leader reports a committed, non-joint
   * configuration without the broker; it reads it from another member, because the leaving broker
   * is fenced. Then the broker is {@code LEFT} and stops.
   */
  void memberLeave(final MemberId member) {
    dynamic.setState(member, BrokerState.LEAVING);
    Awaitility.await("the system partition removes " + member)
        .atMost(Duration.ofSeconds(60))
        .until(
            () -> {
              reconcileOnce();
              return leaderConfigurationWithout(member, Set.of(member));
            });
    dynamic.setState(member, BrokerState.LEFT);
    stop(member);
  }

  /**
   * Reads the leader's configuration from a running member that is not one of {@code readers to
   * avoid}, as the appliers of 5.6 do.
   */
  private boolean leaderConfigurationWithout(
      final MemberId member, final Set<MemberId> avoidReaders) {
    for (final var broker : runningBrokers()) {
      if (avoidReaders.contains(broker.id)) {
        continue;
      }
      try {
        final var read = broker.server.readLeaderConfiguration().get(5, TimeUnit.SECONDS);
        return read.isCommittedAndNotJoint() && !read.configuration().hasMember(member);
      } catch (final Exception e) {
        LOG.debug("{} could not read the leader's configuration", broker.id, e);
      }
    }
    return false;
  }

  /**
   * Phase 0 of a force-remove plan (5.6.3), run by the lowest surviving broker, then the dynamic
   * configuration marks the removed brokers LEFT (phase 2).
   */
  RecoveryDecision forceRemove(final Set<MemberId> removed) {
    return forceRemove(removed, null, false);
  }

  /** Like {@link #forceRemove(Set)}, run by the given broker. */
  RecoveryDecision forceRemove(
      final Set<MemberId> removed, final MemberId executor, final boolean acceptPossibleLoss) {
    removed.forEach(dynamic::addPendingRemoval);
    final var decision =
        applyRecovery(
            removed,
            reports ->
                SystemPartitionRecovery.removeMembers(removed, reports, policy, acceptPossibleLoss),
            executor,
            "phase 0 removes " + removed);
    if (!(decision instanceof RecoveryDecision.Rejected)) {
      removed.forEach(member -> dynamic.setState(member, BrokerState.LEFT));
    }
    return decision;
  }

  /** "Force system partition voters" (5.6.5), run by the lowest surviving broker. */
  RecoveryDecision forceVoters(final Set<MemberId> down, final boolean acceptPossibleLoss) {
    return applyRecovery(
        down,
        reports -> SystemPartitionRecovery.forceVoters(down, reports, policy, acceptPossibleLoss),
        null,
        "force voters without " + down);
  }

  private RecoveryDecision applyRecovery(
      final Set<MemberId> failed,
      final Function<List<MemberReport>, RecoveryDecision> decide,
      final MemberId executorId,
      final String description) {
    final var first = new AtomicReference<RecoveryDecision>();
    Awaitility.await(description)
        .atMost(Duration.ofSeconds(60))
        .until(
            () -> {
              final var reports = collectReports(failed);
              final var decision = decide.apply(reports);
              first.compareAndSet(null, decision);
              final var executor =
                  brokers.get(
                          executorId != null
                              ? executorId
                              : reports.stream()
                                  .filter(report -> report.configuration() != null)
                                  .map(MemberReport::memberId)
                                  .min(MemberId.ID_COMPARATOR)
                                  .orElseThrow())
                      .server;
              LOG.info("{}: {}", description, decision);
              return switch (decision) {
                case final RecoveryDecision.AlreadyDone done -> true;
                case final RecoveryDecision.Rejected rejected -> true;
                case final RecoveryDecision.NormalChange normal -> {
                  // the leader removes the targets of the pending MemberRemove operations
                  reconcileOnce();
                  yield normal.removed().stream()
                      .allMatch(member -> leaderConfigurationWithout(member, failed));
                }
                case final RecoveryDecision.ForcedChange forced ->
                    forceConfigure(executor, forced.target());
                case final RecoveryDecision.ResendForced resend ->
                    forceConfigure(executor, resend.target());
              };
            });
    return first.get();
  }

  private static boolean forceConfigure(final RaftServer executor, final Configuration target) {
    try {
      executor.forceConfigure(target).get(30, TimeUnit.SECONDS);
      return true;
    } catch (final Exception e) {
      LOG.info("Forced change failed, trying again", e);
      return false;
    }
  }

  /** Step 1 of 5.6.3: the reports of the running brokers that did not fail. */
  List<MemberReport> collectReports(final Set<MemberId> failed) {
    final var reports = new ArrayList<MemberReport>();
    for (final var broker : runningBrokers()) {
      if (failed.contains(broker.id)) {
        continue;
      }
      final var state = broker.server.getReplicaState().join();
      reports.add(
          new MemberReport(
              broker.id,
              state.configuration(),
              state.incarnation(),
              state.lastLogTerm(),
              state.lastLogIndex(),
              state.commitIndex(),
              policy,
              1));
    }
    return reports;
  }

  // ---------------------------------------------------------------------------------------------
  // Brokers
  // ---------------------------------------------------------------------------------------------

  /** Stops the broker; it keeps its data. */
  void stop(final MemberId member) {
    stopServer(brokers.get(member));
  }

  /** The broker loses its disk. */
  void loseDisk(final MemberId member) {
    stop(member);
    deleteData(member);
  }

  RaftServer server(final MemberId member) {
    return brokers.get(member).server;
  }

  boolean isRunning(final MemberId member) {
    final var broker = brokers.get(member);
    // A passive member that started with its stored configuration is not READY until a leader
    // reaches it, but its process runs and publishes its incarnation.
    return broker != null && broker.server != null;
  }

  long incarnation(final MemberId member) {
    return brokers.get(member).incarnation;
  }

  List<Long> fencedNotices(final MemberId member) {
    return brokers.get(member).fencedNotices;
  }

  Optional<Broker> leader() {
    return runningBrokers().stream().filter(broker -> broker.server.isLeader()).findAny();
  }

  MemberId awaitLeader() {
    return Awaitility.await("a leader is elected")
        .atMost(Duration.ofSeconds(30))
        .until(this::leader, Optional::isPresent)
        .orElseThrow()
        .id;
  }

  void awaitNoLeaderFor(final Duration duration) {
    Awaitility.await("no leader is elected")
        .during(duration)
        .atMost(duration.plusSeconds(10))
        .until(() -> leader().isEmpty());
  }

  Optional<Configuration> leaderView() {
    return leader().flatMap(broker -> view(broker.server));
  }

  Configuration awaitLeaderView() {
    return Awaitility.await("the leader has a configuration")
        .atMost(Duration.ofSeconds(30))
        .until(this::leaderView, Optional::isPresent)
        .orElseThrow();
  }

  /** Records every configuration that the broker applies from now on. */
  List<Configuration> recordConfigurations(final MemberId member) {
    final var recorded = new CopyOnWriteArrayList<Configuration>();
    server(member)
        .addConfigurationListener((configuration, committed) -> recorded.add(configuration));
    return recorded;
  }

  void partition(final MemberId member) {
    protocolFactory.partition(member);
  }

  void heal(final MemberId member) {
    protocolFactory.heal(member);
  }

  /** The configuration of the broker. */
  Configuration configuration(final MemberId member) {
    return server(member).getConfiguration().orTimeout(5, TimeUnit.SECONDS).join();
  }

  /** The election priority that the broker uses right now. */
  int nodePriority(final MemberId member) {
    return brokers.get(member).nodePriority;
  }

  int targetPriority(final MemberId member) {
    return brokers.get(member).targetPriority;
  }

  /** Appends an entry on the leader and waits until it is committed. */
  long appendEntry() {
    final var leader = leader().orElseThrow().server;
    final var position = positions.incrementAndGet();
    final var result = new AppendResult();
    final var context = leader.getContext();
    context
        .getThreadContext()
        .execute(
            () ->
                ((LeaderRole) context.getRaftRole())
                    .appendEntry(position, position, ByteBuffer.wrap(new byte[8]), result));
    final long index = result.commit().orTimeout(10, TimeUnit.SECONDS).join();
    synchronized (committedEntries) {
      committedEntries.put(index, position);
    }
    return index;
  }

  /** Every entry appended with {@link #appendEntry()} is in the log of the given brokers. */
  void assertCommittedEntriesReadable(final Collection<MemberId> members) {
    final Map<Long, Long> expected;
    synchronized (committedEntries) {
      expected = Map.copyOf(committedEntries);
    }
    Awaitility.await("committed entries are readable on " + members)
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              for (final var member : members) {
                final var server = server(member);
                final Map<Long, Long> actual =
                    onRaftThread(
                        server,
                        () -> {
                          final var found = new TreeMap<Long, Long>();
                          try (final var reader =
                              server.getContext().getLog().openCommittedReader()) {
                            for (final var index : expected.keySet()) {
                              reader.seek(index);
                              if (reader.hasNext()) {
                                final var entry = reader.next();
                                if (entry.index() == index && entry.isApplicationEntry()) {
                                  found.put(index, entry.getApplicationEntry().lowestPosition());
                                }
                              }
                            }
                          }
                          return found;
                        });
                assertThat(actual).as("entries of %s", member).isEqualTo(expected);
              }
            });
  }

  // ---------------------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------------------

  static Map<MemberId, Type> typesOf(final Configuration configuration) {
    return configuration.newMembers().stream()
        .collect(
            Collectors.toMap(
                RaftMember::memberId, RaftMember::getType, (a, b) -> a, LinkedHashMap::new));
  }

  static Configuration configuration(final Map<MemberId, Type> members) {
    final var raftMembers =
        members.entrySet().stream()
            .map(
                entry ->
                    (RaftMember)
                        new DefaultRaftMember(entry.getKey(), entry.getValue(), Instant.now()))
            .toList();
    return new Configuration(0, 0, 1, raftMembers, List.of(), false, List.of(), 0);
  }

  private Optional<Configuration> view(final RaftServer server) {
    try {
      return Optional.ofNullable(server.getConfiguration().get(5, TimeUnit.SECONDS));
    } catch (final Exception e) {
      return Optional.empty();
    }
  }

  /** The configuration of the server if it is committed and not a joint configuration. */
  private Optional<Configuration> committedView(final RaftServer server) {
    return view(server)
        .filter(configuration -> !configuration.requiresJointConsensus())
        .filter(
            configuration -> configuration.force() || commitIndex(server) >= configuration.index());
  }

  private static long commitIndex(final RaftServer server) {
    return onRaftThread(server, () -> server.getContext().getCommitIndex());
  }

  static <T> T onRaftThread(final RaftServer server, final Supplier<T> supplier) {
    final var result = new CompletableFuture<T>();
    server
        .getContext()
        .getThreadContext()
        .execute(
            () -> {
              try {
                result.complete(supplier.get());
              } catch (final Exception e) {
                result.completeExceptionally(e);
              }
            });
    return result.orTimeout(5, TimeUnit.SECONDS).join();
  }

  private List<Broker> runningBrokers() {
    return brokers.values().stream()
        .filter(broker -> broker.server != null)
        .sorted(Comparator.comparing(broker -> broker.id, MemberId.ID_COMPARATOR))
        .toList();
  }

  private Optional<Configuration> storedConfiguration(final MemberId member) {
    final var storage = storage(member);
    final var metaStore = storage.openMetaStore();
    try {
      return Optional.ofNullable(metaStore.loadConfiguration());
    } finally {
      metaStore.close();
    }
  }

  private RaftStorage storage(final MemberId member) {
    return RaftStorage.builder(meterRegistry)
        .withDirectory(root.resolve(member.id()).toFile())
        .withSnapshotStore(new TestSnapshotStore(new AtomicReference<>()))
        .withMaxSegmentSize(1024 * 10)
        .build();
  }

  /**
   * Creates the server of a broker. Its priorities come from the given priorities (or 0 for a
   * broker without data), and a configuration listener recalculates them on each new configuration
   * (5.3). A fenced broker deletes its data and starts again without data (5.6.6).
   */
  private RaftServer createServer(
      final MemberId member, final ElectionPriorities priorities, final long incarnation) {
    final var broker = brokers.computeIfAbsent(member, Broker::new);
    broker.incarnation = incarnation;
    final var others =
        brokers.keySet().stream().filter(id -> !id.equals(member)).toArray(MemberId[]::new);
    final var partitionConfig =
        new RaftPartitionConfig()
            .setElectionTimeout(ELECTION_TIMEOUT)
            .setHeartbeatInterval(Duration.ofMillis(100));
    partitionConfig.setSystemPartition(true);
    final int target = priorities == null ? policyTotal() : priorities.targetPriority();
    final int nodePriority = priorities == null ? 0 : priorities.priorityOf(member);
    broker.nodePriority = nodePriority;
    broker.targetPriority = target;
    final var server =
        RaftServer.builder(member)
            .withMembershipService(StaticClusterMembershipService.of(member, others))
            .withProtocol(protocolFactory.newServerProtocol(member))
            .withStorage(storage(member))
            .withPartitionConfig(partitionConfig)
            .withElectionConfig(RaftElectionConfig.ofPriorityElection(target, nodePriority))
            .withMeterRegistry(meterRegistry)
            .build();
    server.addFencedListener(
        fencedIncarnation -> {
          broker.fencedNotices.add(fencedIncarnation);
          background.execute(
              () -> {
                if (broker.server == server && !closed) {
                  startWithoutData(member);
                }
              });
        });
    server.addConfigurationListener(
        (configuration, committed) -> {
          final var updated = SystemPartitionMembership.priorities(configuration, policy);
          if (broker.nodePriority != updated.priorityOf(member)) {
            broker.nodePriority = updated.priorityOf(member);
            server.reconfigurePriority(broker.nodePriority);
          }
          if (broker.targetPriority != updated.targetPriority()) {
            broker.targetPriority = updated.targetPriority();
            server.reconfigureTargetPriority(broker.targetPriority);
          }
        });
    broker.server = server;
    return server;
  }

  private int policyTotal() {
    return policy.zones().stream().mapToInt(VoterPolicy.ZoneVoters::voters).sum();
  }

  private void stopServer(final Broker broker) {
    final var server = broker.server;
    if (server != null) {
      broker.server = null;
      server.shutdown().orTimeout(30, TimeUnit.SECONDS).join();
    }
  }

  private void deleteData(final MemberId member) {
    try {
      FileUtil.deleteFolderIfExists(root.resolve(member.id()));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void close() {
    closed = true;
    background.shutdownNow();
    for (final var broker : brokers.values()) {
      try {
        stopServer(broker);
      } catch (final Exception e) {
        LOG.warn("Failed to stop {}", broker.id, e);
      }
    }
    meterRegistry.close();
  }

  static final class Broker {
    final MemberId id;
    final List<Long> fencedNotices = new CopyOnWriteArrayList<>();
    volatile RaftServer server;
    volatile long incarnation;
    volatile int nodePriority;
    volatile int targetPriority;

    Broker(final MemberId id) {
      this.id = id;
    }
  }

  /**
   * Stand-in of the dynamic configuration: the state of each cluster member and the targets of
   * pending operations. The leader reads it as its view (5.5).
   */
  static final class DynamicConfiguration {
    private final Map<MemberId, BrokerState> states = new ConcurrentHashMap<>();
    private final Set<MemberId> pendingJoins = ConcurrentHashMap.newKeySet();
    private final Set<MemberId> pendingRemoves = ConcurrentHashMap.newKeySet();
    private final AtomicLong version = new AtomicLong();
    private volatile boolean initialized = true;

    void setState(final MemberId member, final BrokerState state) {
      states.put(member, state);
      if (state != BrokerState.JOINING) {
        pendingJoins.remove(member);
      }
      if (state == BrokerState.LEFT || state == BrokerState.JOINING) {
        pendingRemoves.remove(member);
      }
      if (state == BrokerState.LEFT) {
        // a completed plan removes LEFT brokers
        states.remove(member);
      }
      version.incrementAndGet();
    }

    void addPendingJoin(final MemberId member) {
      pendingJoins.add(member);
      version.incrementAndGet();
    }

    void addPendingRemoval(final MemberId member) {
      pendingRemoves.add(member);
      version.incrementAndGet();
    }

    void setInitialized(final boolean initialized) {
      this.initialized = initialized;
    }

    ClusterView view() {
      if (!initialized) {
        return ClusterView.notInitialized();
      }
      return new ClusterView(
          true, version.get(), Map.copyOf(states), pendingJoins, Set.of(), pendingRemoves);
    }

    BrokerState state(final MemberId member) {
      return states.get(member);
    }

    Set<MemberId> members() {
      return new HashSet<>(states.keySet());
    }
  }
}
