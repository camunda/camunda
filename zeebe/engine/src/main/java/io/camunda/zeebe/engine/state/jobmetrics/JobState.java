/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.jobmetrics;

/** Represents the different statuses a job can have for metrics tracking. */
public enum JobState {
  CREATED(0),
  COMPLETED(1),
  CANCELED(2),
  ERROR_THROWN(3),
  FAILED(4),
  MIGRATED(5),
  RETRIES_UPDATED(6),
  TIMED_OUT(7);
  private final int index;

  JobState(final int index) {
    this.index = index;
  }

  public int getIndex() {
    return index;
  }

  public static int count() {
    return values().length;
  }
}
