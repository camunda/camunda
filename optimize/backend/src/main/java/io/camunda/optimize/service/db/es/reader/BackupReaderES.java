/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static io.camunda.optimize.service.util.SnapshotUtil.REPOSITORY_MISSING_EXCEPTION_TYPE;
import static io.camunda.optimize.service.util.SnapshotUtil.SNAPSHOT_MISSING_EXCEPTION_TYPE;
import static io.camunda.optimize.service.util.SnapshotUtil.getAllWildcardedSnapshotNamesForBackupId;
import static io.camunda.optimize.service.util.SnapshotUtil.getAllWildcardedSnapshotNamesForWildcardedBackupId;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;

import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.reader.BackupReader;
import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import io.camunda.optimize.service.exceptions.OptimizeElasticsearchConnectionException;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.exceptions.OptimizeSnapshotRepositoryNotFoundException;
import io.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
import io.camunda.optimize.service.util.SnapshotUtil;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.cluster.repositories.get.GetRepositoriesRequest;
import org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsRequest;
import org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsResponse;
import org.elasticsearch.snapshots.SnapshotInfo;
import org.elasticsearch.transport.TransportException;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class BackupReaderES implements BackupReader {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(BackupReaderES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;

  public BackupReaderES(
      final OptimizeElasticsearchClient esClient, final ConfigurationService configurationService) {
    this.esClient = esClient;
    this.configurationService = configurationService;
  }

  @Override
  public void validateRepositoryExistsOrFail() {
    final String repositoryName = getRepositoryName();
    if (StringUtils.isEmpty(repositoryName)) {
      final String reason =
          "Cannot trigger backup because no Elasticsearch snapshot repository name found in Optimize configuration.";
      log.error(reason);
      throw new OptimizeConfigurationException(reason);
    } else {
      final GetRepositoriesRequest getRepositoriesRequest =
          new GetRepositoriesRequest().repositories(new String[] {repositoryName});
      try {
        esClient.verifyRepositoryExists(getRepositoriesRequest);
      } catch (final ElasticsearchStatusException e) {
        if (e.getDetailedMessage().contains(REPOSITORY_MISSING_EXCEPTION_TYPE)) {
          final String reason =
              String.format("No repository with name [%s] could be found.", repositoryName);
          log.error(reason, e);
          throw new OptimizeSnapshotRepositoryNotFoundException(reason);
        } else {
          final String reason =
              String.format(
                  "Error while retrieving repository with name [%s] due to an ElasticsearchStatusException.",
                  repositoryName);
          log.error(reason, e);
          throw new OptimizeRuntimeException(reason, e);
        }
      } catch (final IOException | TransportException e) {
        final String reason =
            String.format(
                "Encountered an error connecting to Elasticsearch while retrieving repository with name [%s].",
                repositoryName);
        log.error(reason, e);
        throw new OptimizeElasticsearchConnectionException(reason, e);
      }
    }
  }

  @Override
  public void validateNoDuplicateBackupId(final Long backupId) {
    final List<SnapshotInfo> existingSnapshots = getOptimizeSnapshotsForBackupId(backupId);
    if (!existingSnapshots.isEmpty()) {
      final String reason =
          String.format(
              "A backup with ID [%s] already exists. Found snapshots: [%s]",
              backupId,
              existingSnapshots.stream()
                  .map(snapshotInfo -> snapshotInfo.snapshotId().toString())
                  .collect(joining(", ")));
      log.error(reason);
      throw new OptimizeConflictException(reason);
    }
  }

  @Override
  public Map<Long, List<SnapshotInfo>> getAllOptimizeSnapshotsByBackupId() {
    return getAllOptimizeSnapshots().stream()
        .collect(
            groupingBy(
                snapshotInfo ->
                    SnapshotUtil.getBackupIdFromSnapshotName(
                        snapshotInfo.snapshot().getSnapshotId().getName())));
  }

  @Override
  public List<SnapshotInfo> getAllOptimizeSnapshots() {
    return getOptimizeSnapshots(getAllWildcardedSnapshotNamesForWildcardedBackupId());
  }

  @Override
  public List<SnapshotInfo> getOptimizeSnapshotsForBackupId(final Long backupId) {
    return getOptimizeSnapshots(getAllWildcardedSnapshotNamesForBackupId(backupId));
  }

  private List<SnapshotInfo> getOptimizeSnapshots(final String[] snapshots) {
    final GetSnapshotsRequest snapshotsStatusRequest =
        new GetSnapshotsRequest().repository(getRepositoryName()).snapshots(snapshots);
    final GetSnapshotsResponse response;
    try {
      response = esClient.getSnapshots(snapshotsStatusRequest);
    } catch (final ElasticsearchStatusException e) {
      if (e.getDetailedMessage().contains(SNAPSHOT_MISSING_EXCEPTION_TYPE)) {
        // no snapshot with given backupID exists
        return Collections.emptyList();
      }
      final String reason =
          String.format(
              "Could not retrieve snapshots with names [%s] due to an ElasticsearchStatusException.",
              String.join(", ", snapshots));
      log.error(reason);
      throw new OptimizeRuntimeException(reason, e);
    } catch (final IOException | TransportException e) {
      final String reason =
          String.format(
              "Encountered an error connecting to Elasticsearch while retrieving snapshots with names [%s].",
              String.join(", ", snapshots));
      log.error(reason, e);
      throw new OptimizeElasticsearchConnectionException(reason, e);
    }
    return response.getSnapshots();
  }

  private String getRepositoryName() {
    return configurationService.getElasticSearchConfiguration().getSnapshotRepositoryName();
  }
}
