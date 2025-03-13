/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.backup.repository.elasticsearch;

import static io.camunda.webapps.backup.repository.elasticsearch.SnapshotState.*;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.indices.GetIndexRequest;
import co.elastic.clients.elasticsearch.snapshot.CreateSnapshotRequest;
import co.elastic.clients.elasticsearch.snapshot.CreateSnapshotResponse;
import co.elastic.clients.elasticsearch.snapshot.GetSnapshotRequest;
import co.elastic.clients.elasticsearch.snapshot.GetSnapshotResponse;
import co.elastic.clients.elasticsearch.snapshot.SnapshotInfo;
import co.elastic.clients.elasticsearch.snapshot.SnapshotShardFailure;
import co.elastic.clients.elasticsearch.snapshot.SnapshotSort;
import io.camunda.webapps.backup.BackupException;
import io.camunda.webapps.backup.BackupException.BackupRepositoryConnectionException;
import io.camunda.webapps.backup.BackupException.InvalidRequestException;
import io.camunda.webapps.backup.BackupException.MissingRepositoryException;
import io.camunda.webapps.backup.BackupException.ResourceNotFoundException;
import io.camunda.webapps.backup.BackupRepository;
import io.camunda.webapps.backup.BackupService;
import io.camunda.webapps.backup.BackupService.SnapshotRequest;
import io.camunda.webapps.backup.BackupStateDto;
import io.camunda.webapps.backup.GetBackupStateResponseDetailDto;
import io.camunda.webapps.backup.GetBackupStateResponseDto;
import io.camunda.webapps.backup.Metadata;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import io.camunda.webapps.backup.repository.SnapshotNameProvider;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchBackupRepository implements BackupRepository {
  public static final String SNAPSHOT_MISSING_EXCEPTION_TYPE = "snapshot_missing_exception";
  private static final String REPOSITORY_MISSING_EXCEPTION_TYPE = "repository_missing_exception";
  private static final String INDEX_MISSING_EXCEPTION_TYPE = "index_not_found_exception";
  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchBackupRepository.class);
  private final ElasticsearchClient esClient;
  private final BackupRepositoryProps backupProps;
  private final SnapshotNameProvider snapshotNameProvider;
  private final Executor executor;

  public ElasticsearchBackupRepository(
      final ElasticsearchClient esClient,
      final BackupRepositoryProps backupProps,
      final SnapshotNameProvider snapshotNameProvider,
      final Executor executor) {
    this.esClient = esClient;
    this.backupProps = backupProps;
    this.snapshotNameProvider = snapshotNameProvider;
    this.executor = executor;
  }

  @Override
  public SnapshotNameProvider snapshotNameProvider() {
    return snapshotNameProvider;
  }

  @Override
  public void deleteSnapshot(final String repositoryName, final String snapshotName) {
    executor.execute(
        () -> {
          try {
            final var response =
                esClient
                    .snapshot()
                    .delete(q -> q.repository(repositoryName).snapshot(snapshotName));
            LOGGER.debug(
                "Delete snapshot was acknowledged by Elasticsearch node: {}",
                response.acknowledged());
          } catch (final ElasticsearchException e) {
            if (isSnapshotMissingException(e)) {
              // no snapshot with given backupID exists, this is fine, log warning
              LOGGER.warn("No snapshot found for snapshot deletion: {} ", e.getMessage());
            } else {
              LOGGER.error("Exception occurred while deleting the snapshot: {}", e.getMessage(), e);
            }
          } catch (final IOException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Override
  public void validateRepositoryExists(final String repositoryName) {
    try {
      final var repository = esClient.snapshot().getRepository(r -> r.name(repositoryName));
      if (repository.result().isEmpty()) {
        final String reason =
            String.format("No repository with name [%s] could be found.", repositoryName);
        throw new MissingRepositoryException(reason);
      }
    } catch (final IOException ex) {
      final String reason =
          String.format(
              "Encountered an error connecting to Elasticsearch while retrieving repository with name [%s].",
              repositoryName);
      throw new BackupRepositoryConnectionException(reason, ex);
    } catch (final ElasticsearchException e) {
      if (isRepositoryMissingException(e)) {
        final String reason =
            String.format("No repository with name [%s] could be found.", repositoryName);
        throw new MissingRepositoryException(reason);
      }
      final String reason =
          String.format(
              "Exception occurred when validating existence of repository with name [%s].",
              repositoryName);
      throw new BackupException(reason, e);
    }
  }

  @Override
  public void validateNoDuplicateBackupId(final String repositoryName, final Long backupId) {
    try {
      final var response =
          esClient
              .snapshot()
              .get(
                  r ->
                      r.repository(repositoryName)
                          .snapshot(snapshotNameProvider.getSnapshotNamePrefix(backupId) + "*"));
      if (!response.snapshots().isEmpty()) {
        final String reason =
            String.format(
                "A backup with ID [%s] already exists. Found snapshots: [%s]",
                backupId,
                response.snapshots().stream().map(SnapshotInfo::snapshot).collect(joining(", ")));
        throw new InvalidRequestException(reason);
      }
    } catch (final IOException ex) {
      final String reason =
          String.format(
              "Encountered an error connecting to Elasticsearch while searching for duplicate backup. Repository name: [%s].",
              repositoryName);
      throw new BackupRepositoryConnectionException(reason, ex);
    } catch (final ElasticsearchException e) {
      if (isSnapshotMissingException(e)) {
        // no snapshot with given backupID exists
        return;
      }
      final String reason =
          String.format(
              "Exception occurred when validating whether backup with ID [%s] already exists.",
              backupId);
      throw new BackupRepositoryConnectionException(reason, e);
    }
  }

  @Override
  public GetBackupStateResponseDto getBackupState(
      final String repositoryName, final Long backupId) {
    final List<SnapshotInfo> snapshots = findSnapshots(repositoryName, backupId);
    return getBackupResponse(backupId, snapshots);
  }

  @Override
  public Optional<Metadata> getMetadata(final String repositoryName, final Long backupId) {
    final var snapshots = findSnapshots(repositoryName, backupId);
    if (snapshots.isEmpty()) {
      return Optional.empty();
    } else {
      final var first = snapshots.getFirst();
      return Optional.of(
          MetadataMarshaller.fromMetadata(first.metadata(), esClient._jsonpMapper()));
    }
  }

  @Override
  public Set<String> checkAllIndicesExist(final List<String> indices) {
    try {
      final var response =
          esClient
              .indices()
              .get(
                  GetIndexRequest.of(
                      b ->
                          b.index(indices)
                              // setting this to true to not receive an exception, but only the list
                              // of available indices
                              .ignoreUnavailable(true)));
      return response.result().keySet();
    } catch (final IOException e) {
      throw new BackupRepositoryConnectionException("Unable to connect to Elasticsearch", e);
    }
  }

  @Override
  public List<GetBackupStateResponseDto> getBackups(final String repositoryName) {
    final GetSnapshotRequest snapshotsStatusRequest =
        GetSnapshotRequest.of(
            b ->
                b.repository(repositoryName)
                    .snapshot(snapshotNameProvider.snapshotNamePrefix() + "*")
                    // it looks like sorting as well as size/offset are not working, need to sort
                    // additionally before return
                    .sort(SnapshotSort.StartTime)
                    .order(SortOrder.Desc));
    final GetSnapshotResponse response;
    try {
      response = esClient.snapshot().get(snapshotsStatusRequest);
      final List<SnapshotInfo> snapshots =
          response.snapshots().stream()
              .sorted(Comparator.comparing(SnapshotInfo::startTimeInMillis).reversed())
              .toList();

      final LinkedHashMap<Long, List<SnapshotInfo>> groupedSnapshotInfos =
          snapshots.stream()
              .collect(
                  groupingBy(
                      si -> {
                        final Metadata metadata =
                            MetadataMarshaller.fromMetadata(si.metadata(), esClient._jsonpMapper());
                        Long backupId = metadata.backupId();
                        // backward compatibility with v. 8.1
                        if (backupId == null) {
                          backupId = snapshotNameProvider.extractBackupId(si.snapshot());
                        }
                        return backupId;
                      },
                      LinkedHashMap::new,
                      toList()));

      final List<GetBackupStateResponseDto> responses =
          groupedSnapshotInfos.entrySet().stream()
              .map(entry -> getBackupResponse(entry.getKey(), entry.getValue()))
              .collect(toList());

      return responses;
    } catch (final IOException ex) {
      final String reason =
          String.format(
              "Encountered an error connecting to Elasticsearch while searching for snapshots. Repository name: [%s].",
              repositoryName);
      throw new BackupRepositoryConnectionException(reason, ex);
    } catch (final Exception e) {
      if (isRepositoryMissingException(e)) {
        final String reason =
            String.format("No repository with name [%s] could be found.", repositoryName);
        throw new MissingRepositoryException(reason);
      }
      if (isSnapshotMissingException(e)) {
        // no snapshots exist
        return new ArrayList<>();
      }
      final String reason =
          String.format("Exception occurred when searching for backups: %s", e.getMessage());
      throw new BackupRepositoryConnectionException(reason, e);
    }
  }

  @Override
  public void executeSnapshotting(
      final SnapshotRequest snapshotRequest, final Runnable onSuccess, final Runnable onFailure) {
    final var request =
        CreateSnapshotRequest.of(
            b ->
                b.repository(snapshotRequest.repositoryName())
                    .snapshot(snapshotRequest.snapshotName())
                    .indices(snapshotRequest.indices().allIndices())
                    // ignoreUnavailable = false - indices defined by their exact name MUST be
                    // present
                    // allowNoIndices = true - indices defined by wildcards, e.g. archived, MIGHT BE
                    // absent
                    .ignoreUnavailable(false)
                    // TODO not all migrated
                    //                    .(IndicesOptions.fromOptions(false, true, true, true))
                    .includeGlobalState(backupProps.includeGlobalState())
                    .metadata(
                        MetadataMarshaller.asJson(
                            snapshotRequest.metadata(), esClient._jsonpMapper()))
                    .featureStates("none")
                    .waitForCompletion(true));
    final var listener = new CreateSnapshotListener(snapshotRequest, onSuccess, onFailure);

    executor.execute(
        () -> {
          try {
            final var response = esClient.snapshot().create(request);
            listener.onResponse(response);
          } catch (final Exception e) {
            listener.onFailure(e);
          }
        });
  }

  private boolean isErrorType(final Exception e, final String errorType) {
    if (e instanceof ElasticsearchException) {
      final var type = ((ElasticsearchException) e).error().type();
      return Objects.equals(type, errorType);
    }
    return false;
  }

  private boolean isSnapshotMissingException(final Exception e) {
    return isErrorType(e, SNAPSHOT_MISSING_EXCEPTION_TYPE);
  }

  private boolean isRepositoryMissingException(final Exception e) {
    return isErrorType(e, REPOSITORY_MISSING_EXCEPTION_TYPE);
  }

  // Check: see inner
  public List<SnapshotInfo> findSnapshots(final String repositoryName, final Long backupId) {
    final GetSnapshotRequest snapshotsStatusRequest =
        GetSnapshotRequest.of(
            b ->
                b.repository(repositoryName)
                    .snapshot(snapshotNameProvider.getSnapshotNamePrefix(backupId) + "*"));

    final GetSnapshotResponse response;
    try {
      response = esClient.snapshot().get(snapshotsStatusRequest);
      if (response.snapshots().isEmpty()) {
        throw new ResourceNotFoundException(
            String.format("No backup with id [%s] found.", backupId));
      }
      return response.snapshots();
    } catch (final IOException ex) {
      final String reason =
          String.format(
              "Encountered an error connecting to Elasticsearch while searching for snapshots. Repository name: [%s].",
              repositoryName);
      throw new BackupRepositoryConnectionException(reason, ex);
    } catch (final ElasticsearchException e) {
      if (isSnapshotMissingException(e)) {
        // no snapshot with given backupID exists
        throw new ResourceNotFoundException(
            String.format("No backup with id [%s] found.", backupId));
      }
      if (isRepositoryMissingException(e)) {
        final String reason =
            String.format("No repository with name [%s] could be found.", repositoryName);
        throw new BackupException(reason);
      }
      final String reason =
          String.format("Exception occurred when searching for backup with ID [%s].", backupId);
      throw new BackupException(reason, e);
    }
  }

  // Check: Missing in tasklist!
  public boolean isSnapshotFinishedWithinTimeout(
      final String repositoryName, final String snapshotName) {
    int count = 0;
    final long startTime = System.currentTimeMillis();
    final int snapshotTimeout = backupProps.snapshotTimeout();
    final long backupId = snapshotNameProvider.extractBackupId(snapshotName);
    while (snapshotTimeout == 0
        || System.currentTimeMillis() - startTime <= snapshotTimeout * 1000L) {
      final List<SnapshotInfo> snapshotInfos = findSnapshots(repositoryName, backupId);
      final SnapshotInfo currentSnapshot =
          snapshotInfos.stream()
              .filter(x -> Objects.equals(x.snapshot(), snapshotName))
              .findFirst()
              .orElse(null);
      if (currentSnapshot == null) {
        LOGGER.error(
            "Expected (but not found) snapshot [{}] for backupId [{}].", snapshotName, backupId);
        // No need to continue
        return false;
      }
      if (Objects.equals(currentSnapshot.state(), IN_PROGRESS.name())) {
        try {
          Thread.sleep(100);
        } catch (final InterruptedException e) {
          throw new RuntimeException(e);
        }
        count++;
        if (count % 600 == 0) { // approx. 1 minute, depending on how long findSnapshots takes
          LOGGER.info("Waiting for snapshot [{}] to finish.", snapshotName);
        }
      } else {
        return snapshotWentWell(currentSnapshot);
      }
    }
    LOGGER.error(
        "Snapshot [{}] did not finish after configured timeout. Snapshot process won't continue.",
        snapshotName);
    return false;
  }

  private boolean snapshotWentWell(final SnapshotInfo snapshotInfo) {
    if (snapshotInfo != null && Objects.equals(snapshotInfo.state(), SUCCESS.name())) {
      LOGGER.info("Snapshot done: {}", snapshotInfo.snapshot());
      return true;
    } else if (snapshotInfo != null && Objects.equals(snapshotInfo.state(), FAILED.name())) {
      LOGGER.error(
          "Snapshot taking failed for {}, reason {}",
          snapshotInfo.snapshot(),
          snapshotInfo.reason());
      // No need to continue
      return false;
    } else {
      LOGGER.warn(
          "Snapshot state is {} for snapshot {}",
          snapshotInfo != null ? snapshotInfo.state() : null,
          snapshotInfo != null ? snapshotInfo.snapshot() : null);
      return false;
    }
  }

  // Check: done with differences see ChecK: BELOW
  private GetBackupStateResponseDto getBackupResponse(
      final Long backupId, final List<SnapshotInfo> snapshots) {
    final GetBackupStateResponseDto response = new GetBackupStateResponseDto(backupId);
    final var firstSnapshot = snapshots.getFirst();
    final Metadata metadata =
        MetadataMarshaller.fromMetadata(firstSnapshot.metadata(), esClient._jsonpMapper());
    final Integer expectedSnapshotsCount = metadata.partCount();

    if (snapshots.size() == expectedSnapshotsCount
        && snapshots.stream().map(SnapshotInfo::state).allMatch(SUCCESS.name()::equals)) {
      response.setState(BackupStateDto.COMPLETED);
    } else if (snapshots.stream()
        .map(SnapshotInfo::state)
        .anyMatch(s -> FAILED.name().equals(s) || PARTIAL.name().equals(s))) {
      response.setState(BackupStateDto.FAILED);
      // INCOMPATIBLE used to be present in the enum, but in the REST api docs we only have the
      // cases defined in SnapshotState
    } else if (snapshots.stream().map(SnapshotInfo::state).anyMatch("INCOMPATIBLE"::equals)) {
      response.setState(BackupStateDto.INCOMPATIBLE);
    } else if (snapshots.stream().map(SnapshotInfo::state).anyMatch(IN_PROGRESS.name()::equals)) {
      response.setState(BackupStateDto.IN_PROGRESS);
    } else if (snapshots.size() < expectedSnapshotsCount) {
      // Check: if missing in tasklist
      if (isIncompleteCheckTimedOut(
          backupProps.incompleteCheckTimeoutInSeconds(), snapshots.getLast().endTimeInMillis())) {
        response.setState(BackupStateDto.INCOMPLETE);
      } else {
        response.setState(BackupStateDto.IN_PROGRESS);
      }
    } else {
      response.setState(BackupStateDto.FAILED);
    }
    final List<GetBackupStateResponseDetailDto> details = new ArrayList<>();
    for (final SnapshotInfo snapshot : snapshots) {
      final GetBackupStateResponseDetailDto detail = new GetBackupStateResponseDetailDto();
      detail.setSnapshotName(snapshot.snapshot());
      detail.setStartTime(
          OffsetDateTime.ofInstant(
              Instant.ofEpochMilli(snapshot.startTimeInMillis()), ZoneId.systemDefault()));
      if (snapshot.failures() != null && !snapshot.failures().isEmpty()) {
        detail.setFailures(
            snapshot.failures().stream().map(SnapshotShardFailure::reason).toArray(String[]::new));
      }
      detail.setState(snapshot.state());
      details.add(detail);
    }
    response.setDetails(details);
    if (response.getState().equals(BackupStateDto.FAILED)) {
      String failureReason = null;
      final String failedSnapshots =
          snapshots.stream()
              .filter(s -> Objects.equals(s.state(), FAILED.name()))
              .map(SnapshotInfo::snapshot)
              .collect(Collectors.joining(", "));
      if (!failedSnapshots.isEmpty()) {
        failureReason =
            String.format("There were failures with the following snapshots: %s", failedSnapshots);
      } else {
        final String partialSnapshot =
            snapshots.stream()
                .filter(s -> Objects.equals(s.state(), PARTIAL.name()))
                .map(SnapshotInfo::snapshot)
                .collect(Collectors.joining(", "));
        if (!partialSnapshot.isEmpty()) {
          failureReason = String.format("Some of the snapshots are partial: %s", partialSnapshot);
        } else if (snapshots.size() > expectedSnapshotsCount) {
          failureReason = "More snapshots found than expected.";
        }
      }
      if (failureReason != null) {
        response.setFailureReason(failureReason);
      }
    }
    return response;
  }

  /** CreateSnapshotListener */
  public class CreateSnapshotListener {

    private final BackupService.SnapshotRequest snapshotRequest;
    private final long backupId;
    private final Runnable onSuccess;
    private final Runnable onFailure;

    public CreateSnapshotListener(
        final BackupService.SnapshotRequest snapshotRequest,
        final Runnable onSuccess,
        final Runnable onFailure) {
      this.snapshotRequest = snapshotRequest;
      backupId = snapshotNameProvider.extractBackupId(snapshotRequest.snapshotName());
      this.onSuccess = onSuccess;
      this.onFailure = onFailure;
    }

    public void onResponse(final CreateSnapshotResponse response) {
      if (snapshotWentWell(response.snapshot())) {
        onSuccess.run();
      } else {
        onFailure.run();
      }
    }

    // Check: tasklist does not wait for the snapshot to finish
    public void onFailure(final Exception ex) {
      if (ex instanceof SocketTimeoutException) {
        // This is thrown even if the backup is still running
        final int snapshotTimeout = backupProps.snapshotTimeout();
        LOGGER.warn(
            "Socket timeout while creating snapshot [{}] for backup id [{}]. Start waiting with polling timeout, {}",
            snapshotRequest.snapshotName(),
            backupId,
            (snapshotTimeout == 0)
                ? "until completion."
                : "at most " + snapshotTimeout + " seconds.");
        if (isSnapshotFinishedWithinTimeout(
            snapshotRequest.snapshotName(), snapshotRequest.repositoryName())) {
          onSuccess.run();
        } else {
          onFailure.run();
        }
      } else {
        LOGGER.error(
            "Exception while creating snapshot [{}] for backup id [{}].",
            snapshotRequest.snapshotName(),
            backupId,
            ex);
        // No need to continue
        onFailure.run();
      }
    }
  }
}
