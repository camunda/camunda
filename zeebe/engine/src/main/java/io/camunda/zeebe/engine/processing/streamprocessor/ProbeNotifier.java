/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

/**
 * Tells the gateways that probes of a probe ID can be collected, so a long poll waiting for that
 * probe ID tries again, as {@link JobStreamer#notifyWorkAvailable(String)} does for job types.
 */
@FunctionalInterface
public interface ProbeNotifier {

  static ProbeNotifier noop() {
    return probeId -> {};
  }

  void notifyProbesAvailable(String probeId);
}
