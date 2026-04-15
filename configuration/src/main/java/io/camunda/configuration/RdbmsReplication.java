/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

public class RdbmsReplication {

  /** Enables asynchronous replication support in the RDBMS exporter. */
  private boolean enabled = false;

  /** Minimum number of synchronous replicas that must acknowledge writes. */
  private int minSyncReplicas = 0;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public int getMinSyncReplicas() {
    return minSyncReplicas;
  }

  public void setMinSyncReplicas(final int minSyncReplicas) {
    this.minSyncReplicas = minSyncReplicas;
  }
}
