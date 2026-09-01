/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.engine.EngineConfiguration.OutputMappingMode;
import io.camunda.zeebe.engine.processing.deployment.model.element.OutputMappings;
import org.jspecify.annotations.NullMarked;

/** Utility methods for working with {@link MappingResolver} instances for output mappings. */
@NullMarked
public final class OutputMappingResolvers {

  private OutputMappingResolvers() {}

  /** Returns a resolver for the given output mapping mode. */
  public static MappingResolver<OutputMappings> forMode(final OutputMappingMode outputMappingMode) {
    return outputMappingMode == OutputMappingMode.COMBINED
        ? new CombinedOutputMappingResolver()
        : new OrderedOutputMappingResolver();
  }
}
