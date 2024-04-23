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

import static io.camunda.operate.webapp.opensearch.backup.OpensearchBackupRepository.REPOSITORY_MISSING_EXCEPTION_TYPE;
import static io.camunda.operate.webapp.opensearch.backup.OpensearchBackupRepository.SNAPSHOT_MISSING_EXCEPTION_TYPE;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.store.opensearch.client.async.OpenSearchAsyncSnapshotOperations;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchSnapshotOperations;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.response.OpenSearchGetSnapshotResponse;
import io.camunda.operate.store.opensearch.response.OpenSearchSnapshotInfo;
import io.camunda.operate.store.opensearch.response.SnapshotState;
import io.camunda.operate.webapp.backup.BackupService;
import io.camunda.operate.webapp.backup.Metadata;
import io.camunda.operate.webapp.management.dto.BackupStateDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch._types.ErrorCause;
import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.snapshot.*;

@ExtendWith(MockitoExtension.class)
class OpensearchBackupRepositoryTest {
  @Mock private RichOpenSearchClient richOpenSearchClient;

  @Mock private RichOpenSearchClient.Async richOpenSearchClientAsync;

  @Mock private OpenSearchSnapshotOperations openSearchSnapshotOperations;

  @Mock private OpenSearchAsyncSnapshotOperations openSearchAsyncSnapshotOperations;

  @Mock private ObjectMapper objectMapper;

  private OpensearchBackupRepository repository;

  @BeforeEach
  public void setUp() {
    repository = new OpensearchBackupRepository(richOpenSearchClient, objectMapper);
  }

  private void mockAsynchronSnapshotOperations() {
    when(richOpenSearchClient.async()).thenReturn(richOpenSearchClientAsync);
    when(richOpenSearchClientAsync.snapshot()).thenReturn(openSearchAsyncSnapshotOperations);
  }

  private void mockObjectMapperForMetadata(final Metadata metadata) {
    when(objectMapper.convertValue(any(), eq(Metadata.class))).thenReturn(metadata);
  }

  private void mockSynchronSnapshotOperations() {
    when(richOpenSearchClient.snapshot()).thenReturn(openSearchSnapshotOperations);
  }

  @Test
  void getBackupsReturnsEmptyListOfBackups() {
    mockSynchronSnapshotOperations();

    final var response = new OpenSearchGetSnapshotResponse();
    when(openSearchSnapshotOperations.get(any())).thenReturn(response);

    assertThat(repository.getBackups("repo")).isEmpty();
  }

  @Test
  void getBackupsReturnsNotEmptyListOfBackups() {
    final var metadata =
        new Metadata().setBackupId(5L).setVersion("1").setPartNo(1).setPartCount(3);
    final var snapshotInfos =
        List.of(
            new OpenSearchSnapshotInfo()
                .setSnapshot("test-snapshot")
                .setState(SnapshotState.STARTED)
                .setStartTimeInMillis(23L));
    final var response = new OpenSearchGetSnapshotResponse(snapshotInfos);

    mockObjectMapperForMetadata(metadata);
    when(openSearchSnapshotOperations.get(any())).thenReturn(response);
    mockSynchronSnapshotOperations();

    final var snapshotDtoList = repository.getBackups("repo");
    assertThat(snapshotDtoList).hasSize(1);

    final var snapshotDto = snapshotDtoList.get(0);
    assertThat(snapshotDto.getBackupId()).isEqualTo(5L);
    assertThat(snapshotDto.getState()).isEqualTo(BackupStateDto.IN_PROGRESS);
    assertThat(snapshotDto.getFailureReason()).isNull();
    final var snapshotDtoDetails = snapshotDto.getDetails();
    assertThat(snapshotDtoDetails).hasSize(1);
    final var snapshotDtoDetail = snapshotDtoDetails.get(0);
    assertThat(snapshotDtoDetail.getSnapshotName()).isEqualTo("test-snapshot");
    assertThat(snapshotDtoDetail.getState()).isEqualTo("STARTED");
    assertThat(snapshotDtoDetail.getFailures()).isNull();
    assertThat(snapshotDtoDetail.getStartTime().toInstant().toEpochMilli()).isEqualTo(23L);
  }

  @Test
  void successForExecuteSnapshotting() {
    mockAsynchronSnapshotOperations();

    final var snapshotRequest =
        new BackupService.SnapshotRequest(
            "repo", "camunda_operate_1_2", List.of("index-1", "index-2"), new Metadata());
    final Runnable onSuccess = () -> {};
    final Runnable onFailure = () -> fail("Should execute snapshot successfully.");

    final var createSnapShotResponse =
        new CreateSnapshotResponse.Builder()
            .snapshot(
                new SnapshotInfo.Builder()
                    .snapshot("snapshot")
                    .dataStreams(List.of())
                    .indices(List.of("index-1", "index-2"))
                    .uuid("uuid")
                    .state(SnapshotState.SUCCESS.toString())
                    .build())
            .build();
    when(openSearchAsyncSnapshotOperations.create(any()))
        .thenReturn(CompletableFuture.completedFuture(createSnapShotResponse));

    repository.executeSnapshotting(snapshotRequest, onSuccess, onFailure);
  }

