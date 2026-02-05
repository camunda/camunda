/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.admin.backup;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.client.api.BackupAlreadyExistException;
import io.camunda.zeebe.backup.client.api.BackupDeleteRequest;
import io.camunda.zeebe.backup.client.api.BackupListRequest;
import io.camunda.zeebe.backup.client.api.BackupRequestHandler;
import io.camunda.zeebe.backup.client.api.BackupResponse;
import io.camunda.zeebe.backup.client.api.BackupStatus;
import io.camunda.zeebe.backup.client.api.BrokerBackupRangesRequest;
import io.camunda.zeebe.backup.client.api.BrokerCheckpointStateRequest;
import io.camunda.zeebe.backup.client.api.State;
import io.camunda.zeebe.backup.common.CheckpointIdGenerator;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.broker.client.api.dto.BrokerErrorResponse;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.protocol.impl.encoding.BackupListResponse;
import io.camunda.zeebe.protocol.impl.encoding.BackupRangesResponse;
import io.camunda.zeebe.protocol.impl.encoding.BackupRangesResponse.CheckpointInfo;
import io.camunda.zeebe.protocol.impl.encoding.BackupRangesResponse.PartitionBackupRange;
import io.camunda.zeebe.protocol.impl.encoding.BackupStatusResponse;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse;
import io.camunda.zeebe.protocol.management.BackupStatusCode;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;

public class BackupRequestHandlerTest extends GatewayTest {

  BackupRequestHandler requestHandler;

  @Before
  public void setup() {
    requestHandler = new BackupRequestHandler(brokerClient, new CheckpointIdGenerator());
  }

  @Test
  public void shouldCompleteRequestWhenAllPartitionsSucceeds() {
    // given
    final BackupStub stub = new BackupStub();
    stub.registerWith(brokerClient);

    // when
    final var future = requestHandler.takeBackup(1);

    // then
    assertThat(future).succeedsWithin(Duration.ofMillis(500));
  }

  @Test
  public void shouldFailFutureWhenOnePartitionFails() {
    // given
    final int lastPartitionId =
        brokerClient.getTopologyManager().getTopology().getPartitionsCount();
    final BackupStub stub = new BackupStub().withErrorResponseFor(lastPartitionId);
    stub.registerWith(brokerClient);

    // when
    final var future = requestHandler.takeBackup(1);

    // then
    assertThat(future)
        .failsWithin(Duration.ofMillis(500))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(BrokerErrorException.class);
  }

  @Test
  public void shouldFailTakeBackupIfAHigherCheckpointExists() {
    // given
    final BackupStub stub = new BackupStub();
    stub.registerWith(brokerClient);
    stub.withResponse(new BackupResponse(false, 2), 1);

    // when
    final var future = requestHandler.takeBackup(1);

    // then
    assertThat(future)
        .failsWithin(Duration.ofMillis(500))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(BackupAlreadyExistException.class);
  }

  @Test
  public void shouldFailTakeBackupIfAllPartitionsReject() {
    // given
    final BackupStub stub = new BackupStub();
    stub.registerWith(brokerClient);
    brokerClient
        .getTopologyManager()
        .getTopology()
        .getPartitions()
        .forEach(
            p -> {
              stub.withResponse(new BackupResponse(false, 1), p);
            });

    // when
    final var future = requestHandler.takeBackup(1);

    // then
    assertThat(future)
        .failsWithin(Duration.ofMillis(500))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(BackupAlreadyExistException.class);
  }

  @Test
  public void shouldNotFailIfOnlySomePartitionsReject() {
    // given
    final BackupStub stub = new BackupStub();
    stub.registerWith(brokerClient);
    // only partition 1 rejects, all other partitions return (true, 1)
    stub.withResponse(new BackupResponse(false, 1), 1);

    // when
    final var future = requestHandler.takeBackup(1);

    // then
    assertThat(future).succeedsWithin(Duration.ofMillis(500));
  }

  @Test
  public void shouldReturnCompleteStatusWhenAllPartitionsHaveCompleteBackup() {
    // given
    final BackupQueryStub stub = new BackupQueryStub();
    stub.registerWith(brokerClient);

    // when
    final var future = requestHandler.getStatus(1);

    // then
    assertThat(future)
        .succeedsWithin(Duration.ofMillis(500))
        .returns(State.COMPLETED, BackupStatus::status);

    final var status = future.toCompletableFuture().join();
    assertThat(status.partitions())
        .hasSize(brokerClient.getTopologyManager().getTopology().getPartitionsCount());
  }

  @Test
  public void shouldReturnInProgressStatusWhenOnePartitionIsInProgress() {
    // given
    final BackupQueryStub stub = new BackupQueryStub();
    stub.withInProgressResponseFor(1);
    stub.registerWith(brokerClient);

    // when
    final var future = requestHandler.getStatus(1);

    // then
    assertThat(future)
        .succeedsWithin(Duration.ofMillis(500))
        .returns(State.IN_PROGRESS, BackupStatus::status);

    final var status = future.toCompletableFuture().join();
    assertThat(status.partitions())
        .hasSize(brokerClient.getTopologyManager().getTopology().getPartitionsCount());
  }

