/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.partitioning;

import io.atomix.cluster.messaging.MessagingService;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.eventbridge.broker.compaction.ManifestStore;
import io.camunda.eventbridge.broker.compaction.ReaderLeaseRegistry;
import io.camunda.eventbridge.messaging.fetch.FetchPurgatory;
import io.camunda.eventbridge.messaging.stream.EventBridgeEventStream;
import io.camunda.eventbridge.messaging.transport.fetch.FetchRequestHandler;
import io.camunda.eventbridge.messaging.transport.publish.PublishRequestCorrelator;
import io.camunda.eventbridge.messaging.transport.publish.PublishRequestHandler;
import io.camunda.eventbridge.messaging.watermark.HighWatermark;
import io.camunda.eventbridge.protocol.request.coordination.CleanupPolicy;
import io.camunda.eventbridge.transport.RequestHandlerRegistry;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import java.nio.file.Path;
import java.time.InstantSource;
import java.util.concurrent.ExecutorService;
import org.agrona.concurrent.IdGenerator;

public final class PartitionContext {

  private final int partitionId;
  private final int partitionCount;
  private final String routingGroup;
  private final RaftPartition raftPartition;
  private final ActorSchedulingService actorScheduler;
  private final MessagingService messagingService;
  private final InstantSource clock;
  private final IdGenerator idGenerator;
  private final ExecutorService executorService;

  // Stamped once at construction and never reassigned: the topic's cleanup policy is immutable for
  // the lifetime of a partition replica (event-bridge ADR 0001), so every read of these three
  // fields
  // is a plain field access — no lookup, no cache, no invalidation to reason about. DELETE-policy
  // partitions carry null compaction handles; only COMPACT partitions populate them.
  private final CleanupPolicy cleanupPolicy;
  private final ManifestStore compactionManifestStore;
  private final ReaderLeaseRegistry compactionLeaseRegistry;
  private final Path compactionDirectory;

  private LogStorage logStorage;
  private PublishRequestCorrelator correlator;
  private EventBridgeEventStream eventStream;
  private PublishRequestHandler publishRequestHandler;
  private FetchRequestHandler fetchRequestHandler;
  private RequestHandlerRegistry requestHandlerRegistry;
  private HighWatermark highWatermark;
  private FetchPurgatory fetchPurgatory;

  public PartitionContext(
      final int partitionId,
      final int partitionCount,
      final String routingGroup,
      final RaftPartition raftPartition,
      final ActorSchedulingService actorScheduler,
      final MessagingService messagingService,
      final InstantSource clock,
      final IdGenerator idGenerator,
      final ExecutorService executorService,
      final CleanupPolicy cleanupPolicy,
      final ManifestStore compactionManifestStore,
      final ReaderLeaseRegistry compactionLeaseRegistry,
      final Path compactionDirectory) {
    this.partitionId = partitionId;
    this.partitionCount = partitionCount;
    this.routingGroup = routingGroup;
    this.raftPartition = raftPartition;
    this.actorScheduler = actorScheduler;
    this.messagingService = messagingService;
    this.clock = clock;
    this.idGenerator = idGenerator;
    this.executorService = executorService;
    this.cleanupPolicy = cleanupPolicy;
    this.compactionManifestStore = compactionManifestStore;
    this.compactionLeaseRegistry = compactionLeaseRegistry;
    this.compactionDirectory = compactionDirectory;
  }

  /** The topic's cleanup policy (event-bridge ADR 0001); immutable for the partition's lifetime. */
  public CleanupPolicy getCleanupPolicy() {
    return cleanupPolicy;
  }

  /**
   * The committed manifest seam for a {@code COMPACT} partition, or {@code null} for a {@code
   * DELETE} partition. Consulted by the fetch path to serve positions at or below the cleaner point
   * from the clean set.
   */
  public ManifestStore getCompactionManifestStore() {
    return compactionManifestStore;
  }

  /**
   * The reader-lease registry a {@code COMPACT} partition's cleaner condemns clean segments
   * through, or {@code null} for a {@code DELETE} partition. The fetch path acquires leases from
   * the same registry so the cleaner's deferred deletion never unlinks a segment an in-flight fetch
   * response still streams from.
   */
  public ReaderLeaseRegistry getCompactionLeaseRegistry() {
    return compactionLeaseRegistry;
  }

  /**
   * The directory a {@code COMPACT} partition's manifest and clean segments live in, or {@code
   * null} for a {@code DELETE} partition. The fetch path resolves a clean segment's file within it.
   */
  public Path getCompactionDirectory() {
    return compactionDirectory;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public int getPartitionCount() {
    return partitionCount;
  }

  /** The gateway routing group whose request subjects this partition's handlers register under. */
  public String getRoutingGroup() {
    return routingGroup;
  }

  public RaftPartition getRaftPartition() {
    return raftPartition;
  }

  public ActorSchedulingService getActorScheduler() {
    return actorScheduler;
  }

  public MessagingService getMessagingService() {
    return messagingService;
  }

  public InstantSource getClock() {
    return clock;
  }

  public IdGenerator getIdGenerator() {
    return idGenerator;
  }

  public LogStorage getLogStorage() {
    return logStorage;
  }

  public void setLogStorage(final LogStorage logStorage) {
    this.logStorage = logStorage;
  }

  public PublishRequestCorrelator getCorrelator() {
    return correlator;
  }

  public void setCorrelator(final PublishRequestCorrelator requestCorrelator) {
    correlator = requestCorrelator;
  }

  public EventBridgeEventStream getEventStream() {
    return eventStream;
  }

  public void setEventStream(final EventBridgeEventStream eventStream) {
    this.eventStream = eventStream;
  }

  public PublishRequestHandler getPublishRequestHandler() {
    return publishRequestHandler;
  }

  public void setPublishRequestHandler(final PublishRequestHandler publishRequestHandler) {
    this.publishRequestHandler = publishRequestHandler;
  }

  public FetchRequestHandler getFetchRequestHandler() {
    return fetchRequestHandler;
  }

  public void setFetchRequestHandler(final FetchRequestHandler fetchRequestHandler) {
    this.fetchRequestHandler = fetchRequestHandler;
  }

  public RequestHandlerRegistry getRequestHandlerRegistry() {
    return requestHandlerRegistry;
  }

  public void setRequestHandlerRegistry(final RequestHandlerRegistry requestHandlerRegistry) {
    this.requestHandlerRegistry = requestHandlerRegistry;
  }

  public ExecutorService getExecutorService() {
    return executorService;
  }

  public HighWatermark getHighWatermark() {
    return highWatermark;
  }

  public void setHighWatermark(final HighWatermark highWatermark) {
    this.highWatermark = highWatermark;
  }

  public FetchPurgatory getFetchPurgatory() {
    return fetchPurgatory;
  }

  public void setFetchPurgatory(final FetchPurgatory fetchPurgatory) {
    this.fetchPurgatory = fetchPurgatory;
  }
}
