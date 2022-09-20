/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.es.backup;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.backup.Prio1Backup;
import io.camunda.operate.schema.backup.Prio2Backup;
import io.camunda.operate.schema.backup.Prio3Backup;
import io.camunda.operate.schema.backup.Prio4Backup;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import io.camunda.operate.webapp.management.dto.TakeBackupRequestDto;
import io.camunda.operate.webapp.management.dto.TakeBackupResponseDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.cluster.repositories.get.GetRepositoriesRequest;
import org.elasticsearch.action.admin.cluster.repositories.get.GetRepositoriesResponse;
import org.elasticsearch.action.admin.cluster.snapshots.create.CreateSnapshotRequest;
import org.elasticsearch.action.admin.cluster.snapshots.create.CreateSnapshotResponse;
import org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsRequest;
import org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.util.stream.Collectors.joining;

@Component
@Configuration
public class BackupManager {

  private static final Logger logger = LoggerFactory.getLogger(BackupManager.class);

  private final static String SNAPSHOT_NAME_PREFIX = "camunda_operate_{backupId}";
  private final static String SNAPSHOT_NAME_PATTERN = "{prefix}_{version}_part_{index}_of_{count}";
  private static final String REPOSITORY_MISSING_EXCEPTION_TYPE = "type=repository_missing_exception";
  private static final String SNAPSHOT_MISSING_EXCEPTION_TYPE = "type=snapshot_missing_exception";

  @Autowired
  private List<Prio1Backup> prio1BackupIndices;

  @Autowired
  private List<Prio2Backup> prio2BackupTemplates;

  @Autowired
  private List<Prio3Backup> prio3BackupTemplates;

  @Autowired
  private List<Prio4Backup> prio4BackupIndices;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private RestHighLevelClient esClient;

  private Queue<CreateSnapshotRequest> requestsQueue = new ConcurrentLinkedQueue<>();

  private SimpleAsyncTaskExecutor asyncTaskExecutor;

  private String[][] indexPatternsOrdered;

