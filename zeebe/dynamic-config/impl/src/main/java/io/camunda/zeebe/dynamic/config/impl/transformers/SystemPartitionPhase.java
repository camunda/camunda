/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.impl.transformers;

/**
 * Whether a force-remove plan gets phase 0, the removal of the brokers from the system partition
 * (system partition design 5.6.3), and whether that phase may lose committed entries of the system
 * partition.
 *
 * @param enabled true if the cluster runs a system partition
 * @param acceptPossibleLoss true if a forced change may run although the completeness check fails
 */
public record SystemPartitionPhase(boolean enabled, boolean acceptPossibleLoss) {

  public static final SystemPartitionPhase DISABLED = new SystemPartitionPhase(false, false);
}
