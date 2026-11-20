/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.concurrency.kit;

/**
 * How far a thread got with {@link ThreadControl#runTo}: it reached the sync point, it waits for a
 * lock on the way (a statement still runs after the step timeout of 3 s; {@link #lockWaits} holds
 * the database's waiting lock requests at that moment), or it ended before.
 *
 * <p>Assert on it to state what a test expects, e.g. that a flush does not wait for the cleanup's
 * locks: {@code assertThat(progress.waitsForLock()).describedAs("%s", progress).isFalse()}.
 */
public record Progress(
    String thread, SyncPoint.Match target, Outcome outcome, String lockWaits, String report) {

  public boolean reached() {
    return outcome == Outcome.REACHED;
  }

  public boolean waitsForLock() {
    return outcome == Outcome.WAITS_FOR_LOCK;
  }

  @Override
  public String toString() {
    return switch (outcome) {
      case REACHED -> thread + " reached " + target;
      case WAITS_FOR_LOCK ->
          thread
              + " waits for a lock before reaching "
              + target
              + "\n  lock waits: "
              + lockWaits
              + "\n"
              + report;
      case ENDED -> thread + " ended before reaching " + target + "\n" + report;
    };
  }

  public enum Outcome {
    REACHED,
    WAITS_FOR_LOCK,
    ENDED
  }
}
