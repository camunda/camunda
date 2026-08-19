/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.backup;

import io.camunda.application.commons.backup.BackupServiceRegistry.PhysicalTenantBackup;
import io.camunda.service.backup.HistoryBackupApi;
import io.camunda.service.backup.HistoryBackupSnapshot;
import io.camunda.service.backup.HistoryBackupState;
import io.camunda.service.backup.HistoryBackupStateCode;
import io.camunda.service.backup.HistoryBackupTaken;
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
import io.camunda.webapps.backup.BackupStateDto;
import io.camunda.webapps.backup.GetBackupStateResponseDetailDto;
import io.camunda.webapps.backup.GetBackupStateResponseDto;
import io.camunda.webapps.backup.TakeBackupRequestDto;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Serves {@link HistoryBackupApi} from the per-physical-tenant {@link BackupServiceRegistry}.
 *
 * <p>Registered by {@link BackupServiceRegistryConfiguration}, which is already conditional on an
 * Elasticsearch or OpenSearch secondary storage — that is where this bean's ES/OS-only availability
 * comes from. On other storages the bean is absent, and the endpoints are rejected by the
 * {@code @RequiresSecondaryStorage} interceptor before any handler runs.
 *
 * <p>Translates {@link BackupException} into {@link ServiceException} so the gateway's error mapper
 * produces the right status. Two statuses deliberately diverge from the {@code backupHistory}
 * actuator: a repository connection failure maps to 503 here, not 502, because 503 is what the
 * {@code /v2/backups/*} spec documents; and a repository missing from the store maps to 403, not
 * 404, to match how {@code @RequiresSecondaryStorage} already reports a storage that cannot serve
 * history backups.
 */
@NullMarked
public class RegistryHistoryBackupApi implements HistoryBackupApi {

  private final BackupServiceRegistry registry;

  public RegistryHistoryBackupApi(final BackupServiceRegistry registry) {
    this.registry = registry;
  }

  @Override
  public HistoryBackupTaken takeBackup(final String physicalTenantId, final long backupId) {
    final var backup = backupFor(physicalTenantId);
    final var response =
        mapErrors(
            () ->
                backup
                    .backupService()
                    .takeBackup(new TakeBackupRequestDto().setBackupId(backupId)));
    // The take-backup response carries only the snapshot names, so the id is echoed from the
    // request rather than read back from the store.
    return new HistoryBackupTaken(backupId, response.getScheduledSnapshots());
  }

  @Override
  public HistoryBackupState getBackupState(final String physicalTenantId, final long backupId) {
    final var backup = backupFor(physicalTenantId);
    return toState(mapErrors(() -> backup.backupService().getBackupState(backupId)));
  }

  @Override
  public List<HistoryBackupState> getBackups(
      final String physicalTenantId, final boolean verbose, final @Nullable String prefix) {
    final var backup = backupFor(physicalTenantId);
    return mapErrors(() -> backup.backupService().getBackups(verbose, prefix)).stream()
        .map(RegistryHistoryBackupApi::toState)
        .toList();
  }

  @Override
  public void deleteBackup(final String physicalTenantId, final long backupId) {
    final var backup = backupFor(physicalTenantId);
    mapErrors(
        () -> {
          backup.backupService().deleteBackup(backupId);
          return null;
        });
  }

  private PhysicalTenantBackup backupFor(final String physicalTenantId) {
    final PhysicalTenantBackup backup;
    try {
      backup = registry.forPhysicalTenant(physicalTenantId);
    } catch (final IllegalArgumentException e) {
      throw new ServiceException(e.getMessage(), Status.NOT_FOUND);
    }
    final var repositoryName = backup.repositoryProps().repositoryName();
    if (repositoryName == null || repositoryName.isBlank()) {
      throw new ServiceException(
          "No backup repository configured for physical tenant '%s'".formatted(physicalTenantId),
          Status.FORBIDDEN);
    }
    return backup;
  }

  private static <T> T mapErrors(final Supplier<T> call) {
    try {
      return call.get();
    } catch (final BackupException e) {
      throw new ServiceException(e.getMessage(), statusOf(e));
    }
  }

  private static Status statusOf(final BackupException e) {
    return switch (e) {
      case final DuplicateBackupIdException ignored -> Status.ALREADY_EXISTS;
      case final BackupAlreadyRunningException ignored -> Status.INVALID_STATE;
      case final InvalidRequestException ignored -> Status.INVALID_ARGUMENT;
      case final ResourceNotFoundException ignored -> Status.NOT_FOUND;
      case final MissingRepositoryException ignored -> Status.FORBIDDEN;
      case final BackupRepositoryConnectionException ignored -> Status.UNAVAILABLE;
      case final IndexNotFoundException ignored -> Status.INTERNAL;
      default -> Status.INTERNAL;
    };
  }

  private static HistoryBackupState toState(final GetBackupStateResponseDto dto) {
    final var details = dto.getDetails();
    return new HistoryBackupState(
        dto.getBackupId(),
        toStateCode(dto.getState()),
        dto.getFailureReason(),
        details == null
            ? List.of()
            : details.stream().map(RegistryHistoryBackupApi::toSnapshot).toList());
  }

  private static HistoryBackupSnapshot toSnapshot(final GetBackupStateResponseDetailDto detail) {
    final var failures = detail.getFailures();
    return new HistoryBackupSnapshot(
        detail.getSnapshotName(),
        detail.getState(),
        detail.getStartTime(),
        failures == null ? List.of() : Arrays.asList(failures));
  }

  private static HistoryBackupStateCode toStateCode(final BackupStateDto state) {
    return switch (state) {
      case IN_PROGRESS -> HistoryBackupStateCode.IN_PROGRESS;
      case INCOMPLETE -> HistoryBackupStateCode.INCOMPLETE;
      case COMPLETED -> HistoryBackupStateCode.COMPLETED;
      case FAILED -> HistoryBackupStateCode.FAILED;
      case INCOMPATIBLE -> HistoryBackupStateCode.INCOMPATIBLE;
    };
  }
}
