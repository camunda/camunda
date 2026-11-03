/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Releases the physical tenant resources of a node that nothing has used for their idle timeout, so
 * that a node gives back what a tenant's traffic made it create once that traffic stops: the
 * tenant's leader moved away, or a gateway served one burst of its queries.
 */
@NullMarked
public final class IdlePhysicalTenantResourceReleaser implements AutoCloseable {

  private static final Logger LOG =
      LoggerFactory.getLogger(IdlePhysicalTenantResourceReleaser.class);
  private static final Duration MIN_CHECK_INTERVAL = Duration.ofMillis(100);
  private static final Duration MAX_CHECK_INTERVAL = Duration.ofMinutes(1);

  private final List<Entry> entries;
  private final @Nullable ScheduledExecutorService executor;

  private IdlePhysicalTenantResourceReleaser(
      final List<Entry> entries, final @Nullable ScheduledExecutorService executor) {
    this.entries = entries;
    this.executor = executor;
  }

  /**
   * Starts checking the resources whose idle timeout is positive, each at least twice per timeout.
   * A resource with a zero timeout is never released.
   */
  public static IdlePhysicalTenantResourceReleaser start(final String name, final List<Entry> all) {
    final var releasable = all.stream().filter(e -> e.idleTimeout().isPositive()).toList();
    if (releasable.isEmpty()) {
      return new IdlePhysicalTenantResourceReleaser(releasable, null);
    }
    final var shortest =
        releasable.stream().map(Entry::idleTimeout).min(Duration::compareTo).orElseThrow();
    final var interval = clamp(shortest.dividedBy(2));
    final var executor =
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("camunda-pt-idle-release-" + name).factory());
    final var releaser = new IdlePhysicalTenantResourceReleaser(releasable, executor);
    executor.scheduleWithFixedDelay(
        releaser::releaseIdle, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    return releaser;
  }

  void releaseIdle() {
    for (final var entry : entries) {
      try {
        entry.resource().releaseIfIdle(entry.idleTimeout());
      } catch (final RuntimeException e) {
        LOG.warn(
            "Failed to release the idle resources of physical tenant '{}'",
            entry.resource().physicalTenantId(),
            e);
      }
    }
  }

  @Override
  public void close() {
    if (executor != null) {
      executor.shutdownNow();
    }
  }

  private static Duration clamp(final Duration interval) {
    if (interval.compareTo(MIN_CHECK_INTERVAL) < 0) {
      return MIN_CHECK_INTERVAL;
    }
    return interval.compareTo(MAX_CHECK_INTERVAL) > 0 ? MAX_CHECK_INTERVAL : interval;
  }

  public record Entry(LazyPhysicalTenantResource<?> resource, Duration idleTimeout) {}
}
