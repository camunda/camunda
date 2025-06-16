/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.es.backup.os;

import static java.lang.String.format;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.TasklistElasticsearchConnectionException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.ThreadUtil;
import io.camunda.tasklist.webapp.es.backup.BackupManager;
import io.camunda.tasklist.webapp.es.backup.Metadata;
import io.camunda.tasklist.webapp.es.backup.os.response.SnapshotState;
import io.camunda.tasklist.webapp.management.dto.BackupStateDto;
import io.camunda.tasklist.webapp.management.dto.GetBackupStateResponseDetailDto;
import io.camunda.tasklist.webapp.management.dto.GetBackupStateResponseDto;
import io.camunda.tasklist.webapp.management.dto.TakeBackupRequestDto;
import io.camunda.tasklist.webapp.management.dto.TakeBackupResponseDto;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import io.camunda.zeebe.util.VisibleForTesting;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.snapshot.CreateSnapshotRequest;
import org.opensearch.client.opensearch.snapshot.DeleteSnapshotRequest;
import org.opensearch.client.opensearch.snapshot.DeleteSnapshotResponse;
import org.opensearch.client.opensearch.snapshot.GetRepositoryRequest;
import org.opensearch.client.opensearch.snapshot.GetRepositoryResponse;
import org.opensearch.client.opensearch.snapshot.GetSnapshotRequest;
import org.opensearch.client.opensearch.snapshot.SnapshotInfo;
import org.opensearch.client.opensearch.snapshot.SnapshotShardFailure;
import org.opensearch.client.transport.JsonEndpoint;
import org.opensearch.client.transport.endpoints.SimpleEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@Configuration
@Conditional(OpenSearchCondition.class)
public class BackupManagerOpenSearch extends BackupManager {

  public static final String SNAPSHOT_MISSING_EXCEPTION_TYPE = "snapshot_missing_exception";
  private static final Logger LOGGER = LoggerFactory.getLogger(BackupManagerOpenSearch.class);
  private static final String REPOSITORY_MISSING_EXCEPTION_TYPE = "repository_missing_exception";
  private final Queue<CreateSnapshotRequest> requestsQueue = new ConcurrentLinkedQueue<>();
  private final AtomicReference<CreateSnapshotRequest> currentRequest = new AtomicReference<>(null);

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired
  @Qualifier("tasklistOsAsyncClient")
  private OpenSearchAsyncClient openSearchAsyncClient;

  @Autowired
  @Qualifier("tasklistOsClient")
  private OpenSearchClient openSearchClient;

  @Override
  public void deleteBackup(final Long backupId) {
    validateRepositoryExists();
    final String repositoryName = getRepositoryName();
    final int count = getIndexPatternsOrdered().length;
    final String version = getCurrentTasklistVersion();
    for (int index = 0; index < count; index++) {
      final String snapshotName =
          new Metadata()
              .setVersion(version)
              .setPartCount(count)
              .setPartNo(index + 1)
              .setBackupId(backupId)
              .buildSnapshotName();
      final DeleteSnapshotRequest request =
          DeleteSnapshotRequest.of(dsr -> dsr.repository(repositoryName).snapshot(snapshotName));
      try {
        openSearchAsyncClient
            .snapshot()
            .delete(request)
            .whenComplete(BackupManagerOpenSearch::handleSnapshotDeletion);
      } catch (final IOException | OpenSearchException e) {
        LOGGER.error("Exception occurred while deleting the snapshot: " + e.getMessage(), e);
        throw new TasklistRuntimeException("Exception occurred while deleting the snapshot", e);
      }
    }
  }

  @Override
  public TakeBackupResponseDto takeBackup(final TakeBackupRequestDto request) {
    validateRepositoryExists();
    validateNoDuplicateBackupId(request.getBackupId());
    if (requestsQueue.size() > 0) {
      throw new InvalidRequestException("Another backup is running at the moment");
    }
    synchronized (requestsQueue) {
      if (requestsQueue.size() > 0) {
        throw new InvalidRequestException("Another backup is running at the moment");
      }
      return scheduleSnapshots(request);
    }
  }

