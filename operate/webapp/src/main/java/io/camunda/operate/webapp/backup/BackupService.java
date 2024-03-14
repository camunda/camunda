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
package io.camunda.operate.webapp.backup;

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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@Configuration
public class BackupService {
  public record SnapshotRequest(
      String repositoryName, String snapshotName, List<String> indices, Metadata metadata) {}

  private static final Logger logger = LoggerFactory.getLogger(BackupService.class);
  private final ThreadPoolTaskExecutor threadPoolTaskExecutor;

  private final List<Prio1Backup> prio1BackupIndices;

  private final List<Prio2Backup> prio2BackupTemplates;

  private final List<Prio3Backup> prio3BackupTemplates;

  private final List<Prio4Backup> prio4BackupIndices;

  private final OperateProperties operateProperties;

  private final BackupRepository repository;

  public BackupService(
      @Qualifier("backupThreadPoolExecutor") ThreadPoolTaskExecutor threadPoolTaskExecutor,
      List<Prio1Backup> prio1BackupIndices,
      List<Prio2Backup> prio2BackupTemplates,
      List<Prio3Backup> prio3BackupTemplates,
      List<Prio4Backup> prio4BackupIndices,
      OperateProperties operateProperties,
      BackupRepository repository) {
    this.threadPoolTaskExecutor = threadPoolTaskExecutor;
    this.prio1BackupIndices = prio1BackupIndices;
    this.prio2BackupTemplates = prio2BackupTemplates;
    this.prio3BackupTemplates = prio3BackupTemplates;
    this.prio4BackupIndices = prio4BackupIndices;
    this.repository = repository;
    this.operateProperties = operateProperties;
  }

  private final Queue<SnapshotRequest> requestsQueue = new ConcurrentLinkedQueue<>();

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
    if (!requestsQueue.isEmpty()) {
      throw new InvalidRequestException("Another backup is running at the moment");
    }
    synchronized (requestsQueue) {
      if (!requestsQueue.isEmpty()) {
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
      logger.debug("Snapshot scheduled: {}", snapshotName);
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
          () ->
              repository.executeSnapshotting(
                  nextRequest, this::scheduleNextSnapshot, requestsQueue::clear));
      logger.debug("Snapshot picked for execution: {}", nextRequest);
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
                .flatMap(Arrays::stream)
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
                .flatMap(Arrays::stream)
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
}
