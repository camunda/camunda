/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.application.commons.backup.BackupServiceRegistry.PhysicalTenantBackup;
import io.camunda.service.backup.HistoryBackupStateCode;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.webapps.backup.BackupException;
import io.camunda.webapps.backup.BackupException.BackupAlreadyRunningException;
import io.camunda.webapps.backup.BackupException.BackupRepositoryConnectionException;
import io.camunda.webapps.backup.BackupException.DuplicateBackupIdException;
import io.camunda.webapps.backup.BackupException.IndexNotFoundException;
import io.camunda.webapps.backup.BackupException.InvalidRequestException;
import io.camunda.webapps.backup.BackupException.MissingRepositoryException;
import io.camunda.webapps.backup.BackupException.ResourceNotFoundException;
import io.camunda.webapps.backup.BackupService;
import io.camunda.webapps.backup.BackupStateDto;
import io.camunda.webapps.backup.GetBackupStateResponseDetailDto;
import io.camunda.webapps.backup.GetBackupStateResponseDto;
import io.camunda.webapps.backup.TakeBackupRequestDto;
import io.camunda.webapps.backup.TakeBackupResponseDto;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

class RegistryHistoryBackupApiTest {

  private static final String TENANT_ID = "tenanta";
  private static final OffsetDateTime START_TIME =
      OffsetDateTime.of(2026, 8, 11, 9, 30, 0, 0, ZoneOffset.UTC);

  private BackupService backupService;
  private BackupRepositoryProps repositoryProps;
  private RegistryHistoryBackupApi api;

  @BeforeEach
  void setUp() {
    backupService = mock(BackupService.class);
    repositoryProps = mock(BackupRepositoryProps.class);
    when(repositoryProps.repositoryName()).thenReturn("repo");
    api = apiFor(new PhysicalTenantBackup(TENANT_ID, backupService, repositoryProps));
  }

  @Test
  void shouldEchoTheRequestedBackupIdAlongsideTheScheduledSnapshots() {
    // given the take-backup response carries only snapshot names
    when(backupService.takeBackup(any()))
        .thenReturn(new TakeBackupResponseDto().setScheduledSnapshots(List.of("part-1", "part-2")));

    // when
    final var taken = api.takeBackup(TENANT_ID, 42L);

    // then
    assertThat(taken.backupId()).isEqualTo(42L);
    assertThat(taken.scheduledSnapshots()).containsExactly("part-1", "part-2");
  }

  @Test
  void shouldNotSkipTheSchemaCheckWhenTakingABackup() {
    // given
    when(backupService.takeBackup(any())).thenReturn(new TakeBackupResponseDto());

    // when
    api.takeBackup(TENANT_ID, 42L);

    // then
    final var request = captureTakeBackupRequest();
    assertThat(request.getBackupId()).isEqualTo(42L);
    assertThat(request.isSkipSchemaCheck()).isFalse();
  }

  @Test
  void shouldMapBackupStateAndSnapshotDetails() {
    // given
    when(backupService.getBackupState(42L))
        .thenReturn(
            new GetBackupStateResponseDto(42L)
                .setState(BackupStateDto.FAILED)
                .setFailureReason("out of disk space")
                .setDetails(
                    List.of(
                        new GetBackupStateResponseDetailDto()
                            .setSnapshotName("part-1")
                            .setState("PARTIAL")
                            .setStartTime(START_TIME)
                            .setFailures(new String[] {"shard 0 failed"}))));

    // when
    final var state = api.getBackupState(TENANT_ID, 42L);

    // then
    assertThat(state.backupId()).isEqualTo(42L);
    assertThat(state.state()).isEqualTo(HistoryBackupStateCode.FAILED);
    assertThat(state.failureReason()).isEqualTo("out of disk space");
    assertThat(state.snapshots())
        .singleElement()
        .satisfies(
            snapshot -> {
              assertThat(snapshot.snapshotName()).isEqualTo("part-1");
              assertThat(snapshot.state()).isEqualTo("PARTIAL");
              assertThat(snapshot.startTime()).isEqualTo(START_TIME);
              assertThat(snapshot.failures()).containsExactly("shard 0 failed");
            });
  }

