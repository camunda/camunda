/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.jobmetrics;

/** Represents the different states a job can be in, used for tracking job worker metrics. */
public enum JobMetricState {
  CANCELED(0),
  COMPLETED(1),
  CREATED(2),
  ERROR_THROWN(3),
  FAILED(4),
  MIGRATED(5),
  RETRIES_UPDATED(6),
  TIMED_OUT(7);

  public static final int STATE_COUNT = values().length;

  private final int index;

  JobMetricState(final int index) {
    this.index = index;
  }

  public int getIndex() {
    return index;
  }

  public static JobMetricState fromIndex(final int index) {
    for (final JobMetricState state : values()) {
      if (state.index == index) {
        return state;
      }
    }
    throw new IllegalArgumentException("Unknown job metric state index: " + index);
  }
}
