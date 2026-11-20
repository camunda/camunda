/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import org.jspecify.annotations.NullMarked;

/**
 * Tells the broker the policy state of partition 1, so components outside the stream processor
 * learn it without reading the engine's state. Only the leader of partition 1 calls it: after a
 * command that changed the state is committed, and once it has recovered.
 */
@NullMarked
public interface PolicyStateNotifier {

  static PolicyStateNotifier noop() {
    return new PolicyStateNotifier() {
      @Override
      public void publish(final PolicyStatus status) {}

      @Override
      public void clear() {}
    };
  }

  /** Publishes the current policy state of partition 1. */
  void publish(PolicyStatus status);

  /** Withdraws the published state, because this partition stops processing. */
  void clear();
}