  /**
   * Absent detail is normal when a backup is read without snapshot detail, so it must map to empty
   * collections rather than nulls the response mapper would have to defend against.
   */
  @Test
  void shouldMapAbsentDetailsAndFailuresToEmptyLists() {
    // given
    when(backupService.getBackupState(42L))
        .thenReturn(
            new GetBackupStateResponseDto(42L)
                .setState(BackupStateDto.COMPLETED)
                .setDetails(
                    List.of(new GetBackupStateResponseDetailDto().setSnapshotName("part-1"))));

    // when
    final var state = api.getBackupState(TENANT_ID, 42L);

    // then
    assertThat(state.snapshots())
        .singleElement()
        .satisfies(s -> assertThat(s.failures()).isEmpty());
  }

  @Test
  void shouldMapEveryBackupStateCode() {
    for (final BackupStateDto dto : BackupStateDto.values()) {
      // given
      when(backupService.getBackupState(1L))
          .thenReturn(new GetBackupStateResponseDto(1L).setState(dto).setDetails(List.of()));

      // when / then
      assertThat(api.getBackupState(TENANT_ID, 1L).state())
          .isEqualTo(HistoryBackupStateCode.valueOf(dto.name()));
    }
  }

  @Test
  void shouldListBackupsWithTheRequestedVerbosityAndPrefix() {
    // given
    when(backupService.getBackups(false, "17*"))
        .thenReturn(
            List.of(
                new GetBackupStateResponseDto(17L)
                    .setState(BackupStateDto.COMPLETED)
                    .setDetails(List.of())));

    // when
    final var states = api.getBackups(TENANT_ID, false, "17*");

    // then
    assertThat(states).singleElement().satisfies(s -> assertThat(s.backupId()).isEqualTo(17L));
    verify(backupService).getBackups(false, "17*");
  }

  @Test
  void shouldDeleteBackup() {
    // when
    api.deleteBackup(TENANT_ID, 42L);

    // then
    verify(backupService).deleteBackup(42L);
  }

  @Test
  void shouldReportAnUnknownBackupAsNotFound() {
    // given
    when(backupService.getBackupState(42L)).thenThrow(new ResourceNotFoundException("no such id"));

    // when / then
    assertThatExceptionOfType(ServiceException.class)
        .isThrownBy(() -> api.getBackupState(TENANT_ID, 42L))
        .satisfies(e -> assertThat(e.getStatus()).isEqualTo(Status.NOT_FOUND));
  }

  /**
   * Every operation, not just one: {@code BackupServiceImpl.getBackups} does not call {@code
   * validateRepositoryExists} the way take and delete do, so this pre-check is the only guard on
   * the list path.
   *
   * <p>Answers 403, like a repository the store does not have: an unconfigured repository says this
   * deployment cannot serve history backups for the tenant, which is what the endpoint's
   * secondary-storage gate already answers 403 for one level up.
   */
  @ParameterizedTest
  @MethodSource("allOperations")
  void shouldRejectATenantWithoutAConfiguredRepository(
      final String name, final Consumer<RegistryHistoryBackupApi> operation) {
    // given
    when(repositoryProps.repositoryName()).thenReturn("  ");

    // when / then
    assertThatExceptionOfType(ServiceException.class)
        .isThrownBy(() -> operation.accept(api))
        .satisfies(
            e -> {
              assertThat(e.getStatus()).isEqualTo(Status.FORBIDDEN);
              assertThat(e.getMessage()).contains(TENANT_ID);
            });
  }

  private static Stream<Arguments> allOperations() {
    return Stream.of(
        Arguments.of(
            "take", (Consumer<RegistryHistoryBackupApi>) a -> a.takeBackup(TENANT_ID, 42L)),
        Arguments.of(
            "get", (Consumer<RegistryHistoryBackupApi>) a -> a.getBackupState(TENANT_ID, 42L)),
        Arguments.of(
            "list", (Consumer<RegistryHistoryBackupApi>) a -> a.getBackups(TENANT_ID, true, "*")),
        Arguments.of(
            "delete", (Consumer<RegistryHistoryBackupApi>) a -> a.deleteBackup(TENANT_ID, 42L)));
  }

  @Test
  void shouldReportAnUnknownPhysicalTenantAsNotFound() {
    // when / then
    assertThatExceptionOfType(ServiceException.class)
        .isThrownBy(() -> api.getBackupState("no-such-tenant", 42L))
        .satisfies(e -> assertThat(e.getStatus()).isEqualTo(Status.NOT_FOUND));
  }

