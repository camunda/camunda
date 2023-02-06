/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration;

public final class ProcessingCfg implements ConfigurationEntry {
  private static final int DEFAULT_PROCESSING_BATCH_LIMIT = 100;
  private Integer maxCommandsInBatch = DEFAULT_PROCESSING_BATCH_LIMIT;

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {
    if (maxCommandsInBatch < 1) {
      throw new IllegalArgumentException(
          "maxCommandsInBatch must be >= 1 but was %s".formatted(maxCommandsInBatch));
    }
  }

  public int getMaxCommandsInBatch() {
    return maxCommandsInBatch;
  }

  public void setMaxCommandsInBatch(final int maxCommandsInBatch) {
    this.maxCommandsInBatch = maxCommandsInBatch;
  }

  @Override
  public String toString() {
    return "ProcessingCfg{" + "maxCommandsInBatch=" + maxCommandsInBatch + '}';
  }
}
