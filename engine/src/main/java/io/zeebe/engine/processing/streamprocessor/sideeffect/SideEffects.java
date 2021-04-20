/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.streamprocessor.sideeffect;

/** A chain of side effects that are executed/flushed together at the end of the processing. */
public interface SideEffects {

  /** Chain the given side effect. It will be executed/flushed at the end of the processing. */
  void add(SideEffectProducer sideEffect);
}
