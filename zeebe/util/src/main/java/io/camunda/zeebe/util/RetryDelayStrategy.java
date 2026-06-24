/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import java.time.Duration;

public interface RetryDelayStrategy {

  /**
   * @return the duration for the next retry
   */
  Duration nextDelay();

  /** resets the retry delay to the initial value */
  void reset();
}
