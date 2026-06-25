/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata;

import io.camunda.eventbridge.clustermetadata.reconfig.ReconfigurationExecutor;
import io.camunda.eventbridge.clustermetadata.reconfig.ReconfigurationPlanner;
import io.camunda.eventbridge.clustermetadata.record.TopicRecord;
import io.camunda.eventbridge.clustermetadata.state.topic.TopicMetadata;
import io.camunda.eventbridge.clustermetadata.state.topic.TopicQueryService;
import io.camunda.eventbridge.clustermetadata.stream.MetadataStream;
import io.camunda.eventbridge.core.coordinator.CoordinatorRouting;
import io.camunda.zeebe.scheduler.Actor;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The write path for topic admin on the metadata group: it writes the create/delete/reassign
 * commands (built by the service layer) straight to the stream, and runs the leader-only control
 * plane — the {@code CREATING -> ACTIVE} transition and the change-coordinator that drives
 * committed assignment toward a target one safe Raft step at a time. The sink/change-coordinator
 * act only on the registry shard ({@link CoordinatorRouting#TOPIC_REGISTRY_SHARD}). List-topics
 * reads are served by {@link MetadataQueryHandler} on its own actor; placement is resolved by the
 * processors.
 *
 * <p>It runs on the dedicated single-partition {@code event-bridge-metadata} Raft group (its
 * leader), over a {@link MetadataStream}. Consumer-group coordination + offsets stay in the
 * coordinator group ({@code CoordinationManager}).
 *
 * <p>Propagation to brokers is no longer a push from here: every broker observes the metadata Raft
 * group (follower or passive observer) and reconciles its local topic groups from its own
 * replicated registry copy, so this actor no longer broadcasts the registry.
 */
public class MetadataManager extends Actor {

  private static final Logger LOG = LoggerFactory.getLogger(MetadataManager.class);

  // Change-coordinator kickoff/retry/anti-entropy tick.
  private static final Duration RECONFIG_INTERVAL = Duration.ofSeconds(1);

  private final int partitionId;
  private final MetadataStream metadataStream;
  private final TopicQueryService topics;

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
      final MetadataStream metadataStream,
      final TopicQueryService topics,
      final AtomicReference<BiConsumer<String, List<Integer>>> provisionedSinkRef,
      final ReconfigurationExecutor reconfigurationExecutor) {
    this.partitionId = partitionId;
    this.metadataStream = metadataStream;
    this.topics = topics;
    this.provisionedSinkRef = provisionedSinkRef;
    this.reconfigurationExecutor = reconfigurationExecutor;
  }

  @Override
  public String getName() {
    return "MetadataManager-" + partitionId;
  }

  /**
   * Writes the {@code CREATE_TOPIC} command (the {@link TopicRecord} built by the service layer)
   * straight to the stream and forwards the committed reply. The {@link
   * io.camunda.eventbridge.clustermetadata.processing.CreateTopicProcessor} validates it (name,
   * counts, not-already-exists) against the replicated registry and resolves the placement — the
   * leader that produces the durable event makes that decision.
   */
  public CompletableFuture<byte[]> handleCreateTopic(final TopicRecord command) {
    return writeTopicRequest(command, metadataStream::createTopic);
  }

  /** Writes the {@code DELETE_TOPIC} command as received and forwards the committed reply. */
  public CompletableFuture<byte[]> handleDeleteTopic(final TopicRecord command) {
    return writeTopicRequest(command, metadataStream::deleteTopic);
  }

  /**
   * Writes the {@code REASSIGN_TOPIC} command as received and forwards the committed reply. The
   * {@code ReassignTopicProcessor} validates the topic exists and resolves the new target placement
   * from the current partition count + requested replication factor; the change-coordinator then
   * drives committed → target.
   */
  public CompletableFuture<byte[]> handleReassignTopic(final TopicRecord command) {
    return writeTopicRequest(command, metadataStream::reassignTopic);
  }

  /**
   * Writes the wire-supplied command straight to the stream (no request→record mapping) and
   * completes with the raw committed reply (or fails with a {@code CommandRejectionException} for a
   * rejected command / the transport error for a genuine failure). The {@code
   * MetadataRequestHandler} frames the result for the broker client, so the handler — not this
   * manager — owns the framing.
   */
  private CompletableFuture<byte[]> writeTopicRequest(
      final TopicRecord command, final Function<TopicRecord, CompletableFuture<byte[]>> write) {
    final var result = new CompletableFuture<byte[]>();
    actor.run(
        () ->
            write
                .apply(command)
                .whenComplete(
                    (response, error) -> {
                      if (error == null) {
                        result.complete(response);
                      } else {
                        result.completeExceptionally(error);
                      }
                    }));
    return result;
  }

  @Override
  protected void onActorStarted() {
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
   * Records partitions a broker reported as provisioned and advances the topic to {@code ACTIVE}
   * once every partition is covered. Idempotent.
   */
  private void onTopicProvisioned(final String topic, final List<Integer> partitions) {
    final var meta = topics.topic(topic);
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
   * target (set by a client reassignment or the {@code PlacementHealTask}). After a failover the
   * new leader picks up here from the persisted committed/target.
   */
  protected void scheduleReconfiguration() {
    if (reconfigurationExecutor != null) {
      topics
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
    final var meta = topics.topic(name);
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
