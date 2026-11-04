/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.ecv;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.ecv.CommitEvaluator.Evaluation;
import io.camunda.zeebe.broker.ecv.CommitEvaluator.MemberData;
import io.camunda.zeebe.broker.ecv.EcvApi.CommitResponse;
import io.camunda.zeebe.broker.ecv.EcvApi.CommitStatus;
import io.camunda.zeebe.broker.ecv.EcvApi.EcvState;
import io.camunda.zeebe.broker.ecv.EcvProtocol.EnabledData;
import io.camunda.zeebe.broker.ecv.EcvProtocol.EnabledQueryResponse;
import io.camunda.zeebe.broker.ecv.EcvProtocol.PartitionProgress;
import io.camunda.zeebe.broker.ecv.EcvProtocol.ProgressResponse;
import io.camunda.zeebe.broker.ecv.EcvProtocol.ReadinessData;
import io.camunda.zeebe.broker.ecv.EcvProtocol.Request;
import io.camunda.zeebe.broker.ecv.EcvProtocol.StateResponse;
import io.camunda.zeebe.broker.ecv.EcvProtocol.SupportedData;
import io.camunda.zeebe.broker.ecv.UpgradeReadinessRegistry.Component;
import io.camunda.zeebe.broker.system.configuration.EcvCfg;
import io.camunda.zeebe.broker.system.configuration.EcvCfg.EcvCommitMode;
import io.camunda.zeebe.broker.system.syspartition.SystemKeyValueReader;
import io.camunda.zeebe.broker.system.syspartition.SystemKeyValueWriter;
import io.camunda.zeebe.broker.system.syspartition.SystemPartition;
import io.camunda.zeebe.broker.system.syspartition.SystemPartitionKeyType;
import io.camunda.zeebe.broker.system.syspartition.SystemPartitionListener;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationUpdateNotifier.ClusterConfigurationUpdateListener;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.TenantAvailability;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import io.camunda.zeebe.util.ecv.Capability;
import io.camunda.zeebe.util.ecv.CapabilitySet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * ECVManagement: the ECV tracking and commit of Distributed Systems (design).
 *
 * <p>On each broker, it gossips the supported capabilities and the readiness of this broker, keeps
 * the local view of the enabled capabilities, and checks before the broker starts the partitions of
 * its physical tenants (design section 6).
 *
 * <p>On the system partition leader only, it writes the initial enabled set (R29), runs the commit
 * checks, writes the commit decision, runs the automatic commit, and answers the state read.
 */
