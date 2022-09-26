/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.admin.backup;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.gateway.cmd.BrokerErrorException;
import io.camunda.zeebe.protocol.management.BackupStatusCode;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;

public class BackupRequestHandlerTest extends GatewayTest {
  BackupRequestHandler requestHandler;

  @Before
  public void setup() {
    requestHandler = new BackupRequestHandler(brokerClient);
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
  public void shouldReturnCompleteStatusWhenAllPartitionsHaveCompleteBackup() {
    // given
    final BackupQueryStub stub = new BackupQueryStub();
    stub.registerWith(brokerClient);

    // when
    final var future = requestHandler.getStatus(1);

    // then
    assertThat(future)
        .succeedsWithin(Duration.ofMillis(500))
        .returns(BackupStatusCode.COMPLETED, BackupStatus::status);

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
        .returns(BackupStatusCode.IN_PROGRESS, BackupStatus::status);

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
        .returns(BackupStatusCode.FAILED, BackupStatus::status);

    final var status = future.toCompletableFuture().join();
    assertThat(status.failureReason().orElseThrow()).contains("FAILED");
    assertThat(status.partitions())
        .hasSize(brokerClient.getTopologyManager().getTopology().getPartitionsCount());
  }

  @Test
  public void shouldReturnDoesNotExistStatusWhenOnePartitionBackupDoesNotExist() {
    // given
    final BackupQueryStub stub = new BackupQueryStub();
    stub.withDoesNotExistFor(1).withInProgressResponseFor(2);
    stub.registerWith(brokerClient);

    // when
    final var future = requestHandler.getStatus(1);

    // then
    assertThat(future)
        .succeedsWithin(Duration.ofMillis(500))
        .returns(BackupStatusCode.DOES_NOT_EXIST, BackupStatus::status);

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
}
