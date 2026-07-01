/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clusterversion;

import io.camunda.zeebe.engine.state.immutable.ClusterVersionState;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;

/**
 * Seeds the {@link ClusterVersionUpdateListener} with the active ECV reconstructed from the log
 * once stream-processor recovery completes. Without this, the broker's admission layer would
 * default to {@code (0, 0)} after every leader transition until the first {@code RAISE} was
 * processed — meaning previously-admitted gated commands would be rejected for a window.
 */
public final class ClusterVersionSeedLifecycleListener implements StreamProcessorLifecycleAware {

  private final ClusterVersionState state;
  private final ClusterVersionUpdateListener listener;

  public ClusterVersionSeedLifecycleListener(
      final ClusterVersionState state, final ClusterVersionUpdateListener listener) {
    this.state = state;
    this.listener = listener;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    listener.onClusterVersionUpdate(state.getActiveLine(), state.getActiveOrdinal());
  }
}
