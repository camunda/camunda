/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.view.process.frequency;

import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;

public interface ProcessViewFrequencyInterpreter {
  default ViewResult createViewResult(final Double value) {
    return ViewResult.builder()
        .viewMeasure(CompositeCommandResult.ViewMeasure.builder().value(value).build())
        .build();
  }
}
