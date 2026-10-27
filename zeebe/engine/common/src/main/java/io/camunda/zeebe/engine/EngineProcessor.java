/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine;

import io.camunda.zeebe.stream.api.RecordProcessor;

/**
 * The engine as seen by the broker: a {@link RecordProcessor} that additionally reports on the
 * scaling state of the partition it runs on.
 */
public interface EngineProcessor extends RecordProcessor {

  /** Returns whether scaling is currently in progress, e.g. to block checkpoint creation. */
  boolean isScalingInProgress();

  /**
   * Returns the current partition count from the routing information.
   *
   * @param fallBackPartitionCount used if the dynamic routing state is not initialized
   */
  int getCurrentPartitionCount(int fallBackPartitionCount);
}