  @Override
  public GetBackupStateResponseDto getBackupState(final Long backupId) {
    final boolean isBackupInProgress = isBackupInProgress(backupId);
    final List<SnapshotInfo> snapshots = findSnapshots(backupId);
    return getBackupResponse(backupId, snapshots, isBackupInProgress);
  }

  @Override
  public List<GetBackupStateResponseDto> getBackups(final boolean verbose, final String pattern) {
    final var validatedPattern = validPattern(pattern);

    validatedPattern.ifLeft(
        ex -> {
          throw new InvalidRequestException(ex.getMessage(), ex);
        });
    final GetSnapshotRequest snapshotStatusRequest =
        GetSnapshotRequest.of(
            gsr ->
                gsr.repository(getRepositoryName())
                    .snapshot(Metadata.SNAPSHOT_NAME_PREFIX + validatedPattern.get())
                    .verbose(verbose));
    final GetCustomSnapshotResponse response;
    try {
      response = getCustomSnapshotResponse(snapshotStatusRequest);
      var snapshots = response.snapshots();
      if (verbose) {
        snapshots =
            snapshots.stream()
                .sorted(Comparator.comparing(SnapshotInfo::startTimeInMillis).reversed())
                .toList();
      }

      final LinkedHashMap<Long, List<SnapshotInfo>> groupedSnapshotInfos =
          snapshots.stream()
              .collect(
                  groupingBy(
                      si -> {
                        final Metadata metadata = getMetadata(si.metadata(), si.snapshot());
                        Long backupId = metadata.getBackupId();
                        // backward compatibility with v. 8.1
                        if (backupId == null) {
                          backupId = Metadata.extractBackupIdFromSnapshotName(si.snapshot());
                        }
                        return backupId;
                      },
                      LinkedHashMap::new,
                      toList()));

      return groupedSnapshotInfos.entrySet().stream()
          .map(
              entry ->
                  getBackupResponse(
                      entry.getKey(), entry.getValue(), isBackupInProgress(entry.getKey())))
          .collect(toList());
    } catch (final IOException ex) {
      final String reason =
          String.format(
              "Encountered an error connecting to Elasticsearch while searching for snapshots. Repository name: [%s].",
              getRepositoryName());
      throw new TasklistElasticsearchConnectionException(reason, ex);
    } catch (final Exception e) {
      if (isRepositoryMissingException(e)) {
        final String reason =
            String.format(
                "No repository with name [%s] could be found.",
                tasklistProperties.getBackup().getRepositoryName());
        throw new TasklistRuntimeException(reason);
      }
      if (isSnapshotMissingException(e)) {
        // no snapshots exist
        return new ArrayList<>();
      }
      final String reason =
          String.format("Exception occurred when searching for backups: %s", e.getMessage());
      throw new TasklistRuntimeException(reason, e);
    }
  }

  private static void handleSnapshotDeletion(
      final DeleteSnapshotResponse result, final Throwable ex) {
    if (ex != null) {
      if (isSnapshotMissingException(ex)) {
        LOGGER.warn("No snapshot found for snapshot deletion: " + ex.getMessage());
      } else {
        LOGGER.error("Exception occurred while deleting the snapshot: " + ex.getMessage(), ex);
      }
    } else {
      LOGGER.debug("Delete snapshot was acknowledged by OpenSearch node: " + result.acknowledged());
    }
  }

  @VisibleForTesting
  void validateRepositoryExists() {
    final String repositoryName = getRepositoryName();
    final GetRepositoryRequest getRepositoryRequest =
        GetRepositoryRequest.of(grr -> grr.name(repositoryName));
    try {
      getRepository(getRepositoryRequest);
    } catch (final IOException ex) {
      final String reason =
          String.format(
              "Encountered an error connecting to Elasticsearch while retrieving repository with name [%s].",
              repositoryName);
      throw new TasklistElasticsearchConnectionException(reason, ex);
    } catch (final Exception e) {
      if (isRepositoryMissingException(e)) {
        final String reason =
            String.format("No repository with name [%s] could be found.", repositoryName);
        throw new TasklistRuntimeException(reason);
      }
      final String reason =
          String.format(
              "Exception occurred when validating existence of repository with name [%s].",
              repositoryName);
      throw new TasklistRuntimeException(reason, e);
    }
  }

