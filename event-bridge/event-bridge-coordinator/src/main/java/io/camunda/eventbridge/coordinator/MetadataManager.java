/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.coordinator;

import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.NONE;

import io.camunda.eventbridge.coordinator.placement.PlacementStrategy;
import io.camunda.eventbridge.coordinator.placement.RoundRobinPlacement;
import io.camunda.eventbridge.coordinator.reconfig.ReconfigurationExecutor;
import io.camunda.eventbridge.coordinator.reconfig.ReconfigurationPlanner;
import io.camunda.eventbridge.coordinator.stream.MetadataStream;
import io.camunda.eventbridge.coordinator.stream.TopicAssignmentGossip;
import io.camunda.eventbridge.coordinator.stream.TopicMetadata;
import io.camunda.eventbridge.core.coordinator.CoordinatorRouting;
import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import io.camunda.eventbridge.protocol.request.coordination.CreateTopicRequest;
import io.camunda.eventbridge.protocol.request.coordination.CreateTopicResponse;
import io.camunda.eventbridge.protocol.request.coordination.DeleteTopicRequest;
import io.camunda.eventbridge.protocol.request.coordination.DeleteTopicResponse;
import io.camunda.eventbridge.protocol.request.coordination.ListTopicsRequest;
import io.camunda.eventbridge.protocol.request.coordination.ListTopicsResponse;
import io.camunda.eventbridge.protocol.request.coordination.ReassignTopicRequest;
import io.camunda.eventbridge.protocol.request.coordination.ReassignTopicResponse;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the topic registry on the coordinator: topic admin (create/delete/reassign/list), central
 * placement, the anti-entropy broadcast of the registry to brokers, the {@code CREATING -> ACTIVE}
 * transition, and the change-coordinator that drives committed assignment toward a target one safe
 * Raft step at a time. Leader-only; the broadcast/sink/change-coordinator act only on the registry
 * shard ({@link CoordinatorRouting#TOPIC_REGISTRY_SHARD}).
 *
 * <p>This is the metadata control plane: it runs on the dedicated single-partition {@code
 * event-bridge-metadata} Raft group (its leader), over a {@link MetadataStream}. Consumer-group
 * coordination + offsets stay in the coordinator group ({@code CoordinationManager}).
 */
public class MetadataManager extends Actor {

  private static final Logger LOG = LoggerFactory.getLogger(MetadataManager.class);
  private static final Pattern TOPIC_NAME = Pattern.compile("[a-zA-Z0-9._-]{1,249}");

  // Anti-entropy broadcast of the topic registry to brokers (registry-shard leader only).
  private static final Duration TOPIC_BROADCAST_INTERVAL = Duration.ofSeconds(2);
  // Change-coordinator kickoff/retry/anti-entropy tick.
  private static final Duration RECONFIG_INTERVAL = Duration.ofSeconds(1);

  private final int partitionId;
  private final int clusterSize;
  private final MetadataStream metadataStream;
  private final PlacementStrategy placement = new RoundRobinPlacement();
  private final TopicAssignmentGossip.Publisher topicAssignmentPublisher;

  // Brokers report provisioned partitions here; the registry-shard leader registers itself as the
  // sink. Per-topic covered partition ids drive the CREATING -> ACTIVE transition.
  private final AtomicReference<BiConsumer<String, List<Integer>>> provisionedSinkRef;
  private final Map<String, Set<Integer>> provisionedPartitions = new HashMap<>();
  private final Set<String> activated = new HashSet<>();

  // Change-coordinator: drives committed -> target one safe Raft step at a time.
  private final ReconfigurationExecutor reconfigurationExecutor;
  private final Set<String> reconfiguring = new HashSet<>();

  public MetadataManager(
      final int partitionId,
      final int clusterSize,
      final MetadataStream metadataStream,
      final TopicAssignmentGossip.Publisher topicAssignmentPublisher,
      final AtomicReference<BiConsumer<String, List<Integer>>> provisionedSinkRef,
      final ReconfigurationExecutor reconfigurationExecutor) {
    this.partitionId = partitionId;
    this.clusterSize = clusterSize;
    this.metadataStream = metadataStream;
    this.topicAssignmentPublisher = topicAssignmentPublisher;
    this.provisionedSinkRef = provisionedSinkRef;
    this.reconfigurationExecutor = reconfigurationExecutor;
  }

  @Override
  public String getName() {
    return "MetadataManager-" + partitionId;
  }

  public ActorFuture<CreateTopicResponse> handleCreateTopic(final CreateTopicRequest request) {
    return actor.call(() -> createTopic(request));
  }

  public ActorFuture<DeleteTopicResponse> handleDeleteTopic(final DeleteTopicRequest request) {
    return actor.call(() -> deleteTopic(request));
  }

  public ActorFuture<ReassignTopicResponse> handleReassignTopic(
      final ReassignTopicRequest request) {
    return actor.call(() -> reassignTopic(request));
  }

  public ActorFuture<ListTopicsResponse> handleListTopics(final ListTopicsRequest request) {
    return actor.call(this::listTopics);
  }

  /**
   * The broker node ids the coordinator may place partitions on. Bootstrap seam: derived from the
   * configured cluster size today; the single point that becomes a live, advertised broker set when
   * dynamic membership lands.
   */
  private List<Integer> availableBrokers() {
    return IntStream.range(0, clusterSize).boxed().toList();
  }

  private CreateTopicResponse createTopic(final CreateTopicRequest request) {
    final var name = request.getName();
    if (name == null || !TOPIC_NAME.matcher(name).matches()) {
      return new CreateTopicResponse().setErrorCode(CoordinationErrorCode.INVALID_TOPIC);
    }
    if (request.getPartitionCount() < 1 || request.getReplicationFactor() < 1) {
      return new CreateTopicResponse().setErrorCode(CoordinationErrorCode.INVALID_TOPIC);
    }
    if (metadataStream.topicsSnapshot().containsKey(name)) {
      return new CreateTopicResponse().setErrorCode(CoordinationErrorCode.TOPIC_ALREADY_EXISTS);
    }
    final var assignment =
        placement.assign(
            request.getPartitionCount(), request.getReplicationFactor(), availableBrokers());
    metadataStream.registerTopic(
        name,
        request.getPartitionCount(),
        request.getReplicationFactor(),
        TopicMetadata.TopicStatus.CREATING,
        assignment);
    return new CreateTopicResponse().setErrorCode(NONE);
  }

  private DeleteTopicResponse deleteTopic(final DeleteTopicRequest request) {
    final var name = request.getName();
    if (!metadataStream.topicsSnapshot().containsKey(name)) {
      return new DeleteTopicResponse().setErrorCode(CoordinationErrorCode.TOPIC_NOT_FOUND);
    }
    metadataStream.deleteTopic(name);
    return new DeleteTopicResponse().setErrorCode(NONE);
  }

  private ReassignTopicResponse reassignTopic(final ReassignTopicRequest request) {
    final var response = new ReassignTopicResponse();
    final var name = request.getName();
    final var meta = metadataStream.topicsSnapshot().get(name);
    if (meta == null) {
      return response.setErrorCode(CoordinationErrorCode.TOPIC_NOT_FOUND);
    }
    if (request.getReplicationFactor() < 1) {
      return response.setErrorCode(CoordinationErrorCode.INVALID_TOPIC);
    }
    // Compute the new target placement centrally; the change-coordinator drives committed -> target
    // one safe Raft step at a time. A no-op (already at target) just clears to NONE.
    final var target =
        placement.assign(meta.partitionCount(), request.getReplicationFactor(), availableBrokers());
    if (!target.equals(meta.assignment())) {
      metadataStream.registerTopic(
          name,
          new TopicMetadata(
              meta.partitionCount(),
              request.getReplicationFactor(),
              meta.status(),
              meta.assignment(),
              target));
    }
    return response.setErrorCode(NONE);
  }

  private ListTopicsResponse listTopics() {
    final var sb = new StringBuilder();
    metadataStream
        .topicsSnapshot()
        .forEach(
            (name, meta) ->
                sb.append(name)
                    .append(';')
                    .append(meta.partitionCount())
                    .append(';')
                    .append(meta.replicationFactor())
                    .append(';')
                    .append(meta.status().name())
                    .append(';')
                    .append(meta.encodedAssignment())
                    .append('\n'));
    return new ListTopicsResponse().setErrorCode(NONE).setPayload(sb.toString());
  }

  @Override
  protected void onActorStarted() {
    scheduleTopicBroadcast();
    if (partitionId == CoordinatorRouting.TOPIC_REGISTRY_SHARD && provisionedSinkRef != null) {
      provisionedSinkRef.set(
          (topic, partitions) -> actor.run(() -> onTopicProvisioned(topic, partitions)));
    }
    if (partitionId == CoordinatorRouting.TOPIC_REGISTRY_SHARD) {
      scheduleReconfiguration();
    }
  }

  @Override
  protected void onActorClosing() {
    if (provisionedSinkRef != null) {
      provisionedSinkRef.set(null);
    }
  }

  /**
   * Periodically broadcasts the topic registry to all brokers so each can reconcile its local topic
   * Raft groups. Only the registry shard's leader broadcasts. Re-asserting the full registry on an
   * interval is the anti-entropy that lets a broker that missed an update still converge.
   */
  protected void scheduleTopicBroadcast() {
    if (topicAssignmentPublisher == null
        || partitionId != CoordinatorRouting.TOPIC_REGISTRY_SHARD) {
      return;
    }
    try {
      topicAssignmentPublisher.publish(
          TopicAssignmentGossip.encode(metadataStream.topicsSnapshot()));
    } catch (final Exception e) {
      LOG.warn("Failed to broadcast topic registry", e);
    }
    schedule(TOPIC_BROADCAST_INTERVAL, this::scheduleTopicBroadcast);
  }

  /**
   * Records partitions a broker reported as provisioned and advances the topic to {@code ACTIVE}
   * once every partition is covered. Idempotent.
   */
  private void onTopicProvisioned(final String topic, final List<Integer> partitions) {
    final var meta = metadataStream.topicsSnapshot().get(topic);
    if (meta == null || meta.status() != TopicMetadata.TopicStatus.CREATING) {
      return;
    }
    final var covered = provisionedPartitions.computeIfAbsent(topic, t -> new HashSet<>());
    covered.addAll(partitions);
    final var allCovered =
        IntStream.rangeClosed(1, meta.partitionCount()).allMatch(covered::contains);
    if (allCovered && activated.add(topic)) {
      LOG.info(
          "Topic {} fully provisioned ({} partitions) — marking ACTIVE",
          topic,
          meta.partitionCount());
      metadataStream.registerTopic(
          topic,
          meta.partitionCount(),
          meta.replicationFactor(),
          TopicMetadata.TopicStatus.ACTIVE,
          meta.assignment());
    }
  }

  /**
   * Change-coordinator kickoff/retry/anti-entropy tick: starts driving any topic with an in-flight
   * target. After a failover the new leader picks up here from the persisted committed/target.
   */
  protected void scheduleReconfiguration() {
    if (reconfigurationExecutor != null) {
      metadataStream
          .topicsSnapshot()
          .forEach(
              (name, meta) -> {
                if (meta.hasTarget() && reconfiguring.add(name)) {
                  driveReconfiguration(name);
                }
              });
    }
    schedule(RECONFIG_INTERVAL, this::scheduleReconfiguration);
  }

  /** Executes the next single step toward a topic's target, chaining until committed == target. */
  private void driveReconfiguration(final String name) {
    final var meta = metadataStream.topicsSnapshot().get(name);
    if (meta == null || !meta.hasTarget()) {
      reconfiguring.remove(name);
      return;
    }
    final var committed = meta.assignment();
    final var target = meta.target();
    final var op = ReconfigurationPlanner.nextOp(name, committed, target);
    if (op.isEmpty()) {
      LOG.info("Reassignment of topic {} complete", name);
      metadataStream.registerTopic(
          name,
          new TopicMetadata(
              meta.partitionCount(), meta.replicationFactor(), meta.status(), committed, Map.of()));
      reconfiguring.remove(name);
      return;
    }

    final var step = op.get();
    final var advanced = ReconfigurationPlanner.apply(committed, step);
    final var partitionMembers = advanced.getOrDefault(step.partitionId(), List.of());
    reconfigurationExecutor
        .execute(step, partitionMembers, meta.partitionCount())
        .whenComplete(
            (ok, error) ->
                actor.run(
                    () -> {
                      if (error != null) {
                        LOG.warn(
                            "Reassignment step {} for topic {} failed; retrying",
                            step,
                            name,
                            error);
                        reconfiguring.remove(name);
                        return;
                      }
                      metadataStream.registerTopic(
                          name,
                          new TopicMetadata(
                              meta.partitionCount(),
                              meta.replicationFactor(),
                              meta.status(),
                              advanced,
                              target));
                      driveReconfiguration(name);
                    }));
  }
}
