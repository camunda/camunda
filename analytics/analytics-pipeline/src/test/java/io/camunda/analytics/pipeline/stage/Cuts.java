/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.stage;

import io.camunda.eventbridge.streaming.CommitCut;
import io.camunda.eventbridge.streaming.Task;

/** Test-side commit driver: runs a task's cut synchronously, as the runtime's stop path does. */
final class Cuts {

  private Cuts() {}

  /** Freeze → publish → persist → complete, inline — every commit is a cut (streaming ADR 0008). */
  static void commit(final Task<?> task, final long offset) {
    final CommitCut cut = task.freezeCut(offset);
    try {
      cut.publish();
      cut.persist();
    } catch (final RuntimeException e) {
      cut.complete(false);
      throw e;
    }
    cut.complete(true);
  }
}
