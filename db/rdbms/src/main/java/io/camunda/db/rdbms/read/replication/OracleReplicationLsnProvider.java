/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.replication;

import io.camunda.db.rdbms.sql.ReplicationStatusMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.exceptions.PersistenceException;

/** Oracle provider using exact applied SCNs and optional approximate timing metrics. */
public final class OracleReplicationLsnProvider implements ReplicationLsnProvider {

  private static final String EXPIRED_SCN_TIMESTAMP_ERROR = "ORA-08181";

  private final ReplicationStatusMapper mapper;

  public OracleReplicationLsnProvider(final ReplicationStatusMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public long getCurrent() {
    return mapper.getCurrentLogStatus();
  }

  @Override
  public long getCurrentDbTime() {
    return mapper.getCurrentDbTime();
  }

  @Override
  public List<ReplicationLsnStatus> getReplicationStatuses() {
    final var exactStatuses = mapper.getReplicationStatus();
    if (exactStatuses.isEmpty()) {
      return exactStatuses;
    }

    try {
      return mergeTimingStatuses(exactStatuses, mapper.getOracleReplicationStatusWithTiming());
    } catch (final PersistenceException exception) {
      if (isExpiredScnTimestamp(exception)) {
        return exactStatuses;
      }
      throw exception;
    }
  }

  private List<ReplicationLsnStatus> mergeTimingStatuses(
      final List<ReplicationLsnStatus> exactStatuses,
      final List<ReplicationLsnStatus> timingStatuses) {
    final Map<String, ReplicationLsnStatus> timingByReplicaId = new HashMap<>();
    timingStatuses.forEach(status -> timingByReplicaId.put(status.replicaId(), status));

    return exactStatuses.stream()
        .map(
            exactStatus -> {
              final var timingStatus = timingByReplicaId.get(exactStatus.replicaId());
              return timingStatus == null
                  ? exactStatus
                  : new ReplicationLsnStatus(
                      exactStatus.logStatus(),
                      exactStatus.replicaId(),
                      timingStatus.replicationLagMs(),
                      timingStatus.replicatedUntilMs());
            })
        .toList();
  }

  private boolean isExpiredScnTimestamp(final PersistenceException exception) {
    Throwable cause = exception;
    while (cause != null) {
      if (cause.getMessage() != null && cause.getMessage().contains(EXPIRED_SCN_TIMESTAMP_ERROR)) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }
}
