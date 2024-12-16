/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.backup.repository.opensearch;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.snapshot.SnapshotInfo;
import org.opensearch.client.opensearch.snapshot.SnapshotShardFailure;

public class OpenSearchSnapshotInfo {

  private String snapshot;
  private String uuid;

  private SnapshotState state;

  private List<SnapshotShardFailure> failures = List.of();

  private Long startTimeInMillis;

  private Long endTimeInMillis;

  private Map<String, JsonData> metadata = Map.of();

  public String getSnapshot() {
    return snapshot;
  }

  public OpenSearchSnapshotInfo setSnapshot(final String snapshot) {
    this.snapshot = snapshot;
    return this;
  }

  public String getUuid() {
    return uuid;
  }

  public OpenSearchSnapshotInfo setUuid(final String uuid) {
    this.uuid = uuid;
    return this;
  }

  public SnapshotState getState() {
    return state;
  }

  public OpenSearchSnapshotInfo setState(final SnapshotState state) {
    this.state = state;
    return this;
  }

  public List<SnapshotShardFailure> getFailures() {
    return failures;
  }

  public OpenSearchSnapshotInfo setFailures(final List<SnapshotShardFailure> failures) {
    this.failures = failures;
    return this;
  }

  public Long getStartTimeInMillis() {
    return startTimeInMillis;
  }

  public OpenSearchSnapshotInfo setStartTimeInMillis(final Long startTimeInMillis) {
    this.startTimeInMillis = startTimeInMillis;
    return this;
  }

  public Long getEndTimeInMillis() {
    return endTimeInMillis;
  }

  public OpenSearchSnapshotInfo setEndTimeInMillis(final Long endTimeInMillis) {
    this.endTimeInMillis = endTimeInMillis;
    return this;
  }

  public Map<String, JsonData> getMetadata() {
    return metadata;
  }

  public OpenSearchSnapshotInfo setMetadata(final Map<String, JsonData> metadata) {
    this.metadata = metadata;
    return this;
  }

  public static OpenSearchSnapshotInfo fromResponse(final SnapshotInfo snapshotInfo) {
    return new OpenSearchSnapshotInfo()
        .setSnapshot(snapshotInfo.snapshot())
        .setUuid(snapshotInfo.uuid())
        .setState(SnapshotState.valueOf(snapshotInfo.state()))
        .setStartTimeInMillis(
            snapshotInfo.startTimeInMillis() != null
                ? Long.parseLong(snapshotInfo.startTimeInMillis())
                : 0L)
        .setEndTimeInMillis(
            snapshotInfo.endTimeInMillis() != null
                ? Long.parseLong(snapshotInfo.endTimeInMillis())
                : 0)
        .setMetadata(snapshotInfo.metadata())
        .setFailures(snapshotInfo.failures());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        snapshot, uuid, state, failures, startTimeInMillis, endTimeInMillis, metadata);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final OpenSearchSnapshotInfo that = (OpenSearchSnapshotInfo) o;
    return Objects.equals(snapshot, that.snapshot)
        && Objects.equals(uuid, that.uuid)
        && Objects.equals(state, that.state)
        && Objects.equals(failures, that.failures)
        && Objects.equals(startTimeInMillis, that.startTimeInMillis)
        && Objects.equals(endTimeInMillis, that.endTimeInMillis)
        && Objects.equals(metadata, that.metadata);
  }

  @Override
  public String toString() {
    return "SnapshotInfo{"
        + "snapshot='"
        + snapshot
        + '\''
        + ", uuid='"
        + uuid
        + '\''
        + ", state='"
        + state
        + '\''
        + ", failures="
        + failures
        + ", startTimeInMillis="
        + startTimeInMillis
        + ", endTimeInMillis="
        + endTimeInMillis
        + ", metadata="
        + metadata
        + '}';
  }
}
