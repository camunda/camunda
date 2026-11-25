/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy.hub;

import org.jspecify.annotations.NullMarked;

/** How a policy update ended, as committed in the log of partition 1. */
@NullMarked
sealed interface PolicyUpdateOutcome {

  /** The update's policy version is committed. */
  record Applied() implements PolicyUpdateOutcome {}

  /**
   * The engine failed the update; none of it is committed.
   *
   * @param reason why the engine failed it
   */
  record Rejected(String reason) implements PolicyUpdateOutcome {}
}
