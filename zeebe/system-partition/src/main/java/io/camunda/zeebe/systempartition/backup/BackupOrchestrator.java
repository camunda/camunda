/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.systempartition.backup;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.protocol.impl.record.value.backupmetadata.BackupMetadataRecord;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BackupMetadataIntent;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.systempartition.SystemPartition;
import io.camunda.zeebe.systempartition.SystemPartitionFacadeImpl;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Leader-only actor that fans BackupMetadata RECORDED rows out to data partitions.
 *
 * <p>Lifecycle: subscribed via {@link
 * SystemPartitionFacadeImpl#addBackupMetadataListener(SystemPartitionFacadeImpl.BackupMetadataListener)}
 * once on actor start. For every {@code RECORDED} event whose status is {@code PENDING}, the
 * orchestrator sends a {@link CheckpointIntent#CREATE} command to the data partition via the broker
 * client. The reply (success or error) is folded back into a {@code RECORD} or {@code MARK_FAILED}
 * command on the system partition with the terminal status.
 *
 * <p>Threading: replies arrive on the broker-client's IO thread and are funnelled through {@link
 * Actor#run(Runnable)} so all state mutation happens on the actor thread.
 */
public final class BackupOrchestrator extends Actor {

  private static final Logger LOG = LoggerFactory.getLogger(BackupOrchestrator.class);

  private final SystemPartition systemPartition;
  private final BrokerClient brokerClient;

  public BackupOrchestrator(final SystemPartition sp, final BrokerClient bc) {
    systemPartition = sp;
    brokerClient = bc;
  }

  @Override
  public String getName() {
    return "BackupOrchestrator";
  }

  @Override
  protected void onActorStarted() {
    if (systemPartition instanceof final SystemPartitionFacadeImpl facade) {
      facade.addBackupMetadataListener(this::onBackupEvent);
    } else {
      LOG.warn(
          "SystemPartition is not a facade impl ({}); BackupOrchestrator will not receive events",
          systemPartition.getClass().getName());
    }
  }

  private void onBackupEvent(final BackupMetadataIntent intent, final BackupMetadataRecord record) {
    if (intent != BackupMetadataIntent.RECORDED) {
      return;
    }
    if (!"PENDING".equals(record.getStatus())) {
      return;
    }
    actor.run(() -> fanOut(record.getCheckpointId(), record.getPartitionId()));
  }

  private void fanOut(final long checkpointId, final int partitionId) {
    if (!systemPartition.isLeader()) {
      // Followers don't fan out — only the leader drives the backup orchestration.
      return;
    }
    LOG.debug(
        "Fanning out checkpoint create for backup {}/partition {}", checkpointId, partitionId);
    final BrokerExecuteCommand<?> request = new CheckpointCreateRequest(checkpointId, partitionId);
    brokerClient
        .sendRequestWithRetry(request)
        .whenComplete(
            (reply, err) -> actor.run(() -> handleReply(checkpointId, partitionId, reply, err)));
  }

  private void handleReply(
      final long checkpointId,
      final int partitionId,
      final BrokerResponse<?> reply,
      final Throwable err) {
    final BackupMetadataRecord followUp =
        new BackupMetadataRecord().setCheckpointId(checkpointId).setPartitionId(partitionId);
    if (err == null && reply != null) {
      followUp.setStatus("COMPLETED");
      systemPartition.submitBackupCommand(BackupMetadataIntent.RECORD, followUp);
    } else {
      followUp.setStatus("FAILED");
      if (err != null) {
        followUp.setFailureReason(String.valueOf(err.getMessage()));
      }
      systemPartition.submitBackupCommand(BackupMetadataIntent.MARK_FAILED, followUp);
    }
  }

  /** Minimal {@link BrokerExecuteCommand} for {@code CheckpointIntent.CREATE}. */
  private static final class CheckpointCreateRequest
      extends BrokerExecuteCommand<CheckpointRecord> {

    private final CheckpointRecord requestDto = new CheckpointRecord();

    CheckpointCreateRequest(final long checkpointId, final int partitionId) {
      super(ValueType.CHECKPOINT, CheckpointIntent.CREATE);
      requestDto.setCheckpointId(checkpointId);
      setPartitionId(partitionId);
    }

    @Override
    public BufferWriter getRequestWriter() {
      return requestDto;
    }

    @Override
    protected CheckpointRecord toResponseDto(final DirectBuffer buffer) {
      final CheckpointRecord responseDto = new CheckpointRecord();
      responseDto.wrap(buffer);
      return responseDto;
    }
  }
}
