/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.controllers;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.management.backups.HistoryBackupDetail;
import io.camunda.management.backups.HistoryBackupInfo;
import io.camunda.management.backups.HistoryStateCode;
import io.camunda.management.backups.TakeBackupHistoryResponse;
import io.camunda.operate.exceptions.OperateElasticsearchConnectionException;
import io.camunda.operate.exceptions.OperateOpensearchConnectionException;
import io.camunda.operate.property.BackupProperties;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.webapps.backup.BackupService;
import io.camunda.webapps.backup.BackupStateDto;
import io.camunda.webapps.backup.GetBackupStateResponseDetailDto;
import io.camunda.webapps.backup.GetBackupStateResponseDto;
import io.camunda.webapps.backup.TakeBackupResponseDto;
import io.camunda.zeebe.shared.management.BackupEndpointStandalone;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {BackupEndpointStandalone.class})
public abstract class BackupControllerTest {

  private static final OffsetDateTime startTime =
      OffsetDateTime.of(2024, 12, 1, 8, 29, 13, 0, ZoneOffset.UTC);
  private static final GetBackupStateResponseDetailDto detailDTO =
      new GetBackupStateResponseDetailDto()
          .setSnapshotName("snapshot-1")
          .setState("FAILED")
          .setFailures(new String[] {"Out of disk space"})
          .setStartTime(startTime);
  private static final HistoryBackupInfo expectedInfo =
      new HistoryBackupInfo(
          new BigDecimal(1L),
          HistoryStateCode.FAILED,
          List.of(
              new HistoryBackupDetail()
                  .snapshotName("snapshot-1")
                  .state(HistoryStateCode.FAILED)
                  .failures(Arrays.asList(detailDTO.getFailures()))
                  .startTime(startTime)));

  @Mock private BackupService backupService;
  @Mock private BackupProperties backupProperties;
  @InjectMocks private BackupController backupController;

  @BeforeEach
  public void setup() {
    when(backupProperties.getRepositoryName()).thenReturn("repo-1");
  }

  private void mockErrorWith(final Exception e) {
    when(backupService.getBackupState(anyLong())).thenThrow(e);
    when(backupService.getBackups()).thenThrow(e);
    when(backupService.takeBackup(any())).thenThrow(e);
    doThrow(e).when(backupService).deleteBackup(any());
  }

  private void mockResourceNotFound() {
    mockErrorWith(new ResourceNotFoundException("not found"));
  }

  private void mockESConnectionError() {
    mockErrorWith(new OperateElasticsearchConnectionException("not found"));
  }

  private void mockOSConnectionError() {
    mockErrorWith(new OperateOpensearchConnectionException("not found"));
  }

  private void mockGenericException() {
    mockErrorWith(new RuntimeException("generic error"));
  }

  @Test
  public void shouldReturnBadRequestOnInvalidBackupId() {
    assertThat(backupController.takeBackup(-1L).getStatus()).isEqualTo(400);
    assertThat(backupController.takeBackup(0L).getStatus()).isEqualTo(400);
    assertThat(backupController.deleteBackup(-1L).getStatus()).isEqualTo(400);
    assertThat(backupController.deleteBackup(0L).getStatus()).isEqualTo(400);
  }

  @Test
  public void shouldTakeBackup() {
    final var backups = List.of("snapshot-1");
    when(backupService.takeBackup(any()))
        .thenReturn(new TakeBackupResponseDto().setScheduledSnapshots(backups));
    final var response = backupController.takeBackup(1L);
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getBody())
        .isEqualTo(new TakeBackupHistoryResponse().scheduledSnapshots(backups));
    verify(backupService).takeBackup(argThat(r -> r.getBackupId() == 1L));
  }

  @Test
  public void shouldDeleteBackup() {
    final var response = backupController.deleteBackup(1L);
    assertThat(response.getStatus()).isEqualTo(204);
    verify(backupService).deleteBackup(eq(1L));
  }

  @Test
  public void shouldGetBackup() {
    when(backupService.getBackupState(eq(1L)))
        .thenReturn(
            new GetBackupStateResponseDto()
                .setBackupId(1L)
                .setState(BackupStateDto.FAILED)
                .setFailureReason("Out of disk space")
                .setDetails(List.of(detailDTO)));
    final var response = backupController.getBackupState(1L);
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getBody()).isEqualTo(expectedInfo);
  }

  @Test
  public void shouldGetBackups() {
    when(backupService.getBackups())
        .thenReturn(
            List.of(
                new GetBackupStateResponseDto()
                    .setBackupId(1L)
                    .setState(BackupStateDto.FAILED)
                    .setDetails(List.of(detailDTO))));
    final var response = backupController.getBackups();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getBody()).isEqualTo(List.of(expectedInfo));
  }

  @EnumSource(BackupStateDto.class)
  @ParameterizedTest
  public void testEnumConversion(final BackupStateDto backupStateDto) {
    final var mapped = BackupController.mapTo(backupStateDto);
    if (backupStateDto != BackupStateDto.COMPLETED) {
      assertThat(mapped.name()).isEqualTo(backupStateDto.name());
    } else {
      assertThat(mapped).isEqualTo(HistoryStateCode.SUCCESS);
    }
  }

  @Nested
  class ESConnectionError extends ErrorTest {
    ESConnectionError() {
      super(502, BackupControllerTest.this::mockESConnectionError);
    }
  }

  @Nested
  class OSConnectionError extends ErrorTest {
    OSConnectionError() {
      super(502, BackupControllerTest.this::mockOSConnectionError);
    }
  }

  @Nested
  class NotFoundError extends ErrorTest {
    NotFoundError() {
      super(404, BackupControllerTest.this::mockResourceNotFound);
    }
  }

  @Nested
  class GenericError extends ErrorTest {
    GenericError() {
      super(500, BackupControllerTest.this::mockGenericException);
    }
  }

  abstract class ErrorTest {
    final int errorCode;
    private final Runnable setupMocks;

    ErrorTest(final int errorCode, final Runnable setupMocks) {
      this.errorCode = errorCode;
      this.setupMocks = setupMocks;
    }

    @Test
    public void shouldReturnCorrectStatusOnList() {
      // given
      setupMocks.run();
      // when
      final var response = backupController.getBackups();
      // then
      assertThat(response.getStatus()).isEqualTo(errorCode);
    }

    @Test
    public void shouldReturnCorrectStatusOnTake() {
      // given
      setupMocks.run();
      // when
      final var response = backupController.takeBackup(11L);
      // then
      assertThat(response.getStatus()).isEqualTo(errorCode);
    }

    @Test
    public void shouldReturnCorrectStatusOnGet() {
      // given
      setupMocks.run();

      // when
      final var response = backupController.getBackupState(1L);
      // then
      assertThat(response.getStatus()).isEqualTo(errorCode);
    }

    @Test
    public void shouldReturnCorrecStatusnDelete() {
      // given
      setupMocks.run();
      // when
      final var response = backupController.takeBackup(11L);
      // then
      assertThat(response.getStatus()).isEqualTo(errorCode);
    }
  }
}
