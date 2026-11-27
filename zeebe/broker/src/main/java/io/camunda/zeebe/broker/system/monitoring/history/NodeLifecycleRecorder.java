/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring.history;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Records when the broker starts and stops. A broker that crashes cannot record its own crash, so
 * the start looks back instead: if the history holds no {@link #NODE_STOPPED} after the previous
 * {@link #NODE_STARTED}, the previous run ended without a graceful shutdown, for example by a kill,
 * an out-of-memory error or a lost machine. The last entry of that run tells roughly when it ended.
 */
public final class NodeLifecycleRecorder {

  public static final String NODE_STARTED = "NODE_STARTED";
  public static final String NODE_STOPPED = "NODE_STOPPED";

  private final HealthHistory history;

  public NodeLifecycleRecorder(final HealthHistory history) {
    this.history = history;
  }

  public void recordStart(final String version) {
    final var previousRun = previousRun(history.entries());
    final var details = new HashMap<String, String>();
    details.put("version", version);
    final String message;

    if (previousRun.isEmpty()) {
      details.put("previousShutdown", "NONE");
      message = "started; no previous run is recorded";
    } else {
      final var start = previousRun.getFirst();
      final var last = previousRun.getLast();
      final var clean = last.type().equals(NODE_STOPPED);
      details.put("previousShutdown", clean ? "CLEAN" : "UNCLEAN");
      details.put("previousLastEntryAt", last.timestamp().toString());
      if (start.type().equals(NODE_STARTED)) {
        details.put("previousStartedAt", start.timestamp().toString());
        final var previousVersion = start.details().get("version");
        if (previousVersion != null) {
          details.put("previousVersion", previousVersion);
        }
      }
      message =
          clean
              ? "started after a graceful shutdown at %s".formatted(last.timestamp())
              : "started after an unclean shutdown; the previous run's last entry is from %s"
                  .formatted(last.timestamp());
    }

    history.record("", NODE_STARTED, message, details);
  }

  public void recordStop() {
    history.record("", NODE_STOPPED, "stopped gracefully", Map.of());
  }

  /**
   * The entries of the previous run: from its {@link #NODE_STARTED} up to its {@link #NODE_STOPPED}
   * if it has one, otherwise up to its last entry. Entries recorded after a stop, while components
   * were shutting down, do not count as the run's end.
   */
  private static List<HealthHistoryEntry> previousRun(final List<HealthHistoryEntry> entries) {
    int start = -1;
    for (int i = entries.size() - 1; i >= 0; i--) {
      if (entries.get(i).type().equals(NODE_STARTED)) {
        start = i;
        break;
      }
    }
    // the run's start may have been dropped from a full history; then the whole history is the run
    final var run = entries.subList(Math.max(start, 0), entries.size());
    for (int i = 0; i < run.size(); i++) {
      if (run.get(i).type().equals(NODE_STOPPED)) {
        return run.subList(0, i + 1);
      }
    }
    return run;
  }
}
