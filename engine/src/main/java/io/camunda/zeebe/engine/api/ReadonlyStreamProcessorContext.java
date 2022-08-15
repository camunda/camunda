/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.api;

import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.logstreams.log.LogStream;

public interface ReadonlyStreamProcessorContext {

  ProcessingScheduleService getScheduleService();

  /**
   * @return the logstream, on which the processor runs
   */
  @Deprecated // only used in EngineRule; TODO remove this
  LogStream getLogStream();

  /**
   * @return the state, where the data is stored during processing
   */
  MutableZeebeState getZeebeState();

  /**
   * Returns the partition ID
   *
   * @return partition ID
   */
  int getPartitionId();
}
