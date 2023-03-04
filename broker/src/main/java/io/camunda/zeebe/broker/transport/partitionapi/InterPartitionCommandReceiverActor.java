/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.partitionapi;

import static io.camunda.zeebe.broker.transport.partitionapi.InterPartitionCommandSenderImpl.TOPIC_PREFIX;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.backup.api.CheckpointListener;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.scheduler.Actor;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;

/**
 * Receives messages send by @{@link InterPartitionCommandSenderImpl} and tries to write them as
 * commands to the partition's log stream. Failure to write to the log stream, for example because
 * no disk space is available, the logstream rejected the write operation or message decoding
 * failure, are ignored. The sender is responsible for recognizing failures and retrying.
 */
public final class InterPartitionCommandReceiverActor extends Actor
    implements DiskSpaceUsageListener, CheckpointListener {
  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;
  private final String actorName;
  private final ClusterCommunicationService communicationService;
  private final int partitionId;
  private final InterPartitionCommandReceiverImpl receiver;

  public InterPartitionCommandReceiverActor(
      final int nodeId,
      final int partitionId,
      final ClusterCommunicationService communicationService,
      final LogStreamWriter logStreamWriter) {
    this.partitionId = partitionId;
    this.communicationService = communicationService;
    receiver = new InterPartitionCommandReceiverImpl(logStreamWriter);
    actorName = buildActorName(nodeId, getClass().getSimpleName(), partitionId);
  }

  @Override
  protected Map<String, String> createContext() {
    final var context = super.createContext();
    context.put(ACTOR_PROP_PARTITION_ID, Integer.toString(partitionId));
    return context;
  }

  @Override
  public String getName() {
    return actorName;
  }

  @Override
  protected void onActorStarting() {
    communicationService.subscribe(
        TOPIC_PREFIX + partitionId, Function.identity(), this::tryHandleMessage, actor::run);
  }

  @Override
  protected void onActorClosing() {
    communicationService.unsubscribe(TOPIC_PREFIX + partitionId);
  }

  @Override
  public void onDiskSpaceNotAvailable() {
    actor.run(() -> receiver.setDiskSpaceAvailable(false));
  }

  @Override
  public void onDiskSpaceAvailable() {
    actor.run(() -> receiver.setDiskSpaceAvailable(true));
  }

  @Override
  public void onNewCheckpointCreated(final long checkpointId) {
    actor.run(() -> receiver.setCheckpointId(checkpointId));
  }

  private void tryHandleMessage(final MemberId memberId, final byte[] message) {
    try {
      receiver.handleMessage(memberId, message);
    } catch (final RuntimeException e) {
      LOG.error("Error while handling message", e);
    }
  }
}
