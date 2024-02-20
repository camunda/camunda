/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.opensearch.backup;

import static io.camunda.operate.store.opensearch.dsl.RequestDSL.createSnapshotRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.deleteSnapshotRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.getSnapshotRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.repositoryRequestBuilder;
import static io.camunda.operate.webapp.opensearch.backup.SnapshotState.FAILED;
import static io.camunda.operate.webapp.opensearch.backup.SnapshotState.PARTIAL;
import static io.camunda.operate.webapp.opensearch.backup.SnapshotState.STARTED;
import static io.camunda.operate.webapp.opensearch.backup.SnapshotState.SUCCESS;
import static java.lang.String.format;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.exceptions.OperateOpensearchConnectionException;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.ThreadUtil;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.backup.BackupRepository;
import io.camunda.operate.webapp.backup.BackupService;
import io.camunda.operate.webapp.backup.Metadata;
import io.camunda.operate.webapp.management.dto.BackupStateDto;
import io.camunda.operate.webapp.management.dto.GetBackupStateResponseDetailDto;
import io.camunda.operate.webapp.management.dto.GetBackupStateResponseDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
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
import java.util.stream.Collectors;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.snapshot.GetSnapshotResponse;
import org.opensearch.client.opensearch.snapshot.SnapshotInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

interface SnapshotState {
  String FAILED = "FAILED";
  String PARTIAL = "PARTIAL";
  String STARTED = "STARTED";
  String SUCCESS = "SUCCESS";
}

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchBackupRepository implements BackupRepository {
  public static final String SNAPSHOT_MISSING_EXCEPTION_TYPE = "type=snapshot_missing_exception";
  private static final String REPOSITORY_MISSING_EXCEPTION_TYPE =
      "type=repository_missing_exception";
  private static final Logger logger = LoggerFactory.getLogger(OpensearchBackupRepository.class);

  @Autowired private RichOpenSearchClient richOpenSearchClient;
  @Autowired private ObjectMapper objectMapper;

  @Override
  public void deleteSnapshot(String repositoryName, String snapshotName) {
    var requestBuilder = deleteSnapshotRequestBuilder(repositoryName, snapshotName);
    richOpenSearchClient
        .async()
        .snapshot()
        .delete(requestBuilder)
        .thenAccept(
            response ->
                logger.debug(
                    "Delete snapshot was acknowledged by Opensearch node: "
                        + response.acknowledged()))
        .exceptionally(
            t -> {
              if (isSnapshotMissingException(t)) {
                // no snapshot with given backupID exists, this is fine, log warning
                logger.warn("No snapshot found for snapshot deletion: " + t.getMessage());
              } else {
                logger.error(
                    "Exception occurred while deleting the snapshot: " + t.getMessage(), t);
              }
              return null;
            });
  }

  private boolean isSnapshotMissingException(Throwable t) {
    return t instanceof OpenSearchException
        && t.getMessage().contains(SNAPSHOT_MISSING_EXCEPTION_TYPE);
  }

  private boolean isRepositoryMissingException(Exception e) {
    return e instanceof OpenSearchException
        && e.getMessage().contains(REPOSITORY_MISSING_EXCEPTION_TYPE);
  }

  @Override
  public void validateRepositoryExists(String repositoryName) {
    try {
      richOpenSearchClient.snapshot().getRepository(repositoryRequestBuilder(repositoryName));
    } catch (IOException e) {
      final String reason =
          format(
              "Encountered an error connecting to Opensearch while retrieving repository with name [%s].",
              repositoryName);
      throw new OperateOpensearchConnectionException(reason, e);
    } catch (Exception e) {
      if (isRepositoryMissingException(e)) {
        final String reason =
            format("No repository with name [%s] could be found.", repositoryName);
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
  public void validateNoDuplicateBackupId(String repositoryName, Long backupId) {
    String snapshot = Metadata.buildSnapshotNamePrefix(backupId) + "*";

    GetSnapshotResponse response;
    try {
      response =
          richOpenSearchClient.snapshot().get(getSnapshotRequestBuilder(repositoryName, snapshot));
    } catch (IOException e) {
      final String reason =
          format(
              "Encountered an error connecting to Opensearch while searching for duplicate backup. Repository name: [%s].",
              repositoryName);
      throw new OperateOpensearchConnectionException(reason, e);
    } catch (Exception e) {
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
              response.snapshots().stream().map(SnapshotInfo::uuid).collect(joining(", ")));
      throw new InvalidRequestException(reason);
    }
  }

  @Override
  public void executeSnapshotting(
      BackupService.SnapshotRequest snapshotRequest, Runnable onSuccess, Runnable onFailure) {
    Long backupId = backupId(snapshotRequest);
    var requestBuilder =
        createSnapshotRequestBuilder(
                snapshotRequest.repositoryName(),
                snapshotRequest.snapshotName(),
                snapshotRequest.indices())
            .ignoreUnavailable(
                false) // ignoreUnavailable = false - indices defined by their exact name MUST be
            // present
            .metadata(
                objectMapper.convertValue(snapshotRequest.metadata(), new TypeReference<>() {}))
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
                logger.warn(
                    format(
                        "Timeout while creating snapshot [%s] for backup id [%d]. Need to keep waiting with polling...",
                        snapshotRequest.snapshotName(), backupId));
                // Keep waiting
                while (true) {
                  List<SnapshotInfo> snapshotInfos =
                      findSnapshots(snapshotRequest.repositoryName(), backupId);
                  Optional<SnapshotInfo> maybeCurrentSnapshot =
                      snapshotInfos.stream()
                          .filter(x -> Objects.equals(x.snapshot(), snapshotRequest.snapshotName()))
                          .findFirst();

                  if (maybeCurrentSnapshot.isEmpty()) {
                    logger.error(
                        format(
                            "Expected (but not found) snapshot [%s] for backupId [%d].",
                            snapshotRequest.snapshotName(), backupId));
                    // No need to continue
                    onFailure.run();
                    break;
                  } else if (STARTED.equals(maybeCurrentSnapshot.get().state())) {
                    ThreadUtil.sleepFor(100);
                  } else {
                    handleSnapshotReceived(maybeCurrentSnapshot.get(), onSuccess, onFailure);
                    break;
                  }
                }
              } else {
                logger.error(
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

  private Long backupId(BackupService.SnapshotRequest snapshotRequest) {
    return Metadata.extractBackupIdFromSnapshotName(snapshotRequest.snapshotName());
  }

  private void handleSnapshotReceived(
      SnapshotInfo snapshotInfo, Runnable onSuccess, Runnable onFailure) {
    if (SUCCESS.equals(snapshotInfo.state())) {
      logger.info("Snapshot done: " + snapshotInfo.uuid());
      onSuccess.run();
    } else if (FAILED.equals(snapshotInfo.state())) {
      logger.error(
          format("Snapshot taking failed for %s, reason %s", snapshotInfo.uuid()),
          snapshotInfo.reason());
      // No need to continue
      onFailure.run();
    } else {
      logger.warn(
          format(
              "Snapshot state is %s for snapshot %s", snapshotInfo.state(), snapshotInfo.uuid()));
      onSuccess.run();
    }
  }

  private List<SnapshotInfo> findSnapshots(String repositoryName, Long backupId) {
    var requestBuilder =
        getSnapshotRequestBuilder(repositoryName, Metadata.buildSnapshotNamePrefix(backupId) + "*");

    GetSnapshotResponse response;
    try {
      response = richOpenSearchClient.snapshot().get(requestBuilder);
      return response.snapshots();
    } catch (IOException e) {
      final String reason =
          format(
              "Encountered an error connecting to Opensearch while searching for snapshots. Repository name: [%s].",
              repositoryName);
      throw new OperateOpensearchConnectionException(reason, e);
    } catch (Exception e) {
      if (isSnapshotMissingException(e)) {
        // no snapshot with given backupID exists
        throw new ResourceNotFoundException(format("No backup with id [%s] found.", backupId));
      }
      if (isRepositoryMissingException(e)) {
        final String reason =
            format("No repository with name [%s] could be found.", repositoryName);
        throw new OperateRuntimeException(reason);
      }
      final String reason =
          format("Exception occurred when searching for backup with ID [%s].", backupId);
      throw new OperateRuntimeException(reason, e);
    }
  }

  @Override
  public GetBackupStateResponseDto getBackupState(String repositoryName, Long backupId) {
    List<SnapshotInfo> snapshots = findSnapshots(repositoryName, backupId);
    GetBackupStateResponseDto response = toGetBackupStateResponseDto(backupId, snapshots);
    return response;
  }

  private GetBackupStateResponseDto toGetBackupStateResponseDto(
      Long backupId, List<SnapshotInfo> snapshots) {
    GetBackupStateResponseDto response = new GetBackupStateResponseDto(backupId);
    Metadata metadata = objectMapper.convertValue(snapshots.get(0).metadata(), Metadata.class);
    final Integer expectedSnapshotsCount = metadata.getPartCount();

    if (snapshots.size() == expectedSnapshotsCount
        && snapshots.stream().map(SnapshotInfo::state).allMatch(SUCCESS::equals)) {
      response.setState(BackupStateDto.COMPLETED);
    } else if (snapshots.stream()
        .map(SnapshotInfo::state)
        .anyMatch(s -> FAILED.equals(s) || PARTIAL.equals(s))) {
      response.setState(BackupStateDto.FAILED);
    } else if (snapshots.stream().map(SnapshotInfo::state).anyMatch(s -> STARTED.equals(s))) {
      response.setState(BackupStateDto.IN_PROGRESS);
    } else if (snapshots.size() < expectedSnapshotsCount) {
      response.setState(BackupStateDto.INCOMPLETE);
    } else {
      response.setState(BackupStateDto.FAILED);
    }

    List<GetBackupStateResponseDetailDto> details = new ArrayList<>();
    for (SnapshotInfo snapshot : snapshots) {
      GetBackupStateResponseDetailDto detail = new GetBackupStateResponseDetailDto();
      detail.setSnapshotName(snapshot.snapshot());
      detail.setStartTime(
          OffsetDateTime.ofInstant(
              Instant.ofEpochMilli(Long.parseLong(snapshot.startTimeInMillis())),
              ZoneId.systemDefault()));
      if (!snapshot.failures().isEmpty()) {
        detail.setFailures(
            snapshot.failures().stream().map(Object::toString).toArray(String[]::new));
      }
      detail.setState(snapshot.state());
      details.add(detail);
    }
    response.setDetails(details);

    if (response.getState() == BackupStateDto.FAILED) {
      String failureReason = null;
      String failedSnapshots =
          snapshots.stream()
              .filter(s -> FAILED.equals(s.state()))
              .map(SnapshotInfo::snapshot)
              .collect(Collectors.joining(", "));
      if (!failedSnapshots.isEmpty()) {
        failureReason =
            format("There were failures with the following snapshots: %s", failedSnapshots);
      } else {
        String partialSnapshot =
            snapshots.stream()
                .filter(s -> PARTIAL.equals(s.state()))
                .map(SnapshotInfo::snapshot)
                .collect(Collectors.joining(", "));
        if (!partialSnapshot.isEmpty()) {
          failureReason = format("Some of the snapshots are partial: %s", partialSnapshot);
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

  @Override
  public List<GetBackupStateResponseDto> getBackups(String repositoryName) {
    var requestBuilder =
        getSnapshotRequestBuilder(repositoryName, Metadata.SNAPSHOT_NAME_PREFIX + "*");
    // TODO check unsupported: it looks like sorting as well as size/offset are not working, need to
    // sort additionally before return
    //      .sort(GetSnapshotsRequest.SortBy.START_TIME)
    //      .order(SortOrder.DESC);
    GetSnapshotResponse response;
    try {
      response = richOpenSearchClient.snapshot().get(requestBuilder);
      List<SnapshotInfo> snapshots =
          response.snapshots().stream()
              .sorted(Comparator.comparing(SnapshotInfo::startTimeInMillis).reversed())
              .toList();

      LinkedHashMap<Long, List<SnapshotInfo>> groupedSnapshotInfos =
          snapshots.stream()
              .collect(
                  groupingBy(
                      si -> {
                        Metadata metadata =
                            objectMapper.convertValue(si.metadata(), Metadata.class);
                        Long backupId = metadata.getBackupId();
                        // backward compatibility with v. 8.1
                        if (backupId == null) {
                          backupId = Metadata.extractBackupIdFromSnapshotName(si.snapshot());
                        }
                        return backupId;
                      },
                      LinkedHashMap::new,
                      toList()));

      List<GetBackupStateResponseDto> responses =
          groupedSnapshotInfos.entrySet().stream()
              .map(entry -> toGetBackupStateResponseDto(entry.getKey(), entry.getValue()))
              .toList();

      return responses;
    } catch (IOException e) {
      final String reason =
          format(
              "Encountered an error connecting to Opensearch while searching for snapshots. Repository name: [%s].",
              repositoryName);
      throw new OperateOpensearchConnectionException(reason, e);
    } catch (Exception e) {
      if (isRepositoryMissingException(e)) {
        final String reason =
            format("No repository with name [%s] could be found.", repositoryName);
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
}
