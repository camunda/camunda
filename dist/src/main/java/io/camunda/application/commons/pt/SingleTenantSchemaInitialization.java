/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import io.camunda.zeebe.util.CheckedRunnable;
import org.jspecify.annotations.NullMarked;

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
 */
@NullMarked
public final class SingleTenantSchemaInitialization implements SchemaInitialization {

  private final CheckedRunnable pass;

  private volatile boolean initialized;

  public SingleTenantSchemaInitialization(final CheckedRunnable pass) {
    this.pass = pass;
  }

  /** Applies the schemas, propagating the first failure unwrapped to the caller. */
  @Override
  public void start() throws Exception {
    pass.run();
    initialized = true;
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
    // nothing outlives start()
  }
}
