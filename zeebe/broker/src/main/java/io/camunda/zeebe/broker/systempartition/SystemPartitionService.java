/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.systempartition;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.primitive.partition.impl.DefaultPartitionManagementService;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.cluster.Incarnations;
import io.atomix.raft.cluster.LeaderConfiguration;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.systempartition.SystemPartitionMembership;
import io.atomix.raft.partition.systempartition.SystemPartitionMembership.BrokerState;
import io.atomix.raft.partition.systempartition.SystemPartitionMembership.ClusterView;
import io.atomix.raft.partition.systempartition.SystemPartitionMembership.LeaderInput;
import io.atomix.raft.partition.systempartition.SystemPartitionRecovery;
import io.atomix.raft.partition.systempartition.SystemPartitionRecovery.MemberReport;
import io.atomix.raft.partition.systempartition.SystemPartitionRecovery.RecoveryDecision;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.raft.storage.serializer.MetaStoreSerializer;
import io.atomix.raft.storage.system.Configuration;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.broker.partitioning.startup.RaftPartitionFactory;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.systempartition.SystemPartitionMessages.Origin;
import io.camunda.zeebe.broker.systempartition.SystemPartitionMessages.ProbeAnswer;
import io.camunda.zeebe.dynamic.config.PersistedCurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.impl.changes.ClusterMembershipChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberRemoveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.SystemPartitionRemoveMembersOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Proof of concept of the system partition in the broker (design {@code
 * docs/system-partition/system-partition-reconfiguration.md}, revision 7, 5.4 to 5.6 and 5.9).
 *
 * <ul>
 *   <li>The start (5.4): the origin marker, the probe and the start table; it runs in the
 *       background and does not block the start of the broker.
 *   <li>The incarnation of the replica, published as a membership property (5.4.4).
 *   <li>The fenced listener: close, delete the data, start without data (5.6.6).
 *   <li>The membership component, which runs only on the leader (5.5).
 *   <li>The waits of {@code MemberLeave} and {@code MemberRemove} (5.6.2) and phase 0 of a
 *       force-remove plan (5.6.3), as the {@link ClusterMembershipChangeExecutor} of the dynamic
 *       configuration.
 * </ul>
 *
 * <p>All state changes run on one thread of the service.
 */
