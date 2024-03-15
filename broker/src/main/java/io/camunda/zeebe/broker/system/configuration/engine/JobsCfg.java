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

public class JobsCfg implements ConfigurationEntry {
  private Duration timeoutCheckerPollingInterval =
      EngineConfiguration.DEFAULT_JOBS_TIMEOUT_POLLING_INTERVAL;

  public Duration getTimeoutCheckerPollingInterval() {
    return timeoutCheckerPollingInterval;
  }

  public void setTimeoutCheckerPollingInterval(final Duration timeoutCheckerPollingInterval) {
    this.timeoutCheckerPollingInterval = timeoutCheckerPollingInterval;
  }

  @Override
  public String toString() {
    return "JobsCfg{" + "timeoutCheckerPollingInterval=" + timeoutCheckerPollingInterval + '}';
  }
}
