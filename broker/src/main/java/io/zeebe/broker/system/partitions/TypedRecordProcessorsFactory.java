/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.partitions;

import io.zeebe.engine.processor.ProcessingContext;
import io.zeebe.engine.processor.TypedRecordProcessors;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.util.sched.ActorControl;

@FunctionalInterface
public interface TypedRecordProcessorsFactory {

  TypedRecordProcessors createTypedStreamProcessor(
      ActorControl actor, ZeebeState zeebeState, ProcessingContext processingContext);
}
