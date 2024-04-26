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
package io.camunda.operate.schema.store.opensearch.client.sync;

import static io.camunda.operate.store.opensearch.dsl.RequestDSL.getSnapshotRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.repositoryRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.opensearch.ExtendedOpenSearchClient;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchSnapshotOperations;
import io.camunda.operate.store.opensearch.response.OpenSearchSnapshotInfo;
import io.camunda.operate.store.opensearch.response.SnapshotState;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.snapshot.GetRepositoryRequest;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
class OpenSearchSnapshotOperationsTest {

  @Mock ExtendedOpenSearchClient openSearchClient;

  @Mock Logger logger;

  private OpenSearchSnapshotOperations snapshotOperations;

  @BeforeEach
  void setUp() {
    snapshotOperations = new OpenSearchSnapshotOperations(logger, openSearchClient);
    assertThat(snapshotOperations).isNotNull();
  }

  @Test
  void shouldThrowExceptionForNoRepository() {
    final var exception =
        assertThrows(
            OperateRuntimeException.class,
            () -> snapshotOperations.getRepository(new GetRepositoryRequest.Builder()));
    assertThat(exception.getMessage()).isEqualTo("Get repository needs at least one name.");
  }

  @Test
  void shouldGetRepository() throws IOException {
    final var response =
        snapshotOperations.getRepository(repositoryRequestBuilder("test-repository"));
    assertThat(response).isNotNull();
    verify(openSearchClient).arbitraryRequest("GET", "/_snapshot/test-repository", "{}");
  }

  @Test
  void shouldGetOnlyFirstRepository() throws IOException {
    final var response =
        snapshotOperations.getRepository(
            new GetRepositoryRequest.Builder().name("test-repository", "test-repository2"));
    assertThat(response).isNotNull();
    verify(openSearchClient).arbitraryRequest("GET", "/_snapshot/test-repository", "{}");
  }

  @Test
  void shouldThrowExceptionIfCantGetRepository() throws Exception {
    when(openSearchClient.arbitraryRequest(
            "GET", "/_snapshot/test-repository-does-not-exist", "{}"))
        .thenThrow(IOException.class);
    final var exception =
        assertThrows(
            OperateRuntimeException.class,
            () -> snapshotOperations.getRepository(repositoryRequestBuilder("test-repository")));
    assertThat(exception).hasMessage("Failed to get repository test-repository");
  }

  @Test
  void shouldGetSnapshot() throws IOException {
    final Map<String, Object> openSearchResponse =
        Map.of(
            "snapshots",
            List.of(
                Map.of(
                    "snapshot",
                    "snapshot-name",
                    "uuid",
                    "uuid-value",
                    "state",
                    "STARTED",
                    "start_time_in_millis",
                    23L,
                    "metadata",
                    Map.of(),
                    "failures",
                    List.of())));
    when(openSearchClient.arbitraryRequest("GET", "/_snapshot/test-repository/test-snapshot", "{}"))
        .thenReturn(openSearchResponse);
    final var response =
        snapshotOperations.get(getSnapshotRequestBuilder("test-repository", "test-snapshot"));
    assertThat(response.snapshots()).hasSize(1);
    final var snapshotInfo = response.snapshots().getFirst();
    assertSnapshotInfo(snapshotInfo);
  }

  @Test
  void shouldThrowExceptionIfCantGetSnapshot() throws Exception {
    when(openSearchClient.arbitraryRequest("GET", "/_snapshot/test-repository/test-snapshot", "{}"))
        .thenThrow(new IOException("Connection error"));
    final var exception =
        assertThrows(
            OperateRuntimeException.class,
            () ->
                snapshotOperations.get(
                    getSnapshotRequestBuilder("test-repository", "test-snapshot")));
    assertThat(exception)
        .hasMessage("Failed to get snapshot test-snapshot in repository test-repository");
    assertThat(exception.getCause()).isInstanceOf(IOException.class);
  }

  private void assertSnapshotInfo(final OpenSearchSnapshotInfo snapshotInfo) {
    assertThat(snapshotInfo.getState()).isEqualTo(SnapshotState.STARTED);
    assertThat(snapshotInfo.getStartTimeInMillis()).isEqualTo(23L);
    assertThat(snapshotInfo.getMetadata()).isEmpty();
    assertThat(snapshotInfo.getFailures()).isEmpty();
    assertThat(snapshotInfo.getUuid()).isEqualTo("uuid-value");
    assertThat(snapshotInfo.getSnapshot()).isEqualTo("snapshot-name");
  }
}
