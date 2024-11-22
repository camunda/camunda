/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.backup;

import io.camunda.operate.exceptions.OperateElasticsearchConnectionException;
import io.camunda.operate.exceptions.OperateOpensearchConnectionException;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.webapp.elasticsearch.backup.ElasticsearchBackupRepository;
import io.camunda.operate.webapp.opensearch.backup.OpensearchBackupRepository;
import io.camunda.webapps.backup.BackupRepository;
import io.camunda.webapps.backup.BackupService.SnapshotRequest;
import io.camunda.webapps.backup.GetBackupStateResponseDto;
import io.camunda.webapps.backup.exceptions.InvalidRequestException;
import io.camunda.webapps.backup.exceptions.ResourceNotFoundException;
import io.camunda.webapps.backup.repository.BackupRepositoryConnectionException;
import io.camunda.webapps.backup.repository.GenericBackupException;
import io.camunda.webapps.backup.repository.SnapshotNameProvider;
import java.util.List;
import java.util.function.Supplier;

/**
 * Temporary class while we are moving operates' backup code into webapp-backup. This class will be
 * removed when the BackupController will be moved to a common module.
 *
 * <p>Wraps a BackupRepository in order to map the exception thrown by the generic implementation
 * (from webapp-backups) to the exception that the controller in operate expects to be raised:
 *
 * <ul>
 *   <li>{@link io.camunda.operate.exceptions.OperateElasticsearchConnectionException}
 *   <li>{@link io.camunda.operate.exceptions.OperateOpensearchConnectionException}
 *   <li>{@link io.camunda.operate.exceptions.OperateRuntimeException}
 *   <li>{@link io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException}
 * </ul>
 */
public class BackupRepositoryWrapper implements BackupRepository {

  private final BackupRepository repository;

  BackupRepositoryWrapper(final BackupRepository repository) {
    this.repository = repository;
  }

  @Override
  public SnapshotNameProvider snapshotNameProvider() {
    return repository.snapshotNameProvider();
  }

  @Override
  public void deleteSnapshot(final String repositoryName, final String snapshotName) {
    remap(() -> repository.deleteSnapshot(repositoryName, snapshotName));
  }

  @Override
  public void validateRepositoryExists(final String repositoryName) {
    remap(() -> repository.validateRepositoryExists(repositoryName));
  }

  @Override
  public void validateNoDuplicateBackupId(final String repositoryName, final Long backupId) {
    remap(() -> repository.validateNoDuplicateBackupId(repositoryName, backupId));
  }

  @Override
  public GetBackupStateResponseDto getBackupState(
      final String repositoryName, final Long backupId) {
    return remap(() -> repository.getBackupState(repositoryName, backupId));
  }

  @Override
  public List<GetBackupStateResponseDto> getBackups(final String repositoryName) {
    return remap(() -> repository.getBackups(repositoryName));
  }

  @Override
  public void executeSnapshotting(
      final SnapshotRequest snapshotRequest, final Runnable onSuccess, final Runnable onFailure) {
    remap(() -> repository.executeSnapshotting(snapshotRequest, onSuccess, onFailure));
  }

  private void remap(final Runnable runnable) {
    remap(
        () -> {
          runnable.run();
          return null;
        });
  }

  private <A> A remap(final Supplier<A> supplier) {
    try {
      return supplier.get();
    } catch (final InvalidRequestException e) {
      throw new io.camunda.operate.webapp.rest.exception.InvalidRequestException(e.getMessage(), e);
    } catch (final GenericBackupException e) {
      throw new OperateRuntimeException(e.getMessage(), e);
    } catch (final ResourceNotFoundException e) {
      throw new io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException(
          e.getMessage());
    } catch (final BackupRepositoryConnectionException e) {
      if (repository instanceof ElasticsearchBackupRepository) {
        throw new OperateElasticsearchConnectionException(e.getMessage(), e);
      } else if (repository instanceof OpensearchBackupRepository) {
        throw new OperateOpensearchConnectionException(e.getMessage(), e);
      } else {
        throw e;
      }
    }
  }
}
