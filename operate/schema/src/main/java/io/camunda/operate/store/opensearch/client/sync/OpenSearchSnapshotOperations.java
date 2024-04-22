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
package io.camunda.operate.store.opensearch.client.sync;

import static java.lang.String.format;

import io.camunda.operate.store.opensearch.response.OpenSearchGetSnapshotResponse;
import io.camunda.operate.store.opensearch.response.OpenSearchSnapshotInfo;
import io.camunda.operate.store.opensearch.response.SnapshotState;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.snapshot.*;
import org.slf4j.Logger;

public class OpenSearchSnapshotOperations extends OpenSearchSyncOperation {
  public OpenSearchSnapshotOperations(
      final Logger logger, final OpenSearchClient openSearchClient) {
    super(logger, openSearchClient);
  }

  public Map<String, Object> getRepository(final GetRepositoryRequest.Builder requestBuilder) {
    final var request = requestBuilder.build();
    final var repository = request.name().get(0);
    return withExtendedOpenSearchClient(
        extendedOpenSearchClient ->
            safe(
                () ->
                    extendedOpenSearchClient.arbitraryRequest(
                        "GET", String.format("/_snapshot/%s", repository), "{}"),
                e -> format("Failed to get repository %s", repository)));
  }

  public OpenSearchGetSnapshotResponse get(final GetSnapshotRequest.Builder requestBuilder) {
    final var request = requestBuilder.build();
    final var repository = request.repository();
    final var snapshot = request.snapshot().get(0);
    final var result =
        withExtendedOpenSearchClient(
            extendedOpenSearchClient ->
                safe(
                    () ->
                        extendedOpenSearchClient.arbitraryRequest(
                            "GET", String.format("/_snapshot/%s/%s", repository, snapshot), "{}"),
                    e ->
                        format(
                            "Failed to get snapshot %s in repository %s", snapshot, repository)));
    final var snapshotInfosAsMap = (List<Map<String, Object>>) result.get("snapshots");
    final var snapshotInfos = snapshotInfosAsMap.stream().map(this::mapToSnapshotInfo).toList();
    return new OpenSearchGetSnapshotResponse(snapshotInfos);
  }

  private OpenSearchSnapshotInfo mapToSnapshotInfo(final Map<String, Object> map) {
    final Map<String, Object> metadata = (Map<String, Object>) map.get("metadata");
    final List<Object> failures = (List<Object>) map.get("failures");
    return new OpenSearchSnapshotInfo()
        .setSnapshot((String) map.get("snapshot"))
        .setUuid((String) map.get("uuid"))
        .setState(SnapshotState.valueOf((String) map.get("state")))
        .setStartTimeInMillis((Long) map.get("start_time_in_millis"))
        .setMetadata(metadata)
        .setFailures(failures);
  }
}
