/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.backup.Prio1Backup;
import io.camunda.operate.schema.backup.Prio2Backup;
import io.camunda.operate.schema.backup.Prio3Backup;
import io.camunda.operate.schema.backup.Prio4Backup;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import io.camunda.operate.webapp.management.dto.GetBackupStateResponseDto;
import io.camunda.operate.webapp.management.dto.TakeBackupRequestDto;
import io.camunda.operate.webapp.management.dto.TakeBackupResponseDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@Configuration
public class BackupService {
  private static final Logger logger = LoggerFactory.getLogger(BackupService.class);

  @Qualifier("backupThreadPoolExecutor")
  @Autowired
  ThreadPoolTaskExecutor threadPoolTaskExecutor;

  private final Queue<SnapshotRequest> requestsQueue = new ConcurrentLinkedQueue<>();
  @Autowired private List<Prio1Backup> prio1BackupIndices;
  @Autowired private List<Prio2Backup> prio2BackupTemplates;
  @Autowired private List<Prio3Backup> prio3BackupTemplates;
  @Autowired private List<Prio4Backup> prio4BackupIndices;
  @Autowired private OperateProperties operateProperties;
  @Autowired private BackupRepository repository;
  @Autowired private ObjectMapper objectMapper;
  private String[][] indexPatternsOrdered;

  public void deleteBackup(Long backupId) {
    repository.validateRepositoryExists(getRepositoryName());
    String repositoryName = getRepositoryName();
    int count = getIndexPatternsOrdered().length;
    String version = getCurrentOperateVersion();
    for (int index = 0; index < count; index++) {
      String snapshotName =
          new Metadata()
              .setVersion(version)
              .setPartCount(count)
              .setPartNo(index + 1)
              .setBackupId(backupId)
              .buildSnapshotName();
      repository.deleteSnapshot(repositoryName, snapshotName);
    }
  }

  public TakeBackupResponseDto takeBackup(TakeBackupRequestDto request) {
    repository.validateRepositoryExists(getRepositoryName());
    repository.validateNoDuplicateBackupId(getRepositoryName(), request.getBackupId());
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
    String version = getCurrentOperateVersion();
    for (int index = 0; index < count; index++) {
      List<String> indexPattern = Arrays.asList(getIndexPatternsOrdered()[index]);
      Metadata metadata =
          new Metadata()
              .setVersion(version)
              .setPartCount(count)
              .setPartNo(index + 1)
              .setBackupId(request.getBackupId());
      String snapshotName = metadata.buildSnapshotName();
      SnapshotRequest snapshotRequest =
          new SnapshotRequest(repositoryName, snapshotName, indexPattern, metadata);

      requestsQueue.offer(snapshotRequest);
      logger.debug("Snapshot scheduled: " + snapshotName);
      snapshotNames.add(snapshotName);
    }
    // schedule next snapshot
    scheduleNextSnapshot();
    return new TakeBackupResponseDto().setScheduledSnapshots(snapshotNames);
  }

  private void scheduleNextSnapshot() {
    SnapshotRequest nextRequest = requestsQueue.poll();
    if (nextRequest != null) {
      threadPoolTaskExecutor.submit(
          () -> {
            repository.executeSnapshotting(
                nextRequest, () -> scheduleNextSnapshot(), () -> requestsQueue.clear());
          });
      logger.debug("Snapshot picked for execution: " + nextRequest);
    }
  }

  private String getRepositoryName() {
    return operateProperties.getBackup().getRepositoryName();
  }

  private String[][] getIndexPatternsOrdered() {
    if (indexPatternsOrdered == null) {
      indexPatternsOrdered =
          new String[][] {
            prio1BackupIndices.stream()
                .map(index -> ((IndexDescriptor) index).getFullQualifiedName())
                .toArray(String[]::new),
            prio2BackupTemplates.stream()
                .map(index -> ((TemplateDescriptor) index).getFullQualifiedName())
                .toArray(String[]::new),
            // dated indices
            prio2BackupTemplates.stream()
                .map(
                    index ->
                        new String[] {
                          ((TemplateDescriptor) index).getFullQualifiedName() + "*",
                          "-" + ((TemplateDescriptor) index).getFullQualifiedName()
                        })
                .flatMap(x -> Arrays.stream(x))
                .toArray(String[]::new),
            prio3BackupTemplates.stream()
                .map(index -> ((TemplateDescriptor) index).getFullQualifiedName())
                .toArray(String[]::new),
            // dated indices
            prio3BackupTemplates.stream()
                .map(
                    index ->
                        new String[] {
                          ((TemplateDescriptor) index).getFullQualifiedName() + "*",
                          "-" + ((TemplateDescriptor) index).getFullQualifiedName()
                        })
                .flatMap(x -> Arrays.stream(x))
                .toArray(String[]::new),
            prio4BackupIndices.stream()
                .map(index -> ((IndexDescriptor) index).getFullQualifiedName())
                .toArray(String[]::new),
          };
    }
    return indexPatternsOrdered;
  }

  private String getCurrentOperateVersion() {
    return operateProperties.getVersion().toLowerCase();
  }

  public GetBackupStateResponseDto getBackupState(Long backupId) {
    return repository.getBackupState(getRepositoryName(), backupId);
  }

  public List<GetBackupStateResponseDto> getBackups() {
    return repository.getBackups(getRepositoryName());
  }

  public record SnapshotRequest(
      String repositoryName, String snapshotName, List<String> indices, Metadata metadata) {}
}
