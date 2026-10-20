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
import io.camunda.zeebe.eventplane.publisher.Announcer;
import io.camunda.zeebe.eventplane.publisher.CutPolicy;
import io.camunda.zeebe.eventplane.publisher.HttpAnnouncer;
import io.camunda.zeebe.eventplane.segment.Identity;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.net.URI;
import org.jspecify.annotations.Nullable;

/**
 * Publishes the partition's journal into the Event Plane's store while this broker leads it, if the
 * Event Plane is on. Comes after the exporter director, which keeps the upload floor.
 */
public final class EventPlanePartitionTransitionStep implements PartitionTransitionStep {

  private @Nullable PublisherService publisher;

  @Override
  public ActorFuture<Void> prepareTransition(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    if (publisher == null) {
      return CompletableActorFuture.completed(null);
    }
    final var closing = publisher;
    publisher = null;
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
    final var config = context.getBrokerCfg().getExperimental().getEventPlane();
    if (!config.isEnabled() || targetRole != Role.LEADER || publisher != null) {
      return CompletableActorFuture.completed(null);
    }
    final var clusterKey =
        context
            .getClusterConfigurationService()
            .getCurrentClusterConfiguration()
            .clusterId()
            .orElse(context.getBrokerCfg().getCluster().getClusterName());
    final var log =
        new Identity(
            config.getClusterId() != null ? config.getClusterId() : clusterKey,
            context.partitionId().group(),
            context.getPartitionId());
    final var raftPartition = context.getRaftPartition();
    publisher =
        new PublisherService(
            log,
            clusterKey,
            raftPartition.dataDirectory().toPath(),
            EventPlaneStores.open(config.getStore()),
            new RaftEntrySource(raftPartition.getServer().openReader()),
            new ExporterStateFloorStore(context.getExporterDirector()),
            config.getServiceUrl() != null
                ? new HttpAnnouncer(URI.create(config.getServiceUrl()))
                : Announcer.NONE,
            new CutPolicy(config.getMaxSegmentSize().toBytes(), config.getCutInterval()),
            context.getPersistedSnapshotStore(),
            config.getSnapshotInterval());
    publisher.start(config.getPollInterval());
    return CompletableActorFuture.completed(null);
  }

  @Override
  public String getName() {
    return "EventPlane";
  }
}
