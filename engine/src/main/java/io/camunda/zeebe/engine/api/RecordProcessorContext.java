/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.api;

import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessorListener;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.LastProcessedPositionState;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;

public interface RecordProcessorContext {

  int getPartitionId();

  ProcessingScheduleService getScheduleService();

  MutableZeebeState getZeebeState();

  Writers getWriters();

  LastProcessedPositionState getLastProcessedPositionState();

  RecordProcessorContext listener(StreamProcessorListener streamProcessorListener);
}
