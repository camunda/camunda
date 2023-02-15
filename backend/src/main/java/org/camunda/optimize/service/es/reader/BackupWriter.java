/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.MappingMetadataUtil;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.exceptions.OptimizeElasticsearchConnectionException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.cluster.snapshots.create.CreateSnapshotRequest;
import org.elasticsearch.action.admin.cluster.snapshots.create.CreateSnapshotResponse;
import org.elasticsearch.action.admin.cluster.snapshots.delete.DeleteSnapshotRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.snapshots.SnapshotInfo;
import org.elasticsearch.transport.TransportException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static org.camunda.optimize.service.util.SnapshotUtil.getSnapshotNameForImportIndices;
import static org.camunda.optimize.service.util.SnapshotUtil.getSnapshotNameForNonImportIndices;
import static org.camunda.optimize.service.util.SnapshotUtil.getSnapshotPrefixWithBackupId;

@RequiredArgsConstructor
@Component
@Slf4j
public class BackupWriter {
  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final OptimizeIndexNameService indexNameService;

  public void triggerSnapshotCreation(final Integer backupId) {
    final String snapshot1Name = getSnapshotNameForImportIndices(backupId);
    final String snapshot2Name = getSnapshotNameForNonImportIndices(backupId);
    CompletableFuture.runAsync(() -> {
      triggerSnapshot(snapshot1Name, getIndexAliasesWithImportIndexFlag(true));
      triggerSnapshot(snapshot2Name, getIndexAliasesWithImportIndexFlag(false));
    });
  }

  public void deleteOptimizeSnapshots(final Integer backupId) {
    final DeleteSnapshotRequest deleteSnapshotRequest = new DeleteSnapshotRequest()
      .repository(configurationService.getEsSnapshotRepositoryName())
      .snapshots(getSnapshotPrefixWithBackupId(backupId) + "*");
    esClient.deleteSnapshotAsync(deleteSnapshotRequest, getDeleteSnapshotActionListener(backupId));
  }

  private void triggerSnapshot(final String snapshotName, final String[] indexNames) {
    log.info("Triggering async snapshot {}.", snapshotName);
    esClient.triggerSnapshotAsync(
      new CreateSnapshotRequest()
        .repository(configurationService.getEsSnapshotRepositoryName())
        .snapshot(snapshotName)
        .indices(indexNames)
        .includeGlobalState(false)
        .waitForCompletion(true),
      getCreateSnapshotActionListener(snapshotName)
    );
  }

  private ActionListener<CreateSnapshotResponse> getCreateSnapshotActionListener(final String snapshotName) {
    return new ActionListener<>() {
      @Override
      public void onResponse(CreateSnapshotResponse createSnapshotResponse) {
        final SnapshotInfo snapshotInfo = createSnapshotResponse.getSnapshotInfo();
        switch (snapshotInfo.state()) { // should not be null as waitForCompletion is true on snapshot request
          case SUCCESS:
            log.info("Successfully taken snapshot [{}].", snapshotInfo.snapshotId());
            break;
          case FAILED:
          case INCOMPATIBLE:
            String reason = String.format(
              "Snapshot execution failed for [%s], reason: %s",
              snapshotInfo.snapshotId(),
              snapshotInfo.reason()
            );
            log.error(reason);
            throw new OptimizeRuntimeException(reason);
          case PARTIAL:
          default:
            reason = String.format(
              "Snapshot status [%s] for snapshot with ID [%s]",
              snapshotInfo.state(),
              snapshotInfo.snapshotId()
            );
            log.warn(reason);
            throw new OptimizeRuntimeException(reason);
        }
      }

      @Override
      public void onFailure(Exception e) {
        if (e instanceof IOException || e instanceof TransportException) {
          final String reason = String.format(
            "Encountered an error connecting to Elasticsearch while attempting to create snapshot [%s].",
            snapshotName
          );
          log.error(reason, e);
          throw new OptimizeElasticsearchConnectionException(reason, e);
        } else {
          final String reason = String.format("Failed to take snapshot [%s]", snapshotName);
          log.error(reason, e);
          throw new OptimizeRuntimeException(reason, e);
        }
      }
    };
  }

  private ActionListener<AcknowledgedResponse> getDeleteSnapshotActionListener(final Integer backupId) {
    return new ActionListener<>() {
      @Override
      public void onResponse(AcknowledgedResponse deleteSnapshotResponse) {
        if (deleteSnapshotResponse.isAcknowledged()) {
          String reason = String.format(
            "Request to delete all Optimize snapshots with the backupID [%d] successfully submitted",
            backupId
          );
          log.info(reason);
        } else {
          String reason = String.format(
            "Request to delete all Optimize snapshots with the backupID [%d] was not acknowledged by Elasticsearch.",
            backupId
          );
          log.error(reason);
          throw new OptimizeRuntimeException(reason);
        }
      }

      @Override
      public void onFailure(Exception e) {
        if (e instanceof IOException || e instanceof TransportException) {
          final String reason = String.format(
            "Encountered an error connecting to Elasticsearch while attempting to delete snapshots for backupID [%s].",
            backupId
          );
          log.error(reason, e);
          throw new OptimizeElasticsearchConnectionException(reason, e);
        } else {
          String reason = String.format("Failed to delete snapshots for backupID [%s]", backupId);
          log.error(reason, e);
          throw new OptimizeRuntimeException(reason, e);
        }
      }
    };
  }

  private String[] getIndexAliasesWithImportIndexFlag(final boolean isImportIndex) {
    return MappingMetadataUtil.getAllMappings(esClient)
      .stream()
      .filter(mapping -> isImportIndex == mapping.isImportIndex())
      .map(indexNameService::getOptimizeIndexAliasForIndex)
      .toArray(String[]::new);
  }
}