  private void getRepository(final GetRepositoryRequest getRepositoryRequest) throws IOException {
    /**
     * Workaround for erroneous required properties in {@link
     * org.opensearch.client.opensearch.snapshot.RepositorySettings}. Skipping response
     * deserialization as it is not needed.
     *
     * <p>see https://github.com/camunda/tasklist/issues/5125
     */
    final SimpleEndpoint<GetRepositoryRequest, Object> endpoint =
        ((SimpleEndpoint<GetRepositoryRequest, GetRepositoryResponse>)
                (GetRepositoryRequest._ENDPOINT))
            .withResponseDeserializer(null);

    openSearchAsyncClient
        ._transport()
        .performRequestAsync(
            getRepositoryRequest, endpoint, openSearchAsyncClient._transportOptions())
        .join();
  }

  private static boolean isSnapshotMissingException(final Throwable e) {
    return e.getMessage() != null && e.getMessage().contains(SNAPSHOT_MISSING_EXCEPTION_TYPE);
  }

  private boolean isRepositoryMissingException(final Exception e) {
    return e.getMessage() != null && e.getMessage().contains(REPOSITORY_MISSING_EXCEPTION_TYPE);
  }

  private void validateNoDuplicateBackupId(final Long backupId) {
    final GetSnapshotRequest snapshotsStatusRequest =
        GetSnapshotRequest.of(
            gsr ->
                gsr.repository(getRepositoryName())
                    .snapshot(Metadata.buildSnapshotNamePrefix(backupId) + "*"));

    final GetCustomSnapshotResponse response;
    try {
      response = getCustomSnapshotResponse(snapshotsStatusRequest);
    } catch (final IOException ex) {
      final String reason =
          String.format(
              "Encountered an error connecting to Elasticsearch while searching for duplicate backup. Repository name: [%s].",
              getRepositoryName());
      throw new TasklistElasticsearchConnectionException(reason, ex);
    } catch (final Exception e) {
      if (isSnapshotMissingException(e)) {
        // no snapshot with given backupID exists
        return;
      }
      final String reason =
          String.format(
              "Exception occurred when validating whether backup with ID [%s] already exists.",
              backupId);
      throw new TasklistRuntimeException(reason, e);
    }
    if (!response.snapshots().isEmpty()) {
      final String reason =
          String.format(
              "A backup with ID [%s] already exists. Found snapshots: [%s]",
              backupId,
              response.snapshots().stream().map(this::getSnapshotId).collect(joining(", ")));
      throw new InvalidRequestException(reason);
    }
  }

  private TakeBackupResponseDto scheduleSnapshots(final TakeBackupRequestDto request) {
    final String repositoryName = getRepositoryName();
    final int count = getIndexPatternsOrdered().length;
    final List<String> snapshotNames = new ArrayList<>();
    final String version = getCurrentTasklistVersion();
    for (int index = 0; index < count; index++) {
      final String[] indexPattern = getIndexPatternsOrdered()[index];
      final Metadata metadata =
          new Metadata()
              .setVersion(version)
              .setPartCount(count)
              .setPartNo(index + 1)
              .setBackupId(request.getBackupId());
      final String snapshotName = metadata.buildSnapshotName();
      requestsQueue.offer(
          CreateSnapshotRequest.of(
              csr ->
                  csr.repository(repositoryName)
                      .snapshot(snapshotName)
                      .indices(Arrays.stream(indexPattern).toList())
                      // ignoreUnavailable = false - indices defined by their exact name MUST be
                      // present
                      .ignoreUnavailable(false)
                      .includeGlobalState(true)
                      .metadata(
                          Map.of(
                              "backupId", JsonData.of(metadata.getBackupId()),
                              "version", JsonData.of(metadata.getVersion()),
                              "partNo", JsonData.of(metadata.getPartNo()),
                              "partCount", JsonData.of(metadata.getPartCount())))
                      .featureStates("none")
                      .waitForCompletion(true)));
      LOGGER.debug("Snapshot scheduled: " + snapshotName);
      snapshotNames.add(snapshotName);
    }
    // schedule next snapshot
    scheduleNextSnapshot();
    return new TakeBackupResponseDto().setScheduledSnapshots(snapshotNames);
  }