  /**
   * Asserts the exception-to-status mapping only. {@code BackupAlreadyRunningException} in
   * particular says nothing about concurrency: the check behind it is node-local and best-effort.
   */
  @ParameterizedTest
  @MethodSource("backupExceptions")
  void shouldMapBackupExceptionsToTheirServiceStatus(
      final BackupException exception, final Status expectedStatus) {
    // given
    when(backupService.takeBackup(any())).thenThrow(exception);

    // when / then
    assertThatExceptionOfType(ServiceException.class)
        .isThrownBy(() -> api.takeBackup(TENANT_ID, 42L))
        .satisfies(e -> assertThat(e.getStatus()).isEqualTo(expectedStatus));
  }

  private static Stream<Arguments> backupExceptions() {
    return Stream.of(
        Arguments.of(new DuplicateBackupIdException("duplicate"), Status.ALREADY_EXISTS),
        Arguments.of(new BackupAlreadyRunningException("running"), Status.INVALID_STATE),
        Arguments.of(new InvalidRequestException("invalid"), Status.INVALID_ARGUMENT),
        Arguments.of(new ResourceNotFoundException("missing"), Status.NOT_FOUND),
        Arguments.of(new MissingRepositoryException("no repo"), Status.FORBIDDEN),
        Arguments.of(new BackupRepositoryConnectionException("unreachable"), Status.UNAVAILABLE),
        Arguments.of(new IndexNotFoundException(List.of("index-1")), Status.INTERNAL),
        Arguments.of(new BackupException("something else"), Status.INTERNAL));
  }

  @Test
  void shouldMapBackupExceptionsRaisedByDelete() {
    // given delete returns void, so it needs its own stubbing route
    doThrow(new BackupRepositoryConnectionException("unreachable"))
        .when(backupService)
        .deleteBackup(anyLong());

    // when / then
    assertThatExceptionOfType(ServiceException.class)
        .isThrownBy(() -> api.deleteBackup(TENANT_ID, 42L))
        .satisfies(e -> assertThat(e.getStatus()).isEqualTo(Status.UNAVAILABLE));
  }

  @Test
  void shouldMapBackupExceptionsRaisedByList() {
    // given
    when(backupService.getBackups(anyBoolean(), any()))
        .thenThrow(new MissingRepositoryException("no repo"));

    // when / then
    assertThatExceptionOfType(ServiceException.class)
        .isThrownBy(() -> api.getBackups(TENANT_ID, true, "*"))
        .satisfies(e -> assertThat(e.getStatus()).isEqualTo(Status.FORBIDDEN));
  }

  /**
   * The two must not collapse onto one status: the cluster-wide endpoints read {@code NOT_FOUND} as
   * "this physical tenant was reached and holds nothing", so a repository missing from the store
   * has to be distinguishable from a backup that simply does not exist.
   */
  @Test
  void shouldDistinguishAMissingRepositoryFromAMissingBackup() {
    // given
    when(backupService.getBackupState(42L)).thenThrow(new MissingRepositoryException("no repo"));
    when(backupService.getBackupState(43L)).thenThrow(new ResourceNotFoundException("no such id"));

    // when / then
    assertThatExceptionOfType(ServiceException.class)
        .isThrownBy(() -> api.getBackupState(TENANT_ID, 42L))
        .satisfies(e -> assertThat(e.getStatus()).isEqualTo(Status.FORBIDDEN));
    assertThatExceptionOfType(ServiceException.class)
        .isThrownBy(() -> api.getBackupState(TENANT_ID, 43L))
        .satisfies(e -> assertThat(e.getStatus()).isEqualTo(Status.NOT_FOUND));
  }

  private static RegistryHistoryBackupApi apiFor(final PhysicalTenantBackup backup) {
    return new RegistryHistoryBackupApi(new BackupServiceRegistry(List.of(backup)));
  }

  private TakeBackupRequestDto captureTakeBackupRequest() {
    final var captor = ArgumentCaptor.forClass(TakeBackupRequestDto.class);
    verify(backupService).takeBackup(captor.capture());
    return captor.getValue();
  }
}
