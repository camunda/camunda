/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreParameters;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Which backups one physical tenant is restored from: either explicit checkpoint ids, or a time
 * range to resolve the latest restore point within.
 *
 * <p>Mirrors {@code ClusterConfigurationManagementRequest.RestoreParameters}, so the standalone
 * restore application and the {@code /cluster/v2/restore} endpoint accept the same selection; see
 * {@link #toRestoreParameters()} for the conversion between the two.
 *
 * @param backupIds explicit checkpoint ids, empty when a time range is used instead
 * @param from start of the time range to resolve a restore point in
 * @param to end of that range; may be open even when {@code from} is set
 */
@NullMarked
public record RestoreSelection(List<Long> backupIds, @Nullable Instant from, @Nullable Instant to) {

  public RestoreSelection {
    backupIds = List.copyOf(backupIds);
    if (!backupIds.isEmpty() && (from != null || to != null)) {
      throw new IllegalArgumentException(
          "Expected either backup ids or a time range, but got both: backupIds=%s, from=%s, to=%s"
              .formatted(backupIds, from, to));
    }
  }

  public static RestoreSelection ofBackupIds(final List<Long> backupIds) {
    return new RestoreSelection(backupIds, null, null);
  }

  public static RestoreSelection ofTimeRange(
      final @Nullable Instant from, final @Nullable Instant to) {
    return new RestoreSelection(List.of(), from, to);
  }

  /**
   * Nothing named at all — for RDBMS, the restore point is then derived from exported positions.
   */
  public static RestoreSelection latest() {
    return new RestoreSelection(List.of(), null, null);
  }

  public boolean hasBackupIds() {
    return !backupIds.isEmpty();
  }

  public boolean hasTimeRange() {
    return from != null || to != null;
  }

  public long[] backupIdsAsArray() {
    return backupIds.stream().mapToLong(Long::longValue).toArray();
  }

  /**
   * This selection expressed as {@code ClusterConfigurationManagementRequest.RestoreParameters} —
   * the wire shape a {@code RestoreRequest} carries, with {@code from}/{@code to} as strings rather
   * than {@link Instant}s.
   */
  public RestoreParameters toRestoreParameters() {
    return new RestoreParameters(
        backupIds, from != null ? from.toString() : null, to != null ? to.toString() : null);
  }
}
