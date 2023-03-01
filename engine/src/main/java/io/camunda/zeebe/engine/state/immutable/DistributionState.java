/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;

public interface DistributionState {

  /**
   * Returns whether there are any distributions pending for a given key.
   *
   * @param distributionKey the key of the distribution
   * @return true if there are pending distributions for the given key, otherwise false
   */
  boolean hasPendingDistribution(long distributionKey);

  /**
   * Returns the {@link CommandDistributionRecord} for the given distribution key
   *
   * @param distributionKey the key of the distribution
   * @param partition the partition to distribute to
   * @return an new instance of the {@link CommandDistributionRecord}
   */
  CommandDistributionRecord getCommandDistributionRecord(long distributionKey, int partition);
}
