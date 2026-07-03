/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms.metadata.row;

/** Row model for {@code ANALYTICS_DATASET_ACTIVATION}. */
public final class ActivationRow {

  private long cubeId;
  private int partitionId;
  private long activationPosition;

  public ActivationRow() {}

  public ActivationRow(final long cubeId, final int partitionId, final long activationPosition) {
    this.cubeId = cubeId;
    this.partitionId = partitionId;
    this.activationPosition = activationPosition;
  }

  public long getCubeId() {
    return cubeId;
  }

  public void setCubeId(final long cubeId) {
    this.cubeId = cubeId;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public void setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
  }

  public long getActivationPosition() {
    return activationPosition;
  }

  public void setActivationPosition(final long activationPosition) {
    this.activationPosition = activationPosition;
  }
}
