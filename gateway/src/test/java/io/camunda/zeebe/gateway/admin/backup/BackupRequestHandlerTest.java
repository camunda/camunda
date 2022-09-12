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
        .withCauseInstanceOf(BackupOperationFailedException.class);
  }
}