  @Test
  public void shouldReturnFailedStatusWhenOnePartitionIsFailed() {
    // given
    final BackupQueryStub stub = new BackupQueryStub();
    stub.withFailedResponseFor(1).withInProgressResponseFor(2);
    stub.registerWith(brokerClient);

    // when
    final var future = requestHandler.getStatus(1);

    // then
    assertThat(future)
        .succeedsWithin(Duration.ofMillis(500))
        .returns(State.FAILED, BackupStatus::status);

    final var status = future.toCompletableFuture().join();
    assertThat(status.failureReason().orElseThrow()).contains("FAILED");
    assertThat(status.partitions())
        .hasSize(brokerClient.getTopologyManager().getTopology().getPartitionsCount());
  }

  @Test
  public void shouldReturnIncompleteStatusWhenOnePartitionBackupDoesNotExist() {
    // given
    final BackupQueryStub stub = new BackupQueryStub();
    stub.withDoesNotExistFor(1).withInProgressResponseFor(2);
    stub.registerWith(brokerClient);

    // when
    final var future = requestHandler.getStatus(1);

    // then
    assertThat(future)
        .succeedsWithin(Duration.ofMillis(500))
        .returns(State.INCOMPLETE, BackupStatus::status);

    final var status = future.toCompletableFuture().join();
    assertThat(status.partitions())
        .hasSize(brokerClient.getTopologyManager().getTopology().getPartitionsCount());
  }

  @Test
  public void shouldFailWhenQueryToOnePartitionFails() {
    // given
    final BackupQueryStub stub = new BackupQueryStub();
    stub.withErrorResponseFor(1).withInProgressResponseFor(2);
    stub.registerWith(brokerClient);

    // when
    final var future = requestHandler.getStatus(1);

    // then
    assertThat(future)
        .failsWithin(Duration.ofMillis(500))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(BrokerErrorException.class);
  }

  @Test
  public void shouldListAllBackups() {
    // given
    brokerClient.registerHandler(
        BackupListRequest.class,
        request ->
            new BrokerResponse<>(
                new BackupListResponse(
                    List.of(
                        getCompletedBackup(1, request.getPartitionId()),
                        getCompletedBackup(2, request.getPartitionId())))));

    // when
    final var future = requestHandler.listBackups();

    // then
    assertThat(future).succeedsWithin(Duration.ofMillis(500));
    final var backups = future.toCompletableFuture().join();
    assertThat(backups)
        .hasSize(2)
        .extracting(BackupStatus::backupId)
        .containsExactlyInAnyOrder(1L, 2L);

    assertThat(backups)
        .extracting(BackupStatus::status)
        .containsExactly(State.COMPLETED, State.COMPLETED);
  }

  @Test
  public void shouldListReturnCompletedWhenDuplicateBackupIdForAPartition() {
    // given
    brokerClient.registerHandler(
        BackupListRequest.class,
        request ->
            new BrokerResponse<>(
                new BackupListResponse(
                    List.of(
                        getCompletedBackup(1, request.getPartitionId()),
                        getInProgressBackup(1, request.getPartitionId())))));

    // when
    final var future = requestHandler.listBackups();

    // then
    assertThat(future).succeedsWithin(Duration.ofMillis(500));
    final var backups = future.toCompletableFuture().join();
    assertThat(backups).hasSize(1).extracting(BackupStatus::backupId).containsExactlyInAnyOrder(1L);

    assertThat(backups).extracting(BackupStatus::status).containsExactly(State.COMPLETED);
  }

  @Test
  public void shouldListAllBackupsWhenIncomplete() {
    // given
    brokerClient.registerHandler(
        BackupListRequest.class,
        request -> {
          final List<BackupListResponse.BackupStatus> backups;
          if (request.getPartitionId() == 1) {
            backups = List.of(getCompletedBackup(2, request.getPartitionId()));
          } else {
            backups =
                List.of(
                    getCompletedBackup(1, request.getPartitionId()),
                    getCompletedBackup(2, request.getPartitionId()));
          }
          return new BrokerResponse<>(new BackupListResponse(backups));
        });

    // when
    final var future = requestHandler.listBackups();

    // then
    assertThat(future).succeedsWithin(Duration.ofMillis(500));
    final var backups = future.toCompletableFuture().join();
    assertThat(backups).hasSize(2).extracting(BackupStatus::backupId).containsExactly(2L, 1L);

    assertThat(backups)
        .extracting(BackupStatus::status)
        .containsExactly(State.COMPLETED, State.INCOMPLETE);
  }

