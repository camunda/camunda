/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.partitionapi;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.utils.serializer.serializers.DefaultSerializers;
import io.camunda.zeebe.backup.api.CheckpointListener;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.scheduler.Actor;
import java.util.List;
import java.util.Map;
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
  private final List<String> receivingSubjects;

  public InterPartitionCommandReceiverActor(
      final int partitionId,
      final ClusterCommunicationService communicationService,
      final LogStreamWriter logStreamWriter,
      final List<String> receivingSubjects) {
    this.partitionId = partitionId;
    this.communicationService = communicationService;
    receiver = new InterPartitionCommandReceiverImpl(logStreamWriter);
    actorName = buildActorName(getClass().getSimpleName(), partitionId);
    this.receivingSubjects = receivingSubjects;
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
    receivingSubjects.forEach(this::consume);
  }

  @Override
  protected void onActorClosing() {
    receivingSubjects.forEach(communicationService::unsubscribe);
  }

  private void consume(final String subject) {
    communicationService.consume(
        subject, DefaultSerializers.BASIC::decode, this::tryHandleMessage, actor::run);
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
  public void onNewCheckpointCreated(final long checkpointId, final CheckpointType checkpointType) {
    actor.run(() -> receiver.setCheckpointInfo(checkpointId, checkpointType));
  }

  private void tryHandleMessage(final MemberId memberId, final byte[] message) {
    try {
      receiver.handleMessage(memberId, message);
    } catch (final RuntimeException e) {
      LOG.error("Error while handling message", e);
    }
  }
}
