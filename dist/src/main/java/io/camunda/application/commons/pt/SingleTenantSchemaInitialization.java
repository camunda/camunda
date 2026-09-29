/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import io.camunda.application.commons.pt.PerTenantSchemaInitialization.Deferral;
import io.camunda.zeebe.util.CheckedRunnable;
import java.time.Duration;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies the node's schemas in one pass during startup, letting the first failure abort it. The
 * shape a node with a single physical tenant keeps: there is no second tenant for a failure to be
 * isolated from, so isolating it would only turn a node that fails in seconds into one that never
 * starts and never says why.
 *
 * <p>It also keeps a one-shot process — a restore, a migration — terminating: such a process has to
 * exit non-zero when its schema cannot be applied, where the isolated shape would retry for as long
 * as it ran.
 *
 * <p>Which tenants the node has is the caller's to know: with at most one there is no partial
 * result to report, so this shape holds no tenant ids and the pass is opaque to it.
 *
 * <p>A tenant that is being recovered is left untouched: the node starts without the pass, and a
 * background task watches for the recovery to end and applies the schema then. A restore applies it
 * earlier through {@link #initializeNow()}, after which the task has nothing left to do. Once
 * startup is over there is nothing left to abort, so a failure of that deferred pass is logged and
 * leaves the tenant uninitialized until the node is restarted. While the decision is still pending
 * discovery, startup waits for it.
 */
@NullMarked
public final class SingleTenantSchemaInitialization implements SchemaInitialization {

  private static final Logger LOG = LoggerFactory.getLogger(SingleTenantSchemaInitialization.class);

  private static final Duration PENDING_POLL_INTERVAL = Duration.ofMillis(100);
  private static final Duration DEFERRED_POLL_INTERVAL = Duration.ofMillis(500);

  private final CheckedRunnable pass;
  private final Supplier<Deferral> deferral;
  private final ReentrantLock passLock = new ReentrantLock();

  private volatile boolean initialized;
  private volatile boolean closed;
  private volatile @Nullable Thread deferredPass;

  /**
   * @param deferral whether the tenant must be left untouched because it is being recovered. A
   *     {@link Deferral#PENDING} answer is polled until it resolves; the supplier is expected to
   *     bound how long it stays pending.
   */
  public SingleTenantSchemaInitialization(
      final CheckedRunnable pass, final Supplier<Deferral> deferral) {
    this.pass = pass;
    this.deferral = deferral;
  }

  /**
   * Applies the schemas, propagating the first failure unwrapped to the caller, unless the tenant
   * is being recovered.
   */
  @Override
  public void start() throws Exception {
    if (awaitDeferralDecision() == Deferral.DEFERRED) {
      LOG.info(
          "Not initializing the schema yet: the physical tenant is recovering. It is applied when"
              + " the tenant is restored or leaves recovery.");
      startDeferredPass();
      return;
    }
    initializeNow();
  }

  @Override
  public void awaitGate() {
    // start() already returned, or threw; there is nothing left to wait for
  }

  /** Answers for the pass as a whole, which with at most one tenant is the same answer. */
  @Override
  public boolean isInitialized(final String physicalTenantId) {
    return initialized;
  }

  @Override
  public void close() {
    closed = true;
    final var task = deferredPass;
    if (task != null) {
      task.interrupt();
    }
  }

  /**
   * Applies the schemas regardless of any deferral, and even if they were already applied: a
   * restore replaces the storage the earlier pass ran against.
   */
  public void initializeNow() throws Exception {
    passLock.lock();
    try {
      runPass();
    } finally {
      passLock.unlock();
    }
  }

  private void runPass() throws Exception {
    pass.run();
    initialized = true;
  }

  /** The deferred pass, which a restore that already applied the schemas leaves nothing to do. */
  private void initializeIfNotYet() throws Exception {
    passLock.lock();
    try {
      if (!initialized) {
        runPass();
        LOG.info("Schema is initialized after the physical tenant left recovery.");
      }
    } finally {
      passLock.unlock();
    }
  }

  private void startDeferredPass() {
    deferredPass =
        Thread.ofPlatform().daemon().name("schema-init-deferred").start(this::runDeferredPass);
  }

  private void runDeferredPass() {
    try {
      while (!closed && !initialized) {
        if (deferral.get() == Deferral.NONE) {
          initializeIfNotYet();
          return;
        }
        Thread.sleep(DEFERRED_POLL_INTERVAL);
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (final Exception e) {
      if (!closed) {
        LOG.error(
            "Schema initialization failed after the physical tenant left recovery. The tenant stays"
                + " uninitialized until the node is restarted.",
            e);
      }
    }
  }

  private Deferral awaitDeferralDecision() throws InterruptedException {
    var decision = deferral.get();
    while (decision == Deferral.PENDING) {
      Thread.sleep(PENDING_POLL_INTERVAL);
      decision = deferral.get();
    }
    return decision;
  }
}