public final class SystemPartitionService
    implements ClusterMembershipChangeExecutor, AutoCloseable {

  /** The membership property with the incarnation of the replica (5.4.4). */
  public static final String INCARNATION_PROPERTY = "systemPartitionIncarnation";

  public static final PartitionId PARTITION_ID = new PartitionId("system", 1);

  private static final Logger LOG = LoggerFactory.getLogger(SystemPartitionService.class);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(2);
  private static final Duration RETRY_DELAY = Duration.ofMillis(500);
  private static final String ORIGIN_FILE = "origin";

  private final BrokerCfg brokerCfg;
  private final SystemPartitionSettings settings;
  private final MemberId localMemberId;
  private final ClusterMembershipService membershipService;
  private final ClusterCommunicationService communicationService;
  private final MeterRegistry meterRegistry;
  private final Supplier<@Nullable ClusterConfigurationService> configurationService;
  private final Path dataDirectory;
  private final Path systemDirectory;
  private final Path partitionDirectory;
  private final ScheduledExecutorService executor;
  // Answers the probe and the reports of other brokers. Not the executor of the service: its start
  // waits for the probe answers of the other brokers, which wait for its answer at the same time.
  private final ExecutorService handlerExecutor;
  private final DefaultPartitionManagementService managementService;
  private final NoSnapshotStore snapshotStore;
  private volatile @Nullable RaftPartition partition;
  private volatile Origin origin = Origin.NEW;
  private volatile boolean closed;
  private volatile boolean stopped;

  public SystemPartitionService(
      final BrokerCfg brokerCfg,
      final ClusterMembershipService membershipService,
      final ClusterCommunicationService communicationService,
      final MeterRegistry meterRegistry,
      final Supplier<@Nullable ClusterConfigurationService> configurationService) {
    this.brokerCfg = brokerCfg;
    settings = SystemPartitionSettings.of(brokerCfg);
    localMemberId = membershipService.getLocalMember().id();
    this.membershipService = membershipService;
    this.communicationService = communicationService;
    this.meterRegistry = meterRegistry;
    this.configurationService = configurationService;
    dataDirectory = Path.of(brokerCfg.getData().getDirectory());
    systemDirectory = dataDirectory.resolve(PARTITION_ID.group());
    partitionDirectory =
        RaftPartitionFactory.getPartitionDirectory(
            PARTITION_ID, brokerCfg.getData().getDirectory());
    executor =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              final var thread = new Thread(runnable, "system-partition-" + localMemberId.id());
              thread.setDaemon(true);
              return thread;
            });
    handlerExecutor =
        Executors.newCachedThreadPool(
            runnable -> {
              final var thread =
                  new Thread(runnable, "system-partition-handler-" + localMemberId.id());
              thread.setDaemon(true);
              return thread;
            });
    managementService =
        new DefaultPartitionManagementService(membershipService, communicationService);
    snapshotStore = new NoSnapshotStore(partitionDirectory);
  }

  // -----------------------------------------------------------------------------------------------
  // Start (5.4)
  // -----------------------------------------------------------------------------------------------

  /**
   * Records the origin of the broker before any other step writes to the data directory, then
   * starts the system partition in the background.
   */
  public void start() {
    origin = determineOrigin();
    LOG.info(
        "Starting the system partition of {} (origin {}, static members {}, voter policy {})",
        localMemberId,
        origin,
        settings.staticMembers(),
        settings.staticPolicy());
    communicationService.replyTo(
        SystemPartitionMessages.PROBE_SUBJECT,
        Function.identity(),
        (byte[] ignored) -> CompletableFuture.supplyAsync(this::probeAnswer, handlerExecutor),
        SystemPartitionMessages::encodeProbe);
    communicationService.replyTo(
        SystemPartitionMessages.REPORT_SUBJECT,
        Function.identity(),
        (byte[] ignored) -> CompletableFuture.supplyAsync(this::localReport, handlerExecutor),
        SystemPartitionMessages::encodeReport);
    communicationService.replyTo(
        SystemPartitionMessages.READ_LEADER_SUBJECT,
        Function.identity(),
        (byte[] ignored) -> readLeaderConfigurationLocally(),
        SystemPartitionMessages::encodeLeaderConfiguration);
    executor.execute(this::tryStart);
    final var interval =
        brokerCfg.getExperimental().getSystemPartition().getMembershipCheckInterval();
    executor.scheduleWithFixedDelay(
        this::checkMembersSafely, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
  }

  /**
   * 5.4.5: the origin is written once, before the dynamic configuration writes its file. A data
   * directory with the stored dynamic configuration or a data partition means that the broker ran
   * before with a version without the system partition.
   */
  private Origin determineOrigin() {
    final var originFile = systemDirectory.resolve(ORIGIN_FILE);
    try {
      if (Files.exists(originFile)) {
        return Origin.valueOf(Files.readString(originFile).trim());
      }
      final boolean existing =
          Files.exists(
                  dataDirectory.resolve(PersistedCurrentClusterConfiguration.TOPOLOGY_FILE_NAME))
              || hasDataPartition();
      final var determined = existing ? Origin.EXISTING : Origin.NEW;
      FileUtil.ensureDirectoryExists(systemDirectory);
      final var temporary = systemDirectory.resolve(ORIGIN_FILE + ".tmp");
      Files.writeString(temporary, determined.name(), StandardCharsets.UTF_8);
      FileUtil.moveDurably(temporary, originFile, StandardCopyOption.ATOMIC_MOVE);
      return determined;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private boolean hasDataPartition() throws IOException {
    if (!Files.isDirectory(dataDirectory)) {
      return false;
    }
    try (final Stream<Path> children = Files.list(dataDirectory)) {
      return children.anyMatch(
          child ->
              !child.getFileName().toString().equals(PARTITION_ID.group())
                  && Files.isDirectory(child.resolve("partitions")));
    }
  }

  /** The start table of 5.4, retried until a row applies. */
  private void tryStart() {
    if (closed || stopped || partition != null) {
      return;
    }
    try {
      final var stored = readStoredConfiguration();
      if (stored != null) {
        startWithStoredConfiguration(stored); // row 1
        return;
      }
      final var answers = probe();
      if (answers.values().stream().anyMatch(a -> a.hasConfiguration() && a.index() > 0)) {
        startWithoutData(); // row 2
        return;
      }
      final var others = new HashSet<>(settings.staticMembers());
      others.remove(localMemberId);
      final boolean allAnswered = answers.keySet().containsAll(others);
      final boolean allNew =
          origin == Origin.NEW && answers.values().stream().allMatch(a -> a.origin() == Origin.NEW);
      if (allAnswered && allNew && settings.staticMembers().contains(localMemberId)) {
        final var hash = settings.initialConfigurationHash();
        final var different =
            answers.entrySet().stream().filter(e -> !e.getValue().hash().equals(hash)).toList();
        if (!different.isEmpty()) {
          LOG.error(
              "Not starting the system partition: brokers {} calculate another initial configuration (hash {} instead of {})",
              different.stream().map(Map.Entry::getKey).toList(),
              different.getFirst().getValue().hash(),
              hash);
          stopped = true;
          return;
        }
        bootstrap(); // row 3
        return;
      }
      LOG.debug(
          "No row of the start table applies yet (answers {}, origin {}), probing again",
          answers,
          origin);
    } catch (final Exception e) {
      LOG.warn("Failed to start the system partition, trying again", e);
    }
    schedule(this::tryStart, Duration.ofSeconds(1));
  }

  /** The probe (5.4.2): asks each broker of the static configuration and of the cluster view. */
  private Map<MemberId, ProbeAnswer> probe() {
    final var targets = new HashSet<>(settings.staticMembers());
    membershipService.getMembers().forEach(member -> targets.add(member.id()));
    targets.remove(localMemberId);
    final var futures = new HashMap<MemberId, CompletableFuture<ProbeAnswer>>();
    for (final var target : targets) {
      futures.put(
          target,
          communicationService.send(
              SystemPartitionMessages.PROBE_SUBJECT,
              new byte[0],
              Function.identity(),
              SystemPartitionMessages::decodeProbe,
              target,
              REQUEST_TIMEOUT));
    }
    final var answers = new LinkedHashMap<MemberId, ProbeAnswer>();
    futures.forEach(
        (target, future) -> {
          try {
            answers.put(
                target, future.get(REQUEST_TIMEOUT.toMillis() + 500, TimeUnit.MILLISECONDS));
          } catch (final Exception e) {
            LOG.trace("No probe answer of {}", target, e);
          }
        });
    return answers;
  }

  private ProbeAnswer probeAnswer() {
    final var configuration = currentConfiguration();
    return new ProbeAnswer(
        configuration != null,
        configuration == null ? 0 : configuration.index(),
        configuration == null ? 0 : configuration.term(),
        settings.initialConfigurationHash(),
        origin);
  }

  /** Row 3: all brokers calculate the same initial configuration (5.4.1). */
  private void bootstrap() {
    final var initial =
        SystemPartitionMembership.initialConfiguration(
            settings.staticPolicy(), settings.staticMembers());
    LOG.info("Bootstrapping the system partition with {}", initial);
    publishIncarnation(0);
    final var raftPartition = createPartition(initial.keySet(), configurationOf(initial));
    raftPartition
        .bootstrap(managementService, snapshotStore, initial)
        .whenComplete((ok, error) -> logStart("bootstrap", error));
  }

  /** Row 1: the member ids and types come from the stored configuration. */
  private void startWithStoredConfiguration(final Configuration stored) {
    final var types = new LinkedHashMap<MemberId, Type>();
    Stream.concat(stored.oldMembers().stream(), stored.newMembers().stream())
        .forEach(member -> types.put(member.memberId(), member.getType()));
    types.putIfAbsent(localMemberId, Type.PASSIVE);
    final long incarnation = stored.incarnationOf(localMemberId).orElse(0);
    LOG.info(
        "Starting the system partition with its stored configuration {} and incarnation {}",
        stored,
        incarnation);
    publishIncarnation(incarnation);
    final var raftPartition = createPartition(types.keySet(), stored);
    raftPartition
        .bootstrap(managementService, snapshotStore, types)
        .whenComplete((ok, error) -> logStart("restart", error));
  }

  /** Row 2 and 5.4.3: a new incarnation, no configuration, wait until the leader adds it. */
  private void startWithoutData() {
    deletePartitionData();
    final long incarnation = Incarnations.newIncarnation();
    LOG.info("Starting the system partition without data with incarnation {}", incarnation);
    publishIncarnation(incarnation);
    final var members = new HashSet<>(settings.staticMembers());
    members.add(localMemberId);
    final var raftPartition = createPartition(members, null);
    raftPartition
        .startWithoutConfiguration(managementService, snapshotStore, incarnation)
        .whenComplete((ok, error) -> logStart("start without data", error));
  }

  private void logStart(final String how, final @Nullable Throwable error) {
    if (error == null) {
      LOG.info("System partition started ({})", how);
    } else {
      LOG.info("System partition did not start ({})", how, error);
    }
  }

  private RaftPartition createPartition(
      final Set<MemberId> members, final @Nullable Configuration configuration) {
    try {
      FileUtil.ensureDirectoryExists(partitionDirectory);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    final var priorities =
        configuration == null
            ? null
            : SystemPartitionMembership.priorities(configuration, settings.staticPolicy());
    final var priorityMap = new HashMap<MemberId, Integer>();
    members.forEach(
        member -> priorityMap.put(member, priorities == null ? 0 : priorities.priorityOf(member)));
    final int target = settings.staticPolicy().zones().stream().mapToInt(z -> z.voters()).sum();
    final var metadata =
        new PartitionMetadata(
            PARTITION_ID, Set.copyOf(members), priorityMap, Math.max(1, target), localMemberId);
    final var raftPartition =
        new RaftPartitionFactory(brokerCfg)
            .createSystemRaftPartition(metadata, partitionDirectory, meterRegistry);
    raftPartition.addFencedListener(
        incarnation -> executor.execute(() -> onFenced(raftPartition, incarnation)));
    raftPartition.addConfigurationListener(
        (newConfiguration, committed) -> updatePriority(raftPartition, newConfiguration));
    partition = raftPartition;
    return raftPartition;
  }

  /** 5.3: the priority comes from the configuration and the voter policy. */
  private void updatePriority(
      final RaftPartition raftPartition, final Configuration configuration) {
    if (!brokerCfg.getCluster().getRaft().isEnablePriorityElection()) {
      return;
    }
    final var priorities =
        SystemPartitionMembership.priorities(configuration, settings.staticPolicy());
    final var server = raftPartition.getServer();
    if (server != null) {
      server.reconfigurePriority(priorities.priorityOf(localMemberId));
    }
  }

  /** 5.6.6: close, delete the data, start without data with a new incarnation. */
  private void onFenced(final RaftPartition fenced, final long incarnation) {
    if (closed || partition != fenced) {
      return;
    }
    LOG.warn(
        "The system partition replica with incarnation {} is fenced; it deletes its data and starts without data",
        incarnation);
    partition = null;
    try {
      fenced.close().get(30, TimeUnit.SECONDS);
    } catch (final Exception e) {
      LOG.warn("Failed to close the fenced system partition", e);
    }
    startWithoutData();
  }

  private void publishIncarnation(final long incarnation) {
    membershipService
        .getLocalMember()
        .properties()
        .setProperty(INCARNATION_PROPERTY, Long.toString(incarnation));
  }

  // -----------------------------------------------------------------------------------------------
  // The membership component of the leader (5.5)
  // -----------------------------------------------------------------------------------------------

  private void checkMembersSafely() {
    try {
      checkMembers();
    } catch (final Exception e) {
      LOG.debug("The check of the system partition members failed", e);
    }
  }

  private void checkMembers() throws Exception {
    final var raftPartition = partition;
    if (closed || raftPartition == null || raftPartition.getRole() != Role.LEADER) {
      return;
    }
    final var server = raftPartition.getServer();
    final var configuration = server.getConfiguration().get(1, TimeUnit.SECONDS);
    final var replication = server.getReplicationStatus().get(1, TimeUnit.SECONDS);
    if (configuration == null || replication.isEmpty()) {
      return;
    }
    final boolean committed =
        configuration.force() || replication.get().commitIndex() >= configuration.index();
    final var input =
        new LeaderInput(
            localMemberId,
            configuration,
            committed,
            clusterView(),
            publishedIncarnations(),
            replication.get(),
            settings.staticPolicy(),
            System.currentTimeMillis(),
            brokerCfg.getCluster().getElectionTimeout().toMillis(),
            brokerCfg.getExperimental().getRaft().getPromotionLagThreshold().toBytes());
    final var decision = SystemPartitionMembership.decide(input);
    for (final var member : decision.fencedNotices()) {
      LOG.info("Sending the fenced notice to {}", member);
      server.sendFencedNotice(member);
    }
    if (decision.transferLeadership()) {
      LOG.info("{}", decision.reason());
      server.reconfigurePriority(0);
      server.stepDown();
      return;
    }
    final var change = decision.change();
    if (change == null) {
      return;
    }
    LOG.info("Changing the members of the system partition: {}", decision.reason());
    final var response = server.changeMembers(change).get(30, TimeUnit.SECONDS);
    if (response.status() != Status.OK) {
      LOG.info("The change was rejected: {}", response.error());
    }
  }

  /** The leader's view of the cluster members: its dynamic configuration (5.5). */
  private ClusterView clusterView() {
    final var service = configurationService.get();
    if (service == null) {
      return ClusterView.notInitialized();
    }
    final CurrentClusterConfiguration current = service.getCurrentClusterConfiguration();
    if (current == null || current.isUninitialized()) {
      return ClusterView.notInitialized();
    }
    final var global = current.globalConfiguration();
    final var states = new HashMap<MemberId, BrokerState>();
    global
        .members()
        .forEach(
            (member, brokerState) -> {
              switch (brokerState.state()) {
                case JOINING -> states.put(member, BrokerState.JOINING);
                case ACTIVE -> states.put(member, BrokerState.ACTIVE);
                case LEAVING -> states.put(member, BrokerState.LEAVING);
                case LEFT -> states.put(member, BrokerState.LEFT);
                default -> {}
              }
            });
    final var pendingOperations = new ArrayList<ClusterConfigurationChangeOperation>();
    global.pendingChanges().ifPresent(plan -> pendingOperations.addAll(plan.pendingOperations()));
    current
        .phasedChangeState()
        .pending()
        .values()
        .forEach(
            plan -> {
              for (int i = plan.currentPhaseIndex(); i < plan.phases().size(); i++) {
                if (plan.phases().get(i) instanceof final GlobalPhase globalPhase) {
                  pendingOperations.addAll(globalPhase.operations());
                }
              }
            });
    final var joins = new HashSet<MemberId>();
    final var leaves = new HashSet<MemberId>();
    final var removes = new HashSet<MemberId>();
    for (final var operation : pendingOperations) {
      switch (operation) {
        case final MemberJoinOperation join -> joins.add(join.memberId());
        case final MemberLeaveOperation leave -> leaves.add(leave.memberId());
        case final MemberRemoveOperation remove -> removes.add(remove.memberToRemove());
        case final SystemPartitionRemoveMembersOperation phaseZero ->
            removes.addAll(phaseZero.removedBrokers());
        default -> {}
      }
    }
    return new ClusterView(true, global.version(), states, joins, leaves, removes);
  }

  /** The incarnations that the reachable brokers publish (5.4.4). */
  private Map<MemberId, Long> publishedIncarnations() {
    final var incarnations = new HashMap<MemberId, Long>();
    for (final Member member : membershipService.getMembers()) {
      final var value = member.properties().getProperty(INCARNATION_PROPERTY);
      if (value != null && (member.isReachable() || member.id().equals(localMemberId))) {
        incarnations.put(member.id(), Long.parseLong(value));
      }
    }
    return incarnations;
  }

  // -----------------------------------------------------------------------------------------------
  // MemberLeave, MemberRemove and phase 0 (5.6.2, 5.6.3)
  // -----------------------------------------------------------------------------------------------

  @Override
  public ActorFuture<Void> addBroker(final MemberId memberId) {
    // MemberJoin does not change the system partition; the leader adds the broker (5.6.1).
    return CompletableActorFuture.completed(null);
  }

  /**
   * {@code MemberLeave} and {@code MemberRemove} wait until the leader reports a committed,
   * non-joint configuration without the broker (5.6.2). The wait has a limit; then the applier
   * fails and the dynamic configuration retries it. It never undoes a change.
   */
  @Override
  public ActorFuture<Void> removeBroker(final MemberId memberId) {
    final var result = new CompletableActorFuture<Void>();
    final var deadline = System.currentTimeMillis() + leaveTimeout().toMillis();
    executor.execute(() -> awaitRemoved(Set.of(memberId), deadline, result));
    return result;
  }

  @Override
  public ActorFuture<Void> removeSystemPartitionMembers(
      final Set<MemberId> removedBrokers, final boolean acceptPossibleLoss) {
    final var result = new CompletableActorFuture<Void>();
    final var deadline = System.currentTimeMillis() + leaveTimeout().toMillis();
    executor.execute(
        () -> runPhaseZero(Set.copyOf(removedBrokers), acceptPossibleLoss, deadline, result));
    return result;
  }

  @Override
  public boolean managesSystemPartition() {
    return true;
  }

  private void awaitRemoved(
      final Set<MemberId> removed, final long deadline, final CompletableActorFuture<Void> result) {
    if (closed) {
      result.completeExceptionally(new IllegalStateException("The system partition is closed"));
      return;
    }
    final var read = readLeaderConfiguration(removed);
    if (read.isPresent()
        && read.get().isCommittedAndNotJoint()
        && removed.stream().noneMatch(member -> read.get().configuration().hasMember(member))) {
      LOG.info("The system partition does not have {} any more", removed);
      result.complete(null);
      return;
    }
    if (System.currentTimeMillis() > deadline) {
      result.completeExceptionally(
          new TimeoutException(
              "The leader of the system partition did not report a committed configuration without %s in time (last read: %s); the operation is retried"
                  .formatted(removed, read.orElse(null))));
      return;
    }
    schedule(() -> awaitRemoved(removed, deadline, result), RETRY_DELAY);
  }

  /** Phase 0 of a force-remove plan (5.6.3). */
  private void runPhaseZero(
      final Set<MemberId> removed,
      final boolean acceptPossibleLoss,
      final long deadline,
      final CompletableActorFuture<Void> result) {
    final List<MemberReport> reports = collectReports(removed);
    if (reports.stream().noneMatch(report -> report.configuration() != null)) {
      if (System.currentTimeMillis() > deadline) {
        result.completeExceptionally(
            new TimeoutException("No member of the system partition answered step 1 of phase 0"));
      } else {
        schedule(() -> runPhaseZero(removed, acceptPossibleLoss, deadline, result), RETRY_DELAY);
      }
      return;
    }
    final var decision =
        SystemPartitionRecovery.removeMembers(
            removed, reports, settings.staticPolicy(), acceptPossibleLoss);
    LOG.info("Phase 0 removes {} from the system partition: {}", removed, decision);
    switch (decision) {
      case final RecoveryDecision.AlreadyDone done -> result.complete(null);
      case final RecoveryDecision.NormalChange normal ->
          // the leader removes the targets of the pending operations of the plan (5.5, rule 1)
          awaitRemoved(removed, deadline, result);
      case final RecoveryDecision.ForcedChange forced -> {
        forceConfigure(forced.target(), result);
        LOG.warn(
            "Recovery record (stub, not written to the system partition state): forced configuration {}, fenced {}, completeness {}, acceptPossibleLoss {}",
            forced.target(),
            forced.fenced(),
            forced.completeness(),
            acceptPossibleLoss);
      }
      case final RecoveryDecision.ResendForced resend -> forceConfigure(resend.target(), result);
      case final RecoveryDecision.Rejected rejected ->
          result.completeExceptionally(
              new IllegalStateException("%s: %s".formatted(rejected.reason(), rejected.message())));
    }
  }

  private void forceConfigure(
      final Configuration target, final CompletableActorFuture<Void> result) {
    final var raftPartition = partition;
    if (raftPartition == null) {
      result.completeExceptionally(
          new IllegalStateException("The system partition of the executor does not run"));
      return;
    }
    try {
      raftPartition.getServer().forceConfigure(target).get(30, TimeUnit.SECONDS);
      result.complete(null);
    } catch (final Exception e) {
      result.completeExceptionally(e);
    }
  }

  /** Step 1 of 5.6.3: the reports of the reachable brokers that are not removed. */
  private List<MemberReport> collectReports(final Set<MemberId> removed) {
    final var reports = new ArrayList<MemberReport>();
    if (!removed.contains(localMemberId)) {
      reports.add(localReport());
    }
    final var futures = new HashMap<MemberId, CompletableFuture<MemberReport>>();
    for (final var member : membershipService.getMembers()) {
      if (member.id().equals(localMemberId) || removed.contains(member.id())) {
        continue;
      }
      futures.put(
          member.id(),
          communicationService.send(
              SystemPartitionMessages.REPORT_SUBJECT,
              new byte[0],
              Function.identity(),
              SystemPartitionMessages::decodeReport,
              member.id(),
              REQUEST_TIMEOUT));
    }
    futures.forEach(
        (member, future) -> {
          try {
            reports.add(future.get(REQUEST_TIMEOUT.toMillis() + 500, TimeUnit.MILLISECONDS));
          } catch (final Exception e) {
            LOG.debug("No report of {}", member, e);
          }
        });
    return reports;
  }

  private MemberReport localReport() {
    final var raftPartition = partition;
    if (raftPartition == null || raftPartition.getServer() == null) {
      return new MemberReport(localMemberId, null, -1, 0, 0, 0, null, 0);
    }
    try {
      final var state = raftPartition.getServer().getReplicaState().get(1, TimeUnit.SECONDS);
      return new MemberReport(
          localMemberId,
          state.configuration(),
          state.incarnation(),
          state.lastLogTerm(),
          state.lastLogIndex(),
          state.commitIndex(),
          null,
          0);
    } catch (final Exception e) {
      return new MemberReport(localMemberId, null, -1, 0, 0, 0, null, 0);
    }
  }

  /**
   * Reads the leader's configuration through any reachable broker that is not one of {@code avoid}
   * (R3): the leaving broker is fenced and cannot read it itself.
   */
  private Optional<LeaderConfiguration> readLeaderConfiguration(final Set<MemberId> avoid) {
    final var candidates = new ArrayList<MemberId>();
    if (!avoid.contains(localMemberId)) {
      candidates.add(localMemberId);
    }
    membershipService.getMembers().stream()
        .map(Member::id)
        .filter(member -> !member.equals(localMemberId) && !avoid.contains(member))
        .sorted(MemberId.ID_COMPARATOR)
        .forEach(candidates::add);
    for (final var candidate : candidates) {
      try {
        final var read =
            candidate.equals(localMemberId)
                ? readLeaderConfigurationLocally()
                : communicationService.send(
                    SystemPartitionMessages.READ_LEADER_SUBJECT,
                    new byte[0],
                    Function.identity(),
                    SystemPartitionMessages::decodeLeaderConfiguration,
                    candidate,
                    REQUEST_TIMEOUT);
        return Optional.of(read.get(REQUEST_TIMEOUT.toMillis() + 500, TimeUnit.MILLISECONDS));
      } catch (final Exception e) {
        LOG.trace("{} could not read the leader's configuration", candidate, e);
      }
    }
    return Optional.empty();
  }

  private CompletableFuture<LeaderConfiguration> readLeaderConfigurationLocally() {
    final var raftPartition = partition;
    if (raftPartition == null || raftPartition.getServer() == null) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("The system partition does not run"));
    }
    return raftPartition.getServer().readLeaderConfiguration();
  }

  // -----------------------------------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------------------------------

  private @Nullable Configuration currentConfiguration() {
    final var raftPartition = partition;
    if (raftPartition != null && raftPartition.getServer() != null) {
      try {
        return raftPartition.getServer().getConfiguration().get(1, TimeUnit.SECONDS);
      } catch (final Exception e) {
        LOG.debug("Failed to read the configuration of the running system partition", e);
      }
    }
    return readStoredConfiguration();
  }

  /** Reads the {@code .conf} file of the partition without opening its storage. */
  private @Nullable Configuration readStoredConfiguration() {
    final var confFile =
        partitionDirectory.resolve(
            RaftPartition.PARTITION_NAME_FORMAT.formatted(
                    PARTITION_ID.group(), PARTITION_ID.number())
                + ".conf");
    try {
      if (!Files.exists(confFile) || Files.size(confFile) == 0) {
        return null;
      }
      return new MetaStoreSerializer()
          .readConfiguration(ByteBuffer.wrap(Files.readAllBytes(confFile)));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void deletePartitionData() {
    try {
      FileUtil.deleteFolderIfExists(partitionDirectory);
      FileUtil.ensureDirectoryExists(partitionDirectory);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Configuration configurationOf(final Map<MemberId, Type> members) {
    final List<RaftMember> raftMembers =
        members.entrySet().stream()
            .map(
                entry ->
                    (RaftMember)
                        new io.atomix.raft.cluster.impl.DefaultRaftMember(
                            entry.getKey(), entry.getValue(), java.time.Instant.now()))
            .toList();
    return new Configuration(0, 0, 1, raftMembers, List.of(), false, List.of(), 0);
  }

  private Duration leaveTimeout() {
    return brokerCfg.getExperimental().getSystemPartition().getLeaveTimeout();
  }

  private void schedule(final Runnable task, final Duration delay) {
    if (!closed) {
      executor.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
    }
  }

  /** The running replica, for tests. */
  public Optional<RaftPartition> partition() {
    return Optional.ofNullable(partition);
  }

  /** The incarnation that this broker publishes, for tests. */
  public Optional<Long> publishedIncarnation() {
    return Optional.ofNullable(
            membershipService.getLocalMember().properties().getProperty(INCARNATION_PROPERTY))
        .map(Long::parseLong);
  }

  @Override
  public void close() {
    closed = true;
    communicationService.unsubscribe(SystemPartitionMessages.PROBE_SUBJECT);
    communicationService.unsubscribe(SystemPartitionMessages.REPORT_SUBJECT);
    communicationService.unsubscribe(SystemPartitionMessages.READ_LEADER_SUBJECT);
    final var future = new CompletableFuture<Void>();
    executor.execute(
        () -> {
          final var raftPartition = partition;
          partition = null;
          if (raftPartition != null) {
            raftPartition.close().whenComplete((ok, error) -> future.complete(null));
          } else {
            future.complete(null);
          }
        });
    try {
      future.get(30, TimeUnit.SECONDS);
    } catch (final Exception e) {
      LOG.warn("Failed to close the system partition", e);
    }
    executor.shutdownNow();
    handlerExecutor.shutdownNow();
  }
}
