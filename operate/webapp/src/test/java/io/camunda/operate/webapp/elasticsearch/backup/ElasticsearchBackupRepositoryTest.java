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
package io.camunda.operate.webapp.elasticsearch.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.property.BackupProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.backup.Metadata;
import io.camunda.operate.webapp.management.dto.BackupStateDto;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.SnapshotClient;
import org.elasticsearch.snapshots.SnapshotId;
import org.elasticsearch.snapshots.SnapshotInfo;
import org.elasticsearch.snapshots.SnapshotState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ElasticsearchBackupRepositoryTest {

  private final String repositoryName = "repo1";
  private final long backupId = 555;
  private final String snapshotName = "camunda_operate_" + backupId + "_8.6_part_1_of_6";

  private final long incompleteCheckTimeoutLengthSeconds =
      new BackupProperties().getIncompleteCheckTimeoutInSeconds();
  private final long incompleteCheckTimeoutLength = incompleteCheckTimeoutLengthSeconds * 1000;

  @Mock private RestHighLevelClient esClient;
  @Mock private SnapshotClient snapshotClient;
  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private OperateProperties operateProperties;

  @InjectMocks @Spy private ElasticsearchBackupRepository backupRepository;

  @Test
  void shouldCreateRepository() {
    assertThat(backupRepository).isNotNull();
  }

  @Test
  void shouldReturnBackupStateCompleted() throws IOException {
    final var snapshotClient = mock(SnapshotClient.class);
    final var firstSnapshotInfo = mock(SnapshotInfo.class);
    final var snapshotResponse = mock(GetSnapshotsResponse.class);

    // Set up Snapshot client
    when(esClient.snapshot()).thenReturn(snapshotClient);
    // Set up Snapshot details
    final Map<String, Object> metadata =
        objectMapper.convertValue(new Metadata().setPartCount(1), Map.class);
    when(firstSnapshotInfo.userMetadata()).thenReturn(metadata);
    when(firstSnapshotInfo.snapshotId()).thenReturn(new SnapshotId("snapshot-name", "uuid"));
    when(firstSnapshotInfo.state()).thenReturn(SnapshotState.SUCCESS);

    // Set up Snapshot response
    when(snapshotResponse.getSnapshots()).thenReturn(List.of(firstSnapshotInfo));
    when(snapshotClient.get(any(), any())).thenReturn(snapshotResponse);

    // Test
    final var backupState = backupRepository.getBackupState("repository-name", 5L);
    assertThat(backupState.getState()).isEqualTo(BackupStateDto.COMPLETED);
  }

  @Test
  void shouldReturnBackupStateIncomplete() throws IOException {
    final var snapshotClient = mock(SnapshotClient.class);
    final var firstSnapshotInfo = mock(SnapshotInfo.class);
    final var snapshotResponse = mock(GetSnapshotsResponse.class);
    final var lastSnapshotInfo = mock(SnapshotInfo.class);

    // Set up Snapshot client
    when(esClient.snapshot()).thenReturn(snapshotClient);
    // Set up operate properties
    when(operateProperties.getBackup().getIncompleteCheckTimeoutInSeconds())
        .thenReturn(incompleteCheckTimeoutLengthSeconds);
    // Set up first Snapshot details
    final Map<String, Object> metadata =
        objectMapper.convertValue(new Metadata().setPartCount(3), Map.class);
    when(firstSnapshotInfo.userMetadata()).thenReturn(metadata);
    when(firstSnapshotInfo.snapshotId()).thenReturn(new SnapshotId("snapshot-name", "uuid"));
    when(firstSnapshotInfo.state()).thenReturn(SnapshotState.SUCCESS);
    when(firstSnapshotInfo.startTime()).thenReturn(23L);

    // Set up last Snapshot details
    when(lastSnapshotInfo.snapshotId()).thenReturn(new SnapshotId("snapshot-name", "uuid"));
    when(lastSnapshotInfo.endTime()).thenReturn(23L + 6 * 60 * 1_000);
    when(lastSnapshotInfo.state()).thenReturn(SnapshotState.SUCCESS);

    // Set up Snapshot response
    when(snapshotResponse.getSnapshots()).thenReturn(List.of(firstSnapshotInfo, lastSnapshotInfo));
    when(snapshotClient.get(any(), any())).thenReturn(snapshotResponse);

    // Test
    final var backupState = backupRepository.getBackupState("repository-name", 5L);
    assertThat(backupState.getState()).isEqualTo(BackupStateDto.INCOMPLETE);
  }

  @Test
  void shouldReturnBackupStateIncompleteWhenLastSnapshotEndTimeIsTimedOut() throws IOException {
    final var snapshotClient = mock(SnapshotClient.class);
    final var firstSnapshotInfo = mock(SnapshotInfo.class);
    final var snapshotResponse = mock(GetSnapshotsResponse.class);
    final var lastSnapshotInfo = mock(SnapshotInfo.class);
    final long now = Instant.now().toEpochMilli();

    // Set up Snapshot client
    when(esClient.snapshot()).thenReturn(snapshotClient);
    // Set up operate properties
    when(operateProperties.getBackup().getIncompleteCheckTimeoutInSeconds())
        .thenReturn(incompleteCheckTimeoutLengthSeconds);
    // Set up first Snapshot details
    final Map<String, Object> metadata =
        objectMapper.convertValue(new Metadata().setPartCount(3), Map.class);
    when(firstSnapshotInfo.userMetadata()).thenReturn(metadata);
    when(firstSnapshotInfo.snapshotId()).thenReturn(new SnapshotId("snapshot-name", "uuid"));
    when(firstSnapshotInfo.state()).thenReturn(SnapshotState.SUCCESS);
    when(firstSnapshotInfo.startTime()).thenReturn(now - 4_000);

    // Set up last Snapshot details
    when(lastSnapshotInfo.snapshotId()).thenReturn(new SnapshotId("snapshot-name", "uuid"));
    when(lastSnapshotInfo.state()).thenReturn(SnapshotState.SUCCESS);
    when(lastSnapshotInfo.startTime()).thenReturn(now - (incompleteCheckTimeoutLength + 4_000));
    when(lastSnapshotInfo.endTime()).thenReturn(now - (incompleteCheckTimeoutLength + 2_000));

    // Set up Snapshot response
    when(snapshotResponse.getSnapshots()).thenReturn(List.of(firstSnapshotInfo, lastSnapshotInfo));
    when(snapshotClient.get(any(), any())).thenReturn(snapshotResponse);

    // Test
    final var backupState = backupRepository.getBackupState("repository-name", 5L);
    assertThat(backupState.getState()).isEqualTo(BackupStateDto.INCOMPLETE);
  }

  @Test
  void shouldReturnBackupStateProgress() throws IOException {
    final var snapshotClient = mock(SnapshotClient.class);
    final var firstSnapshotInfo = mock(SnapshotInfo.class);
    final var lastSnapshotInfo = mock(SnapshotInfo.class);
    final var snapshotResponse = mock(GetSnapshotsResponse.class);
    final long now = Instant.now().toEpochMilli();

    // Set up Snapshot client
    when(esClient.snapshot()).thenReturn(snapshotClient);
    // Set up operate properties
    when(operateProperties.getBackup().getIncompleteCheckTimeoutInSeconds())
        .thenReturn(incompleteCheckTimeoutLengthSeconds);
    // Set up Snapshot details
    final Map<String, Object> metadata =
        objectMapper.convertValue(new Metadata().setPartCount(3), Map.class);
    when(firstSnapshotInfo.userMetadata()).thenReturn(metadata);
    when(firstSnapshotInfo.snapshotId())
        .thenReturn(new SnapshotId("first-snapshot-name", "uuid-first"));
    when(lastSnapshotInfo.snapshotId())
        .thenReturn(new SnapshotId("last-snapshot-name", "uuid-last"));
    when(firstSnapshotInfo.state()).thenReturn(SnapshotState.SUCCESS);
    when(lastSnapshotInfo.state()).thenReturn(SnapshotState.SUCCESS);
    when(firstSnapshotInfo.startTime()).thenReturn(now - 4_000);
    when(lastSnapshotInfo.startTime()).thenReturn(now - 200);
    when(lastSnapshotInfo.endTime()).thenReturn(now - 5);
    // Set up Snapshot response
    when(snapshotResponse.getSnapshots()).thenReturn(List.of(firstSnapshotInfo, lastSnapshotInfo));
    when(snapshotClient.get(any(), any())).thenReturn(snapshotResponse);

    // Test
    final var backupState = backupRepository.getBackupState("repository-name", 5L);
    assertThat(backupState.getState()).isEqualTo(BackupStateDto.IN_PROGRESS);
  }
}
