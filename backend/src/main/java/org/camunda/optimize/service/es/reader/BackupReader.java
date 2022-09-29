/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.reader;

import com.github.dockerjava.api.exception.ConflictException;
import io.micrometer.core.instrument.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.OptimizeSnapshotRepositoryNotFoundException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.cluster.repositories.get.GetRepositoriesRequest;
import org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsRequest;
import org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsResponse;
import org.elasticsearch.snapshots.SnapshotInfo;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static java.util.stream.Collectors.joining;
import static org.camunda.optimize.service.util.SnapshotUtil.REPOSITORY_MISSING_EXCEPTION_TYPE;
import static org.camunda.optimize.service.util.SnapshotUtil.SNAPSHOT_MISSING_EXCEPTION_TYPE;
import static org.camunda.optimize.service.util.SnapshotUtil.getSnapshotPrefixWithBackupId;

@RequiredArgsConstructor
@Component
@Slf4j
public class BackupReader {
  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;

  public void validateRepositoryExistsOrFail() {
    final String repositoryName = getRepositoryName();
    if (StringUtils.isEmpty(repositoryName)) {
      final String reason =
        "Cannot trigger backup because no Elasticsearch snapshot repository name found in Optimize configuration.";
      log.error(reason);
      throw new OptimizeConfigurationException(reason);
    } else {
      final GetRepositoriesRequest getRepositoriesRequest = new GetRepositoriesRequest()
        .repositories(new String[]{repositoryName});
      try {
        esClient.verifyRepositoryExists(getRepositoriesRequest);
      } catch (ElasticsearchStatusException e) {
        if (e.getDetailedMessage().contains(REPOSITORY_MISSING_EXCEPTION_TYPE)) {
          final String reason = String.format(
            "No repository with name [%s] could be found.",
            repositoryName
          );
          log.error(reason, e);
          throw new OptimizeSnapshotRepositoryNotFoundException(reason);
        } else {
          final String reason = String.format(
            "Error while retrieving repository with name [%s] due to an ElasticsearchStatusException.",
            repositoryName
          );
          log.error(reason, e);
          throw new OptimizeRuntimeException(reason, e);
        }
      } catch (IOException e) {
        final String reason = String.format("Error while retrieving repository with name [%s].", repositoryName);
        log.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }
    }
  }

  public void validateNoDuplicateBackupId(final String backupId) {
    final List<SnapshotInfo> existingSnapshots = getAllOptimizeSnapshots(backupId);
    if (!existingSnapshots.isEmpty()) {
      final String reason = String.format(
        "A backup with ID [%s] already exists. Found snapshots: [%s]",
        backupId,
        existingSnapshots.stream().map(snapshotInfo -> snapshotInfo.snapshotId().toString()).collect(joining(", "))
      );
      log.error(reason);
      throw new ConflictException(reason);
    }
  }

  public List<SnapshotInfo> getAllOptimizeSnapshots(final String backupId) {
    final GetSnapshotsRequest snapshotsStatusRequest = new GetSnapshotsRequest()
      .repository(getRepositoryName())
      .snapshots(new String[]{getSnapshotPrefixWithBackupId(backupId) + "*"});
    GetSnapshotsResponse response;
    try {
      response = esClient.getSnapshots(snapshotsStatusRequest);
    } catch (ElasticsearchStatusException e) {
      if (e.getDetailedMessage().contains(SNAPSHOT_MISSING_EXCEPTION_TYPE)) {
        // no snapshot with given backupID exists
        return Collections.emptyList();
      }
      final String reason = String.format(
        "Could not retrieve snapshots for backupID [%s] due to an ElasticsearchStatusException.",
        backupId
      );
      log.error(reason);
      throw new OptimizeRuntimeException(reason, e);
    } catch (IOException e) {
      final String reason = String.format("Could not retrieve snapshots for backupID [%s].", backupId);
      log.error(reason);
      throw new OptimizeRuntimeException(reason, e);
    }
    return response.getSnapshots();
  }

  private String getRepositoryName() {
    return configurationService.getEsSnapshotRepositoryName();
  }

}
