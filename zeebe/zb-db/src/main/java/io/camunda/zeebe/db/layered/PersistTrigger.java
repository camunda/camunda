/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered;

/** What caused a persist round to be scheduled; the runtime picks the trigger, the store counts. */
public enum PersistTrigger {
  /** The regular persist cadence found buffered writes. */
  INTERVAL("interval"),
  /** The pinned bytes of a store exceeded its budget, forcing a round now. */
  OVER_CAPACITY("overCapacity"),
  /** A snapshot is about to be taken and needs a prefix-consistent durable cut first. */
  PRE_SNAPSHOT("preSnapshot"),
  /**
   * A scheduled task is about to execute and must observe every committed batch through its
   * persisted-state reads.
   */
  SCHEDULED_TASK("scheduledTask");

  private final String label;

  PersistTrigger(final String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
