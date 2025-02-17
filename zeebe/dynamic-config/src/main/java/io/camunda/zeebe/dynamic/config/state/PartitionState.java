/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import java.util.function.UnaryOperator;

public record PartitionState(State state, int priority, DynamicPartitionConfig config) {
  public static PartitionState active(
      final int priority, final DynamicPartitionConfig partitionConfig) {
    return new PartitionState(State.ACTIVE, priority, partitionConfig);
  }

  public static PartitionState joining(
      final int priority, final DynamicPartitionConfig partitionConfig) {
    return new PartitionState(State.JOINING, priority, partitionConfig);
  }

  public static PartitionState bootstrapping(
      final int priority, final DynamicPartitionConfig partitionConfig) {
    return new PartitionState(State.BOOTSTRAPPING, priority, partitionConfig);
  }

  public PartitionState toActive() {
    if (state == State.LEAVING) {
      throw new IllegalStateException(
          String.format("Cannot transition to ACTIVE when current state is %s", state));
    }
    return new PartitionState(State.ACTIVE, priority, config);
  }

  public PartitionState toLeaving() {
    return new PartitionState(State.LEAVING, priority, config);
  }

  public PartitionState updateConfig(final DynamicPartitionConfig config) {
    return new PartitionState(state, priority, config);
  }

  public PartitionState updateConfig(final UnaryOperator<DynamicPartitionConfig> configUpdater) {
    return new PartitionState(state, priority, configUpdater.apply(config));
  }

  /**
   * Please note that when <a href="https://github.com/camunda/camunda/issues/14786">order of
   * priority in priority election</a> is changed, this method must be updated.
   */
  public boolean hasHigherPriority(final int priority) {
    return this.priority > priority;
  }

  public enum State {
    UNKNOWN,
    JOINING,
    ACTIVE,
    LEAVING,
    BOOTSTRAPPING
  }
}
