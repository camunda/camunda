/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.replication.ReplicationLogStatus;
import java.util.List;

public interface ReplicationStatusMapper {

  long getCurrentLogStatus();

  List<ReplicationLogStatus> getReplicationStatus();

  /** Returns {@code true} when connected to AWS Aurora. */
  boolean isAurora();

  /**
   * Returns {@code true} when the instance is part of an Aurora Global Database. Only meaningful
   * after {@link #isAurora()} has returned {@code true}.
   */
  boolean isAuroraGlobalDatabase();

  /** Returns the primary's current durable LSN for Aurora Global Database. */
  long getAuroraCurrentLogStatus();

  /** Returns per-replica replication status for Aurora Global Database. */
  List<ReplicationLogStatus> getAuroraReplicationStatus();
}
