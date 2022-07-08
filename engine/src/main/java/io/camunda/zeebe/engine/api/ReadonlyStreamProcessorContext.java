/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.api;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.scheduler.ActorControl;

public interface ReadonlyStreamProcessorContext {

  /**
   * @return the actor on which the processing runs
   */
  ActorControl getActor();

  /**
   * @return the logstream, on which the processor runs
   */
  LogStream getLogStream();

  /**
   * @return the actual log stream writer, used to write any record
   */
  TypedStreamWriter getLogStreamWriter();

  /**
   * @return the specific writers, like command, response, etc
   */
  Writers getWriters();

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