  @Test
  void failedForExecuteSnapshotting() {
    final var snapshotRequest =
        new BackupService.SnapshotRequest(
            "repo", "camunda_operate_1_2", List.of("index-1", "index-2"), new Metadata());
    final Runnable onSuccess = () -> fail("Should execute snapshot with failures.");
    final Runnable onFailure = () -> {};

    mockAsynchronSnapshotOperations();
    when(openSearchAsyncSnapshotOperations.create(any()))
        .thenReturn(CompletableFuture.failedFuture(new SocketTimeoutException("no internet")));

    repository.executeSnapshotting(snapshotRequest, onSuccess, onFailure);
  }

  @Test
  void deleteSnapshotSucceed() {
    mockAsynchronSnapshotOperations();

    when(openSearchAsyncSnapshotOperations.delete(any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new DeleteSnapshotResponse.Builder().acknowledged(true).build()));
    repository.deleteSnapshot("repo", "snapshot");
  }

  @Test
  void deleteSnapshotFails() {
    mockAsynchronSnapshotOperations();

    when(openSearchAsyncSnapshotOperations.delete(any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new OpenSearchException(
                    new ErrorResponse.Builder()
                        .status(5)
                        .error(
                            new ErrorCause.Builder()
                                .type(SNAPSHOT_MISSING_EXCEPTION_TYPE)
                                .reason("test reason")
                                .build())
                        .build())));

    repository.deleteSnapshot("repo", "snapshot");
  }

  @Test
  void getBackupState() {
    mockSynchronSnapshotOperations();
    mockObjectMapperForMetadata(new Metadata().setPartCount(3));

    when(openSearchSnapshotOperations.get(any()))
        .thenReturn(
            new OpenSearchGetSnapshotResponse(
                List.of(
                    new OpenSearchSnapshotInfo()
                        .setSnapshot("snapshot")
                        .setState(SnapshotState.SUCCESS)
                        .setStartTimeInMillis(23L))));

    final var response = repository.getBackupState("repo", 5L);

    assertThat(response).isNotNull();
    assertThat(response.getState()).isEqualTo(BackupStateDto.INCOMPLETE);
    assertThat(response.getBackupId()).isEqualTo(5L);
    final var snapshotDetails = response.getDetails();
    assertThat(snapshotDetails).hasSize(1);
    final var snapshotDetail = snapshotDetails.get(0);
    assertThat(snapshotDetail.getState()).isEqualTo(SnapshotState.SUCCESS.toString());
    assertThat(snapshotDetail.getStartTime().toInstant().toEpochMilli()).isEqualTo(23L);
    assertThat(snapshotDetail.getSnapshotName()).isEqualTo("snapshot");
    assertThat(snapshotDetail.getFailures()).isNull();
  }

  @Test
  void validateRepositoryExistsSuccess() {
    mockSynchronSnapshotOperations();
    when(openSearchSnapshotOperations.getRepository(any())).thenReturn(Map.of());

    repository.validateRepositoryExists("repo");
  }

  @Test
  void validateRepositoryExistsFailed() {
    mockSynchronSnapshotOperations();
    when(openSearchSnapshotOperations.getRepository(any()))
        .thenThrow(
            new OpenSearchException(
                new ErrorResponse.Builder()
                    .status(5)
                    .error(
                        new ErrorCause.Builder()
                            .type(REPOSITORY_MISSING_EXCEPTION_TYPE)
                            .reason("test")
                            .build())
                    .build()));

    final var exception =
        assertThrows(
            OperateRuntimeException.class, () -> repository.validateRepositoryExists("repo"));
    assertThat(exception.getMessage()).isEqualTo("No repository with name [repo] could be found.");
  }

  @Test
  void validateNoDuplicateBackupIdSuccess() {
    mockSynchronSnapshotOperations();
    when(openSearchSnapshotOperations.get(any())).thenReturn(new OpenSearchGetSnapshotResponse());

    repository.validateNoDuplicateBackupId("repo", 42L);
  }

  @Test
  void validateNoDuplicateBackupIdFailed() {
    mockSynchronSnapshotOperations();
    when(openSearchSnapshotOperations.get(any()))
        .thenReturn(
            new OpenSearchGetSnapshotResponse(
                List.of(new OpenSearchSnapshotInfo().setUuid("test"))));

    final var exception =
        assertThrows(
            InvalidRequestException.class,
            () -> repository.validateNoDuplicateBackupId("repo", 42L));
    assertThat(exception.getMessage())
        .isEqualTo("A backup with ID [42] already exists. Found snapshots: [test]");
  }
}
