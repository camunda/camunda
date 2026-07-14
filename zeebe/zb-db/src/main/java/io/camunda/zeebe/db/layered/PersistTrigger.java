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
  /**
   * The initial entries of a freshly opened exporter domain are drained immediately: committed
   * readers (snapshot selection, log compaction) treat an empty exporter column family as "no
   * exporters configured", so the initial entries must not linger in the buffer where that check
   * cannot see them.
   */
  EXPORTER_INITIAL("exporterInitial"),
  /**
   * The domain's buffered bytes climbed onto the buffer-pressure ladder's start rung (a configured
   * fraction of the buffered-bytes budget), starting a paced round early. Labelled after the
   * default rung fraction (70%).
   */
  LADDER_70("ladder70"),
  /**
   * The domain's buffered bytes climbed onto the buffer-pressure ladder's flat-out rung (a
   * configured fraction of the buffered-bytes budget), or a single store exceeded its own byte
   * budget: the round starts immediately and drains unpaced. Labelled after the default rung
   * fraction (90%).
   */
  LADDER_90("ladder90"),
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
