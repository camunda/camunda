/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch.client.sync;

import static java.lang.String.format;

import io.camunda.operate.exceptions.OperateRuntimeException;
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
    final var names = request.name();
    if (names.isEmpty()) {
      throw new OperateRuntimeException("Get repository needs at least one name.");
    }
    if (names.size() > 1) {
      logger.warn(
          "More than one repository names given: {} . Use only first one: {}",
          names,
          names.getFirst());
    }
    final var repository = names.getFirst();
    return withExtendedOpenSearchClient(
        extendedOpenSearchClient ->
            safe(
                () ->
                    extendedOpenSearchClient.arbitraryRequest(
                        "GET", String.format("/_snapshot/%s", repository), "{}"),
                e -> format("Failed to get repository %s", repository)));
  }

  public OpenSearchGetSnapshotResponse get(final GetSnapshotRequest request) {
    final var repository = request.repository();
    final var snapshots = request.snapshot();
    final var verbose = request.verbose();
    if (snapshots.size() > 1) {
      logger.warn(
          "More than one snapshot names given: {} . Use only first one: {}",
          snapshots,
          snapshots.getFirst());
    }
    final var snapshot = snapshots.getFirst();
    final var result =
        withExtendedOpenSearchClient(
            extendedOpenSearchClient ->
                safe(
                    () ->
                        extendedOpenSearchClient.arbitraryRequest(
                            "GET",
                            String.format(
                                "/_snapshot/%s/%s?verbose=%s", repository, snapshot, verbose),
                            "{}"),
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
