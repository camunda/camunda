/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorContext;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorContextImpl;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;

/** Test setup needs to write state directly; production processors only see the read interface. */
public final class MutableStates {

  private MutableStates() {}

  public static MutableProcessingState of(final TypedRecordProcessorContext context) {
    return ((TypedRecordProcessorContextImpl) context).getMutableProcessingState();
  }
}
