/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.metrics;

import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableUsageMetricState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import org.junit.jupiter.api.extension.ExtendWith;

/** Runs the usage metric state contract against the RocksDB state. */
@ExtendWith(ProcessingStateExtension.class)
public final class UsageMetricStateTest extends AbstractUsageMetricStateContractTest {

  private MutableProcessingState processingState;

  @Override
  protected MutableUsageMetricState createUsageMetricState() {
    return processingState.getUsageMetricState();
  }
}
