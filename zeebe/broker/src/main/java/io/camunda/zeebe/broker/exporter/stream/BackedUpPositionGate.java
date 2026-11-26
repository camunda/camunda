/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import java.time.Duration;
import java.util.OptionalLong;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Polls a {@link BackedUpPositionSource} on the exporter actor and tracks the highest log position
 * covered by a backup common to all partitions. All methods must be called from the owning actor.
 */
@NullMarked
final class BackedUpPositionGate {

  private static final Logger LOG = Loggers.EXPORTER_LOGGER;

  private final BackedUpPositionSource source;
  private final Duration pollInterval;
  private final ConcurrencyControl actor;
  private final Runnable onPositionAdvanced;

  private @Nullable ScheduledTimer pollTimer;
  private boolean requestInFlight;
  private boolean lastPollFailed;
  private boolean active;
  private long backedUpPosition = -1;

  BackedUpPositionGate(
      final BackedUpPositionSource source,
      final Duration pollInterval,
      final ConcurrencyControl actor,
      final Runnable onPositionAdvanced) {
    this.source = source;
    this.pollInterval = pollInterval;
    this.actor = actor;
    this.onPositionAdvanced = onPositionAdvanced;
  }

  void start() {
    active = true;
    if (pollTimer != null) {
      pollTimer.cancel();
    }
    refresh();
    pollTimer = actor.runAtFixedRate(pollInterval, this::refresh);
  }

  void stop() {
    active = false;
    if (pollTimer != null) {
      pollTimer.cancel();
      pollTimer = null;
    }
    backedUpPosition = -1;
  }

  boolean isAllowed(final long position) {
    return position <= backedUpPosition;
  }

  long backedUpPosition() {
    return backedUpPosition;
  }

  private void refresh() {
    if (!active || requestInFlight) {
      return;
    }
    requestInFlight = true;
    final CompletionStage<OptionalLong> positionFuture;
    try {
      positionFuture = source.backedUpPosition();
    } catch (final Exception e) {
      requestInFlight = false;
      logPollFailure(e);
      return;
    }
    positionFuture.whenCompleteAsync(
        (position, error) -> {
          requestInFlight = false;
          if (!active) {
            return;
          }
          if (error != null) {
            logPollFailure(error);
          } else {
            if (lastPollFailed) {
              lastPollFailed = false;
              LOG.info("Resumed polling the backed-up position for exporting");
            }
            if (position != null) {
              position.ifPresent(this::updatePosition);
            }
          }
        },
        actor);
  }

  private void updatePosition(final long position) {
    if (position == backedUpPosition) {
      return;
    }
    LOG.debug(
        "Records up to position {} are backed up on all partitions and may be exported", position);
    final boolean advanced = position > backedUpPosition;
    backedUpPosition = position;
    if (advanced) {
      onPositionAdvanced.run();
    }
  }

  private void logPollFailure(final Throwable error) {
    if (!lastPollFailed) {
      lastPollFailed = true;
      LOG.warn(
          "Failed to determine the backed-up position; keeping current export boundary {}",
          backedUpPosition,
          error);
    } else {
      LOG.debug("Failed to determine the backed-up position", error);
    }
  }
}