  @Test
  public void shouldListAllBackupsWhenOneIsInProgress() {
    // given
    brokerClient.registerHandler(
        BackupListRequest.class,
        request -> {
          final List<BackupListResponse.BackupStatus> backups;
          if (request.getPartitionId() == 1) {
            backups = List.of(getCompletedBackup(1, 1), getInProgressBackup(2, 1));
          } else {
            backups =
                List.of(
                    getCompletedBackup(1, request.getPartitionId()),
                    getCompletedBackup(2, request.getPartitionId()));
          }
          return new BrokerResponse<>(new BackupListResponse(backups));
        });

    // when
    final var future = requestHandler.listBackups();

    // then
    assertThat(future).succeedsWithin(Duration.ofMillis(500));
    final var backups = future.toCompletableFuture().join();
    assertThat(backups).hasSize(2).extracting(BackupStatus::backupId).containsExactly(2L, 1L);

    assertThat(backups)
        .extracting(BackupStatus::status)
        .containsExactly(State.IN_PROGRESS, State.COMPLETED);
  }

  @Test
  public void shouldGetCheckpointState() {
    // given
    final var now = Instant.now();
    brokerClient.registerHandler(
        BrokerCheckpointStateRequest.class,
        request -> {
          final var response = new CheckpointStateResponse();
          response.setCheckpointStates(
              Set.of(
                  new CheckpointStateResponse.PartitionCheckpointState(
                      request.getPartitionId(),
                      1,
                      CheckpointType.MARKER,
                      now.minus(1, ChronoUnit.DAYS).toEpochMilli(),
                      1L)));
          return new BrokerResponse<>(response);
        });

    // when
    final var future = requestHandler.getCheckpointState();

    // then
    assertThat(future).succeedsWithin(Duration.ofMillis(500));
    final var state = future.toCompletableFuture().join();
    assertThat(state.getCheckpointStates())
        .extracting(CheckpointStateResponse.PartitionCheckpointState::partitionId)
        .containsExactlyInAnyOrderElementsOf(
            brokerClient.getTopologyManager().getTopology().getPartitions());
  }

  @Test
  public void shouldGetBackupRanges() {
    // given
    final var now = Instant.now();
    brokerClient.registerHandler(
        BrokerBackupRangesRequest.class,
        request -> {
          final var response = new BackupRangesResponse();
          response.setRanges(
              List.of(
                  new PartitionBackupRange(
                      request.getPartitionId(),
                      new CheckpointInfo(
                          1,
                          1L,
                          1L,
                          CheckpointType.SCHEDULED_BACKUP,
                          now.minus(1, ChronoUnit.DAYS)),
                      new CheckpointInfo(10, 100L, 150L, CheckpointType.SCHEDULED_BACKUP, now),
                      Set.of(5L))));
          return new BrokerResponse<>(response);
        });

    // when
    final var future = requestHandler.getBackupRanges();

    // then
    assertThat(future).succeedsWithin(Duration.ofMillis(500));
    final var ranges = future.toCompletableFuture().join().getRanges();
    assertThat(ranges)
        .extracting(PartitionBackupRange::partitionId)
        .containsExactlyInAnyOrderElementsOf(
            brokerClient.getTopologyManager().getTopology().getPartitions());
    assertThat(ranges).allSatisfy(range -> assertThat(range.first().checkpointId()).isEqualTo(1L));
  }

  @Test
  public void shouldSucceedWhenAllPartitionsDeleteBackup() {
    // given
    final int backupId = 1;
    brokerClient.registerHandler(
        BackupDeleteRequest.class,
        request ->
            new BrokerResponse<>(
                new BackupStatusResponse()
                    .setBackupId(backupId)
                    .setPartitionId(request.getPartitionId())));

    // when
    final var future = requestHandler.deleteBackup(backupId);

    // then
    assertThat(future).succeedsWithin(Duration.ofMillis(500));
  }

  @Test
  public void shouldFailWhenOnePartitionsFailsToDeleteBackup() {
    // given
    final int backupId = 1;
    brokerClient.registerHandler(
        BackupDeleteRequest.class,
        request -> {
          if (request.getPartitionId() == 1) {
            return new BrokerErrorResponse<>(new BrokerError(ErrorCode.INTERNAL_ERROR, "ERROR"));
          }
          return new BrokerResponse<>(
              new BackupStatusResponse()
                  .setBackupId(backupId)
                  .setPartitionId(request.getPartitionId()));
        });

    // when
    final var future = requestHandler.deleteBackup(backupId);

    // then
    assertThat(future)
        .failsWithin(Duration.ofMillis(500))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(BrokerErrorException.class);
  }

  private static BackupListResponse.BackupStatus getCompletedBackup(
      final int backupId, final int partitionId) {
    return new BackupListResponse.BackupStatus(
        backupId, partitionId, BackupStatusCode.COMPLETED, null, "test", "now");
  }

  private static BackupListResponse.BackupStatus getInProgressBackup(
      final int backupId, final int partitionId) {
    return new BackupListResponse.BackupStatus(
        backupId, partitionId, BackupStatusCode.IN_PROGRESS, null, "test", "now");
  }
}
