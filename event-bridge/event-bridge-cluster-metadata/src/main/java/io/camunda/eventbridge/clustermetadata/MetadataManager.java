/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata;

import io.camunda.eventbridge.clustermetadata.processing.CreateTopicProcessor;

import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.NONE;

import io.camunda.eventbridge.clustermetadata.placement.PlacementStrategy;
import io.camunda.eventbridge.clustermetadata.placement.RoundRobinPlacement;
import io.camunda.eventbridge.clustermetadata.reconfig.ReconfigurationExecutor;
import io.camunda.eventbridge.clustermetadata.reconfig.ReconfigurationPlanner;
import io.camunda.eventbridge.clustermetadata.stream.MetadataStream;
import io.camunda.eventbridge.clustermetadata.state.topic.TopicMetadata;
import io.camunda.eventbridge.clustermetadata.record.TopicRecord;
import io.camunda.eventbridge.core.coordinator.CoordinatorRouting;
import io.camunda.eventbridge.protocol.request.coordination.CreateTopicRequest;
import io.camunda.eventbridge.protocol.request.coordination.DeleteTopicRequest;
import io.camunda.eventbridge.protocol.request.coordination.ListTopicsRequest;
import io.camunda.eventbridge.protocol.request.coordination.ListTopicsResponse;
import io.camunda.eventbridge.protocol.request.coordination.ReassignTopicRequest;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.eventbridge.protocol.transport.CoordinationResponseEncoder;
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
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the topic registry on the metadata group: topic admin (create/delete/reassign/list), central
 * placement, the {@code CREATING -> ACTIVE} transition, and the change-coordinator that drives
 * committed assignment toward a target one safe Raft step at a time. Leader-only; the
 * sink/change-coordinator act only on the registry shard ({@link
 * CoordinatorRouting#TOPIC_REGISTRY_SHARD}).
 *
 * <p>This is the metadata control plane: it runs on the dedicated single-partition {@code
 * event-bridge-metadata} Raft group (its leader), over a {@link MetadataStream}. Consumer-group
 * coordination + offsets stay in the coordinator group ({@code CoordinationManager}).
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
  private final Supplier<List<Integer>> registeredBrokers;
  private final MetadataStream metadataStream;
  private final PlacementStrategy placement = new RoundRobinPlacement();

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
      final Supplier<List<Integer>> registeredBrokers,
      final MetadataStream metadataStream,
      final AtomicReference<BiConsumer<String, List<Integer>>> provisionedSinkRef,
      final ReconfigurationExecutor reconfigurationExecutor) {
    this.partitionId = partitionId;
    this.registeredBrokers = registeredBrokers;
    this.metadataStream = metadataStream;
    this.provisionedSinkRef = provisionedSinkRef;
    this.reconfigurationExecutor = reconfigurationExecutor;
  }

  @Override
  public String getName() {
    return "MetadataManager-" + partitionId;
  }

  /**
   * Creates a topic: the manager computes the placement (it needs live broker membership) and
   * writes a {@code CREATE_TOPIC} command; the {@link
   * io.camunda.eventbridge.clustermetadata.processing.CreateTopicProcessor} validates it (name, counts,
   * not-already-exists) against the replicated registry and replies after commit.
   */
  public CompletableFuture<byte[]> handleCreateTopic(final CreateTopicRequest request) {
    return writeTopicRequest(
        () -> {
          final var valid = request.getPartitionCount() >= 1 && request.getReplicationFactor() >= 1;
          final var assignment =
              valid
                  ? placement.assign(
                      request.getPartitionCount(),
                      request.getReplicationFactor(),
                      availableBrokers())
                  : Map.<Integer, List<Integer>>of();
          return new TopicRecord()
              .setName(request.getName() == null ? "" : request.getName())
              .setOp(TopicRecord.OP_REGISTER)
              .setPartitionCount(request.getPartitionCount())
              .setReplicationFactor(request.getReplicationFactor())
              .setStatus(TopicMetadata.TopicStatus.CREATING)
              .setAssignment(assignment);
        },
        metadataStream::createTopic);
  }

  public CompletableFuture<byte[]> handleDeleteTopic(final DeleteTopicRequest request) {
    return writeTopicRequest(
        () ->
            new TopicRecord()
                .setName(request.getName() == null ? "" : request.getName())
                .setOp(TopicRecord.OP_DELETE),
        metadataStream::deleteTopic);
  }

  /**
   * Reassigns a topic: the manager computes the new target placement (from the topic's current
   * partition count + the requested replication factor) and writes a {@code REASSIGN_TOPIC}
   * command; the processor validates the topic exists and replies after commit. The
   * change-coordinator then drives committed → target.
   */
  public CompletableFuture<byte[]> handleReassignTopic(final ReassignTopicRequest request) {
    return writeTopicRequest(
        () -> {
          final var name = request.getName() == null ? "" : request.getName();
          final var command =
              new TopicRecord()
                  .setName(name)
                  .setOp(TopicRecord.OP_REGISTER)
                  .setReplicationFactor(request.getReplicationFactor());
          final var meta = metadataStream.topicsSnapshot().get(name);
          if (meta != null && request.getReplicationFactor() >= 1) {
            final var target =
                placement.assign(
                    meta.partitionCount(), request.getReplicationFactor(), availableBrokers());
            command
                .setPartitionCount(meta.partitionCount())
                .setStatus(meta.status())
                .setAssignment(meta.assignment())
                .setTarget(target);
          }
          return command;
        },
        metadataStream::reassignTopic);
  }

  /** Lists topics (request/response, no log write) and returns the framed reply. */
  public CompletableFuture<byte[]> handleListTopics(final ListTopicsRequest request) {
    final var result = new CompletableFuture<byte[]>();
    actor.run(
        () -> {
          try {
            result.complete(CoordinationResponseEncoder.encode(listTopics()));
          } catch (final RuntimeException e) {
            result.completeExceptionally(e);
          }
        });
    return result;
  }

  /**
   * Builds a topic command on the actor (so placement reads live state safely), writes it through
   * the stream, and completes with the committed reply framed for the broker client — so every
   * handler method returns ready-to-send bytes and the transport layer only routes.
   */
  private CompletableFuture<byte[]> writeTopicRequest(
      final Supplier<TopicRecord> commandBuilder,
      final Function<TopicRecord, CompletableFuture<byte[]>> write) {
    final var result = new CompletableFuture<byte[]>();
    actor.run(
        () ->
            write
                .apply(commandBuilder.get())
                .whenComplete(
                    (response, error) -> {
                      if (error != null) {
                        result.completeExceptionally(error);
                      } else {
                        result.complete(CoordinationResponseEncoder.encodeValue(response));
                      }
                    }));
    return result;
  }

  /**
   * The broker node ids the coordinator may place partitions on — the brokers that have registered
   * by joining the metadata Raft group (voting members + passive observers), derived from the
   * group's live membership rather than a static configured cluster size. As brokers join/leave the
   * group this set tracks them, so placement only targets registered brokers.
   */
  private List<Integer> availableBrokers() {
    return registeredBrokers.get();
  }

  private ListTopicsResponse listTopics() {
    final var response = new ListTopicsResponse().setErrorCode(NONE);
    metadataStream
        .topicsSnapshot()
        .forEach(
            (name, meta) ->
                response.addTopic(
                    name,
                    meta.partitionCount(),
                    meta.replicationFactor(),
                    meta.status().name(),
                    meta.assignment()));
    return response;
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
