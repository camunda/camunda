/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_BUSINESS_ID_UNIQUENESS_ENABLED;
import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_MESSAGE_START_ASK_RETRY_INTERVAL;
import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_MESSAGE_START_DEDUP_EXPIRATION_SWEEP_BATCH_LIMIT;
import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_MESSAGE_START_DEDUP_EXPIRATION_SWEEP_INTERVAL;
import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_MESSAGE_START_LOCK_RELEASE_POLL_BATCH_LIMIT;
import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_MESSAGE_START_LOCK_RELEASE_POLL_INTERVAL;
import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_MESSAGE_START_LOCK_RELEASE_POLL_MAX_BACKOFF;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import java.time.Duration;

public class ProcessInstanceCreationCfg implements ConfigurationEntry {

  private boolean businessIdUniquenessEnabled = DEFAULT_BUSINESS_ID_UNIQUENESS_ENABLED;
  private Duration messageStartDedupExpirationSweepInterval =
      DEFAULT_MESSAGE_START_DEDUP_EXPIRATION_SWEEP_INTERVAL;
  private int messageStartDedupExpirationSweepBatchLimit =
      DEFAULT_MESSAGE_START_DEDUP_EXPIRATION_SWEEP_BATCH_LIMIT;
  private Duration messageStartAskRetryInterval = DEFAULT_MESSAGE_START_ASK_RETRY_INTERVAL;
  private Duration messageStartLockReleasePollInterval =
      DEFAULT_MESSAGE_START_LOCK_RELEASE_POLL_INTERVAL;
  private Duration messageStartLockReleasePollMaxBackoff =
      DEFAULT_MESSAGE_START_LOCK_RELEASE_POLL_MAX_BACKOFF;
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

  @Override
  public String toString() {
    return "ProcessInstanceCreationCfg{"
        + "businessIdUniquenessEnabled="
        + businessIdUniquenessEnabled
        + ", messageStartDedupExpirationSweepInterval="
        + messageStartDedupExpirationSweepInterval
        + ", messageStartDedupExpirationSweepBatchLimit="
        + messageStartDedupExpirationSweepBatchLimit
        + ", messageStartAskRetryInterval="
        + messageStartAskRetryInterval
        + ", messageStartLockReleasePollInterval="
        + messageStartLockReleasePollInterval
        + ", messageStartLockReleasePollMaxBackoff="
        + messageStartLockReleasePollMaxBackoff
        + ", messageStartLockReleasePollBatchLimit="
        + messageStartLockReleasePollBatchLimit
        + '}';
  }
}
