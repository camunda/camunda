/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.es.backup;

import static java.util.stream.Collectors.joining;
import static org.elasticsearch.snapshots.SnapshotState.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.backup.*;
import io.camunda.tasklist.schema.indices.IndexDescriptor;
import io.camunda.tasklist.schema.templates.TemplateDescriptor;
import io.camunda.tasklist.webapp.management.dto.BackupStateDto;
import io.camunda.tasklist.webapp.management.dto.GetBackupStateResponseDto;
import io.camunda.tasklist.webapp.management.dto.TakeBackupRequestDto;
import io.camunda.tasklist.webapp.management.dto.TakeBackupResponseDto;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.cluster.repositories.get.GetRepositoriesRequest;
import org.elasticsearch.action.admin.cluster.repositories.get.GetRepositoriesResponse;
import org.elasticsearch.action.admin.cluster.snapshots.create.CreateSnapshotRequest;
import org.elasticsearch.action.admin.cluster.snapshots.create.CreateSnapshotResponse;
import org.elasticsearch.action.admin.cluster.snapshots.delete.DeleteSnapshotRequest;
import org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsRequest;
import org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.snapshots.SnapshotInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@Configuration
public class BackupManager {

  public static final String SNAPSHOT_MISSING_EXCEPTION_TYPE = "type=snapshot_missing_exception";
  private static final Logger LOGGER = LoggerFactory.getLogger(BackupManager.class);
  private static final String REPOSITORY_MISSING_EXCEPTION_TYPE =
      "type=repository_missing_exception";

  @Autowired private List<Prio1Backup> prio1BackupIndices;

  @Autowired private List<Prio2Backup> prio2BackupTemplates;

  @Autowired private List<Prio3Backup> prio3BackupTemplates;

  @Autowired private List<Prio4Backup> prio4BackupIndices;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private ObjectMapper objectMapper;

  private Queue<CreateSnapshotRequest> requestsQueue = new ConcurrentLinkedQueue<>();

  private SimpleAsyncTaskExecutor asyncTaskExecutor;

  private String[][] indexPatternsOrdered;

