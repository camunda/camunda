/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.controllers;

import static io.camunda.management.backups.HistoryStateCode.COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.application.commons.backup.BackupServiceRegistry;
import io.camunda.application.commons.backup.BackupServiceRegistry.PhysicalTenantBackup;
import io.camunda.management.backups.HistoryBackupDetail;
import io.camunda.management.backups.HistoryBackupInfo;
import io.camunda.management.backups.HistoryBackupTenantInfo;
import io.camunda.management.backups.HistoryStateCode;
import io.camunda.management.backups.TakeBackupHistoryResponse;
import io.camunda.webapps.backup.BackupException.BackupRepositoryConnectionException;
import io.camunda.webapps.backup.BackupException.ResourceNotFoundException;
import io.camunda.webapps.backup.BackupService;
import io.camunda.webapps.backup.BackupStateDto;
import io.camunda.webapps.backup.GetBackupStateResponseDetailDto;
import io.camunda.webapps.backup.GetBackupStateResponseDto;
import io.camunda.webapps.backup.TakeBackupResponseDto;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith({SpringExtension.class, MockitoExtension.class})
@SpringBootTest(classes = {BackupEndpointStandalone.class})
public abstract sealed class BackupControllerTest {

  private static final OffsetDateTime START_TIME =
      OffsetDateTime.of(2024, 12, 1, 8, 29, 13, 0, ZoneOffset.UTC);

  private static final GetBackupStateResponseDetailDto DETAIL_DTO =
      new GetBackupStateResponseDetailDto()
          .setSnapshotName("snapshot-1")
          .setState("FAILED")
          .setFailures(new String[] {"Out of disk space"})
          .setStartTime(START_TIME);

  private static final HistoryBackupInfo EXPECTED_INFO;

  static {
    EXPECTED_INFO =
        new HistoryBackupInfo(
            new BigDecimal(1L),
            HistoryStateCode.FAILED,
            List.of(
                new HistoryBackupDetail()
                    .snapshotName("snapshot-1")
                    .state("FAILED")
                    .failures(Arrays.asList(DETAIL_DTO.getFailures()))
                    .startTime(START_TIME)));
    EXPECTED_INFO.setFailureReason(DETAIL_DTO.getFailures()[0]);
    final var expectedTenantInfo =
        new HistoryBackupTenantInfo(
            "default",
            HistoryStateCode.FAILED,
            List.of(
                new HistoryBackupDetail()
                    .snapshotName("snapshot-1")
                    .state("FAILED")
                    .failures(Arrays.asList(DETAIL_DTO.getFailures()))
                    .startTime(START_TIME)));
    expectedTenantInfo.setFailureReason(DETAIL_DTO.getFailures()[0]);
    EXPECTED_INFO.setPhysicalTenants(List.of(expectedTenantInfo));
  }

  @Mock private BackupService backupService;
  @Mock private BackupRepositoryProps backupProperties;
  private BackupController backupController;

  @BeforeEach
  public void setup() {
    backupController =
        new BackupController(
            new BackupServiceRegistry(
                List.of(new PhysicalTenantBackup("default", backupService, backupProperties))));
    lenient().when(backupProperties.repositoryName()).thenReturn("repo-1");
  }

  private BackupController twoTenantController(
      final BackupService physicalTenantService, final BackupRepositoryProps physicalTenantProps) {
    return new BackupController(
        new BackupServiceRegistry(
            List.of(
                new PhysicalTenantBackup("default", backupService, backupProperties),
                new PhysicalTenantBackup("tenant1", physicalTenantService, physicalTenantProps))));
  }

  private void mockErrorWith(final Exception e) {
    lenient().when(backupService.getBackupState(anyLong())).thenThrow(e);
    lenient().when(backupService.getBackups(anyBoolean(), any())).thenThrow(e);
    lenient().when(backupService.takeBackup(any())).thenThrow(e);
    lenient().doThrow(e).when(backupService).deleteBackup(any());
  }

