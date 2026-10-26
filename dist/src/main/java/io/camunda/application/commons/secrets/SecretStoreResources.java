/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import io.camunda.secretstore.SecretStore;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Everything {@link SecretStoreConfiguration} creates that nothing else owns: the stores, the
 * per-tenant concurrency pools and the per-tenant wrapped meter registries. {@link
 * io.camunda.secretstore.CachingSecretStore} and {@link
 * io.camunda.secretstore.ConcurrentSecretStore} deliberately do not close what they wrap or share,
 * so releasing these falls to whoever built them.
 *
 * <p>Closed both to roll back a failed startup and when the application context shuts down.
 */
@NullMarked
public final class SecretStoreResources implements AutoCloseable {

  /** How long in-flight resolutions may finish after the pools stop accepting new ones. */
  static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(5);

  private static final Logger LOG = LoggerFactory.getLogger(SecretStoreResources.class);

  private final List<SecretStore> stores = new ArrayList<>();
  private final List<MeterRegistry> meterRegistries = new ArrayList<>();
  private final List<ExecutorService> pools = new ArrayList<>();

  void addStore(final SecretStore store) {
    stores.add(store);
  }

  void addMeterRegistry(final MeterRegistry meterRegistry) {
    meterRegistries.add(meterRegistry);
  }

  void addPool(final ExecutorService pool) {
    pools.add(pool);
  }

  /**
   * Stops the pools first so no new backend call starts, then closes the stores they called into,
   * then the registries their caches published to. Each step is attempted even if an earlier one
   * fails. Safe to call more than once.
   */
  @Override
  public void close() {
    drain();
    pools.clear();
    for (final var store : stores) {
      try {
        store.close();
      } catch (final RuntimeException e) {
        LOG.warn("Failed to close secret store", e);
      }
    }
    stores.clear();
    meterRegistries.forEach(MicrometerUtil::close);
    meterRegistries.clear();
  }

  private void drain() {
    pools.forEach(ExecutorService::shutdown);
    final var deadline = System.nanoTime() + DRAIN_TIMEOUT.toNanos();
    var interrupted = false;
    for (final var pool : pools) {
      try {
        if (!interrupted
            && !pool.awaitTermination(deadline - System.nanoTime(), TimeUnit.NANOSECONDS)) {
          LOG.warn("Secret resolutions still in flight after {}, interrupting", DRAIN_TIMEOUT);
          pool.shutdownNow();
        }
      } catch (final InterruptedException e) {
        interrupted = true;
        Thread.currentThread().interrupt();
      }
      if (interrupted) {
        pool.shutdownNow();
      }
    }
  }
}
