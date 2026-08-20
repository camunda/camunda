/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;

public final class PartitionStateAssert
    extends AbstractAssert<PartitionStateAssert, PartitionState> {

  private PartitionStateAssert(final PartitionState partitionState, final Class<?> selfType) {
    super(partitionState, selfType);
  }

  public static PartitionStateAssert assertThat(final PartitionState actual) {
    return new PartitionStateAssert(actual, PartitionStateAssert.class);
  }

  public PartitionStateAssert hasState(final PartitionState.State state) {
    Assertions.assertThat(actual.state()).isEqualTo(state);
    return this;
  }

  public PartitionStateAssert hasPriority(final int priority) {
    Assertions.assertThat(actual.priority()).isEqualTo(priority);
    return this;
  }

  public PartitionStateAssert hasConfig(final DynamicPartitionConfig config) {
    Assertions.assertThat(actual.config()).isEqualTo(config);
    return this;
  }
}