  public void deleteBackup(String backupId) {
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
              .buildSnapshotName(backupId);
      final DeleteSnapshotRequest request = new DeleteSnapshotRequest(repositoryName);
      request.snapshots(snapshotName);
      esClient.snapshot().deleteAsync(request, RequestOptions.DEFAULT, getDeleteListener());
    }
  }

  public ActionListener<AcknowledgedResponse> getDeleteListener() {
    return new ActionListener<>() {
      @Override
      public void onResponse(AcknowledgedResponse response) {
        LOGGER.debug(
            "Delete snapshot was acknowledged by Elasticsearch node: " + response.isAcknowledged());
      }

      @Override
      public void onFailure(Exception e) {
        if (isSnapshotMissingException(e)) {
          // no snapshot with given backupID exists, this is fine, log warning
          LOGGER.warn("No snapshot found for snapshot deletion: " + e.getMessage());
        } else {
          LOGGER.error("Exception occurred while deleting the snapshot: " + e.getMessage(), e);
        }
      }
    };
  }

  private boolean isSnapshotMissingException(Exception e) {
    return e instanceof ElasticsearchStatusException
        && ((ElasticsearchStatusException) e)
            .getDetailedMessage()
            .contains(SNAPSHOT_MISSING_EXCEPTION_TYPE);
  }

  public TakeBackupResponseDto takeBackup(TakeBackupRequestDto request) {
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

  private TakeBackupResponseDto scheduleSnapshots(TakeBackupRequestDto request) {
    final String repositoryName = getRepositoryName();
    final int count = getIndexPatternsOrdered().length;
    final List<String> snapshotNames = new ArrayList<>();
    final String version = getCurrentTasklistVersion();
    for (int index = 0; index < count; index++) {
      final String[] indexPattern = getIndexPatternsOrdered()[index];
      final Metadata metadata =
          new Metadata().setVersion(version).setPartCount(count).setPartNo(index + 1);
      final String snapshotName = metadata.buildSnapshotName(request.getBackupId());
      requestsQueue.offer(
          new CreateSnapshotRequest()
              .repository(repositoryName)
              .snapshot(snapshotName)
              .indices(indexPattern)
              // ignoreUnavailable = false - indices defined by their exact name MUST be present
              // allowNoIndices = true - indices defined by wildcards, e.g. archived, MIGHT BE
              // absent
              .indicesOptions(IndicesOptions.fromOptions(false, true, true, true))
              .userMetadata(objectMapper.convertValue(metadata, new TypeReference<>() {}))
              .featureStates(new String[] {"none"})
              .waitForCompletion(true));
      LOGGER.debug("Snapshot scheduled: " + snapshotName);
      snapshotNames.add(snapshotName);
    }
    // schedule next snapshot
    scheduleNextSnapshot();
    return new TakeBackupResponseDto().setScheduledSnapshots(snapshotNames);
  }

  private void scheduleNextSnapshot() {
    final CreateSnapshotRequest nextRequest = requestsQueue.poll();
    if (nextRequest != null) {
      getTaskExecutor()
          .submit(
              () -> {
                executeSnapshotting(nextRequest);
              });
      LOGGER.debug("Snapshot picked for execution: " + nextRequest.getDescription());
    }
  }

  private void validateRepositoryExists() {
    final String repositoryName = getRepositoryName();
    if (repositoryName == null || repositoryName.isBlank()) {
      final String reason =
          "Cannot trigger backup because no Elasticsearch snapshot repository name found in Tasklist configuration.";
      throw new InvalidRequestException(reason);
    }
    final GetRepositoriesRequest getRepositoriesRequest =
        new GetRepositoriesRequest().repositories(new String[] {repositoryName});
    try {
      final GetRepositoriesResponse repository = getRepository(getRepositoriesRequest);
    } catch (Exception e) {
      if (e instanceof ElasticsearchStatusException
          && ((ElasticsearchStatusException) e)
              .getDetailedMessage()
              .contains(REPOSITORY_MISSING_EXCEPTION_TYPE)) {
        final String reason =
            String.format(
                "Cannot trigger backup because no repository with name [%s] could be found.",
                repositoryName);
        throw new TasklistRuntimeException(reason);
      }
      final String reason =
          String.format(
              "Exception occurred when validating existence of repository with name [%s].",
              repositoryName);
      throw new TasklistRuntimeException(reason, e);
    }
  }

  private GetRepositoriesResponse getRepository(GetRepositoriesRequest getRepositoriesRequest)
      throws IOException {
    return esClient.snapshot().getRepository(getRepositoriesRequest, RequestOptions.DEFAULT);
  }

  private void validateNoDuplicateBackupId(final String backupId) {
    final GetSnapshotsRequest snapshotsStatusRequest =
        new GetSnapshotsRequest()
            .repository(getRepositoryName())
            .snapshots(new String[] {Metadata.buildSnapshotNamePrefix(backupId) + "*"});
    final GetSnapshotsResponse response;
    try {
      response = esClient.snapshot().get(snapshotsStatusRequest, RequestOptions.DEFAULT);
    } catch (Exception e) {
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
    if (!response.getSnapshots().isEmpty()) {
      final String reason =
          String.format(
              "A backup with ID [%s] already exists. Found snapshots: [%s]",
              backupId,
              response.getSnapshots().stream()
                  .map(snapshotInfo -> snapshotInfo.snapshotId().toString())
                  .collect(joining(", ")));
      throw new InvalidRequestException(reason);
    }
  }

  private String getRepositoryName() {
    return tasklistProperties.getBackup().getRepositoryName();
  }

  private void executeSnapshotting(CreateSnapshotRequest snapshotRequest) {
    esClient
        .snapshot()
        .createAsync(snapshotRequest, RequestOptions.DEFAULT, getSnapshotActionListener());
  }

  private String[][] getIndexPatternsOrdered() {
    if (indexPatternsOrdered == null) {
      indexPatternsOrdered =
          new String[][] {
            prio1BackupIndices.stream().map(this::getFullQualifiedName).toArray(String[]::new),
            prio2BackupTemplates.stream().map(this::getFullQualifiedName).toArray(String[]::new),
            // dated indices
            prio2BackupTemplates.stream()
                .filter(i -> i instanceof TemplateDescriptor)
                .map(
                    index ->
                        new String[] {
                          getFullQualifiedName(index) + "*", "-" + getFullQualifiedName(index)
                        })
                .flatMap(x -> Arrays.stream(x))
                .toArray(String[]::new),
            prio3BackupTemplates.stream().map(this::getFullQualifiedName).toArray(String[]::new),
            // dated indices
            prio3BackupTemplates.stream()
                .filter(i -> i instanceof TemplateDescriptor)
                .map(
                    index ->
                        new String[] {
                          getFullQualifiedName(index) + "*", "-" + getFullQualifiedName(index)
                        })
                .flatMap(x -> Arrays.stream(x))
                .toArray(String[]::new),
            prio4BackupIndices.stream().map(this::getFullQualifiedName).toArray(String[]::new),
          };
    }
    return indexPatternsOrdered;
  }

  private String getFullQualifiedName(BackupPriority index) {
    if (index instanceof IndexDescriptor) {
      return ((IndexDescriptor) index).getFullQualifiedName();
    } else if (index instanceof TemplateDescriptor) {
      return ((TemplateDescriptor) index).getFullQualifiedName();
    } else {
      throw new TasklistRuntimeException("Can't find out index name for backup.");
    }
  }

  private String getCurrentTasklistVersion() {
    return tasklistProperties.getVersion().toLowerCase();
  }

  @Bean("backupThreadPoolExecutor")
  public ThreadPoolTaskExecutor getTaskExecutor() {
    final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setThreadNamePrefix("backup_");
    executor.setQueueCapacity(6);
    executor.initialize();
    return executor;
  }

  @Bean
  public ActionListener<CreateSnapshotResponse> getSnapshotActionListener() {
    return new ActionListener<>() {
      @Override
      public void onResponse(CreateSnapshotResponse response) {
        switch (response.getSnapshotInfo().state()) {
          case SUCCESS:
            LOGGER.info("Snapshot done: " + response.getSnapshotInfo().snapshotId());
            scheduleNextSnapshot();
            break;
          case FAILED:
            LOGGER.error(
                "Snapshot taking failed for {}, reason {}",
                response.getSnapshotInfo().snapshotId(),
                response.getSnapshotInfo().reason());
            // no need to continue
            requestsQueue.clear();
            return;
          default:
            LOGGER.warn(
                "Snapshot status {} for the {}",
                response.getSnapshotInfo().state(),
                response.getSnapshotInfo().snapshotId());
            scheduleNextSnapshot();
            break;
        }
      }

      @Override
      public void onFailure(Exception e) {
        LOGGER.error("Exception occurred while creating snapshot: " + e.getMessage(), e);
        // no need to continue
        requestsQueue.clear();
        return;
      }
    };
  }

  public GetBackupStateResponseDto getBackupState(String backupId) {
    final List<SnapshotInfo> snapshots = findSnapshots(backupId);

    final Metadata metadata =
        objectMapper.convertValue(snapshots.get(0).userMetadata(), Metadata.class);
    final Integer expectedSnapshotsCount = metadata.getPartCount();
    if (snapshots.size() == expectedSnapshotsCount
        && snapshots.stream().map(SnapshotInfo::state).allMatch(s -> SUCCESS.equals(s))) {
      return new GetBackupStateResponseDto(BackupStateDto.COMPLETED);
    }
    if (snapshots.stream()
        .map(SnapshotInfo::state)
        .anyMatch(s -> FAILED.equals(s) || PARTIAL.equals(s))) {
      return new GetBackupStateResponseDto(BackupStateDto.FAILED);
    }
    if (snapshots.stream().map(SnapshotInfo::state).anyMatch(s -> INCOMPATIBLE.equals(s))) {
      return new GetBackupStateResponseDto(BackupStateDto.INCOMPATIBLE);
    }
    if (snapshots.stream().map(SnapshotInfo::state).anyMatch(s -> IN_PROGRESS.equals(s))) {
      return new GetBackupStateResponseDto(BackupStateDto.IN_PROGRESS);
    }
    return new GetBackupStateResponseDto(BackupStateDto.INCOMPLETE);
  }

  private List<SnapshotInfo> findSnapshots(String backupId) {
    final GetSnapshotsRequest snapshotsStatusRequest =
        new GetSnapshotsRequest()
            .repository(getRepositoryName())
            .snapshots(new String[] {Metadata.buildSnapshotNamePrefix(backupId) + "*"});
    final GetSnapshotsResponse response;
    try {
      response = esClient.snapshot().get(snapshotsStatusRequest, RequestOptions.DEFAULT);
      return response.getSnapshots();
    } catch (Exception e) {
      if (isSnapshotMissingException(e)) {
        // no snapshot with given backupID exists
        throw new NotFoundException(String.format("No backup with id [%s] found.", backupId), e);
      }
      final String reason =
          String.format("Exception occurred when searching for backup with ID [%s].", backupId);
      throw new TasklistRuntimeException(reason, e);
    }
  }
}
