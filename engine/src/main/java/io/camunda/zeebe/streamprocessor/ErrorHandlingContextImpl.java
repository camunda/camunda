/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import io.camunda.zeebe.engine.api.ErrorHandlingContext;
import io.camunda.zeebe.engine.api.ProcessingResultBuilder;

final class ErrorHandlingContextImpl implements ErrorHandlingContext {

  private final ProcessingResultBuilder processingResultBuilder;

  ErrorHandlingContextImpl(final ProcessingResultBuilder processingResultBuilder) {
    this.processingResultBuilder = processingResultBuilder;
  }

  @Override
  public ProcessingResultBuilder getProcessingResultBuilder() {
    return processingResultBuilder;
  }
}
