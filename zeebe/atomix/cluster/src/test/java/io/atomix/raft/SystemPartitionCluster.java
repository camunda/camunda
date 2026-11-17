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
import io.atomix.raft.cluster.FenceEntry;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.cluster.VoterPolicy;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.partition.RaftElectionConfig;
import io.atomix.raft.partition.RaftPartitionConfig;
import io.atomix.raft.partition.systempartition.SystemPartitionMembership;
import io.atomix.raft.partition.systempartition.SystemPartitionMembership.ElectionPriorities;
import io.atomix.raft.partition.systempartition.SystemPartitionMembership.SelfPromotionCheck;
import io.atomix.raft.partition.systempartition.SystemPartitionRecovery;
import io.atomix.raft.partition.systempartition.SystemPartitionRecovery.MemberReport;
import io.atomix.raft.partition.systempartition.SystemPartitionRecovery.RecoveryDecision;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.raft.protocol.ReconfigureResponse;
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
import java.util.Objects;
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
 * An in-process system partition: one Raft server per broker, with a small in-memory stand-in of
 * the dynamic configuration. It plays the broker side of the design (start table, background join,
 * self-promotion, the leader's demotion, MemberLeave, phase 0 and "force system partition voters")
 * with {@link SystemPartitionMembership} and {@link SystemPartitionRecovery}, so that scenario
 * tests can check the Raft configuration and the quorum at each step.
 *
 * <p>Self-promotion and demotion run only when a test calls {@link #reconcileOnce()}, so that a
 * test can also check that nothing changes. The join of a broker without data and the rejoin of a
 * fenced broker run in the background, as in a broker.
 */
final class SystemPartitionCluster implements AutoCloseable {

  static final Duration ELECTION_TIMEOUT = Duration.ofMillis(500);
  private static final Logger LOG = LoggerFactory.getLogger(SystemPartitionCluster.class);
  private static final Duration JOIN_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration JOIN_RETRY_DELAY = Duration.ofMillis(200);

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
    staticMembers.forEach(member -> dynamic.setState(member, MemberState.ACTIVE));
    final var priorities = SystemPartitionMembership.priorities(configuration(initial, policy));
    final var futures =
        staticMembers.stream()
            .map(
                member ->
                    createServer(member, priorities, 0)
                        .bootstrap(initial, policy)
                        .orTimeout(30, TimeUnit.SECONDS))
            .toArray(CompletableFuture[]::new);
    CompletableFuture.allOf(futures).join();
  }

  /**
   * Row 1 of the start table: the broker starts with the configuration that it stored on disk. Its
   * priorities come from that configuration, without a leader. A fenced broker is notified and
   * rejoins in the background.
   */
  void restart(final MemberId member) {
    restartAll(List.of(member));
  }

  void restartAll(final Collection<MemberId> members) {
    final var futures = new ArrayList<CompletableFuture<?>>();
    for (final var member : members) {
      final var stored = storedConfiguration(member).orElseThrow();
      final var broker = brokers.get(member);
      final var priorities = SystemPartitionMembership.priorities(stored);
      final var server = createServer(member, priorities, broker.incarnation);
      final var ids = stored.allMembers().stream().map(RaftMember::memberId).toList();
      // A fenced broker's start does not complete; its fenced listener takes over
      final var fenced = new CompletableFuture<Void>();
      server.addFencedListener(incarnation -> fenced.complete(null));
      futures.add(
          CompletableFuture.anyOf(server.bootstrap(ids), fenced)
              .orTimeout(30, TimeUnit.SECONDS)
              .exceptionally(
                  error -> {
                    // a fenced broker fails to start; its fenced listener takes over
                    LOG.info("Start of {} failed", member, error);
                    return null;
                  }));
    }
    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
  }

  /**
   * Row 2 of the start table, after a disk loss, a fence notice or {@code MemberJoin}: the broker
   * deletes its data and joins as passive member with a new incarnation and the empty-start guard,
   * in the background, until the leader admits it.
   */
  void joinWithoutData(final MemberId member) {
    joinWithoutData(member, 0);
  }

  /**
   * Like {@link #joinWithoutData(MemberId)}, with a probe term that is higher by {@code
   * extraProbeTerm} than the highest term of the voters, as if a voter had reported the term of an
   * election that it did not win.
   */
  void joinWithoutData(final MemberId member, final long extraProbeTerm) {
    // All join steps of all brokers run on the single background thread, so that a new join
    // never races with the retry of an earlier one.
    try {
      background.submit(() -> startJoin(member, extraProbeTerm)).get(60, TimeUnit.SECONDS);
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private void startJoin(final MemberId member, final long extraProbeTerm) {
    final var broker = brokers.computeIfAbsent(member, Broker::new);
    final var attempt = new CompletableFuture<Void>();
    broker.joined = attempt;
    stopServer(broker);
    deleteData(member);
    broker.incarnation = incarnations.incrementAndGet();
    tryJoin(broker, attempt, extraProbeTerm);
  }

  /** {@code MemberJoin} (5.6.1): the broker becomes JOINING, deletes old data and joins. */
  void memberJoin(final MemberId member) {
    dynamic.setState(member, MemberState.JOINING);
    joinWithoutData(member);
  }

  CompletableFuture<Void> joined(final MemberId member) {
    return brokers.get(member).joined;
  }

  private void tryJoin(
      final Broker broker, final CompletableFuture<Void> attempt, final long extraProbeTerm) {
    if (closed || broker.joined != attempt || attempt.isDone()) {
      return;
    }
    if (!dynamic.mayAskToJoin(broker.id)) {
      retryJoin(broker, attempt, extraProbeTerm);
      return;
    }
    final var probe = probe();
    if (probe.isEmpty()) {
      retryJoin(broker, attempt, extraProbeTerm);
      return;
    }
    final var server = createServer(broker.id, null, broker.incarnation);
    final var contacts =
        probe.get().members().stream().filter(member -> !member.equals(broker.id)).toList();
    final var options =
        JoinOptions.withIncarnation(broker.incarnation)
            .withConfigurationVersion(dynamic.version())
            .withEmptyStartGuard(probe.get().term() + extraProbeTerm);
    server
        .join(Type.PASSIVE, contacts, options)
        .whenCompleteAsync(
            (ok, error) -> {
              if (broker.joined != attempt) {
                if (broker.server != server) {
                  server.shutdown().join();
                }
                return;
              }
              if (error == null) {
                LOG.info("{} joined with incarnation {}", broker.id, broker.incarnation);
                attempt.complete(null);
                return;
              }
              LOG.info("Join of {} failed, trying again", broker.id, error);
              stopServer(broker);
              deleteData(broker.id);
              retryJoin(broker, attempt, extraProbeTerm);
            },
            background);
  }

  private void retryJoin(
      final Broker broker, final CompletableFuture<Void> attempt, final long extraProbeTerm) {
    if (!closed) {
      background.schedule(
          () -> tryJoin(broker, attempt, extraProbeTerm),
          JOIN_RETRY_DELAY.toMillis(),
          TimeUnit.MILLISECONDS);
    }
  }

  /**
   * The probe of a broker without data (5.4.2, 5.4.3): the highest configuration of the running
   * brokers, and the highest term of the voters of that configuration. Empty unless a quorum of
   * these voters answered.
   */
  private Optional<Probe> probe() {
    final var answers = new LinkedHashMap<MemberId, Configuration>();
    final var terms = new LinkedHashMap<MemberId, Long>();
    for (final var broker : brokers.values()) {
      final var server = broker.server;
      if (server == null || !server.isRunning()) {
        continue;
      }
      try {
        final var configuration = server.getConfiguration().get(5, TimeUnit.SECONDS);
        if (configuration != null) {
          answers.put(broker.id, configuration);
          terms.put(broker.id, server.getTerm());
        }
      } catch (final Exception e) {
        LOG.debug("Probe of {} failed", broker.id, e);
      }
    }
    final var highest =
        answers.values().stream()
            .max(
                Comparator.comparingLong(Configuration::index)
                    .thenComparingLong(Configuration::term));
    if (highest.isEmpty() || highest.get().index() == 0) {
      return Optional.empty();
    }
    final var voters =
        highest.get().newMembers().stream()
            .filter(member -> member.getType() == Type.ACTIVE)
            .map(RaftMember::memberId)
            .toList();
    final var answered = voters.stream().filter(terms::containsKey).toList();
    if (answered.size() <= voters.size() / 2) {
      return Optional.empty();
    }
    final long probeTerm = answered.stream().mapToLong(terms::get).max().orElseThrow();
    return Optional.of(
        new Probe(
            highest.get().allMembers().stream().map(RaftMember::memberId).toList(), probeTerm));
  }

  // ---------------------------------------------------------------------------------------------
  // Own membership (5.6.4)
  // ---------------------------------------------------------------------------------------------

  /**
   * Runs the leader's demotion and each broker's self-promotion once, in node index order, and
   * stops at the first accepted change. Each change uses the exact index of the configuration it
   * was calculated from.
   *
   * @return true if a change was accepted
   */
  boolean reconcileOnce() {
    final var leader = leader();
    if (leader.isEmpty()) {
      return false;
    }
    final var leaderServer = leader.get().server;
    final var leaderView = committedView(leaderServer);
    if (leaderView.isPresent()) {
      final var demotion = SystemPartitionMembership.demotion(leaderView.get());
      if (demotion.isPresent()) {
        return accepted(
            leaderServer.reconfigure(
                demotion.get(), leaderView.get().index(), leaderView.get().term()));
      }
    }
    final long leaderCommitIndex = commitIndex(leaderServer);
    final var excluded = dynamic.excluded();
    for (final var broker : runningBrokers()) {
      final var server = broker.server;
      final var view = committedView(server);
      if (view.isEmpty()) {
        continue;
      }
      final var check =
          new SelfPromotionCheck(
              broker.id,
              localIncarnation(server),
              dynamic.isInitialized(),
              excluded.contains(broker.id),
              server.isEmptyStartGuardActive().join(),
              commitIndex(server),
              leaderCommitIndex);
      final var promotion = SystemPartitionMembership.selfPromotion(view.get(), check);
      if (promotion.isPresent()
          && accepted(server.reconfigure(promotion.get(), view.get().index(), view.get().term()))) {
        return true;
      }
    }
    return false;
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
   * {@code MemberLeave} (5.6.2): one change that removes the leaving broker, promotes a replacement
   * of its zone if a voter leaves, and fences it. Then the broker is LEFT and stops.
   */
  SystemPartitionMembership.LeaveChange memberLeave(final MemberId member) {
    dynamic.setState(member, MemberState.LEAVING);
    final var leaving = brokers.get(member).server;
    final var unavailable = new HashSet<MemberId>();
    final var sent = new AtomicReference<SystemPartitionMembership.LeaveChange>();
    final var change =
        Awaitility.await("the system partition removes " + member)
            .atMost(Duration.ofSeconds(60))
            .until(
                () -> {
                  final var current = leaderView().orElse(null);
                  if (current == null) {
                    return null;
                  }
                  final var leave =
                      SystemPartitionMembership.leaveChange(
                          current, member, dynamic.excluded(), m -> !unavailable.contains(m));
                  if (leave.isEmpty()) {
                    return Optional.ofNullable(sent.get());
                  }
                  sent.set(leave.get());
                  final ReconfigureResponse response;
                  try {
                    response =
                        leaving
                            .reconfigure(
                                leave.get().target(),
                                List.of(leave.get().fence()),
                                null,
                                current.index(),
                                current.term())
                            .get(5, TimeUnit.SECONDS);
                  } catch (final Exception e) {
                    // The leader fences the leaving broker when the change commits, and the
                    // broker stops its server before the answer arrives. Step 1 of the next try
                    // finds that the broker is not a member any more.
                    LOG.info("No answer to the removal of {}", member, e);
                    return null;
                  }
                  if (response.status() == Status.OK) {
                    return leave;
                  }
                  if (leave.get().replacement() != null
                      && response.error().message().contains("not caught up")) {
                    unavailable.add(leave.get().replacement());
                  }
                  return null;
                },
                Objects::nonNull);
    dynamic.setState(member, MemberState.LEFT);
    stop(member);
    return change.orElse(null);
  }

  /**
   * Phase 0 of a force-remove plan (5.6.3), run by the lowest surviving broker, then the dynamic
   * configuration marks the removed brokers LEFT (phase 2).
   */
  RecoveryDecision forceRemove(final Set<MemberId> removed) {
    removed.forEach(dynamic::addPendingRemoval);
    final var decision =
        applyRecovery(
            removed,
            reports -> SystemPartitionRecovery.removeMembers(removed, reports),
            "phase 0 removes " + removed);
    removed.forEach(member -> dynamic.setState(member, MemberState.LEFT));
    return decision;
  }

  /** "Force system partition voters" (5.6.5), run by the lowest surviving broker. */
  RecoveryDecision forceVoters(final Set<MemberId> down) {
    return applyRecovery(
        down,
        reports -> SystemPartitionRecovery.forceVoters(down, reports),
        "force voters without " + down);
  }

  private RecoveryDecision applyRecovery(
      final Set<MemberId> failed,
      final Function<List<MemberReport>, RecoveryDecision> decide,
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
                          reports.stream()
                              .map(MemberReport::memberId)
                              .min(MemberId.ID_COMPARATOR)
                              .orElseThrow())
                      .server;
              LOG.info("{}: {}", description, decision);
              return switch (decision) {
                case final RecoveryDecision.AlreadyDone done -> true;
                case final RecoveryDecision.NormalChange normal ->
                    accepted(
                        executor.reconfigure(
                            normal.members(),
                            normal.fenced(),
                            null,
                            normal.index(),
                            normal.term()));
                case final RecoveryDecision.ForcedChange forced ->
                    forceConfigure(executor, forced.members(), forced.fenced());
                case final RecoveryDecision.ResendForced resend ->
                    forceConfigure(executor, resend.members(), resend.fenced());
              };
            });
    return first.get();
  }

  private static boolean forceConfigure(
      final RaftServer executor,
      final Map<MemberId, Type> members,
      final Collection<FenceEntry> fenced) {
    try {
      executor.forceConfigure(members, fenced).get(30, TimeUnit.SECONDS);
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
      final var server = broker.server;
      final var configuration = server.getConfiguration().join();
      if (configuration == null) {
        continue;
      }
      reports.add(
          onRaftThread(
              server,
              () -> {
                final var log = server.getContext().getLog();
                final var last = log.getLastEntry();
                return new MemberReport(
                    broker.id,
                    configuration,
                    last == null ? 0 : last.term(),
                    log.getLastIndex(),
                    server.getContext().getCommitIndex());
              }));
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

  /** The configuration of the broker, with the term that a reconfigure needs. */
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

  static Configuration configuration(final Map<MemberId, Type> members, final VoterPolicy policy) {
    final var raftMembers =
        members.entrySet().stream()
            .map(
                entry ->
                    (RaftMember)
                        new DefaultRaftMember(entry.getKey(), entry.getValue(), Instant.now()))
            .toList();
    return new Configuration(0, 0, 1, raftMembers, List.of(), false, List.of(), policy);
  }

  private static boolean accepted(final CompletableFuture<ReconfigureResponse> future) {
    final var response = future.orTimeout(10, TimeUnit.SECONDS).join();
    if (response.status() != Status.OK) {
      LOG.info("Change rejected: {}", response.error());
    }
    return response.status() == Status.OK;
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

  private static long localIncarnation(final RaftServer server) {
    return onRaftThread(
        server, () -> server.getContext().getCluster().getLocalMember().getIncarnation());
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
        .filter(broker -> broker.server != null && broker.server.isRunning())
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
   * Creates the server of a broker. Its priorities come from the given configuration priorities (or
   * 0 for a broker without data), and a configuration listener recalculates them on each new
   * configuration (5.3).
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
    partitionConfig.setConfigurationChangeTimeout(JOIN_TIMEOUT);
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
    server.setJoinAdmission(dynamic::admit);
    server.addFencedListener(
        fencedIncarnation -> {
          broker.fencedNotices.add(fencedIncarnation);
          // The broker deletes its data and joins again (5.6.6), off the Raft thread
          background.execute(
              () -> {
                if (broker.server == server && !closed) {
                  startJoin(member, 0);
                }
              });
        });
    server.addConfigurationListener(
        (configuration, committed) -> {
          final var updated = SystemPartitionMembership.priorities(configuration);
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

  record Probe(List<MemberId> members, long term) {}

  enum MemberState {
    JOINING,
    ACTIVE,
    LEAVING,
    LEFT
  }

  static final class Broker {
    final MemberId id;
    final List<Long> fencedNotices = new CopyOnWriteArrayList<>();
    volatile RaftServer server;
    volatile long incarnation;
    volatile int nodePriority;
    volatile int targetPriority;
    volatile CompletableFuture<Void> joined = CompletableFuture.completedFuture(null);

    Broker(final MemberId id) {
      this.id = id;
    }
  }

  /**
   * Stand-in of the dynamic configuration: the state of each cluster member and the targets of
   * pending removals. It is shared by all brokers, i.e. every broker has the same, current view.
   */
  static final class DynamicConfiguration {
    private final Map<MemberId, MemberState> states = new ConcurrentHashMap<>();
    private final Set<MemberId> pendingRemovals = ConcurrentHashMap.newKeySet();
    private final Set<MemberId> staleViews = ConcurrentHashMap.newKeySet();
    private final Map<MemberId, AtomicLong> refusals = new ConcurrentHashMap<>();
    private final AtomicLong version = new AtomicLong();
    private volatile boolean initialized = true;

    void setState(final MemberId member, final MemberState state) {
      states.put(member, state);
      if (state == MemberState.LEFT || state == MemberState.JOINING) {
        pendingRemovals.remove(member);
      }
      version.incrementAndGet();
    }

    void addPendingRemoval(final MemberId member) {
      pendingRemovals.add(member);
      version.incrementAndGet();
    }

    void setInitialized(final boolean initialized) {
      this.initialized = initialized;
    }

    boolean isInitialized() {
      return initialized;
    }

    long version() {
      return version.get();
    }

    MemberState state(final MemberId member) {
      return states.get(member);
    }

    /** 5.2: brokers that are not JOINING or ACTIVE, and targets of pending removals. */
    Set<MemberId> excluded() {
      final var excluded = new HashSet<>(pendingRemovals);
      states.forEach(
          (member, state) -> {
            if (state != MemberState.JOINING && state != MemberState.ACTIVE) {
              excluded.add(member);
            }
          });
      return excluded;
    }

    /**
     * The local view of the broker still shows it as a member, e.g. because it was down while it
     * was removed (4.2): it asks to join, and only the leader's admission refuses it.
     */
    void setStaleView(final MemberId member, final boolean stale) {
      if (stale) {
        staleViews.add(member);
      } else {
        staleViews.remove(member);
      }
    }

    /** 5.4.4, steps 1 and 2 of the broker. */
    boolean mayAskToJoin(final MemberId member) {
      return staleViews.contains(member)
          || (initialized && !excluded().contains(member) && states.containsKey(member));
    }

    /** The number of joins of the member that the leader's admission refused. */
    long refusals(final MemberId member) {
      return refusals.computeIfAbsent(member, m -> new AtomicLong()).get();
    }

    /** The leader's admission hook (5.4.4). */
    JoinAdmission.Decision admit(final MemberId member, final long joinerVersion) {
      if (!initialized || joinerVersion > version.get()) {
        return JoinAdmission.Decision.NOT_READY;
      }
      final var state = states.get(member);
      if ((state != MemberState.JOINING && state != MemberState.ACTIVE)
          || pendingRemovals.contains(member)) {
        refusals.computeIfAbsent(member, m -> new AtomicLong()).incrementAndGet();
        return JoinAdmission.Decision.NOT_A_MEMBER;
      }
      return JoinAdmission.Decision.ADMIT;
    }
  }
}
