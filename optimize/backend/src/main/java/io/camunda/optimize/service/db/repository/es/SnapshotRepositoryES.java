/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static io.camunda.optimize.service.util.SnapshotUtil.getSnapshotPrefixWithBackupId;

import co.elastic.clients.elasticsearch.snapshot.CreateSnapshotRequest;
import co.elastic.clients.elasticsearch.snapshot.CreateSnapshotResponse;
import co.elastic.clients.elasticsearch.snapshot.DeleteSnapshotRequest;
import co.elastic.clients.elasticsearch.snapshot.DeleteSnapshotResponse;
import co.elastic.clients.elasticsearch.snapshot.SnapshotInfo;
import io.camunda.optimize.dto.optimize.rest.SnapshotState;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.repository.SnapshotRepository;
import io.camunda.optimize.service.util.ExceptionUtil;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class SnapshotRepositoryES implements SnapshotRepository {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(SnapshotRepositoryES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;

  public SnapshotRepositoryES(
      final OptimizeElasticsearchClient esClient, final ConfigurationService configurationService) {
    this.esClient = esClient;
    this.configurationService = configurationService;
  }

  @Override
  public void deleteOptimizeSnapshots(final Long backupId) {
    final DeleteSnapshotRequest deleteSnapshotRequest =
        DeleteSnapshotRequest.of(
            b ->
                b.repository(
                        configurationService
                            .getElasticSearchConfiguration()
                            .getSnapshotRepositoryName())
                    .snapshot(getSnapshotPrefixWithBackupId(backupId) + "*"));
    getDeleteSnapshotActionListener(esClient.deleteSnapshotAsync(deleteSnapshotRequest), backupId);
  }

  @Override
  public CompletableFuture<Void> triggerSnapshot(
      final String snapshotName, final String[] indexNames) {
    LOG.info("Triggering async snapshot {}.", snapshotName);
    final CreateSnapshotRequest createSnapshotRequest =
        CreateSnapshotRequest.of(
            b ->
                b.repository(
                        configurationService
                            .getElasticSearchConfiguration()
                            .getSnapshotRepositoryName())
                    .snapshot(snapshotName)
                    .indices(Arrays.stream(indexNames).toList())
                    .includeGlobalState(false)
                    .waitForCompletion(true));
    return getCreateSnapshotActionListener(
        esClient.triggerSnapshotAsync(createSnapshotRequest), snapshotName);
  }

  private CompletableFuture<Void> getCreateSnapshotActionListener(
      final CompletableFuture<CreateSnapshotResponse> future, final String snapshotName) {
    return future.handle(
        (v, e) -> {
          if (e != null) {
            onCreateSnapshotError(snapshotName, e);
          } else {
            onCreateSnapshotCompletion(v);
          }
          return null;
        });
  }

  private static void onCreateSnapshotCompletion(final CreateSnapshotResponse response) {
    // should not be null as waitForCompletion is true on snapshot request
    final SnapshotInfo snapshotInfo = response.snapshot();
    try {
      final SnapshotState snapshotState = SnapshotState.valueOf(snapshotInfo.state());
      switch (snapshotState) {
        case SUCCESS:
          LOG.info("Successfully taken snapshot [{}].", snapshotInfo.snapshot());
          break;
        case FAILED:
        case INCOMPATIBLE:
          LOG.error(
              "Snapshot execution failed for [{}], reason: {}",
              snapshotInfo.snapshot(),
              snapshotInfo.reason());
          break;
        case PARTIAL:
        default:
          LOG.warn(
              "Snapshot status [{}] for snapshot with ID [{}]",
              snapshotInfo.state(),
              snapshotInfo.snapshot());
      }
    } catch (final IllegalArgumentException ex) {
      LOG.error(
          "Snapshot state [{}] for snapshot with ID [{}] is not a valid SnapshotState enum value.",
          snapshotInfo.state(),
          snapshotInfo.snapshot(),
          ex);
    }
  }

  private static void onCreateSnapshotError(final String snapshotName, final Throwable e) {
    final String reason;
    if (ExceptionUtil.isConcurrentSnapshotExecutionException(e)) {
      reason =
          String.format(
              "Could not create snapshot [%s] because of concurrent snapshot operations.",
              snapshotName);
    } else if (ExceptionUtil.unwrapCompletionCause(e) instanceof IOException) {
      reason =
          String.format(
              "Encountered an error connecting to Elasticsearch while attempting to create snapshot [%s].",
              snapshotName);
    } else {
      reason = String.format("Failed to take snapshot [%s]", snapshotName);
    }
    LOG.error(reason, e);
  }

  private void getDeleteSnapshotActionListener(
      final CompletableFuture<DeleteSnapshotResponse> future, final Long backupId) {
    future.whenComplete(
        (v, e) -> {
          if (e != null) {
            if (e instanceof IOException) {
              final String reason =
                  String.format(
                      "Encountered an error connecting to Elasticsearch while attempting to delete snapshots for backupID [%s].",
                      backupId);
              LOG.error(reason, e);
            } else {
              final String reason =
                  String.format("Failed to delete snapshots for backupID [%s]", backupId);
              LOG.error(reason, e);
            }
          } else {
            if (v.acknowledged()) {
              final String reason =
                  String.format(
                      "Request to delete all Optimize snapshots with the backupID [%d] successfully submitted",
                      backupId);
              LOG.info(reason);
            } else {
              final String reason =
                  String.format(
                      "Request to delete all Optimize snapshots with the backupID [%d] was not acknowledged by Elasticsearch.",
                      backupId);
              LOG.error(reason);
            }
          }
        });
  }
}
