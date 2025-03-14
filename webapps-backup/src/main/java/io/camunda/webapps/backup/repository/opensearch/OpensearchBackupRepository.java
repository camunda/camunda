/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.backup.repository.opensearch;

import static io.camunda.webapps.backup.repository.opensearch.OpensearchRequestDSL.createSnapshotRequestBuilder;
import static io.camunda.webapps.backup.repository.opensearch.OpensearchRequestDSL.deleteSnapshotRequestBuilder;
import static io.camunda.webapps.backup.repository.opensearch.OpensearchRequestDSL.getSnapshotRequestBuilder;
import static io.camunda.webapps.backup.repository.opensearch.OpensearchRequestDSL.repositoryRequestBuilder;
import static io.camunda.webapps.backup.repository.opensearch.SnapshotState.FAILED;
import static io.camunda.webapps.backup.repository.opensearch.SnapshotState.PARTIAL;
import static io.camunda.webapps.backup.repository.opensearch.SnapshotState.STARTED;
import static io.camunda.webapps.backup.repository.opensearch.SnapshotState.SUCCESS;
import static java.lang.String.format;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import io.camunda.webapps.backup.BackupException;
import io.camunda.webapps.backup.BackupException.*;
import io.camunda.webapps.backup.BackupRepository;
import io.camunda.webapps.backup.BackupService;
import io.camunda.webapps.backup.BackupService.SnapshotRequest;
import io.camunda.webapps.backup.BackupStateDto;
import io.camunda.webapps.backup.GetBackupStateResponseDetailDto;
import io.camunda.webapps.backup.GetBackupStateResponseDto;
import io.camunda.webapps.backup.Metadata;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import io.camunda.webapps.backup.repository.SnapshotNameProvider;
import io.camunda.webapps.util.ExceptionSupplier;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.indices.GetIndexRequest;
import org.opensearch.client.opensearch.snapshot.GetSnapshotResponse;
import org.opensearch.client.opensearch.snapshot.SnapshotInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpensearchBackupRepository implements BackupRepository {
  public static final String SNAPSHOT_MISSING_EXCEPTION_TYPE = "snapshot_missing_exception";
  public static final String REPOSITORY_MISSING_EXCEPTION_TYPE = "repository_missing_exception";
  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchBackupRepository.class);

  private final OpenSearchClient openSearchClient;
  private final OpenSearchAsyncClient openSearchAsyncClient;

  private final BackupRepositoryProps backupProps;
  private final SnapshotNameProvider snapshotNameProvider;

  public OpensearchBackupRepository(
      final OpenSearchClient openSearchClient,
      final OpenSearchAsyncClient openSearchAsyncClient,
      final BackupRepositoryProps operateProperties,
      final SnapshotNameProvider snapshotNameProvider) {
    this.openSearchClient = openSearchClient;
    this.openSearchAsyncClient = openSearchAsyncClient;
    backupProps = operateProperties;
    this.snapshotNameProvider = snapshotNameProvider;
  }

  @Override
  public SnapshotNameProvider snapshotNameProvider() {
    return snapshotNameProvider;
  }

  @Override
  public void deleteSnapshot(final String repositoryName, final String snapshotName) {
    final var request = deleteSnapshotRequestBuilder(repositoryName, snapshotName).build();
    safe(
            () -> openSearchAsyncClient.snapshot().delete(request),
            e -> "Failed to send snapshot delete request")
        .thenAccept(
            response ->
                LOGGER.debug(
                    "Delete snapshot was acknowledged by Opensearch node: {}",
                    response.acknowledged()))
        .exceptionally(
            t -> {
              if (isSnapshotMissingException(t)) {
                // no snapshot with given backupID exists, this is fine, log warning
                LOGGER.warn("No snapshot found for snapshot deletion: {} ", t.getMessage());
              } else {
                LOGGER.error(
                    "Exception occurred while deleting the snapshot: {} ", t.getMessage(), t);
              }
              return null;
            });
  }

  @Override
  public void validateRepositoryExists(final String repositoryName) {
    try {
      final var repositoryResponse =
          openSearchClient
              .snapshot()
              .getRepository(repositoryRequestBuilder(repositoryName).build());
      LOGGER.debug("Repository {} exists", repositoryResponse);
    } catch (final Exception e) {
      if (isRepositoryMissingException(e)) {
        final String reason = noRepositoryErrorMessage(repositoryName);
        throw new BackupException(reason);
      }
      final String reason =
          format(
              "Exception occurred when validating existence of repository with name [%s].",
              repositoryName);
      throw new BackupException(reason, e);
    }
  }

  @Override
  public void validateNoDuplicateBackupId(final String repositoryName, final Long backupId) {
    final String snapshot = snapshotNameProvider.getSnapshotNamePrefix(backupId) + "*";

    final GetSnapshotResponse response;
    try {
      response =
          openSearchClient
              .snapshot()
              .get(getSnapshotRequestBuilder(repositoryName, snapshot).build());
    } catch (final Exception e) {
      if (isSnapshotMissingException(e)) {
        // no snapshot with given backupID exists
        return;
      }
      final String reason =
          format(
              "Exception occurred when validating whether backup with ID [%s] already exists.",
              backupId);
      throw new BackupException(reason, e);
    }
    if (!response.snapshots().isEmpty()) {
      final String reason =
          format(
              "A backup with ID [%s] already exists. Found snapshots: [%s]",
              backupId,
              response.snapshots().stream().map(SnapshotInfo::uuid).collect(joining(", ")));
      throw new InvalidRequestException(reason);
    }
  }

  @Override
  public GetBackupStateResponseDto getBackupState(
      final String repositoryName, final Long backupId) {
    final List<OpenSearchSnapshotInfo> snapshots = findSnapshots(repositoryName, backupId);
    return toGetBackupStateResponseDto(backupId, snapshots);
  }

  @Override
  public Optional<Metadata> getMetadata(final String repositoryName, final Long backupId) {
    final var snapshots = findSnapshots(repositoryName, backupId);
    if (snapshots.isEmpty()) {
      return Optional.empty();
    } else {
      final var first = snapshots.getFirst();
      return Optional.of(
          MetadataMarshaller.fromMetadata(
              first.getMetadata(), openSearchClient._transport().jsonpMapper()));
    }
  }

  @Override
  public Set<String> checkAllIndicesExist(final List<String> indices) {
    try {
      final var response =
          openSearchClient
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
    final var requestBuilder =
        getSnapshotRequestBuilder(repositoryName, snapshotNameProvider.snapshotNamePrefix() + "*")
            .build();
    final OpenSearchGetSnapshotResponse response;
    try {
      response =
          OpenSearchGetSnapshotResponse.fromResponse(
              openSearchClient.snapshot().get(requestBuilder));
      final List<OpenSearchSnapshotInfo> snapshots =
          response.snapshots().stream()
              .sorted(Comparator.comparing(OpenSearchSnapshotInfo::getStartTimeInMillis).reversed())
              .toList();

      final LinkedHashMap<Long, List<OpenSearchSnapshotInfo>> groupedSnapshotInfos =
          snapshots.stream()
              .collect(
                  groupingBy(
                      si -> {
                        final Metadata metadata =
                            MetadataMarshaller.fromMetadata(
                                si.getMetadata(), openSearchClient._transport().jsonpMapper());
                        Long backupId = metadata.backupId();
                        // backward compatibility with v. 8.1
                        if (backupId == null) {
                          backupId = snapshotNameProvider.extractBackupId(si.getSnapshot());
                        }
                        return backupId;
                      },
                      LinkedHashMap::new,
                      toList()));

      return groupedSnapshotInfos.entrySet().stream()
          .map(entry -> toGetBackupStateResponseDto(entry.getKey(), entry.getValue()))
          .toList();

    } catch (final Exception e) {
      if (isRepositoryMissingException(e)) {
        final String reason = noRepositoryErrorMessage(repositoryName);
        throw new BackupException(reason);
      }
      if (isSnapshotMissingException(e)) {
        // no snapshots exist
        return new ArrayList<>();
      }
      final String reason =
          format("Exception occurred when searching for backups: %s", e.getMessage());
      throw new BackupException(reason, e);
    }
  }

  @Override
  public void executeSnapshotting(
      final SnapshotRequest snapshotRequest, final Runnable onSuccess, final Runnable onFailure) {
    final Long backupId = backupId(snapshotRequest);
    final Map<String, JsonData> metadataJson =
        MetadataMarshaller.asJson(
            snapshotRequest.metadata(), openSearchClient._transport().jsonpMapper());

    final var requestBuilder =
        createSnapshotRequestBuilder(
                snapshotRequest.repositoryName(),
                snapshotRequest.snapshotName(),
                snapshotRequest.indices().indices())
            .ignoreUnavailable(
                false) // ignoreUnavailable = false - indices defined by their exact name MUST be
            // present
            .includeGlobalState(backupProps.includeGlobalState())
            .metadata(metadataJson)
            .featureStates("none")
            .waitForCompletion(true)
            .build();

    safe(
            () -> openSearchAsyncClient.snapshot().create(requestBuilder),
            e -> "Failed to send snapshot create request!")
        .thenAccept(response -> handleSnapshotReceived(response.snapshot(), onSuccess, onFailure))
        .exceptionally(
            t -> {
              final Throwable e;
              if (t instanceof final CompletionException ce) {
                e = ce.getCause();
              } else {
                e = t;
              }
              if (e instanceof SocketTimeoutException) {
                // This is thrown even if the backup is still running
                LOGGER.warn(
                    "Timeout while creating snapshot [{}] for backup id [{}]. Need to keep waiting with polling...",
                    snapshotRequest.snapshotName(),
                    backupId);
                // Keep waiting
                while (true) {
                  final List<OpenSearchSnapshotInfo> snapshotInfos =
                      findSnapshots(snapshotRequest.repositoryName(), backupId);
                  final Optional<OpenSearchSnapshotInfo> maybeCurrentSnapshot =
                      snapshotInfos.stream()
                          .filter(
                              x -> Objects.equals(x.getSnapshot(), snapshotRequest.snapshotName()))
                          .findFirst();

                  if (maybeCurrentSnapshot.isEmpty()) {
                    LOGGER.error(
                        "Expected (but not found) snapshot [{}] for backupId [{}].",
                        snapshotRequest.snapshotName(),
                        backupId);
                    // No need to continue
                    onFailure.run();
                    break;
                  } else if (STARTED.equals(maybeCurrentSnapshot.get().getState())) {
                    try {
                      Thread.sleep(100);
                    } catch (final InterruptedException ex) {
                      throw new RuntimeException(ex);
                    }
                  } else {
                    handleSnapshotReceived(maybeCurrentSnapshot.get(), onSuccess, onFailure);
                    break;
                  }
                }
              } else {
                LOGGER.error(
                    "Exception while creating snapshot [{}] for backup id [{}].",
                    snapshotRequest.snapshotName(),
                    backupId,
                    e);
                // No need to continue
                onFailure.run();
              }

              return null;
            });
  }

  private boolean isErrorType(final Throwable t, final String errorType) {
    if (t instanceof final OpenSearchException oe) {
      return Objects.equals(oe.error().type(), errorType);
    }
    return false;
  }

  private boolean isSnapshotMissingException(final Throwable t) {
    return isErrorType(t, SNAPSHOT_MISSING_EXCEPTION_TYPE);
  }

  private boolean isRepositoryMissingException(final Exception e) {
    return isErrorType(e, REPOSITORY_MISSING_EXCEPTION_TYPE);
  }

  private static String noRepositoryErrorMessage(final String repositoryName) {
    return format("No repository with name [%s] could be found.", repositoryName);
  }

  private Long backupId(final BackupService.SnapshotRequest snapshotRequest) {
    return snapshotNameProvider.extractBackupId(snapshotRequest.snapshotName());
  }

  private void handleSnapshotReceived(
      final OpenSearchSnapshotInfo snapshotInfo,
      final Runnable onSuccess,
      final Runnable onFailure) {
    if (SUCCESS.equals(snapshotInfo.getState())) {
      LOGGER.info("Snapshot done: {}", snapshotInfo.getUuid());
      onSuccess.run();
    } else if (FAILED.equals(snapshotInfo.getState())) {
      LOGGER.error("Snapshot taking failed for {}", snapshotInfo.getUuid());
      // No need to continue
      onFailure.run();
    } else {
      LOGGER.warn(
          "Snapshot state is {} for snapshot {}", snapshotInfo.getState(), snapshotInfo.getUuid());
      onSuccess.run();
    }
  }

  private void handleSnapshotReceived(
      final SnapshotInfo snapshotInfo, final Runnable onSuccess, final Runnable onFailure) {
    if (SUCCESS.equals(SnapshotState.valueOf(snapshotInfo.state()))) {
      LOGGER.info("Snapshot done: {}", snapshotInfo.uuid());
      onSuccess.run();
    } else if (FAILED.equals(SnapshotState.valueOf(snapshotInfo.state()))) {
      LOGGER.error(
          "Snapshot taking failed for {}, reason {}", snapshotInfo.uuid(), snapshotInfo.reason());
      // No need to continue
      onFailure.run();
    } else {
      LOGGER.warn(
          "Snapshot state is {} for snapshot {}", snapshotInfo.state(), snapshotInfo.uuid());
      onSuccess.run();
    }
  }

  private List<OpenSearchSnapshotInfo> findSnapshots(
      final String repositoryName, final Long backupId) {
    final var requestBuilder =
        getSnapshotRequestBuilder(
                repositoryName, snapshotNameProvider.getSnapshotNamePrefix(backupId) + "*")
            .build();

    final OpenSearchGetSnapshotResponse response;
    try {
      response =
          OpenSearchGetSnapshotResponse.fromResponse(
              openSearchClient.snapshot().get(requestBuilder));
      return response.snapshots();
    } catch (final Exception e) {
      if (isSnapshotMissingException(e)) {
        // no snapshot with given backupID exists
        throw new ResourceNotFoundException(format("No backup with id [%s] found.", backupId));
      }
      if (isRepositoryMissingException(e)) {
        final String reason = noRepositoryErrorMessage(repositoryName);
        throw new BackupException(reason);
      }
      final String reason =
          format("Exception occurred when searching for backup with ID [%s].", backupId);
      throw new BackupException(reason, e);
    }
  }

  private BackupStateDto getState(
      final List<OpenSearchSnapshotInfo> snapshots, final Integer expectedSnapshotsCount) {
    if (snapshots.size() == expectedSnapshotsCount
        && snapshots.stream().map(OpenSearchSnapshotInfo::getState).allMatch(SUCCESS::equals)) {
      return BackupStateDto.COMPLETED;
    } else if (snapshots.stream()
        .map(OpenSearchSnapshotInfo::getState)
        .anyMatch(s -> FAILED.equals(s) || PARTIAL.equals(s))) {
      return BackupStateDto.FAILED;
    } else if (snapshots.stream().map(OpenSearchSnapshotInfo::getState).anyMatch(STARTED::equals)) {
      return BackupStateDto.IN_PROGRESS;
    } else if (snapshots.size() < expectedSnapshotsCount) {
      if (isIncompleteCheckTimedOut(
          backupProps.incompleteCheckTimeoutInSeconds(),
          snapshots.getLast().getEndTimeInMillis())) {
        return BackupStateDto.INCOMPLETE;
      } else {
        return BackupStateDto.IN_PROGRESS;
      }
    } else {
      return BackupStateDto.FAILED;
    }
  }

  private GetBackupStateResponseDto toGetBackupStateResponseDto(
      final Long backupId, final List<OpenSearchSnapshotInfo> snapshots) {
    final GetBackupStateResponseDto response = new GetBackupStateResponseDto(backupId);
    final Metadata metadata =
        MetadataMarshaller.fromMetadata(
            snapshots.getFirst().getMetadata(), openSearchClient._transport().jsonpMapper());
    final Integer expectedSnapshotsCount = metadata.partCount();

    response.setState(getState(snapshots, expectedSnapshotsCount));
    response.setDetails(getBackupStateDetails(snapshots));

    final var failureReason =
        getFailureReason(snapshots, response.getState(), expectedSnapshotsCount);
    if (failureReason != null) {
      response.setFailureReason(failureReason);
    }
    return response;
  }

  private List<GetBackupStateResponseDetailDto> getBackupStateDetails(
      final List<OpenSearchSnapshotInfo> snapshots) {
    final List<GetBackupStateResponseDetailDto> details = new ArrayList<>();
    for (final OpenSearchSnapshotInfo snapshot : snapshots) {
      final GetBackupStateResponseDetailDto detail = new GetBackupStateResponseDetailDto();
      detail.setSnapshotName(snapshot.getSnapshot());
      detail.setStartTime(
          OffsetDateTime.ofInstant(
              Instant.ofEpochMilli(snapshot.getStartTimeInMillis()), ZoneId.systemDefault()));
      if (!snapshot.getFailures().isEmpty()) {
        detail.setFailures(
            snapshot.getFailures().stream().map(Object::toString).toArray(String[]::new));
      }
      detail.setState(snapshot.getState().toString());
      details.add(detail);
    }
    return details;
  }

  private String getFailureReason(
      final List<OpenSearchSnapshotInfo> snapshots,
      final BackupStateDto state,
      final Integer expectedSnapshotsCount) {
    if (state == BackupStateDto.FAILED) {
      final String failedSnapshots =
          snapshots.stream()
              .filter(s -> FAILED.equals(s.getState()))
              .map(OpenSearchSnapshotInfo::getSnapshot)
              .collect(Collectors.joining(", "));
      if (!failedSnapshots.isEmpty()) {
        return format("There were failures with the following snapshots: %s", failedSnapshots);
      } else {
        final String partialSnapshot =
            snapshots.stream()
                .filter(s -> PARTIAL.equals(s.getState()))
                .map(OpenSearchSnapshotInfo::getSnapshot)
                .collect(Collectors.joining(", "));
        if (!partialSnapshot.isEmpty()) {
          return format("Some of the snapshots are partial: %s", partialSnapshot);
        } else if (snapshots.size() > expectedSnapshotsCount) {
          return "More snapshots found than expected.";
        }
      }
    }
    return null;
  }

  private <R> R safe(
      final ExceptionSupplier<R> supplier, final Function<Exception, String> errorMessage) {
    try {
      return supplier.get();
    } catch (final OpenSearchException e) {
      throw e;
    } catch (final Exception e) {
      final String message = errorMessage.apply(e);
      LOGGER.error(message, e);
      throw new BackupException(message, e);
    }
  }
}
