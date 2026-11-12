/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Tells, when polled, whether the configuration files changed since the contents last handed out,
 * and hands out the physical tenants they declare once the change is stable: the files kept the
 * same contents for the quiet period, and they parse. A file written in several steps, or written
 * again with the same contents, is so handed out once, with its final contents. Contents that do
 * not parse are reported once and skipped until the files change again. A missing file is waited
 * for, not taken as a file without tenants.
 */
@NullMarked
final class PhysicalTenantConfigFileWatcher {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final PhysicalTenantConfigFiles files;
  private final long quietPeriodNanos;
  private final LongSupplier nanoClock;
  private @Nullable String handedOut;
  private @Nullable String candidate;
  private long candidateSince;

  /**
   * @param handedOut the fingerprint of the contents the caller already acts on, e.g. read at boot
   */
  PhysicalTenantConfigFileWatcher(
      final PhysicalTenantConfigFiles files,
      final Duration quietPeriod,
      final @Nullable String handedOut,
      final LongSupplier nanoClock) {
    this.files = files;
    quietPeriodNanos = quietPeriod.toNanos();
    this.handedOut = handedOut;
    candidate = handedOut;
    this.nanoClock = nanoClock;
    candidateSince = nanoClock.getAsLong();
  }

  /**
   * @return the subtree of every tenant the files declare, once they changed and the change is
   *     stable; empty otherwise
   */
  Optional<Map<String, Map<String, String>>> poll() {
    final PhysicalTenantConfigFiles.Contents contents;
    try {
      contents = files.read();
    } catch (final Exception e) {
      LOG.debug("Failed to read the configuration files {}; trying again", files.files(), e);
      return Optional.empty();
    }
    if (contents.bytes().size() < files.files().size()) {
      LOG.debug("A configuration file of {} is missing; waiting for it", files.files());
      return Optional.empty();
    }
    final var fingerprint = contents.fingerprint();
    final var now = nanoClock.getAsLong();
    if (!fingerprint.equals(candidate)) {
      candidate = fingerprint;
      candidateSince = now;
      return Optional.empty();
    }
    if (Objects.equals(fingerprint, handedOut) || now - candidateSince < quietPeriodNanos) {
      return Optional.empty();
    }
    handedOut = fingerprint;
    try {
      return Optional.of(files.tenantSubtrees(contents));
    } catch (final Exception e) {
      LOG.warn(
          "The configuration files {} changed, but cannot be read; the physical tenants keep their"
              + " configuration until the files change again",
          files.files(),
          e);
      return Optional.empty();
    }
  }
}
