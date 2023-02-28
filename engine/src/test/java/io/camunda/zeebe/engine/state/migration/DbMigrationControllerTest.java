/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DbMigrationControllerTest {

  private ReadonlyStreamProcessorContext mockContext;
  private DbMigrator mockDbMigrator;

  private DbMigrationController sutMigrationController;

  @BeforeEach
  public void setUp() {
    mockContext = mock(ReadonlyStreamProcessorContext.class);
    mockDbMigrator = mock(DbMigrator.class);

    sutMigrationController =
        new DbMigrationController(
            mock(MutableProcessingState.class), processingState -> mockDbMigrator);
  }

  @Test
  public void shouldTriggerMigrationsWhenOnRecoveredEventIsReceived() {
    // when
    sutMigrationController.onRecovered(mockContext);

    // then
    verify(mockDbMigrator).runMigrations();
  }

  @Test
  public void shouldAbortMigratorWhenOnCloseEventIsReceived() {
    // given
    final var countdownLatch = new CountDownLatch(1);
    makeMigrationsMockWaitForCountDownLatch(countdownLatch);

    // when
    sendOnRecoveredInNewThreadAndWaitForMigrationToStart();
    sutMigrationController.onClose();
    countdownLatch.countDown();

    // then
    verify(mockDbMigrator).abort();
  }

  @Test
  public void shouldAbortMigratorWhenOnFailedEventIsReceived() {
    // given
    final var countdownLatch = new CountDownLatch(1);
    makeMigrationsMockWaitForCountDownLatch(countdownLatch);

    // when
    sendOnRecoveredInNewThreadAndWaitForMigrationToStart();
    sutMigrationController.onFailed();
    countdownLatch.countDown();

    // then
    verify(mockDbMigrator).abort();
  }

  private void sendOnRecoveredInNewThreadAndWaitForMigrationToStart() {
    new Thread(() -> sutMigrationController.onRecovered(mockContext)).start();
    await()
        .pollInterval(10, TimeUnit.MILLISECONDS)
        .untilAsserted(() -> verify(mockDbMigrator).runMigrations());
  }

  private void makeMigrationsMockWaitForCountDownLatch(final CountDownLatch countDownLatch) {
    doAnswer(
            invocationOnMock -> {
              countDownLatch.await();
              return null;
            })
        .when(mockDbMigrator)
        .runMigrations();
  }
}
