/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import io.camunda.zeebe.engine.EngineConfiguration;
import java.time.Duration;

public final class MessagesCfg implements ConfigurationEntry {

  private int ttlCheckerBatchLimit = EngineConfiguration.DEFAULT_MESSAGES_TTL_CHECKER_BATCH_LIMIT;
  private Duration ttlCheckerInterval = EngineConfiguration.DEFAULT_MESSAGES_TTL_CHECKER_INTERVAL;

  public int getTtlCheckerBatchLimit() {
    return ttlCheckerBatchLimit;
  }

  public void setTtlCheckerBatchLimit(final int ttlCheckerBatchLimit) {
    this.ttlCheckerBatchLimit = ttlCheckerBatchLimit;
  }

  public Duration getTtlCheckerInterval() {
    return ttlCheckerInterval;
  }

  public void setTtlCheckerInterval(final Duration ttlCheckerInterval) {
    this.ttlCheckerInterval = ttlCheckerInterval;
  }

  @Override
  public String toString() {
    return "MessagesCfg{"
        + "ttlCheckerBatchLimit="
        + ttlCheckerBatchLimit
        + ", ttlCheckerInterval="
        + ttlCheckerInterval
        + '}';
  }
}
