/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.streamprocessor.writers;

import io.camunda.zeebe.stream.api.SideEffectProducer;

/** A chain of side effects that are executed/flushed together at the end of the processing. */
public interface SideEffectWriter {

  /** Chain the given side effect. It will be executed/flushed at the end of the processing. */
  void appendSideEffect(SideEffectProducer sideEffect);
}
