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
import org.agrona.CloseHelper;
import org.agrona.ErrorHandler;
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

  /** How long interrupted resolutions may take to unwind before their stores are closed anyway. */
  static final Duration INTERRUPT_TIMEOUT = Duration.ofSeconds(1);

  private static final Logger LOG = LoggerFactory.getLogger(SecretStoreResources.class);

  private static final ErrorHandler LOG_FAILURE =
      error -> LOG.warn("Failed to release secret store resource", error);

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
   * fails with an {@link Exception}. Safe to call more than once.
   */
  @Override
  public void close() {
    final var interrupted = drain();
    pools.clear();
    CloseHelper.closeAll(LOG_FAILURE, stores);
    stores.clear();
    CloseHelper.closeAll(
        LOG_FAILURE,
        meterRegistries.stream()
            .<AutoCloseable>map(registry -> () -> MicrometerUtil.close(registry))
            .toList());
    meterRegistries.clear();
    if (interrupted) {
      // restored only now, so the closes above are not themselves aborted by the flag
      Thread.currentThread().interrupt();
    }
  }

  /** Returns whether the calling thread was interrupted while waiting. */
  private boolean drain() {
    pools.forEach(ExecutorService::shutdown);
    final var deadline = System.nanoTime() + DRAIN_TIMEOUT.toNanos();
    var interrupted = false;
    for (final var pool : pools) {
      if (!interrupted) {
        try {
          if (!pool.awaitTermination(deadline - System.nanoTime(), TimeUnit.NANOSECONDS)) {
            LOG.warn("Secret resolutions still in flight after {}, interrupting", DRAIN_TIMEOUT);
            pool.shutdownNow();
            if (!pool.awaitTermination(INTERRUPT_TIMEOUT.toNanos(), TimeUnit.NANOSECONDS)) {
              LOG.warn("Secret resolutions did not stop after interruption, closing stores anyway");
            }
          }
        } catch (final InterruptedException e) {
          interrupted = true;
        }
      }
      if (interrupted) {
        pool.shutdownNow();
      }
    }
    return interrupted;
  }
}
