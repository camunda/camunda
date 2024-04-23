/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.webapp.opensearch.backup;

import static io.camunda.operate.store.opensearch.dsl.RequestDSL.*;
import static io.camunda.operate.store.opensearch.response.SnapshotState.*;
import static java.lang.String.format;
import static java.util.stream.Collectors.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.response.OpenSearchGetSnapshotResponse;
import io.camunda.operate.store.opensearch.response.OpenSearchSnapshotInfo;
import io.camunda.operate.store.opensearch.response.SnapshotState;
import io.camunda.operate.util.ThreadUtil;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.backup.BackupRepository;
import io.camunda.operate.webapp.backup.BackupService;
import io.camunda.operate.webapp.backup.Metadata;
import io.camunda.operate.webapp.management.dto.BackupStateDto;
import io.camunda.operate.webapp.management.dto.GetBackupStateResponseDetailDto;
import io.camunda.operate.webapp.management.dto.GetBackupStateResponseDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.snapshot.SnapshotInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchBackupRepository implements BackupRepository {
  public static final String SNAPSHOT_MISSING_EXCEPTION_TYPE = "snapshot_missing_exception";
  public static final String REPOSITORY_MISSING_EXCEPTION_TYPE = "repository_missing_exception";
  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchBackupRepository.class);

  private final RichOpenSearchClient richOpenSearchClient;

  private final ObjectMapper objectMapper;

  public OpensearchBackupRepository(
      final RichOpenSearchClient richOpenSearchClient, final ObjectMapper objectMapper) {
    this.richOpenSearchClient = richOpenSearchClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public void deleteSnapshot(final String repositoryName, final String snapshotName) {
    final var requestBuilder = deleteSnapshotRequestBuilder(repositoryName, snapshotName);
    richOpenSearchClient
        .async()
        .snapshot()
        .delete(requestBuilder)
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
          richOpenSearchClient.snapshot().getRepository(repositoryRequestBuilder(repositoryName));
      LOGGER.debug("Repository {} exists", repositoryResponse);
    } catch (final Exception e) {
      if (isRepositoryMissingException(e)) {
        final String reason = noRepositoryErrorMessage(repositoryName);
        throw new OperateRuntimeException(reason);
      }
      final String reason =
          format(
              "Exception occurred when validating existence of repository with name [%s].",
              repositoryName);
      throw new OperateRuntimeException(reason, e);
    }
  }

  @Override
  public void validateNoDuplicateBackupId(final String repositoryName, final Long backupId) {
    final String snapshot = Metadata.buildSnapshotNamePrefix(backupId) + "*";

    final OpenSearchGetSnapshotResponse response;
    try {
      response =
          richOpenSearchClient.snapshot().get(getSnapshotRequestBuilder(repositoryName, snapshot));
    } catch (final Exception e) {
      if (isSnapshotMissingException(e)) {
        // no snapshot with given backupID exists
        return;
      }
      final String reason =
          format(
              "Exception occurred when validating whether backup with ID [%s] already exists.",
              backupId);
      throw new OperateRuntimeException(reason, e);
    }
    if (!response.snapshots().isEmpty()) {
      final String reason =
          format(
              "A backup with ID [%s] already exists. Found snapshots: [%s]",
              backupId,
              response.snapshots().stream()
                  .map(OpenSearchSnapshotInfo::getUuid)
                  .collect(joining(", ")));
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
  public List<GetBackupStateResponseDto> getBackups(final String repositoryName) {
    final var requestBuilder =
        getSnapshotRequestBuilder(repositoryName, Metadata.SNAPSHOT_NAME_PREFIX + "*");
    final OpenSearchGetSnapshotResponse response;
    try {
      response = richOpenSearchClient.snapshot().get(requestBuilder);
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
                            objectMapper.convertValue(si.getMetadata(), Metadata.class);
                        Long backupId = metadata.getBackupId();
                        // backward compatibility with v. 8.1
                        if (backupId == null) {
                          backupId = Metadata.extractBackupIdFromSnapshotName(si.getSnapshot());
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
        throw new OperateRuntimeException(reason);
      }
      if (isSnapshotMissingException(e)) {
        // no snapshots exist
        return new ArrayList<>();
      }
      final String reason =
          format("Exception occurred when searching for backups: %s", e.getMessage());
      throw new OperateRuntimeException(reason, e);
    }
  }

  @Override
  public void executeSnapshotting(
      final BackupService.SnapshotRequest snapshotRequest,
      final Runnable onSuccess,
      final Runnable onFailure) {
    final Long backupId = backupId(snapshotRequest);
    final var metadata = snapshotRequest.metadata();
    final Map<String, JsonData> metadataJson =
        Map.of(
            "backupId", JsonData.of(metadata.getBackupId()),
            "version", JsonData.of(metadata.getVersion()),
            "partNo", JsonData.of(metadata.getPartNo()),
            "partCount", JsonData.of(metadata.getPartCount()));
    final var requestBuilder =
        createSnapshotRequestBuilder(
                snapshotRequest.repositoryName(),
                snapshotRequest.snapshotName(),
                snapshotRequest.indices())
            .ignoreUnavailable(
                false) // ignoreUnavailable = false - indices defined by their exact name MUST be
            // present
            .metadata(metadataJson)
            .featureStates("none")
            .waitForCompletion(true);

    richOpenSearchClient
        .async()
        .snapshot()
        .create(requestBuilder)
        .thenAccept(response -> handleSnapshotReceived(response.snapshot(), onSuccess, onFailure))
        .exceptionally(
            e -> {
              if (e instanceof SocketTimeoutException) {
                // This is thrown even if the backup is still running
                LOGGER.warn(
                    format(
                        "Timeout while creating snapshot [%s] for backup id [%d]. Need to keep waiting with polling...",
                        snapshotRequest.snapshotName(), backupId));
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
                        format(
                            "Expected (but not found) snapshot [%s] for backupId [%d].",
                            snapshotRequest.snapshotName(), backupId));
                    // No need to continue
                    onFailure.run();
                    break;
                  } else if (STARTED.equals(maybeCurrentSnapshot.get().getState())) {
                    ThreadUtil.sleepFor(100);
                  } else {
                    handleSnapshotReceived(maybeCurrentSnapshot.get(), onSuccess, onFailure);
                    break;
                  }
                }
              } else {
                LOGGER.error(
                    format(
                        "Exception while creating snapshot [%s] for backup id [%d].",
                        snapshotRequest.snapshotName(), backupId),
                    e);
                // No need to continue
                onFailure.run();
              }

              return null;
            });
  }

  private boolean isSnapshotMissingException(final Throwable t) {
    return t instanceof OpenSearchException
        && t.getMessage().contains(SNAPSHOT_MISSING_EXCEPTION_TYPE);
  }

  private boolean isRepositoryMissingException(final Exception e) {
    return e instanceof OpenSearchException
        && e.getMessage().contains(REPOSITORY_MISSING_EXCEPTION_TYPE);
  }

  private static String noRepositoryErrorMessage(final String repositoryName) {
    return format("No repository with name [%s] could be found.", repositoryName);
  }

  private Long backupId(final BackupService.SnapshotRequest snapshotRequest) {
    return Metadata.extractBackupIdFromSnapshotName(snapshotRequest.snapshotName());
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
        getSnapshotRequestBuilder(repositoryName, Metadata.buildSnapshotNamePrefix(backupId) + "*");

    final OpenSearchGetSnapshotResponse response;
    try {
      response = richOpenSearchClient.snapshot().get(requestBuilder);
      return response.snapshots();
    } catch (final Exception e) {
      if (isSnapshotMissingException(e)) {
        // no snapshot with given backupID exists
        throw new ResourceNotFoundException(format("No backup with id [%s] found.", backupId));
      }
      if (isRepositoryMissingException(e)) {
        final String reason = noRepositoryErrorMessage(repositoryName);
        throw new OperateRuntimeException(reason);
      }
      final String reason =
          format("Exception occurred when searching for backup with ID [%s].", backupId);
      throw new OperateRuntimeException(reason, e);
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
      return BackupStateDto.INCOMPLETE;
    } else {
      return BackupStateDto.FAILED;
    }
  }

  private GetBackupStateResponseDto toGetBackupStateResponseDto(
      final Long backupId, final List<OpenSearchSnapshotInfo> snapshots) {
    final GetBackupStateResponseDto response = new GetBackupStateResponseDto(backupId);
    final Metadata metadata =
        objectMapper.convertValue(snapshots.get(0).getMetadata(), Metadata.class);
    final Integer expectedSnapshotsCount = metadata.getPartCount();

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
}
