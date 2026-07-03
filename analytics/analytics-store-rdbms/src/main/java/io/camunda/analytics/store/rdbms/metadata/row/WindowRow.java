/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms.metadata.row;

/** Row model for {@code ANALYTICS_DATASET_WINDOW}. */
public final class WindowRow {

  private long cubeId;
  private int ordinal;
  private long windowMs;

  public WindowRow() {}

  public WindowRow(final long cubeId, final int ordinal, final long windowMs) {
    this.cubeId = cubeId;
    this.ordinal = ordinal;
    this.windowMs = windowMs;
  }

  public long getCubeId() {
    return cubeId;
  }

  public void setCubeId(final long cubeId) {
    this.cubeId = cubeId;
  }

  public int getOrdinal() {
    return ordinal;
  }

  public void setOrdinal(final int ordinal) {
    this.ordinal = ordinal;
  }

  public long getWindowMs() {
    return windowMs;
  }

  public void setWindowMs(final long windowMs) {
    this.windowMs = windowMs;
  }
}
