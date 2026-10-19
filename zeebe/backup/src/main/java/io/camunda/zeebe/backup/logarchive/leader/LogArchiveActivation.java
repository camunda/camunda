/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.leader;

import io.camunda.zeebe.scheduler.future.ActorFuture;

/**
 * Lets the partition leader's log archive write its first record (spec section 18.1). Requested by
 * the {@code ACTIVATE_LOG_ARCHIVE} management request once every broker is upgraded.
 */
@FunctionalInterface
public interface LogArchiveActivation {

  /**
   * Activates the log archive of this partition. Idempotent: once activated, or once a timeline is
   * started, a repeat changes nothing.
   *
   * @return a future that completes with {@code true} if the partition's replicated state already
   *     holds a started timeline, and {@code false} while the start is still pending
   */
  ActorFuture<Boolean> activate();
}