  private String currentOperateVersion;

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
    String repositoryName = getRepositoryName();
    int count = getIndexPatternsOrdered().length;
    List<String> snapshotNames = new ArrayList<>();
    List<CreateSnapshotRequest> requests = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      String[] indexPattern = getIndexPatternsOrdered()[index];
      String snapshotName = getSnapshotName(request.getBackupId(), index + 1, count);
      requestsQueue.offer(new CreateSnapshotRequest().repository(repositoryName).snapshot(snapshotName).indices(indexPattern)
          //ignoreUnavailable = false - indices defined by their exact name MUST be present
          //allowNoIndices = true - indices defined by wildcards, e.g. archived, MIGHT BE absent
          .indicesOptions(IndicesOptions.fromOptions(false, true, true, true)).waitForCompletion(true));
      logger.debug("Snapshot scheduled: " + snapshotName);
      snapshotNames.add(snapshotName);
    }
    //schedule next snapshot
    scheduleNextSnapshot();
    return new TakeBackupResponseDto().setScheduledSnapshots(snapshotNames);
  }

  private void scheduleNextSnapshot() {
    CreateSnapshotRequest nextRequest = requestsQueue.poll();
    if (nextRequest != null) {
      getTaskExecutor().submit(() -> {
        executeSnapshotting(nextRequest);
      });
      logger.debug("Snapshot picked for execution: " + nextRequest.getDescription());
    }
  }

  private void validateRepositoryExists() {
    String repositoryName = getRepositoryName();
    if (repositoryName == null || repositoryName.isBlank()) {
      final String reason = "Cannot trigger backup because no Elasticsearch snapshot repository name found in Operate configuration.";
      throw new OperateRuntimeException(reason);
    }
    final GetRepositoriesRequest getRepositoriesRequest = new GetRepositoriesRequest()
        .repositories(new String[]{ repositoryName });
    try {
      GetRepositoriesResponse repository = getRepository(getRepositoriesRequest);
    } catch (Exception e) {
      if (e instanceof ElasticsearchStatusException
          && ((ElasticsearchStatusException) e).getDetailedMessage().contains(REPOSITORY_MISSING_EXCEPTION_TYPE)) {
        final String reason = String.format(
            "Cannot trigger backup because no repository with name [%s] could be found.",
            repositoryName
        );
        throw new OperateRuntimeException(reason);
      }
      final String reason = String.format(
          "Exception occurred when validating existence of repository with name [%s].",
          repositoryName
      );
      throw new OperateRuntimeException(reason, e);
    }
  }

  private GetRepositoriesResponse getRepository(GetRepositoriesRequest getRepositoriesRequest) throws IOException {
    return esClient.snapshot().getRepository(getRepositoriesRequest, RequestOptions.DEFAULT);
  }

  private void validateNoDuplicateBackupId(final String backupId) {
    final GetSnapshotsRequest snapshotsStatusRequest =
        new GetSnapshotsRequest()
            .repository(getRepositoryName())
            .snapshots(new String[]{getSnapshotNamePrefix(backupId) + "*"});
    GetSnapshotsResponse response;
    try {
      response = esClient.snapshot().get(snapshotsStatusRequest, RequestOptions.DEFAULT);
    } catch (Exception e) {
      if (e instanceof ElasticsearchStatusException
          && ((ElasticsearchStatusException) e).getDetailedMessage().contains(SNAPSHOT_MISSING_EXCEPTION_TYPE)) {
        // no snapshot with given backupID exists
        return;
      }
      final String reason = String.format("Exception occurred when validating whether backup with ID [%s] already exists.", backupId);
      throw new OperateRuntimeException(reason, e);
    }
    if (!response.getSnapshots().isEmpty()) {
      final String reason = String.format(
          "A backup with ID [%s] already exists. Found snapshots: [%s]",
          backupId,
          response.getSnapshots().stream().map(snapshotInfo -> snapshotInfo.snapshotId().toString()).collect(joining(", "))
      );
      throw new InvalidRequestException(reason);
    }
  }

  private String getRepositoryName() {
    return operateProperties.getBackup().getRepositoryName();
  }

  private void executeSnapshotting(CreateSnapshotRequest snapshotRequest) {
    esClient.snapshot().createAsync(snapshotRequest, RequestOptions.DEFAULT, getSnapshotActionListener());
  }

  private String getSnapshotName(String backupId, int index, int count) {
    return SNAPSHOT_NAME_PATTERN
        .replace("{prefix}", getSnapshotNamePrefix(backupId))
        .replace("{version}", getCurrentOperateVersion() == null ? "unknown-version" : getCurrentOperateVersion())
        .replace("{index}", index + "")
        .replace("{count}", count + "");
  }

  private String getSnapshotNamePrefix(String backupId) {
    return SNAPSHOT_NAME_PREFIX.replace("{backupId}", backupId);
  }

  private String[][] getIndexPatternsOrdered() {
    if (indexPatternsOrdered == null) {
      indexPatternsOrdered = new String[][] {
          prio1BackupIndices.stream().map(index -> ((IndexDescriptor) index).getFullQualifiedName()).toArray(
              String[]::new),
          prio2BackupTemplates.stream().map(index -> ((TemplateDescriptor) index).getFullQualifiedName()).toArray(
              String[]::new),
          //dated indices
          prio2BackupTemplates.stream().map(index -> new String[] { ((TemplateDescriptor) index).getFullQualifiedName() + "*",
              "-" + ((TemplateDescriptor) index).getFullQualifiedName() }).flatMap(x -> Arrays.stream(x)).toArray(
              String[]::new),
          prio3BackupTemplates.stream().map(index -> ((TemplateDescriptor) index).getFullQualifiedName()).toArray(
              String[]::new),
          //dated indices
          prio3BackupTemplates.stream().map(index -> new String[] { ((TemplateDescriptor) index).getFullQualifiedName() + "*",
              "-" + ((TemplateDescriptor) index).getFullQualifiedName() }).flatMap(x -> Arrays.stream(x)).toArray(
              String[]::new),
          prio4BackupIndices.stream().map(index -> ((IndexDescriptor) index).getFullQualifiedName()).toArray(
              String[]::new),
      };
    }
    return indexPatternsOrdered;
  }

  private String getCurrentOperateVersion() {
    if (currentOperateVersion == null) {
      currentOperateVersion = BackupManager.class.getPackage().getImplementationVersion();
    }
    return currentOperateVersion;
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
          logger.info("Snapshot done: " + response.getSnapshotInfo().snapshotId());
          scheduleNextSnapshot();
          break;
        case FAILED:
          logger.error("Snapshot taking failed for {}, reason {}", response.getSnapshotInfo().snapshotId(),
              response.getSnapshotInfo().reason());
          //no need to continue
          requestsQueue.clear();
          return;
        default:
          logger.warn("Snapshot status {} for the {}", response.getSnapshotInfo().state(),
              response.getSnapshotInfo().snapshotId());
          scheduleNextSnapshot();
          break;
        }
      }

      @Override
      public void onFailure(Exception e) {
        logger.error("Exception occurred while creating snapshot: " + e.getMessage(), e);
        //no need to continue
        requestsQueue.clear();
        return;
      }
    };
  }

}
