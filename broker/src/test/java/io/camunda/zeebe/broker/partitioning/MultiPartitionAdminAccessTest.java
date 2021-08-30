/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning;

import static java.util.List.of;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.util.sched.TestConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MultiPartitionAdminAccessTest {

  private PartitionAdminAccess mockAdminAccess1;
  private PartitionAdminAccess mockAdminAccess2;

  private MultiPartitionAdminAccess sutMultiPartitionAdminAccess;

  private ActorFuture<Void> actorFuture1;
  private ActorFuture<Void> actorFuture2;

  @BeforeEach
  void setUp() {
    final var concurrencyControl = new TestConcurrencyControl();

    actorFuture1 = concurrencyControl.createFuture();
    actorFuture2 = concurrencyControl.createFuture();

    mockAdminAccess1 = mock(PartitionAdminAccess.class);
    mockAdminAccess2 = mock(PartitionAdminAccess.class);

    sutMultiPartitionAdminAccess =
        new MultiPartitionAdminAccess(concurrencyControl, of(mockAdminAccess1, mockAdminAccess2));
  }

  @Test
  void shouldCallPauseExportingOnAllPartitions() {
    // given
    when(mockAdminAccess1.pauseExporting()).thenReturn(actorFuture1);
    when(mockAdminAccess2.pauseExporting()).thenReturn(actorFuture2);

    // when
    sutMultiPartitionAdminAccess.pauseExporting();

    // then
    verify(mockAdminAccess1).pauseExporting();
    verify(mockAdminAccess2).pauseExporting();

    verifyNoMoreInteractions(mockAdminAccess1, mockAdminAccess2);
  }

  @Test
  void shouldCallResumeExportingOnAllPartitions() {
    // given
    when(mockAdminAccess1.resumeExporting()).thenReturn(actorFuture1);
    when(mockAdminAccess2.resumeExporting()).thenReturn(actorFuture2);

    // when
    sutMultiPartitionAdminAccess.resumeExporting();

    // then
    verify(mockAdminAccess1).resumeExporting();
    verify(mockAdminAccess2).resumeExporting();

    verifyNoMoreInteractions(mockAdminAccess1, mockAdminAccess2);
  }

  @Test
  void shouldCallPauseProcessingOnAllPartitions() {
    // given
    when(mockAdminAccess1.pauseProcessing()).thenReturn(actorFuture1);
    when(mockAdminAccess2.pauseProcessing()).thenReturn(actorFuture2);

    // when
    sutMultiPartitionAdminAccess.pauseProcessing();

    // then
    verify(mockAdminAccess1).pauseProcessing();
    verify(mockAdminAccess2).pauseProcessing();

    verifyNoMoreInteractions(mockAdminAccess1, mockAdminAccess2);
  }

  @Test
  void shouldCallResumeProcessingOnAllPartitions() {
    // given
    when(mockAdminAccess1.resumeProcessing()).thenReturn(actorFuture1);
    when(mockAdminAccess2.resumeProcessing()).thenReturn(actorFuture2);

    // when
    sutMultiPartitionAdminAccess.resumeProcessing();

    // then
    verify(mockAdminAccess1).resumeProcessing();
    verify(mockAdminAccess2).resumeProcessing();

    verifyNoMoreInteractions(mockAdminAccess1, mockAdminAccess2);
  }

  @Test
  void shouldCallTakeSnapshotOnAllPartitions() {
    // given
    when(mockAdminAccess1.takeSnapshot()).thenReturn(actorFuture1);
    when(mockAdminAccess2.takeSnapshot()).thenReturn(actorFuture2);

    // when
    sutMultiPartitionAdminAccess.takeSnapshot();

    // then
    verify(mockAdminAccess1).takeSnapshot();
    verify(mockAdminAccess2).takeSnapshot();

    verifyNoMoreInteractions(mockAdminAccess1, mockAdminAccess2);
  }
}
