/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.es.backup;

import static java.util.stream.Collectors.*;
import static org.elasticsearch.snapshots.SnapshotState.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.exceptions.TasklistElasticsearchConnectionException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.backup.*;
import io.camunda.tasklist.schema.indices.IndexDescriptor;
import io.camunda.tasklist.schema.templates.TemplateDescriptor;
import io.camunda.tasklist.webapp.management.dto.BackupStateDto;
import io.camunda.tasklist.webapp.management.dto.GetBackupStateResponseDetailDto;
import io.camunda.tasklist.webapp.management.dto.GetBackupStateResponseDto;
import io.camunda.tasklist.webapp.management.dto.TakeBackupRequestDto;
import io.camunda.tasklist.webapp.management.dto.TakeBackupResponseDto;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundException;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
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
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.snapshots.SnapshotInfo;
import org.elasticsearch.snapshots.SnapshotShardFailure;
import org.elasticsearch.transport.TransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

  private String[][] indexPatternsOrdered;

  public void deleteBackup(Integer backupId) {
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

  private boolean isRepositoryMissingException(Exception e) {
    return e instanceof ElasticsearchStatusException
        && ((ElasticsearchStatusException) e)
            .getDetailedMessage()
            .contains(REPOSITORY_MISSING_EXCEPTION_TYPE);
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
          new Metadata()
              .setVersion(version)
              .setPartCount(count)
              .setPartNo(index + 1)
              .setBackupId(request.getBackupId());
      final String snapshotName = metadata.buildSnapshotName();
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
    final GetRepositoriesRequest getRepositoriesRequest =
        new GetRepositoriesRequest().repositories(new String[] {repositoryName});
    try {
      final GetRepositoriesResponse repository = getRepository(getRepositoriesRequest);
    } catch (IOException | TransportException ex) {
      final String reason =
          String.format(
              "Encountered an error connecting to Elasticsearch while retrieving repository with name [%s].",
              repositoryName);
      throw new TasklistElasticsearchConnectionException(reason, ex);
    } catch (Exception e) {
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

  private GetRepositoriesResponse getRepository(GetRepositoriesRequest getRepositoriesRequest)
      throws IOException {
    return esClient.snapshot().getRepository(getRepositoriesRequest, RequestOptions.DEFAULT);
  }

  private void validateNoDuplicateBackupId(final Integer backupId) {
    final GetSnapshotsRequest snapshotsStatusRequest =
        new GetSnapshotsRequest()
            .repository(getRepositoryName())
            .snapshots(new String[] {Metadata.buildSnapshotNamePrefix(backupId) + "*"});
    final GetSnapshotsResponse response;
    try {
      response = esClient.snapshot().get(snapshotsStatusRequest, RequestOptions.DEFAULT);
    } catch (IOException | TransportException ex) {
      final String reason =
          String.format(
              "Encountered an error connecting to Elasticsearch while searching for duplicate backup. Repository name: [%s].",
              getRepositoryName());
      throw new TasklistElasticsearchConnectionException(reason, ex);
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
      }
    };
  }

  public GetBackupStateResponseDto getBackupState(Integer backupId) {
    final List<SnapshotInfo> snapshots = findSnapshots(backupId);
    final GetBackupStateResponseDto response = getBackupResponse(backupId, snapshots);
    return response;
  }

  private GetBackupStateResponseDto getBackupResponse(
      Integer backupId, List<SnapshotInfo> snapshots) {
    final GetBackupStateResponseDto response = new GetBackupStateResponseDto(backupId);

    final Metadata metadata =
        objectMapper.convertValue(snapshots.get(0).userMetadata(), Metadata.class);
    final Integer expectedSnapshotsCount = metadata.getPartCount();
    if (snapshots.size() == expectedSnapshotsCount
        && snapshots.stream().map(SnapshotInfo::state).allMatch(SUCCESS::equals)) {
      response.setState(BackupStateDto.COMPLETED);
    } else if (snapshots.stream()
        .map(SnapshotInfo::state)
        .anyMatch(s -> FAILED.equals(s) || PARTIAL.equals(s))) {
      response.setState(BackupStateDto.FAILED);
    } else if (snapshots.stream().map(SnapshotInfo::state).anyMatch(INCOMPATIBLE::equals)) {
      response.setState(BackupStateDto.INCOMPATIBLE);
    } else if (snapshots.stream().map(SnapshotInfo::state).anyMatch(IN_PROGRESS::equals)) {
      response.setState(BackupStateDto.IN_PROGRESS);
    } else if (snapshots.size() < expectedSnapshotsCount) {
      response.setState(BackupStateDto.INCOMPLETE);
    } else {
      response.setState(BackupStateDto.FAILED);
    }
    final List<GetBackupStateResponseDetailDto> details = new ArrayList<>();
    for (SnapshotInfo snapshot : snapshots) {
      final GetBackupStateResponseDetailDto detail = new GetBackupStateResponseDetailDto();
      detail.setSnapshotName(snapshot.snapshotId().getName());
      detail.setStartTime(
          OffsetDateTime.ofInstant(
              Instant.ofEpochMilli(snapshot.startTime()), ZoneId.systemDefault()));
      if (snapshot.shardFailures() != null) {
        detail.setFailures(
            snapshot.shardFailures().stream()
                .map(SnapshotShardFailure::toString)
                .toArray(String[]::new));
      }
      detail.setState(snapshot.state().name());
      details.add(detail);
    }
    response.setDetails(details);
    if (response.getState().equals(BackupStateDto.FAILED)) {
      String failureReason = null;
      final String failedSnapshots =
          snapshots.stream()
              .filter(s -> s.state().equals(FAILED))
              .map(s -> s.snapshotId().getName())
              .collect(Collectors.joining(", "));
      if (!failedSnapshots.isEmpty()) {
        failureReason =
            String.format("There were failures with the following snapshots: %s", failedSnapshots);
      } else {
        final String partialSnapshot =
            snapshots.stream()
                .filter(s -> s.state().equals(PARTIAL))
                .map(s -> s.snapshotId().getName())
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

  private List<SnapshotInfo> findSnapshots(Integer backupId) {
    final GetSnapshotsRequest snapshotsStatusRequest =
        new GetSnapshotsRequest()
            .repository(getRepositoryName())
            .snapshots(new String[] {Metadata.buildSnapshotNamePrefix(backupId) + "*"});
    final GetSnapshotsResponse response;
    try {
      response = esClient.snapshot().get(snapshotsStatusRequest, RequestOptions.DEFAULT);
      return response.getSnapshots();
    } catch (IOException | TransportException ex) {
      final String reason =
          String.format(
              "Encountered an error connecting to Elasticsearch while searching for snapshots. Repository name: [%s].",
              getRepositoryName());
      throw new TasklistElasticsearchConnectionException(reason, ex);
    } catch (Exception e) {
      if (isSnapshotMissingException(e)) {
        // no snapshot with given backupID exists
        throw new NotFoundException(String.format("No backup with id [%s] found.", backupId), e);
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

  public List<GetBackupStateResponseDto> getBackups() {
    final GetSnapshotsRequest snapshotsStatusRequest =
        new GetSnapshotsRequest()
            .repository(getRepositoryName())
            .snapshots(new String[] {Metadata.SNAPSHOT_NAME_PREFIX + "*"})
            // it looks like sorting as well as size/offset are not working, need to sort
            // additionally before return
            .sort(GetSnapshotsRequest.SortBy.START_TIME)
            .order(SortOrder.DESC);
    final GetSnapshotsResponse response;
    try {
      response = esClient.snapshot().get(snapshotsStatusRequest, RequestOptions.DEFAULT);
      final List<SnapshotInfo> snapshots =
          response.getSnapshots().stream()
              .sorted(Comparator.comparing(SnapshotInfo::startTime).reversed())
              .collect(toList());

      final LinkedHashMap<Integer, List<SnapshotInfo>> groupedSnapshotInfos =
          snapshots.stream()
              .collect(
                  groupingBy(
                      si -> {
                        final Metadata metadata =
                            objectMapper.convertValue(si.userMetadata(), Metadata.class);
                        Integer backupId = metadata.getBackupId();
                        // backward compatibility with v. 8.1
                        if (backupId == null) {
                          backupId =
                              Metadata.extractBackupIdFromSnapshotName(si.snapshotId().getName());
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
    } catch (IOException | TransportException ex) {
      final String reason =
          String.format(
              "Encountered an error connecting to Elasticsearch while searching for snapshots. Repository name: [%s].",
              getRepositoryName());
      throw new TasklistElasticsearchConnectionException(reason, ex);
    } catch (Exception e) {
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
}
