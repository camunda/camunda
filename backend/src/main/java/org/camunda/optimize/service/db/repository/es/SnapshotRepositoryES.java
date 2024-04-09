/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository.es;

import static org.camunda.optimize.service.util.SnapshotUtil.getSnapshotPrefixWithBackupId;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.repository.SnapshotRepository;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.cluster.snapshots.create.CreateSnapshotRequest;
import org.elasticsearch.action.admin.cluster.snapshots.create.CreateSnapshotResponse;
import org.elasticsearch.action.admin.cluster.snapshots.delete.DeleteSnapshotRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.snapshots.SnapshotInfo;
import org.elasticsearch.transport.TransportException;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class SnapshotRepositoryES implements SnapshotRepository {
  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;

  @Override
  public void deleteOptimizeSnapshots(final Long backupId) {
    final DeleteSnapshotRequest deleteSnapshotRequest =
        new DeleteSnapshotRequest()
            .repository(
                configurationService.getElasticSearchConfiguration().getSnapshotRepositoryName())
            .snapshots(getSnapshotPrefixWithBackupId(backupId) + "*");
    esClient.deleteSnapshotAsync(deleteSnapshotRequest, getDeleteSnapshotActionListener(backupId));
  }

  @Override
  public void triggerSnapshot(final String snapshotName, final String[] indexNames) {
    log.info("Triggering async snapshot {}.", snapshotName);
    esClient.triggerSnapshotAsync(
        new CreateSnapshotRequest()
            .repository(
                configurationService.getElasticSearchConfiguration().getSnapshotRepositoryName())
            .snapshot(snapshotName)
            .indices(indexNames)
            .includeGlobalState(false)
            .waitForCompletion(true),
        getCreateSnapshotActionListener(snapshotName));
  }

  private ActionListener<CreateSnapshotResponse> getCreateSnapshotActionListener(
      final String snapshotName) {
    return new ActionListener<>() {
      @Override
      public void onResponse(CreateSnapshotResponse createSnapshotResponse) {
        final SnapshotInfo snapshotInfo = createSnapshotResponse.getSnapshotInfo();
        switch (snapshotInfo
            .state()) { // should not be null as waitForCompletion is true on snapshot request
          case SUCCESS:
            log.info("Successfully taken snapshot [{}].", snapshotInfo.snapshotId());
            break;
          case FAILED:
          case INCOMPATIBLE:
            String reason =
                String.format(
                    "Snapshot execution failed for [%s], reason: %s",
                    snapshotInfo.snapshotId(), snapshotInfo.reason());
            log.error(reason);
            break;
          case PARTIAL:
          default:
            reason =
                String.format(
                    "Snapshot status [%s] for snapshot with ID [%s]",
                    snapshotInfo.state(), snapshotInfo.snapshotId());
            log.warn(reason);
        }
      }

      @Override
      public void onFailure(Exception e) {
        if (e instanceof IOException || e instanceof TransportException) {
          final String reason =
              String.format(
                  "Encountered an error connecting to Elasticsearch while attempting to create snapshot [%s].",
                  snapshotName);
          log.error(reason, e);
        } else {
          final String reason = String.format("Failed to take snapshot [%s]", snapshotName);
          log.error(reason, e);
        }
      }
    };
  }

  private ActionListener<AcknowledgedResponse> getDeleteSnapshotActionListener(
      final Long backupId) {
    return new ActionListener<>() {
      @Override
      public void onResponse(AcknowledgedResponse deleteSnapshotResponse) {
        if (deleteSnapshotResponse.isAcknowledged()) {
          String reason =
              String.format(
                  "Request to delete all Optimize snapshots with the backupID [%d] successfully submitted",
                  backupId);
          log.info(reason);
        } else {
          String reason =
              String.format(
                  "Request to delete all Optimize snapshots with the backupID [%d] was not acknowledged by Elasticsearch.",
                  backupId);
          log.error(reason);
        }
      }

      @Override
      public void onFailure(Exception e) {
        if (e instanceof IOException || e instanceof TransportException) {
          final String reason =
              String.format(
                  "Encountered an error connecting to Elasticsearch while attempting to delete snapshots for backupID [%s].",
                  backupId);
          log.error(reason, e);
        } else {
          String reason = String.format("Failed to delete snapshots for backupID [%s]", backupId);
          log.error(reason, e);
        }
      }
    };
  }
}
