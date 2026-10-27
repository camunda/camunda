/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static io.camunda.optimize.service.db.os.client.async.OpenSearchAsyncSnapshotOperations.SnapshotStatus.FAILED;
import static io.camunda.optimize.service.db.os.client.async.OpenSearchAsyncSnapshotOperations.SnapshotStatus.SUCCESS;
import static io.camunda.optimize.service.util.SnapshotUtil.getSnapshotPrefixWithBackupId;
import static java.lang.String.format;

import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.repository.SnapshotRepository;
import io.camunda.optimize.service.util.ExceptionUtil;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import org.opensearch.client.opensearch.snapshot.CreateSnapshotResponse;
import org.opensearch.client.opensearch.snapshot.DeleteSnapshotResponse;
import org.opensearch.client.opensearch.snapshot.SnapshotInfo;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class SnapshotRepositoryOS implements SnapshotRepository {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(SnapshotRepositoryOS.class);
  private final OptimizeOpenSearchClient osClient;
  private final ConfigurationService configurationService;

  public SnapshotRepositoryOS(
      final OptimizeOpenSearchClient osClient, final ConfigurationService configurationService) {
    this.osClient = osClient;
    this.configurationService = configurationService;
  }

  @Override
  public void deleteOptimizeSnapshots(final Long backupId) {
    osClient
        .getRichOpenSearchClient()
        .async()
        .snapshot()
        .delete(
            configurationService.getOpenSearchConfiguration().getSnapshotRepositoryName(),
            getSnapshotPrefixWithBackupId(backupId) + "*",
            e -> "Failed to send delete snapshot request to Opensearch!")
        .whenComplete(
            (deleteSnapshotResponse, throwable) -> {
              if (throwable != null) {
                onSnapshotDeletionFailed(throwable, backupId);
              } else if (deleteSnapshotResponse != null) {
                onSnapshotDeleted(deleteSnapshotResponse, backupId);
              }
            });
  }

  @Override
  public CompletableFuture<Void> triggerSnapshot(
      final String snapshotName, final String[] indexNames) {
    LOG.info("Triggering async snapshot {}.", snapshotName);
    return osClient
        .getRichOpenSearchClient()
        .async()
        .snapshot()
        .create(
            configurationService.getOpenSearchConfiguration().getSnapshotRepositoryName(),
            snapshotName,
            Arrays.asList(indexNames),
            e -> format("Failed to send create snapshot %s request to Opensearch!", snapshotName))
        .handle(
            (createSnapshotResponse, throwable) -> {
              if (createSnapshotResponse != null) {
                onCreateSnapshotCompletion(createSnapshotResponse);
              }
              if (throwable != null) {
                onCreateSnapshotError(throwable, snapshotName);
              }
              return null;
            });
  }

  private void onSnapshotDeletionFailed(final Throwable e, final Long backupId) {
    if (e instanceof IOException) {
      final String reason =
          format(
              "Encountered an error connecting to Opensearch while attempting to delete snapshots for backupID [%s].",
              backupId);
      LOG.error(reason, e);
    } else {
      final String reason = format("Failed to delete snapshots for backupID [%s]", backupId);
      LOG.error(reason, e);
    }
  }

  private void onSnapshotDeleted(
      final DeleteSnapshotResponse deleteSnapshotResponse, final Long backupId) {
    if (deleteSnapshotResponse.acknowledged()) {
      final String reason =
          format(
              "Request to delete all Optimize snapshots with the backupID [%d] successfully submitted",
              backupId);
      LOG.info(reason);
    } else {
      final String reason =
          format(
              "Request to delete all Optimize snapshots with the backupID [%d] was not acknowledged by Opencsearch.",
              backupId);
      LOG.error(reason);
    }
  }

  private void onCreateSnapshotError(final Throwable e, final String snapshotName) {
    final String reason;
    if (ExceptionUtil.isConcurrentSnapshotExecutionException(e)) {
      reason =
          format(
              "Could not create snapshot [%s] because of concurrent snapshot operations.",
              snapshotName);
    } else if (ExceptionUtil.unwrapCompletionCause(e) instanceof IOException) {
      reason =
          format(
              "Encountered an error connecting to OpenSearch while attempting to create snapshot [%s].",
              snapshotName);
    } else {
      reason = format("Failed to take snapshot [%s]", snapshotName);
    }
    LOG.error(reason, e);
  }

  private void onCreateSnapshotCompletion(final CreateSnapshotResponse createSnapshotResponse) {
    // should not be null as waitForCompletion is true on snapshot request
    final SnapshotInfo snapshotInfo = createSnapshotResponse.snapshot();
    switch (snapshotInfo.state()) {
      case SUCCESS -> LOG.info("Successfully taken snapshot [{}].", snapshotInfo.snapshot());
      case FAILED -> {
        final String reason =
            format(
                "Snapshot execution failed for [%s], reason: %s",
                snapshotInfo.snapshot(), snapshotInfo.reason());
        LOG.error(reason);
      }
      default -> {
        final String reason =
            format(
                "Snapshot status [%s] for snapshot with ID [%s]",
                snapshotInfo.state(), snapshotInfo.snapshot());
        LOG.warn(reason);
      }
    }
  }
}
