/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.backup.repository.opensearch;

import java.util.List;
import java.util.Objects;
import org.opensearch.client.opensearch.snapshot.GetSnapshotResponse;

public class OpenSearchGetSnapshotResponse {

  private List<OpenSearchSnapshotInfo> snapshotInfos = List.of();

  public OpenSearchGetSnapshotResponse() {}

  public OpenSearchGetSnapshotResponse(final List<OpenSearchSnapshotInfo> snapshotInfos) {
    this.snapshotInfos = snapshotInfos;
  }

  public List<OpenSearchSnapshotInfo> snapshots() {
    return snapshotInfos;
  }

  public static OpenSearchGetSnapshotResponse fromResponse(final GetSnapshotResponse response) {
    final var snapshotInfos =
        response.snapshots().stream().map(OpenSearchSnapshotInfo::fromResponse).toList();
    return new OpenSearchGetSnapshotResponse(snapshotInfos);
  }

  @Override
  public int hashCode() {
    return Objects.hash(snapshotInfos);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final OpenSearchGetSnapshotResponse that = (OpenSearchGetSnapshotResponse) o;
    return Objects.equals(snapshotInfos, that.snapshotInfos);
  }

  @Override
  public String toString() {
    return "OpenSearchGetSnapshotResponse{" + "snapshotInfos=" + snapshotInfos + '}';
  }
}
