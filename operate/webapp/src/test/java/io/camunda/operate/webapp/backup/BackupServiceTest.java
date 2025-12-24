/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.property.BackupProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.management.dto.TakeBackupRequestDto;
import io.camunda.operate.webapp.management.dto.TakeBackupResponseDto;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@ExtendWith(MockitoExtension.class)
public class BackupServiceTest {

  private BackupService backupService;

  @Mock private ThreadPoolTaskExecutor threadPoolTaskExecutor;

  @Mock private BackupRepository backupRepository;

  @Mock private OperateProperties operateProperties;

  @Mock private BackupProperties backupProperties;

  @BeforeEach
  void setup() {
    when(operateProperties.getBackup()).thenReturn(backupProperties);
    when(backupProperties.getRepositoryName()).thenReturn("test-repo");
    when(operateProperties.getVersion()).thenReturn("8.7.0");

    backupService =
        new BackupService(
            threadPoolTaskExecutor,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            operateProperties,
            backupRepository);
  }

  @Test
  void shouldClearCacheWhenExecutorRejectsTask() {
    // given
    final TakeBackupRequestDto request = new TakeBackupRequestDto().setBackupId(123L);

    when(threadPoolTaskExecutor.submit(any(Runnable.class)))
        .thenThrow(new RejectedExecutionException("Task rejected"));

    // when
    final TakeBackupResponseDto response = backupService.takeBackup(request);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getScheduledSnapshots()).hasSize(6);

    final TakeBackupRequestDto secondRequest = new TakeBackupRequestDto().setBackupId(456L);
    when(threadPoolTaskExecutor.submit(any(Runnable.class))).thenReturn(null);

    final TakeBackupResponseDto secondResponse = backupService.takeBackup(secondRequest);
    assertThat(secondResponse).isNotNull();
  }

  @Test
  void shouldClearCacheWhenSnapshotExecutionThrowsException() {
    // given
    final TakeBackupRequestDto request = new TakeBackupRequestDto().setBackupId(789L);

    doAnswer(
            invocation -> {
              final Runnable task = invocation.getArgument(0);
              task.run();
              return null;
            })
        .when(threadPoolTaskExecutor)
        .submit(any(Runnable.class));

    doThrow(new RuntimeException("Snapshot execution failed"))
        .when(backupRepository)
        .executeSnapshotting(any(), any(), any());

    // when
    final TakeBackupResponseDto response = backupService.takeBackup(request);

    // then
    assertThat(response).isNotNull();

    Awaitility.await("cache cleared after exception")
        .untilAsserted(
            () -> {
              final TakeBackupRequestDto secondRequest =
                  new TakeBackupRequestDto().setBackupId(890L);
              final TakeBackupResponseDto secondResponse = backupService.takeBackup(secondRequest);
              assertThat(secondResponse).isNotNull();
            });
  }

  @Test
  void shouldClearCacheWhenOnFailureCallbackIsInvoked() {
    // given
    final TakeBackupRequestDto request = new TakeBackupRequestDto().setBackupId(111L);

    doAnswer(
            invocation -> {
              final Runnable task = invocation.getArgument(0);
              task.run();
              return null;
            })
        .when(threadPoolTaskExecutor)
        .submit(any(Runnable.class));

    doAnswer(
            invocation -> {
              final Runnable onFailure = invocation.getArgument(2);
              onFailure.run();
              return null;
            })
        .when(backupRepository)
        .executeSnapshotting(any(), any(), any());

    // when
    final TakeBackupResponseDto response = backupService.takeBackup(request);

    // then
    assertThat(response).isNotNull();

    Awaitility.await("cache cleared after failure callback")
        .untilAsserted(
            () -> {
              final TakeBackupRequestDto secondRequest =
                  new TakeBackupRequestDto().setBackupId(222L);
              final TakeBackupResponseDto secondResponse = backupService.takeBackup(secondRequest);
              assertThat(secondResponse).isNotNull();
            });
  }

  @Test
  void shouldHandleSuccessfulBackupSequence() {
    // given
    final TakeBackupRequestDto request = new TakeBackupRequestDto().setBackupId(333L);

    final int[] snapshotCount = {0};

    doAnswer(
            invocation -> {
              final Runnable task = invocation.getArgument(0);
              task.run();
              return null;
            })
        .when(threadPoolTaskExecutor)
        .submit(any(Runnable.class));

    doAnswer(
            invocation -> {
              final Runnable onSuccess = invocation.getArgument(1);
              snapshotCount[0]++;
              onSuccess.run();
              return null;
            })
        .when(backupRepository)
        .executeSnapshotting(any(), any(), any());

    // when
    final TakeBackupResponseDto response = backupService.takeBackup(request);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getScheduledSnapshots()).hasSize(6);

    Awaitility.await("all snapshots processed")
        .untilAsserted(
            () -> {
              verify(backupRepository, times(6)).executeSnapshotting(any(), any(), any());
            });

    final TakeBackupRequestDto secondRequest = new TakeBackupRequestDto().setBackupId(444L);
    final TakeBackupResponseDto secondResponse = backupService.takeBackup(secondRequest);
    assertThat(secondResponse).isNotNull();
  }
}