  private void scheduleNextSnapshot() {
    final CreateSnapshotRequest nextRequest = requestsQueue.poll();
    currentRequest.set(nextRequest); // if the queue is empty, currentRequest will be set to null
    if (nextRequest != null) {
      getTaskExecutor().submit(() -> executeSnapshotting(nextRequest));
      LOGGER.debug(
          "Snapshot picked for execution: snapshot [{}:{}]",
          nextRequest.repository(),
          nextRequest.snapshot());
    }
  }

  protected void executeSnapshotting(final CreateSnapshotRequest snapshotRequest) {
    try {
      openSearchAsyncClient
          .snapshot()
          .create(snapshotRequest)
          .whenComplete(
              (response, ex) -> {
                handleSnapshotResponse(response.snapshot());
              })
          .exceptionally(
              e -> {
                final Long backupId =
                    Metadata.extractBackupIdFromSnapshotName(snapshotRequest.snapshot());
                if (e.getCause() instanceof SocketTimeoutException) {
                  // This is thrown even if the backup is still running
                  LOGGER.warn(
                      format(
                          "Timeout while creating snapshot [%s] for backup id [%d]. Need to keep waiting with polling...",
                          snapshotRequest.snapshot(),
                          Metadata.extractBackupIdFromSnapshotName(snapshotRequest.snapshot())));
                  // Keep waiting
                  final List<SnapshotInfo> snapshotInfos = findSnapshots(backupId);
                  final Optional<SnapshotInfo> maybeCurrentSnapshot =
                      snapshotInfos.stream()
                          .filter(x -> Objects.equals(x.snapshot(), snapshotRequest.snapshot()))
                          .findFirst();

                  if (maybeCurrentSnapshot.isEmpty()) {
                    LOGGER.error(
                        format(
                            "Expected (but not found) snapshot [%s] for backupId [%d].",
                            snapshotRequest.snapshot(), backupId));
                    // No need to continue
                    onSnapshotFailure();
                  } else if (isSnapshotFinishedWithinTimeout(
                      maybeCurrentSnapshot.get().snapshot())) {
                    scheduleNextSnapshot();
                  } else {
                    onSnapshotFailure();
                  }
                } else {
                  LOGGER.error(
                      format(
                          "Exception while creating snapshot [%s] for backup id [%d].",
                          snapshotRequest.snapshot(), backupId),
                      e);
                  // No need to continue
                  onSnapshotFailure();
                }
                return null;
              });
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e);
    }
  }

  private void onSnapshotFailure() {
    currentRequest.set(null);
    requestsQueue.clear();
  }

  private void handleSnapshotResponse(final SnapshotInfo snapshotInfo) {
    final String snapshotId = getSnapshotId(snapshotInfo);
    final String snapshotState = Objects.requireNonNullElse(snapshotInfo.state(), "null");
    switch (snapshotState) {
      case "SUCCESS" -> {
        LOGGER.info("Snapshot done: {}", snapshotId);
        scheduleNextSnapshot();
      }
      case "FAILED" -> {
        LOGGER.error("Snapshot failed for {}, reason {}", snapshotId, snapshotInfo.reason());
        onSnapshotFailure(); // no need to continue
      }
      default -> {
        LOGGER.error("Unexpected snapshot state '{}' for {}", snapshotState, snapshotId);
        onSnapshotFailure();
      }
    }
  }

  private boolean isSnapshotFinished(final SnapshotInfo snapshotInfo) {
    return (Objects.requireNonNullElse(snapshotInfo.snapshot(), "null"))
        .equals(SnapshotState.SUCCESS.name());
  }

  private String getSnapshotId(final SnapshotInfo snapshotInfo) {
    return String.format("%s/%s", snapshotInfo.snapshot(), snapshotInfo.uuid());
  }

  List<SnapshotInfo> findSnapshots(final Long backupId) {
    final GetSnapshotRequest snapshotStatusRequest =
        GetSnapshotRequest.of(
            gsr ->
                gsr.repository(getRepositoryName())
                    .snapshot(Metadata.buildSnapshotNamePrefix(backupId) + "*"));

    try {
      return getCustomSnapshotResponse(snapshotStatusRequest).snapshots();
    } catch (final IOException ex) {
      final String reason =
          String.format(
              "Encountered an error connecting to Elasticsearch while searching for snapshots. Repository name: [%s].",
              getRepositoryName());
      throw new TasklistElasticsearchConnectionException(reason, ex);
    } catch (final Exception e) {
      if (isSnapshotMissingException(e)) {
        // no snapshot with given backupID exists
        throw new NotFoundApiException(String.format("No backup with id [%s] found.", backupId), e);
      }
      if (isRepositoryMissingException(e)) {
        final String reason =
            String.format(
                "No repository with name [%s] could be found.",
                tasklistProperties.getBackup().getRepositoryName());
        throw new TasklistRuntimeException(reason);
      }
      final String reason =
          String.format("Exception occurred when searching for backup with ID [%s].", backupId);
      throw new TasklistRuntimeException(reason, e);
    }
  }

  /**
   * This method intended to deal with the following <a
   * href="https://github.com/opensearch-project/opensearch-java/issues/420">opensearch-java issue
   * #420</a> once it will be resolved {@link GetCustomSnapshotResponse} could be removed
   */
  public GetCustomSnapshotResponse getCustomSnapshotResponse(final GetSnapshotRequest request)
      throws IOException, OpenSearchException {
    final JsonEndpoint<GetSnapshotRequest, GetCustomSnapshotResponse, ErrorResponse> endpoint =
        (JsonEndpoint<GetSnapshotRequest, GetCustomSnapshotResponse, ErrorResponse>)
            GetCustomSnapshotResponse.ENDPOINT;

    return openSearchClient
        ._transport()
        .performRequest(request, endpoint, openSearchClient._transportOptions());
  }

  private GetBackupStateResponseDto getBackupResponse(
      final Long backupId, final List<SnapshotInfo> snapshots, final boolean isBackupInProgress) {
    final GetBackupStateResponseDto response = new GetBackupStateResponseDto(backupId);

    final Map<String, JsonData> jsonDataMap = snapshots.get(0).metadata();
    final Metadata metadata = getMetadata(jsonDataMap, snapshots.get(0).snapshot());
    final Integer expectedSnapshotsCount = metadata.getPartCount();
    if (snapshots.size() == expectedSnapshotsCount
        && snapshots.stream().map(SnapshotInfo::state).allMatch("SUCCESS"::equals)) {
      response.setState(BackupStateDto.COMPLETED);
    } else if (snapshots.stream()
        .map(SnapshotInfo::state)
        .anyMatch(s -> "FAILED".equals(s) || "PARTIAL".equals(s))) {
      response.setState(BackupStateDto.FAILED);
    } else if (snapshots.stream().map(SnapshotInfo::state).anyMatch("INCOMPATIBLE"::equals)) {
      response.setState(BackupStateDto.INCOMPATIBLE);
    } else if (isBackupInProgress
        || snapshots.stream().map(SnapshotInfo::state).anyMatch("IN_PROGRESS"::equals)) {
      response.setState(BackupStateDto.IN_PROGRESS);
    } else if (snapshots.size() < expectedSnapshotsCount) {
      response.setState(
          isWithinGracePeriodForIncomplete(snapshots.getLast().endTimeInMillis())
              ? BackupStateDto.IN_PROGRESS
              : BackupStateDto.INCOMPLETE);
    } else {
      response.setState(BackupStateDto.FAILED);
    }
    final List<GetBackupStateResponseDetailDto> details = new ArrayList<>();
    for (final SnapshotInfo snapshot : snapshots) {
      final GetBackupStateResponseDetailDto detail = new GetBackupStateResponseDetailDto();
      detail.setSnapshotName(snapshot.snapshot());
      if (detail.getStartTime() != null) {
        detail.setStartTime(
            OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(
                    Long.parseLong(Objects.requireNonNull(snapshot.startTimeInMillis()))),
                ZoneId.systemDefault()));
      }
      if (snapshot.failures() != null) {
        detail.setFailures(
            snapshot.failures().stream()
                .map(SnapshotShardFailure::toString)
                .toArray(String[]::new));
      }
      detail.setState(snapshot.state());
      details.add(detail);
    }
    response.setDetails(details);
    if (response.getState().equals(BackupStateDto.FAILED)) {
      String failureReason = null;
      final String failedSnapshots =
          snapshots.stream()
              .filter(s -> "FAILED".equals(s.state()))
              .map(SnapshotInfo::snapshot)
              .collect(Collectors.joining(", "));
      if (!failedSnapshots.isEmpty()) {
        failureReason =
            String.format("There were failures with the following snapshots: %s", failedSnapshots);
      } else {
        final String partialSnapshot =
            snapshots.stream()
                .filter(s -> "PARTIAL".equals(s.state()))
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

  private boolean isWithinGracePeriodForIncomplete(final String lastSnapshotFinishedTime) {
    if (lastSnapshotFinishedTime == null) {
      return false;
    }
    final var incompleteCheckTimeoutInMilliseconds =
        tasklistProperties.getBackup().getIncompleteCheckTimeoutInSeconds() * 1000;
    try {
      return Instant.now().toEpochMilli() - Long.valueOf(lastSnapshotFinishedTime)
          < incompleteCheckTimeoutInMilliseconds;
    } catch (final Exception e) {
      LOGGER.warn(
          "Couldn't check incomplete timeout for backup. Return incomplete check is timed out", e);
      return true;
    }
  }

  private static Metadata getMetadata(
      final Map<String, JsonData> jsonDataMap, final String snapshotName) {
    return Metadata.extractFromMetadataOrName(Metadata.fromOSJsonData(jsonDataMap), snapshotName);
  }

  @Bean("tasklistBackupThreadPoolExecutor")
  public ThreadPoolTaskExecutor getTaskExecutor() {
    final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setThreadNamePrefix("backup_os_");
    executor.setQueueCapacity(6);
    executor.initialize();
    return executor;
  }

  protected boolean isSnapshotFinishedWithinTimeout(final String snapshotName) {
    int count = 0;
    final long startTime = System.currentTimeMillis();
    final int snapshotTimeout = tasklistProperties.getBackup().getSnapshotTimeout();
    final long backupId = Metadata.extractBackupIdFromSnapshotName(snapshotName);
    while (snapshotTimeout == 0
        || System.currentTimeMillis() - startTime <= snapshotTimeout * 1000) {
      final List<SnapshotInfo> snapshotInfos = findSnapshots(backupId);
      final SnapshotInfo currentSnapshot =
          snapshotInfos.stream()
              .filter(x -> Objects.equals(x.snapshot(), snapshotName))
              .findFirst()
              .orElse(null);
      if (currentSnapshot == null) {
        LOGGER.error(
            String.format(
                "Expected (but not found) snapshot [%s] for backupId [%d].",
                snapshotName, backupId));
        // No need to continue
        return false;
      }
      if (currentSnapshot.state().equals(SnapshotState.STARTED.name())) {
        ThreadUtil.sleepFor(100);
        count++;
        if (count % 600 == 0) { // approx. 1 minute, depending on how long findSnapshots takes
          LOGGER.info(String.format("Waiting for snapshot [%s] to finish.", snapshotName));
        }
      } else {
        return isSnapshotFinished(currentSnapshot);
      }
    }
    LOGGER.error(
        String.format(
            "Snapshot [%s] did not finish after configured timeout. Snapshot process won't continue.",
            snapshotName));
    return false;
  }

  public long sleepFor(final long milliseconds) {
    try {
      Thread.sleep(milliseconds);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return milliseconds;
  }

  /**
   * Checks if a backup with the given ID is currently in progress, according to the in-memory
   * state. Note this may not reflect the actual state in a multinode deployment.
   */
  private boolean isBackupInProgress(final Long backupId) {
    return Stream.concat(Stream.ofNullable(currentRequest.get()), requestsQueue.stream())
        .anyMatch(
            request -> backupId.equals(Metadata.fromOSJsonData(request.metadata()).getBackupId()));
  }
}
