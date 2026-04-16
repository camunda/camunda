/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

public class ReplicationStatusDto {

  private long logStatus;
  private String replicaId;
  private long replicationLagMs;

  public long getLogStatus() {
    return logStatus;
  }

  public void setLogStatus(final long logStatus) {
    this.logStatus = logStatus;
  }

  public String getReplicaId() {
    return replicaId;
  }

  public void setReplicaId(final String replicaId) {
    this.replicaId = replicaId;
  }

  public long getReplicationLagMs() {
    return replicationLagMs;
  }

  public void setReplicationLagMs(final long replicationLagMs) {
    this.replicationLagMs = replicationLagMs;
  }
}
