/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor.writers;

import io.camunda.zeebe.engine.api.ProcessingResultBuilder;
import java.util.function.Supplier;

abstract class AbstractResultBuilderBackedWriter {

  /* supplier for result builder, result builder must not be cached as the concrete result builder is a
  request scoped object, i.e. it is a new one for each record that is being processed*/
  private final Supplier<ProcessingResultBuilder> resultBuilderSupplier;

  AbstractResultBuilderBackedWriter(final Supplier<ProcessingResultBuilder> resultBuilderSupplier) {
    this.resultBuilderSupplier = resultBuilderSupplier;
  }

  protected ProcessingResultBuilder resultBuilder() {
    return resultBuilderSupplier.get();
  }
}
