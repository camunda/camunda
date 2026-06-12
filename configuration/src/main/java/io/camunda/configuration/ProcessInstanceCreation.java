/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_MESSAGE_START_ASK_RETRY_GRACE;
import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_MESSAGE_START_ASK_RETRY_INTERVAL;
import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_MESSAGE_START_DEDUP_EXPIRATION_SWEEP_BATCH_LIMIT;
import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_MESSAGE_START_DEDUP_EXPIRATION_SWEEP_INTERVAL;
import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_MESSAGE_START_LOCK_RELEASE_POLL_BATCH_LIMIT;
import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_MESSAGE_START_LOCK_RELEASE_POLL_INTERVAL;
import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_MESSAGE_START_LOCK_RELEASE_POLL_MAX_BACKOFF;

import java.time.Duration;

public class ProcessInstanceCreation {
  private static final boolean DEFAULT_BUSINESS_ID_UNIQUENESS_ENABLED = false;

  /**
   * Controls uniqueness enforcement of business IDs across active process instances.
   *
   * <ul>
   *   <li><b>Disabled (default):</b> Multiple active process instances can share the same business
   *       ID. No tracking or validation is performed.
   *   <li><b>Enabled:</b> Creating a process instance with a business ID that is already in use by
   *       an active process instance will be rejected. Business IDs of process instances created
   *       before enabling this setting are not tracked, so duplicates with those are not detected.
   * </ul>
   */
  private boolean businessIdUniquenessEnabled = DEFAULT_BUSINESS_ID_UNIQUENESS_ENABLED;

  /**
   * Interval between scheduled expired-dedup-entry sweeps of the cross-partition message-start
   * dedup state on {@code P_B}. The sweep removes dedup rows whose deletion deadline has passed; it
   * is correctness-irrelevant and only bounds storage growth.
   */
  private Duration messageStartDedupExpirationSweepInterval =
      DEFAULT_MESSAGE_START_DEDUP_EXPIRATION_SWEEP_INTERVAL;

  /**
   * Upper bound on the number of expired dedup entries removed per sweep cycle on {@code P_B}. A
   * follow-up sweep is scheduled when more entries remain.
   */
  private int messageStartDedupExpirationSweepBatchLimit =
      DEFAULT_MESSAGE_START_DEDUP_EXPIRATION_SWEEP_BATCH_LIMIT;

  /**
   * Retry cadence for the cross-partition message-start pending-ask scheduler on {@code P_K}. Every
   * tick re-emits each pending ask whose last-sent time is older than this interval; the same value
   * also drives the scheduler's tick frequency.
   */
  private Duration messageStartAskRetryInterval = DEFAULT_MESSAGE_START_ASK_RETRY_INTERVAL;

  /**
   * Grace by which the cross-partition message-start dedup row on {@code P_B} is kept valid (for
   * re-reply lookups and the expiration sweep) beyond the originating message's deadline. It must
   * cover the worst-case one-way inter-partition command latency plus clock skew so a retry ask
   * sent just before the deadline, but processed slightly after it, still hits the dedup and
   * re-replies the same instance instead of creating a duplicate. Over-sizing is essentially free
   * (the dedup key is unique per publish); under-sizing re-opens the near-deadline duplicate
   * window.
   */
  private Duration messageStartAskRetryGrace = DEFAULT_MESSAGE_START_ASK_RETRY_GRACE;

  /**
   * Base poll interval for the cross-partition correlation-key lock-release scheduler on {@code
   * P_K}. {@code P_K} polls {@code P_B} for the completion of each remotely-created holder
   * instance; this value drives the scheduler's tick frequency and the base interval before
   * back-off applies.
   */
  private Duration messageStartLockReleasePollInterval =
      DEFAULT_MESSAGE_START_LOCK_RELEASE_POLL_INTERVAL;

  /**
   * Upper bound on the per-lock exponential back-off of the cross-partition correlation-key
   * lock-release poll on {@code P_K}, so a long-running holder is not polled at the base rate
   * indefinitely.
   */
  private Duration messageStartLockReleasePollMaxBackoff =
      DEFAULT_MESSAGE_START_LOCK_RELEASE_POLL_MAX_BACKOFF;

  /**
   * Upper bound on the number of holders batched into a single cross-partition correlation-key
   * lock-release query per target partition each poll cycle on {@code P_K}. Remaining due holders
   * are polled on the next tick.
   */
  private int messageStartLockReleasePollBatchLimit =
      DEFAULT_MESSAGE_START_LOCK_RELEASE_POLL_BATCH_LIMIT;

  public boolean isBusinessIdUniquenessEnabled() {
    return businessIdUniquenessEnabled;
  }

  public void setBusinessIdUniquenessEnabled(final boolean businessIdUniquenessEnabled) {
    this.businessIdUniquenessEnabled = businessIdUniquenessEnabled;
  }

  public Duration getMessageStartDedupExpirationSweepInterval() {
    return messageStartDedupExpirationSweepInterval;
  }

  public void setMessageStartDedupExpirationSweepInterval(
      final Duration messageStartDedupExpirationSweepInterval) {
    this.messageStartDedupExpirationSweepInterval = messageStartDedupExpirationSweepInterval;
  }

  public int getMessageStartDedupExpirationSweepBatchLimit() {
    return messageStartDedupExpirationSweepBatchLimit;
  }

  public void setMessageStartDedupExpirationSweepBatchLimit(
      final int messageStartDedupExpirationSweepBatchLimit) {
    this.messageStartDedupExpirationSweepBatchLimit = messageStartDedupExpirationSweepBatchLimit;
  }

  public Duration getMessageStartAskRetryInterval() {
    return messageStartAskRetryInterval;
  }

  public void setMessageStartAskRetryInterval(final Duration messageStartAskRetryInterval) {
    this.messageStartAskRetryInterval = messageStartAskRetryInterval;
  }

  public Duration getMessageStartAskRetryGrace() {
    return messageStartAskRetryGrace;
  }

  public void setMessageStartAskRetryGrace(final Duration messageStartAskRetryGrace) {
    this.messageStartAskRetryGrace = messageStartAskRetryGrace;
  }

  public Duration getMessageStartLockReleasePollInterval() {
    return messageStartLockReleasePollInterval;
  }

  public void setMessageStartLockReleasePollInterval(
      final Duration messageStartLockReleasePollInterval) {
    this.messageStartLockReleasePollInterval = messageStartLockReleasePollInterval;
  }

  public Duration getMessageStartLockReleasePollMaxBackoff() {
    return messageStartLockReleasePollMaxBackoff;
  }

  public void setMessageStartLockReleasePollMaxBackoff(
      final Duration messageStartLockReleasePollMaxBackoff) {
    this.messageStartLockReleasePollMaxBackoff = messageStartLockReleasePollMaxBackoff;
  }

  public int getMessageStartLockReleasePollBatchLimit() {
    return messageStartLockReleasePollBatchLimit;
  }

  public void setMessageStartLockReleasePollBatchLimit(
      final int messageStartLockReleasePollBatchLimit) {
    this.messageStartLockReleasePollBatchLimit = messageStartLockReleasePollBatchLimit;
  }
}
