/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.time.Duration;

public class EngineOrdinal {

  private static final Duration DEFAULT_ROLLOVER_EVALUATION_INTERVAL = Duration.ofMinutes(10);

  private Duration rolloverEvaluationInterval = DEFAULT_ROLLOVER_EVALUATION_INTERVAL;

  public Duration getRolloverEvaluationInterval() {
    return rolloverEvaluationInterval;
  }

  public void setRolloverEvaluationInterval(final Duration rolloverEvaluationInterval) {
    this.rolloverEvaluationInterval = rolloverEvaluationInterval;
  }
}
