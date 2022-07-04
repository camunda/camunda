/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectProducer;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;

public final class FlushResponseWriterSideEffectProducer implements SideEffectProducer {

  private final TypedResponseWriter responseWriter;

  public FlushResponseWriterSideEffectProducer(final TypedResponseWriter responseWriter) {
    this.responseWriter = responseWriter;
  }

  @Override
  public boolean flush() {
    return responseWriter.flush();
  }
}
