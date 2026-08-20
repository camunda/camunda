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

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ShardFailure;
import co.elastic.clients.elasticsearch.snapshot.GetRepositoryRequest;
import co.elastic.clients.elasticsearch.snapshot.GetSnapshotRequest;
import co.elastic.clients.elasticsearch.snapshot.GetSnapshotResponse;
import co.elastic.clients.elasticsearch.snapshot.SnapshotInfo;
import io.camunda.optimize.dto.optimize.rest.SnapshotInfoDto;
import io.camunda.optimize.dto.optimize.rest.SnapshotState;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.reader.AbstractBackupReader;
import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import io.camunda.optimize.service.exceptions.OptimizeElasticsearchConnectionException;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.exceptions.OptimizeSnapshotRepositoryNotFoundException;
import io.camunda.optimize.service.util.SnapshotUtil;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class BackupReaderES extends AbstractBackupReader {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(BackupReaderES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;

  public BackupReaderES(
      final OptimizeElasticsearchClient esClient, final ConfigurationService configurationService) {
    this.esClient = esClient;
    this.configurationService = configurationService;
  }

  @Override
  protected String getSnapshotRepositoryName() {
    return configurationService.getElasticSearchConfiguration().getSnapshotRepositoryName();
  }

  @Override
  protected void validateRepositoryExistsOrFail() {
    final String repositoryName = getRepositoryName();
    if (StringUtils.isEmpty(repositoryName)) {
      final String reason =
          "Cannot trigger backup because no Elasticsearch snapshot repository name found in Optimize configuration.";
      LOG.error(reason);
      throw new OptimizeConfigurationException(reason);
    } else {
      final GetRepositoryRequest getRepositoriesRequest =
          GetRepositoryRequest.of(b -> b.name(repositoryName));
      try {
        esClient.verifyRepositoryExists(getRepositoriesRequest);
      } catch (final ElasticsearchException e) {
        if (StringUtils.contains(e.getMessage(), REPOSITORY_MISSING_EXCEPTION_TYPE)) {
          final String reason =
              String.format("No repository with name [%s] could be found.", repositoryName);
          LOG.error(reason, e);
          throw new OptimizeSnapshotRepositoryNotFoundException(reason, e);
        } else {
          final String reason =
              String.format(
                  "Error while retrieving repository with name [%s] due to an ElasticsearchException.",
                  repositoryName);
          LOG.error(reason, e);
          throw new OptimizeRuntimeException(reason, e);
        }
      } catch (final IOException e) {
        final String reason =
            String.format(
                "Encountered an error connecting to Elasticsearch while retrieving repository with name [%s].",
                repositoryName);
        LOG.error(reason, e);
        throw new OptimizeElasticsearchConnectionException(reason, e);
      }
    }
  }

  @Override
  public Map<Long, List<SnapshotInfoDto>> getAllOptimizeSnapshotsByBackupId() {
    return getAllOptimizeSnapshots().stream()
        .map(BackupReaderES::toSnapshotInfoDto)
        .collect(
            groupingBy(
                snapshotInfo ->
                    SnapshotUtil.getBackupIdFromSnapshotName(snapshotInfo.getSnapshotName())));
  }

  @Override
  public List<SnapshotInfoDto> getOptimizeSnapshotsForBackupId(final Long backupId) {
    return getOptimizeSnapshots(getAllWildcardedSnapshotNamesForBackupId(backupId)).stream()
        .map(BackupReaderES::toSnapshotInfoDto)
        .toList();
  }

  private static SnapshotInfoDto toSnapshotInfoDto(final SnapshotInfo snapshotInfo) {
    final long startTimeMillis = snapshotInfo.startTimeInMillis();
    final List<String> shardFailures =
        snapshotInfo.shards().failures().stream().map(ShardFailure::toString).toList();
    return new SnapshotInfoDto(
        snapshotInfo.snapshot(),
        SnapshotState.valueOf(snapshotInfo.state()),
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(startTimeMillis), ZoneId.systemDefault()),
        shardFailures);
  }

  private List<SnapshotInfo> getAllOptimizeSnapshots() {
    return getOptimizeSnapshots(getAllWildcardedSnapshotNamesForWildcardedBackupId());
  }

  private List<SnapshotInfo> getOptimizeSnapshots(final String[] snapshots) {
    final GetSnapshotRequest snapshotsStatusRequest =
        GetSnapshotRequest.of(
            b -> b.repository(getRepositoryName()).snapshot(Arrays.stream(snapshots).toList()));
    final GetSnapshotResponse response;
    try {
      response = esClient.getSnapshots(snapshotsStatusRequest);
    } catch (final ElasticsearchException e) {
      if (StringUtils.contains(e.getMessage(), SNAPSHOT_MISSING_EXCEPTION_TYPE)) {
        // no snapshot with given backupID exists
        return Collections.emptyList();
      }
      final String reason =
          String.format(
              "Could not retrieve snapshots with names [%s] due to an ElasticsearchException.",
              String.join(", ", snapshots));
      LOG.error(reason);
      throw new OptimizeRuntimeException(reason, e);
    } catch (final IOException e) {
      final String reason =
          String.format(
              "Encountered an error connecting to Elasticsearch while retrieving snapshots with names [%s].",
              String.join(", ", snapshots));
      LOG.error(reason, e);
      throw new OptimizeElasticsearchConnectionException(reason, e);
    }
    return response.snapshots();
  }

  private String getRepositoryName() {
    return configurationService.getElasticSearchConfiguration().getSnapshotRepositoryName();
  }
}
