/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.partitions;

import io.zeebe.engine.processing.streamprocessor.ProcessingContext;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.zeebe.engine.state.mutable.MutableZeebeState;
import io.zeebe.util.sched.ActorControl;

@FunctionalInterface
public interface TypedRecordProcessorsFactory {

  TypedRecordProcessors createTypedStreamProcessor(
      ActorControl actor, MutableZeebeState zeebeState, ProcessingContext processingContext);
}