public final class EcvManagement extends Actor
    implements SystemPartitionListener, ClusterMembershipEventListener {
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;
  private static final Duration SELF_CHECK_RETRY = Duration.ofMillis(500);
  private static final String READINESS_COMPONENT = "tenant-partitions";

  private final EcvCfg cfg;
  private final MemberId localMemberId;
  private final ClusterMembershipService membershipService;
  private final ClusterCommunicationService communicationService;
  private final List<MemberId> systemPartitionMembers;
  private final @Nullable SystemPartition systemPartition;
  private final CapabilitySet supported;
  private final boolean existingCluster;
  private final LocalEnabledCapabilitiesView view;
  private final UpgradeReadinessRegistry readinessRegistry;
  private final PartitionCapabilitiesRegistry partitionRegistry;
  private final Component readiness;
  private final String instance = UUID.randomUUID().toString();
  private final CompletableFuture<SelfCheckResult> selfCheck = new CompletableFuture<>();
  private final Runnable readinessListener = () -> actor.run(this::publishReadiness);
  private final Consumer<CapabilitySet> viewListener = ignored -> actor.run(this::publishEnabled);
  private final SystemKeyValueReader.ValueListener enabledWatch;

  private volatile @Nullable CurrentClusterConfiguration clusterConfiguration;
  private final ClusterConfigurationUpdateListener configurationListener =
      configuration -> clusterConfiguration = configuration;
  // only set while this broker is the system partition leader
  private @Nullable SystemKeyValueWriter writer;
  private boolean initialized;
  private @Nullable ScheduledTimer automaticCommitTimer;
  // commit requests are handled one after the other (R17)
  private CompletableFuture<?> lastCommit = CompletableFuture.completedFuture(null);
  private boolean automaticCommitInFlight;
  private boolean selfCheckStarted;

  public EcvManagement(
      final EcvCfg cfg,
      final MemberId localMemberId,
      final ClusterMembershipService membershipService,
      final ClusterCommunicationService communicationService,
      final Set<MemberId> systemPartitionMembers,
      final @Nullable SystemPartition systemPartition,
      final CapabilitySet supported,
      final boolean existingCluster,
      final LocalEnabledCapabilitiesView view,
      final UpgradeReadinessRegistry readinessRegistry) {
    super("EcvManagement");
    this.cfg = cfg;
    this.localMemberId = localMemberId;
    this.membershipService = membershipService;
    this.communicationService = communicationService;
    this.systemPartitionMembers = systemPartitionMembers.stream().sorted().toList();
    this.systemPartition = systemPartition;
    this.supported = supported;
    this.existingCluster = existingCluster;
    this.view = view;
    enabledWatch = (key, value) -> view.merge(decodeValue(value));
    this.readinessRegistry = readinessRegistry;
    partitionRegistry = new PartitionCapabilitiesRegistry(supported, view);
    readiness = readinessRegistry.register(READINESS_COMPONENT);
    readiness.notReady("the check before the broker takes part did not pass yet");
  }

  @Override
  protected void onActorStarted() {
    publishSupported();
    publishReadiness();
    publishEnabled();
    readinessRegistry.addListener(readinessListener);
    view.addListener(viewListener);
    membershipService.addListener(this);
    membershipService.getMembers().forEach(this::mergeGossipedEnabled);

    communicationService.replyTo(
        EcvProtocol.ENABLED_QUERY_SUBJECT,
        bytes -> EcvProtocol.decode(bytes, Request.class),
        request -> onActor(this::answerEnabledQuery),
        EcvProtocol::encode);
    communicationService.replyTo(
        EcvProtocol.COMMIT_SUBJECT,
        bytes -> EcvProtocol.decode(bytes, Request.class),
        request -> commitAsLeader(),
        EcvProtocol::encode);
    communicationService.replyTo(
        EcvProtocol.STATE_SUBJECT,
        bytes -> EcvProtocol.decode(bytes, Request.class),
        request -> stateAsLeader(),
        EcvProtocol::encode);
    communicationService.replyTo(
        EcvProtocol.PROGRESS_SUBJECT,
        bytes -> EcvProtocol.decode(bytes, Request.class),
        request -> CompletableFuture.completedFuture(localProgress()),
        EcvProtocol::encode);

    if (systemPartition != null) {
      final var reader = systemPartition.keyValueReader();
      reader.watch(SystemPartitionKeyType.ENABLED_CAPABILITIES, enabledWatch);
      systemPartition.addListener(this);
      awaitSystemPartitionReplay();
    }
  }

  @Override
  protected void onActorClosing() {
    membershipService.removeListener(this);
    readinessRegistry.removeListener(readinessListener);
    view.removeListener(viewListener);
    List.of(
            EcvProtocol.ENABLED_QUERY_SUBJECT,
            EcvProtocol.COMMIT_SUBJECT,
            EcvProtocol.STATE_SUBJECT,
            EcvProtocol.PROGRESS_SUBJECT)
        .forEach(communicationService::unsubscribe);
    if (systemPartition != null) {
      systemPartition
          .keyValueReader()
          .unwatch(SystemPartitionKeyType.ENABLED_CAPABILITIES, enabledWatch);
      systemPartition.removeListener(this);
    }
    stopAutomaticCommit();
    selfCheck.cancel(false);
  }

  /** The configuration update listener that keeps the cluster configuration for the checks. */
  public ClusterConfigurationUpdateListener configurationListener() {
    return configurationListener;
  }

  public void setClusterConfiguration(final @Nullable CurrentClusterConfiguration configuration) {
    if (configuration != null) {
      clusterConfiguration = configuration;
    }
  }

  public CapabilitySet supported() {
    return supported;
  }

  public LocalEnabledCapabilitiesView view() {
    return view;
  }

  public UpgradeReadinessRegistry readinessRegistry() {
    return readinessRegistry;
  }

  public PartitionCapabilitiesRegistry partitionRegistry() {
    return partitionRegistry;
  }

  // ---------------------------------------------------------------------------------------------
  // Check before the broker takes part (design section 6)
  // ---------------------------------------------------------------------------------------------

  /**
   * Returns the result of the check before this broker starts the partitions of its physical
   * tenants. The check first compares the supported capabilities with the local view on disk, which
   * refuses a broker without the system partition (open point 14). If the local view passes, it
   * asks the system partition leader for the current enabled set, and asks again until a leader
   * answers (R24). The result does not change until the broker restarts.
   */
  public CompletableFuture<SelfCheckResult> selfCheck() {
    actor.run(
        () -> {
          if (selfCheckStarted) {
            return;
          }
          selfCheckStarted = true;
          if (!checkLocally(view.enabled())) {
            queryEnabledSet();
          }
        });
    return selfCheck;
  }

  /** Called once the partitions of the physical tenants started after the check passed. */
  public void onTenantPartitionsStarted() {
    readiness.ready();
  }

  private boolean checkLocally(final CapabilitySet enabled) {
    final var missing = enabled.minus(supported);
    if (missing.isEmpty()) {
      return false;
    }
    refuse(enabled, missing);
    return true;
  }

  private void queryEnabledSet() {
    if (selfCheck.isDone()) {
      return;
    }
    askLeader(
            EcvProtocol.ENABLED_QUERY_SUBJECT,
            EnabledQueryResponse.class,
            EnabledQueryResponse::leader)
        .whenComplete(
            (response, error) ->
                actor.run(
                    () -> {
                      if (response == null || response.enabled() == null) {
                        LOG.debug(
                            "No system partition leader answered the check of the enabled"
                                + " capabilities; asking again in {}",
                            SELF_CHECK_RETRY);
                        actor.schedule(SELF_CHECK_RETRY, this::queryEnabledSet);
                        return;
                      }
                      final var enabled = CapabilitySet.decode(response.enabled());
                      view.merge(enabled);
                      if (!checkLocally(view.enabled())) {
                        LOG.info(
                            "This broker supports the enabled capabilities {}; it takes part in"
                                + " the partitions of its physical tenants",
                            view.enabled());
                        selfCheck.complete(new SelfCheckResult(true, CapabilitySet.EMPTY, null));
                      }
                    }));
  }

  private void refuse(final CapabilitySet enabled, final CapabilitySet missing) {
    final var reason =
        ("This broker is refused: it does not support the enabled capabilities %s. Its supported"
                + " capabilities are %s, the enabled capabilities are %s. Start it with a binary"
                + " that supports all enabled capabilities.")
            .formatted(missing, supported, enabled);
    LOG.error(reason);
    readiness.notReady(reason);
    selfCheck.complete(new SelfCheckResult(false, missing, reason));
  }

  // ---------------------------------------------------------------------------------------------
  // System partition leader
  // ---------------------------------------------------------------------------------------------

  @Override
  public void onBecomingLeader(final long term, final SystemKeyValueWriter writer) {
    actor.run(
        () -> {
          stopAutomaticCommit();
          this.writer = writer;
          initialized = false;
          initializeEnabledSet(writer);
        });
  }

  @Override
  public void onBecomingFollower(final long term) {
    actor.run(this::onLeadershipLost);
  }

  @Override
  public void onBecomingInactive(final long term) {
    actor.run(this::onLeadershipLost);
  }

  private void onLeadershipLost() {
    writer = null;
    initialized = false;
    stopAutomaticCommit();
  }

  /** Writes the initial enabled set if the system partition has none yet (R29). */
  private void initializeEnabledSet(final SystemKeyValueWriter writer) {
    if (this.writer != writer) {
      return;
    }
    final var current = readEnabled();
    if (current != null) {
      view.merge(current);
      onInitialized(writer);
      return;
    }
    // 8.10 has no system partition: a cluster that has data from before is upgraded from 8.10.
    // Any member with such data decides it, so a leader with an empty data directory does not
    // enable capabilities without a commit.
    final var upgraded = existingCluster || anyMemberHasExistingData();
    final var initial = upgraded ? Capability.base() : supported;
    LOG.info(
        "Writing the initial enabled capabilities {} to the system partition ({})",
        initial,
        upgraded ? "base set of a cluster upgraded from 8.10" : "new cluster");
    actor.runOnCompletion(
        writer.put(
            SystemPartitionKeyType.ENABLED_CAPABILITIES,
            new EnabledCapabilitiesValue().setEnabled(initial)),
        (position, error) -> {
          if (error != null) {
            LOG.warn(
                "Failed to write the initial enabled capabilities; trying again in {}",
                SELF_CHECK_RETRY,
                error);
            actor.schedule(SELF_CHECK_RETRY, () -> initializeEnabledSet(writer));
            return;
          }
          view.merge(initial);
          onInitialized(writer);
        });
  }

  private void onInitialized(final SystemKeyValueWriter writer) {
    if (this.writer != writer) {
      return;
    }
    initialized = true;
    if (cfg.getCommitMode() == EcvCommitMode.AUTOMATIC) {
      LOG.info(
          "Running the automatic ECV commit every {} as the system partition leader",
          cfg.getCommitCheckInterval());
      automaticCommitTimer =
          actor.runAtFixedRate(cfg.getCommitCheckInterval(), this::runAutomaticCommit);
    }
  }

  private void stopAutomaticCommit() {
    if (automaticCommitTimer != null) {
      automaticCommitTimer.cancel();
      automaticCommitTimer = null;
    }
  }

  private void runAutomaticCommit() {
    if (automaticCommitInFlight || !isLeader()) {
      return;
    }
    automaticCommitInFlight = true;
    commitAsLeader()
        .whenComplete(
            (response, error) ->
                actor.run(
                    () -> {
                      automaticCommitInFlight = false;
                      if (error != null) {
                        LOG.debug("The automatic ECV commit failed", error);
                      } else if (response.status() == CommitStatus.COMMITTED) {
                        LOG.info("Automatic ECV commit: enabled {}", response.enabled());
                      } else if (response.status() == CommitStatus.REJECTED) {
                        LOG.debug(
                            "Automatic ECV commit is waiting for the checks: {}",
                            response.failedChecks());
                      }
                    }));
  }

  private boolean anyMemberHasExistingData() {
    return membershipService.getMembers().stream()
        .map(
            member ->
                EcvProtocol.readProperty(
                    member, EcvProtocol.SUPPORTED_PROPERTY, SupportedData.class))
        .flatMap(Optional::stream)
        .anyMatch(SupportedData::existingData);
  }

  private boolean isLeader() {
    return writer != null && initialized;
  }

  private EnabledQueryResponse answerEnabledQuery() {
    if (!isLeader()) {
      return new EnabledQueryResponse(false, null);
    }
    return new EnabledQueryResponse(true, currentEnabled().encode());
  }

  /** Commits as the system partition leader; the requests are handled one after the other. */
  private CompletableFuture<CommitResponse> commitAsLeader() {
    final var result = new CompletableFuture<CommitResponse>();
    actor.run(
        () -> {
          final var previous = lastCommit;
          lastCommit = result;
          previous.whenComplete((ignored, error) -> actor.run(() -> commitNow(result)));
        });
    return result;
  }

  private void commitNow(final CompletableFuture<CommitResponse> result) {
    try {
      if (!isLeader()) {
        result.complete(response(CommitStatus.UNAVAILABLE, List.of()));
        return;
      }
      final var currentWriter = writer;
      evaluate()
          .whenComplete(
              (evaluation, error) ->
                  actor.run(
                      () -> {
                        try {
                          if (error != null) {
                            result.completeExceptionally(error);
                          } else {
                            decide(result, currentWriter, evaluation);
                          }
                        } catch (final RuntimeException e) {
                          result.completeExceptionally(e);
                        }
                      }));
    } catch (final RuntimeException e) {
      // a failed commit must not block the commits queued after it
      result.completeExceptionally(e);
    }
  }

  private void decide(
      final CompletableFuture<CommitResponse> result,
      final @Nullable SystemKeyValueWriter currentWriter,
      final Evaluation evaluation) {
    if (!evaluation.commitAvailable()) {
      result.complete(response(CommitStatus.NO_COMMIT_AVAILABLE, List.of()));
      return;
    }
    final var failed = evaluation.failedChecks();
    if (!failed.isEmpty()) {
      result.complete(response(CommitStatus.REJECTED, failed));
      return;
    }
    if (currentWriter == null || writer != currentWriter) {
      result.complete(response(CommitStatus.UNAVAILABLE, List.of()));
      return;
    }
    // the evaluation read the enabled set before the put; union it with everything committed
    // since, so that a decision never removes a capability (R13)
    final var decision = evaluation.decision().union(currentEnabled());
    LOG.info("ECV commit: all checks passed; enabling {} (was {})", decision, evaluation.enabled());
    actor.runOnCompletion(
        currentWriter.put(
            SystemPartitionKeyType.ENABLED_CAPABILITIES,
            new EnabledCapabilitiesValue().setEnabled(decision)),
        (position, putError) -> {
          if (putError != null) {
            result.completeExceptionally(putError);
            return;
          }
          view.merge(decision);
          result.complete(response(CommitStatus.COMMITTED, List.of()));
        });
  }

  private CommitResponse response(
      final CommitStatus status, final List<EcvApi.CheckResult> failedChecks) {
    return new CommitResponse(status, localMemberId.id(), currentEnabled().names(), failedChecks);
  }

  private CompletableFuture<StateResponse> stateAsLeader() {
    return onActor(() -> isLeader())
        .thenCompose(
            leader ->
                leader
                    ? evaluate()
                        .thenApply(evaluation -> new StateResponse(true, evaluation.state()))
                    : CompletableFuture.completedFuture(new StateResponse(false, null)));
  }

  /** Runs the commit checks with the current data; the progress query is sent to every broker. */
  private CompletableFuture<Evaluation> evaluate() {
    final var configuration = clusterConfiguration;
    final Set<MemberId> brokers = configuration == null ? Set.of() : configuration.liveMembers();
    final var tenants = enabledPhysicalTenants(configuration);
    final var queries = new ArrayList<CompletableFuture<ProgressResponse>>();
    for (final var broker : brokers) {
      queries.add(
          broker.equals(localMemberId)
              ? CompletableFuture.completedFuture(localProgress())
              : communicationService
                  .<Request, ProgressResponse>send(
                      EcvProtocol.PROGRESS_SUBJECT,
                      new Request(localMemberId.id()),
                      EcvProtocol::encode,
                      bytes -> EcvProtocol.decode(bytes, ProgressResponse.class),
                      broker,
                      cfg.getRequestTimeout())
                  .exceptionally(error -> new ProgressResponse(Map.of())));
    }
    return CompletableFuture.allOf(queries.toArray(CompletableFuture[]::new))
        .thenCompose(
            ignored ->
                onActor(
                    () -> {
                      final var progress = new HashMap<String, Map<Integer, PartitionProgress>>();
                      queries.forEach(
                          query ->
                              query
                                  .join()
                                  .physicalTenants()
                                  .forEach(
                                      (tenant, partitions) ->
                                          progress
                                              .computeIfAbsent(tenant, t -> new HashMap<>())
                                              .putAll(partitions)));
                      return CommitEvaluator.evaluate(
                          localMemberId.id(),
                          cfg.getCommitMode().name(),
                          currentEnabled(),
                          brokers,
                          memberData(),
                          tenants,
                          progress);
                    }));
  }

  private Map<MemberId, MemberData> memberData() {
    return membershipService.getMembers().stream()
        .filter(Member::isReachable)
        .collect(
            Collectors.toMap(
                Member::id,
                member ->
                    new MemberData(
                        EcvProtocol.readProperty(
                                member, EcvProtocol.SUPPORTED_PROPERTY, SupportedData.class)
                            .orElse(null),
                        EcvProtocol.readProperty(
                                member, EcvProtocol.READINESS_PROPERTY, ReadinessData.class)
                            .orElse(null)),
                (a, b) -> a));
  }

  private static Map<String, Set<Integer>> enabledPhysicalTenants(
      final @Nullable CurrentClusterConfiguration configuration) {
    if (configuration == null) {
      return Map.of();
    }
    final var tenants = new HashMap<String, Set<Integer>>();
    configuration
        .partitionGroups()
        .forEach(
            (tenant, group) -> {
              if (group.availability().state() != TenantAvailability.State.ENABLED) {
                return;
              }
              tenants.put(
                  tenant,
                  group.members().values().stream()
                      .flatMap(member -> member.partitions().keySet().stream())
                      .collect(Collectors.toSet()));
            });
    return tenants;
  }

  private ProgressResponse localProgress() {
    final var progress = new HashMap<String, Map<Integer, PartitionProgress>>();
    partitionRegistry
        .leaderProgress()
        .forEach(
            (tenant, partitions) -> {
              final var converted = new HashMap<Integer, PartitionProgress>();
              partitions.forEach((id, p) -> converted.put(id, PartitionProgress.of(p)));
              progress.put(tenant, converted);
            });
    return new ProgressResponse(progress);
  }

  /**
   * The enabled set as the leader knows it: its replica of the system partition and its local view.
   * The view has each decision that this broker put as soon as the put is committed, before the
   * state machine applied it, and only committed sets, so the union is safe.
   */
  private CapabilitySet currentEnabled() {
    final var enabled = readEnabled();
    return enabled == null ? view.enabled() : enabled.union(view.enabled());
  }

  private @Nullable CapabilitySet readEnabled() {
    if (systemPartition == null) {
      return null;
    }
    return systemPartition
        .keyValueReader()
        .get(SystemPartitionKeyType.ENABLED_CAPABILITIES, new EnabledCapabilitiesValue())
        .map(EnabledCapabilitiesValue::getEnabled)
        .orElse(null);
  }

  private void awaitSystemPartitionReplay() {
    final var partition = systemPartition;
    if (partition == null) {
      return;
    }
    if (partition.state().isEmpty()) {
      // the system partition starts asynchronously after its Raft server bootstrapped
      actor.schedule(Duration.ofMillis(100), this::awaitSystemPartitionReplay);
      return;
    }
    actor.runOnCompletion(
        partition.awaitReplayed(),
        (ok, error) -> {
          final var enabled = readEnabled();
          if (enabled != null) {
            view.merge(enabled);
          }
        });
  }

  // ---------------------------------------------------------------------------------------------
  // Requests from the API (any broker), sent to the system partition leader
  // ---------------------------------------------------------------------------------------------

  /** Commits through the system partition leader (design section 5). */
  public CompletableFuture<CommitResponse> commit() {
    return askLeader(EcvProtocol.COMMIT_SUBJECT, CommitResponse.class, r -> true)
        .thenApply(
            response ->
                response != null
                    ? response
                    : new CommitResponse(
                        CommitStatus.UNAVAILABLE, localMemberId.id(), List.of(), List.of()));
  }

  /** Reads the ECV state from the system partition leader; empty if no leader answered. */
  public CompletableFuture<@Nullable EcvState> state() {
    return askLeader(EcvProtocol.STATE_SUBJECT, StateResponse.class, StateResponse::leader)
        .thenApply(response -> response == null ? null : response.state());
  }

  /**
   * Sends the request to each member of the system partition until the leader answers. Returns null
   * if no leader answered.
   */
  private <R> CompletableFuture<@Nullable R> askLeader(
      final String subject, final Class<R> type, final Function<R, Boolean> isLeaderAnswer) {
    return askLeader(subject, type, isLeaderAnswer, 0);
  }

  private <R> CompletableFuture<@Nullable R> askLeader(
      final String subject,
      final Class<R> type,
      final Function<R, Boolean> isLeaderAnswer,
      final int index) {
    if (index >= systemPartitionMembers.size()) {
      return CompletableFuture.completedFuture(null);
    }
    final var member = systemPartitionMembers.get(index);
    return communicationService
        .<Request, R>send(
            subject,
            new Request(localMemberId.id()),
            EcvProtocol::encode,
            bytes -> EcvProtocol.decode(bytes, type),
            member,
            subject.equals(EcvProtocol.COMMIT_SUBJECT) || subject.equals(EcvProtocol.STATE_SUBJECT)
                ? cfg.getRequestTimeout().multipliedBy(3)
                : cfg.getRequestTimeout())
        .handle((response, error) -> error == null ? response : null)
        .thenCompose(
            response ->
                response != null && isLeaderAnswer.apply(response) && !isNotLeader(response)
                    ? CompletableFuture.completedFuture(response)
                    : askLeader(subject, type, isLeaderAnswer, index + 1));
  }

  private static boolean isNotLeader(final Object response) {
    return response instanceof final CommitResponse commit
        && commit.status() == CommitStatus.UNAVAILABLE;
  }

  // ---------------------------------------------------------------------------------------------
  // Gossip (design sections 1, 2 and 8)
  // ---------------------------------------------------------------------------------------------

  @Override
  public void event(final ClusterMembershipEvent event) {
    mergeGossipedEnabled(event.subject());
  }

  private void mergeGossipedEnabled(final Member member) {
    EcvProtocol.readProperty(member, EcvProtocol.ENABLED_PROPERTY, EnabledData.class)
        .ifPresent(data -> view.merge(CapabilitySet.decode(data.enabled())));
  }

  private void publishSupported() {
    setProperty(
        EcvProtocol.SUPPORTED_PROPERTY,
        new SupportedData(instance, supported.encode(), existingCluster));
  }

  private void publishReadiness() {
    setProperty(
        EcvProtocol.READINESS_PROPERTY, new ReadinessData(instance, readinessRegistry.snapshot()));
  }

  private void publishEnabled() {
    // the current view, not the set of the notification: notifications can arrive out of order
    setProperty(EcvProtocol.ENABLED_PROPERTY, new EnabledData(view.enabled().encode()));
  }

  private void setProperty(final String key, final Object value) {
    membershipService
        .getLocalMember()
        .properties()
        .setProperty(key, EcvProtocol.encodeProperty(value));
  }

  private <T> CompletableFuture<T> onActor(final java.util.concurrent.Callable<T> callable) {
    final var result = new CompletableFuture<T>();
    actor.run(
        () -> {
          try {
            result.complete(callable.call());
          } catch (final Exception e) {
            result.completeExceptionally(e);
          }
        });
    return result;
  }

  private static CapabilitySet decodeValue(final org.agrona.DirectBuffer buffer) {
    final var value = new EnabledCapabilitiesValue();
    value.wrap(buffer, 0, buffer.capacity());
    return value.getEnabled();
  }

  /**
   * @param passed the broker supports all enabled capabilities and starts its tenant partitions
   * @param unsupported the enabled capabilities that the broker does not support
   * @param reason why the broker is refused, or null
   */
  public record SelfCheckResult(
      boolean passed, CapabilitySet unsupported, @Nullable String reason) {}
}
