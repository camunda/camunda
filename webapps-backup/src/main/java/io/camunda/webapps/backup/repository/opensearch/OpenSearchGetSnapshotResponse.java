/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.backup.repository.opensearch;

import java.util.List;
import org.opensearch.client.opensearch.snapshot.GetSnapshotResponse;

public record OpenSearchGetSnapshotResponse(List<OpenSearchSnapshotInfo> snapshots) {

  public static OpenSearchGetSnapshotResponse fromResponse(final GetSnapshotResponse response) {
    final var snapshotInfos =
        response.snapshots().stream().map(OpenSearchSnapshotInfo::fromResponse).toList();
    return new OpenSearchGetSnapshotResponse(snapshotInfos);
  }
}
