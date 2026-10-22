/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.eventplane;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.eventplane.publisher.Announcer;
import io.camunda.zeebe.eventplane.publisher.CutPolicy;
import io.camunda.zeebe.eventplane.publisher.HttpAnnouncer;
import io.camunda.zeebe.eventplane.segment.LogId;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the upload floor in the partition's state on every replica while the Event Plane is on, and
 * deletes it while it is off. On the leader, it publishes the partition's journal into the Event
 * Plane's store and sends the floor to the followers; on a follower, it stores the floors it gets.
 */
public final class EventPlanePartitionTransitionStep implements PartitionTransitionStep {

  private static final Logger LOG =
      LoggerFactory.getLogger(EventPlanePartitionTransitionStep.class);

  private @Nullable PublisherService publisher;
  private @Nullable ScheduledTimer floorResend;
  private @Nullable FloorReceiver floorReceiver;

  @Override
  public ActorFuture<Void> prepareTransition(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    if (floorResend != null) {
      floorResend.cancel();
      floorResend = null;
    }
    if (floorReceiver != null) {
      floorReceiver.close();
      floorReceiver = null;
    }
    if (publisher == null) {
      return CompletableActorFuture.completed(null);
    }
    final var closing = publisher;
    publisher = null;
    context.setEventPlanePublisher(null);
    final ActorFuture<Void> closed = context.getConcurrencyControl().createFuture();
    closing
        .closeAsync()
        .whenComplete(
            (ok, error) -> context.getConcurrencyControl().run(() -> closed.complete(null)));
    return closed;
  }

  @Override
  public ActorFuture<Void> transitionTo(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    @SuppressWarnings("unchecked")
    final ZeebeDb<ZbColumnFamilies> zeebeDb = context.getZeebeDb();
    if (zeebeDb == null || targetRole == Role.INACTIVE) {
      return CompletableActorFuture.completed(null);
    }
    final var config = context.getBrokerCfg().getExperimental().getEventPlane();
    final var floors = new FloorState(zeebeDb, zeebeDb.createContext());
    if (!config.isEnabled()) {
      // a floor left from when the Event Plane was on would stop compaction for good
      floors.delete();
      return CompletableActorFuture.completed(null);
    }
    // Until the leader stores a floor, compaction stops where it is; on followers too, as one of
    // them may become the leader that still needs the entries.
    floors.holdIfAbsent();
    final var distribution =
        new FloorDistribution(context.getMessagingService(), context.partitionId());
    if (targetRole != Role.LEADER) {
      floorReceiver = new FloorReceiver(floors, distribution);
      floorReceiver.open(context.getConcurrencyControl());
      return CompletableActorFuture.completed(null);
    }
    if (publisher != null) {
      return CompletableActorFuture.completed(null);
    }
    final var clusterKey = EventPlaneClusterKey.of(context);
    if (EventPlaneClusterKey.configured(context.getBrokerCfg()) == null) {
      LOG.warn(
          "The Event Plane names this cluster {} after its generated id. A restore that recreates"
              + " the topology generates a new one, and the Event Plane then sees a new cluster."
              + " Configure camunda.cluster.cluster-id to keep the cluster's logs across restores.",
          clusterKey);
    }
    final var log = new LogId(clusterKey, context.partitionId().group(), context.getPartitionId());
    final var raftPartition = context.getRaftPartition();
    // the publisher's thread has a transaction context of its own
    final var floorStore =
        new ReplicatedFloorStore(new FloorState(zeebeDb, zeebeDb.createContext()), distribution);
    publisher =
        new PublisherService(
            log,
            clusterKey,
            raftPartition.dataDirectory().toPath(),
            EventPlaneStores.open(config.getStore()),
            new RaftEntrySource(raftPartition.getServer().openReader()),
            floorStore,
            config.getServiceUrl() != null
                ? new HttpAnnouncer(URI.create(config.getServiceUrl()))
                : Announcer.NONE,
            new CutPolicy(config.getMaxSegmentSize().toBytes(), config.getCutInterval()));
    publisher.start(config.getPollInterval());
    context.setEventPlanePublisher(publisher);
    floorResend =
        context
            .getConcurrencyControl()
            .runAtFixedRate(
                context.getBrokerCfg().getExporting().distributionInterval(), floorStore::resend);
    return CompletableActorFuture.completed(null);
  }

  @Override
  public String getName() {
    return "EventPlane";
  }
}