  private void mockResourceNotFound() {
    mockErrorWith(new ResourceNotFoundException("not found"));
  }

  private void mockESConnectionError() {
    mockErrorWith(new BackupRepositoryConnectionException("not found"));
  }

  private void mockGenericException() {
    mockErrorWith(new RuntimeException("generic error"));
  }

  private void mockRepositoryNotSet() {
    when(backupProperties.repositoryName()).thenReturn("");
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
                .setDetails(List.of(DETAIL_DTO)));
    final var response = backupController.getBackupState(1L);
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getBody()).isEqualTo(EXPECTED_INFO);
  }

  @Test
  public void shouldGetBackups() {
    when(backupService.getBackups(anyBoolean(), any()))
        .thenReturn(
            List.of(
                new GetBackupStateResponseDto()
                    .setBackupId(1L)
                    .setState(BackupStateDto.FAILED)
                    .setFailureReason("Out of disk space")
                    .setDetails(List.of(DETAIL_DTO))));
    final var response = backupController.getBackups(true, null);
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getBody()).isEqualTo(List.of(EXPECTED_INFO));
  }

  @Test
  public void shouldGetBackupsWithNullParameters() {
    when(backupService.getBackups(true, "*"))
        .thenReturn(
            List.of(
                new GetBackupStateResponseDto()
                    .setBackupId(1L)
                    .setState(BackupStateDto.FAILED)
                    .setFailureReason("Out of disk space")
                    .setDetails(List.of(DETAIL_DTO))));
    final var response = backupController.getBackups(null, null);
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getBody()).isEqualTo(List.of(EXPECTED_INFO));
  }

  @Test
  public void shouldReportWorstStateAcrossTenants() {
    final BackupService physicalTenantService = mock(BackupService.class);
    final BackupRepositoryProps physicalTenantProps = mock(BackupRepositoryProps.class);
    lenient().when(physicalTenantProps.repositoryName()).thenReturn("repo-2");
    final var controller = twoTenantController(physicalTenantService, physicalTenantProps);
    when(backupService.getBackupState(1L))
        .thenReturn(
            new GetBackupStateResponseDto().setBackupId(1L).setState(BackupStateDto.COMPLETED));
    when(physicalTenantService.getBackupState(1L))
        .thenReturn(
            new GetBackupStateResponseDto().setBackupId(1L).setState(BackupStateDto.IN_PROGRESS));

    final var response = controller.getBackupState(1L);

    assertThat(response.getStatus()).isEqualTo(200);
    final var info = (HistoryBackupInfo) response.getBody();
    assertThat(info.getState()).isEqualTo(HistoryStateCode.IN_PROGRESS);
    assertThat(info.getPhysicalTenants())
        .extracting(HistoryBackupTenantInfo::getPhysicalTenantId, HistoryBackupTenantInfo::getState)
        .containsExactlyInAnyOrder(
            tuple("default", HistoryStateCode.COMPLETED),
            tuple("tenant1", HistoryStateCode.IN_PROGRESS));
  }

  @Test
  public void shouldReportCompletedWhenAllTenantsCompleted() {
    final BackupService physicalTenantService = mock(BackupService.class);
    final BackupRepositoryProps physicalTenantProps = mock(BackupRepositoryProps.class);
    lenient().when(physicalTenantProps.repositoryName()).thenReturn("repo-2");
    final var controller = twoTenantController(physicalTenantService, physicalTenantProps);
    when(backupService.getBackupState(1L))
        .thenReturn(
            new GetBackupStateResponseDto().setBackupId(1L).setState(BackupStateDto.COMPLETED));
    when(physicalTenantService.getBackupState(1L))
        .thenReturn(
            new GetBackupStateResponseDto().setBackupId(1L).setState(BackupStateDto.COMPLETED));

    final var response = controller.getBackupState(1L);

    assertThat(((HistoryBackupInfo) response.getBody()).getState())
        .isEqualTo(HistoryStateCode.COMPLETED);
  }

  @Test
  public void shouldFanOutTakeAndDeleteAcrossTenants() {
    final BackupService physicalTenantService = mock(BackupService.class);
    final BackupRepositoryProps physicalTenantProps = mock(BackupRepositoryProps.class);
    lenient().when(physicalTenantProps.repositoryName()).thenReturn("repo-2");
    final var controller = twoTenantController(physicalTenantService, physicalTenantProps);
    when(backupService.takeBackup(any()))
        .thenReturn(new TakeBackupResponseDto().setScheduledSnapshots(List.of("snapshot-1")));
    when(physicalTenantService.takeBackup(any()))
        .thenReturn(new TakeBackupResponseDto().setScheduledSnapshots(List.of("snapshot-2")));

    final var takeResponse = controller.takeBackup(1L);
    final var deleteResponse = controller.deleteBackup(1L);

    assertThat(takeResponse.getBody())
        .isEqualTo(
            new TakeBackupHistoryResponse()
                .scheduledSnapshots(List.of("snapshot-1", "snapshot-2")));
    assertThat(deleteResponse.getStatus()).isEqualTo(204);
    verify(backupService).deleteBackup(1L);
    verify(physicalTenantService).deleteBackup(1L);
  }

  @Test
  public void shouldMergeBackupListsByBackupId() {
    final BackupService physicalTenantService = mock(BackupService.class);
    final BackupRepositoryProps physicalTenantProps = mock(BackupRepositoryProps.class);
    lenient().when(physicalTenantProps.repositoryName()).thenReturn("repo-2");
    final var controller = twoTenantController(physicalTenantService, physicalTenantProps);
    when(backupService.getBackups(anyBoolean(), any()))
        .thenReturn(
            List.of(
                new GetBackupStateResponseDto()
                    .setBackupId(1L)
                    .setState(BackupStateDto.COMPLETED)));
    when(physicalTenantService.getBackups(anyBoolean(), any()))
        .thenReturn(
            List.of(
                new GetBackupStateResponseDto()
                    .setBackupId(1L)
                    .setState(BackupStateDto.IN_PROGRESS),
                new GetBackupStateResponseDto()
                    .setBackupId(2L)
                    .setState(BackupStateDto.COMPLETED)));

    final var response = controller.getBackups(true, null);

    @SuppressWarnings("unchecked")
    final var infos = (List<HistoryBackupInfo>) response.getBody();
    assertThat(infos.size()).isEqualTo(2);
    assertThat(infos.get(0).getState()).isEqualTo(HistoryStateCode.IN_PROGRESS);
    assertThat(infos.get(1).getState()).isEqualTo(HistoryStateCode.COMPLETED);
  }

  @EnumSource(BackupStateDto.class)
  @ParameterizedTest
  public void testEnumConversion(final BackupStateDto backupStateDto) {
    final var mapped = BackupController.mapState(backupStateDto);
    if (backupStateDto != BackupStateDto.COMPLETED) {
      assertThat(mapped.name()).isEqualTo(backupStateDto.name());
    } else {
      assertThat(mapped).isEqualTo(COMPLETED);
    }
  }

  @ActiveProfiles("operate")
  public static final class BackupControllerOperateTest extends BackupControllerTest {}

  @ActiveProfiles("tasklist")
  public static final class BackupControllerTasklistTest extends BackupControllerTest {}

  @Nested
  class ESConnectionError extends ErrorTest {
    ESConnectionError() {
      super(502, BackupControllerTest.this::mockESConnectionError);
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

  @Nested
  class RepositoryNotSet extends ErrorTest {
    RepositoryNotSet() {
      super(400, BackupControllerTest.this::mockRepositoryNotSet);
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
      final var response = backupController.getBackups(true, null);
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
    public void shouldReturnCorrectStatusOnDelete() {
      // given
      setupMocks.run();
      // when
      final var response = backupController.takeBackup(11L);
      // then
      assertThat(response.getStatus()).isEqualTo(errorCode);
      if (errorCode == 200) {
        assertThat(response.getBody()).isEqualTo(null);
      }
    }
  }
}
