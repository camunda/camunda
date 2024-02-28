/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch.client.sync;

import static java.lang.String.format;

import io.camunda.operate.store.opensearch.response.OpenSearchGetSnapshotResponse;
import io.camunda.operate.store.opensearch.response.OpenSearchSnapshotInfo;
import io.camunda.operate.store.opensearch.response.SnapshotState;
import java.io.IOException;
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

  public GetRepositoryResponse getRepository(final GetRepositoryRequest.Builder requestBuilder)
      throws IOException {
    return openSearchClient.snapshot().getRepository(requestBuilder.build());
  }

  public OpenSearchGetSnapshotResponse get(final GetSnapshotRequest.Builder requestBuilder)
      throws IOException {
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
