/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring.detector;

import java.time.Instant;

/** A detector that reads its signal at a fixed interval, always from the same thread. */
public interface SampledDetector {

  /**
   * Reads the signal and updates the detector's health.
   *
   * @param now the time of this sample; the time between two calls is the window of the sample
   */
  void sample(Instant now);
}
