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
  public void triggerSnapshot(final String snapshotName, final String[] indexNames) {
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
    getCreateSnapshotActionListener(
        esClient.triggerSnapshotAsync(createSnapshotRequest), snapshotName);
  }

  private void getCreateSnapshotActionListener(
      final CompletableFuture<CreateSnapshotResponse> future, final String snapshotName) {
    future.whenComplete(
        (v, e) -> {
          if (e != null) {
            if (e instanceof IOException) {
              final String reason =
                  String.format(
                      "Encountered an error connecting to Elasticsearch while attempting to create snapshot [%s].",
                      snapshotName);
              LOG.error(reason, e);
            } else {
              final String reason = String.format("Failed to take snapshot [%s]", snapshotName);
              LOG.error(reason, e);
            }
          } else {
            final SnapshotInfo snapshotInfo = v.snapshot();
            switch (SnapshotState.valueOf(snapshotInfo.state())) {
              // should not be null as waitForCompletion is true on snapshot request
              case SUCCESS:
                LOG.info("Successfully taken snapshot [{}].", snapshotInfo.snapshot());
                break;
              case FAILED:
              case INCOMPATIBLE:
                String reason =
                    String.format(
                        "Snapshot execution failed for [%s], reason: %s",
                        snapshotInfo.snapshot(), snapshotInfo.reason());
                LOG.error(reason);
                break;
              case PARTIAL:
              default:
                reason =
                    String.format(
                        "Snapshot status [%s] for snapshot with ID [%s]",
                        snapshotInfo.state(), snapshotInfo.snapshot());
                LOG.warn(reason);
            }
          }
        });
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
