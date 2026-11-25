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
 * Learns how a policy update ended on partition 1. The processors call it from post-commit tasks,
 * so it only learns outcomes that are committed in the log, and only on the leader. It runs on the
 * stream processor's actor and must return at once.
 */
@NullMarked
public interface PolicyUpdateOutcomeListener {

  /** A listener for a physical tenant that is not connected to Hub. */
  PolicyUpdateOutcomeListener NOOP =
      new PolicyUpdateOutcomeListener() {
        @Override
        public void onCommitted(final String updateId, final long seqNo) {}

        @Override
        public void onFailed(final String updateId, final String reason) {}
      };

  /**
   * The update's policy version is committed on partition 1.
   *
   * @param updateId the ID of the update
   * @param seqNo the Hub sequence of the update
   */
  void onCommitted(String updateId, long seqNo);

  /**
   * The update failed and was removed; none of it is committed.
   *
   * @param updateId the ID of the update
   * @param reason why the engine failed the update
   */
  void onFailed(String updateId, String reason);
}
