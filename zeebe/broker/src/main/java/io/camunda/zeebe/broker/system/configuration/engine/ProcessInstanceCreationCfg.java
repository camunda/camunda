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
import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_MESSAGE_START_DEDUP_TOMBSTONE_SWEEP_BATCH_LIMIT;
import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_MESSAGE_START_DEDUP_TOMBSTONE_SWEEP_INTERVAL;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import java.time.Duration;

public class ProcessInstanceCreationCfg implements ConfigurationEntry {

  private boolean businessIdUniquenessEnabled = DEFAULT_BUSINESS_ID_UNIQUENESS_ENABLED;
  private Duration messageStartDedupTombstoneSweepInterval =
      DEFAULT_MESSAGE_START_DEDUP_TOMBSTONE_SWEEP_INTERVAL;
  private int messageStartDedupTombstoneSweepBatchLimit =
      DEFAULT_MESSAGE_START_DEDUP_TOMBSTONE_SWEEP_BATCH_LIMIT;
  private Duration messageStartAskRetryInterval = DEFAULT_MESSAGE_START_ASK_RETRY_INTERVAL;

  public boolean isBusinessIdUniquenessEnabled() {
    return businessIdUniquenessEnabled;
  }

  public void setBusinessIdUniquenessEnabled(final boolean businessIdUniquenessEnabled) {
    this.businessIdUniquenessEnabled = businessIdUniquenessEnabled;
  }

  public Duration getMessageStartDedupTombstoneSweepInterval() {
    return messageStartDedupTombstoneSweepInterval;
  }

  public void setMessageStartDedupTombstoneSweepInterval(
      final Duration messageStartDedupTombstoneSweepInterval) {
    this.messageStartDedupTombstoneSweepInterval = messageStartDedupTombstoneSweepInterval;
  }

  public int getMessageStartDedupTombstoneSweepBatchLimit() {
    return messageStartDedupTombstoneSweepBatchLimit;
  }

  public void setMessageStartDedupTombstoneSweepBatchLimit(
      final int messageStartDedupTombstoneSweepBatchLimit) {
    this.messageStartDedupTombstoneSweepBatchLimit = messageStartDedupTombstoneSweepBatchLimit;
  }


  public Duration getMessageStartAskRetryInterval() {
    return messageStartAskRetryInterval;
  }

  public void setMessageStartAskRetryInterval(final Duration messageStartAskRetryInterval) {
    this.messageStartAskRetryInterval = messageStartAskRetryInterval;
  }

  @Override
  public String toString() {
    return "ProcessInstanceCreationCfg{"
        + "businessIdUniquenessEnabled="
        + businessIdUniquenessEnabled
        + ", messageStartDedupTombstoneSweepInterval="
        + messageStartDedupTombstoneSweepInterval
        + ", messageStartDedupTombstoneSweepBatchLimit="
        + messageStartDedupTombstoneSweepBatchLimit
        + ", messageStartAskRetryInterval="
        + messageStartAskRetryInterval
        + '}';
  }
}
