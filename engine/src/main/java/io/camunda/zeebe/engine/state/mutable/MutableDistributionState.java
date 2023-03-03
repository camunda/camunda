/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.DistributionState;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;

public interface MutableDistributionState extends DistributionState {

  /**
   * Adds a distribution to the state
   *
   * @param distributionKey the key of the distribution
   * @param commandDistributionRecord the distribution record that needs to be stored
   */
  void addCommandDistribution(
      final long distributionKey, final CommandDistributionRecord commandDistributionRecord);

  /**
   * Removed a distribution from the state
   *
   * @param distributionKey the key of the distribution that will be removed
   */
  void removeCommandDistribution(final long distributionKey);

  /**
   * Adds a pending distribution to the state
   *
   * @param distributionKey the key of the distribution
   * @param partition the partition for which the distribution is pending
   */
  void addPendingDistribution(final long distributionKey, final int partition);

  /**
   * Removes a pending distribution fromm the state
   *
   * @param distributionKey the key of the pending distribution that will be removed
   * @param partition the partition of the pending distribution that will be removed
   */
  void removePendingDistribution(final long distributionKey, final int partition);
}
