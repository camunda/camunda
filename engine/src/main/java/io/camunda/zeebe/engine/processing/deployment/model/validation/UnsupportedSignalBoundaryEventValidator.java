/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.model.bpmn.instance.BoundaryEvent;
import io.camunda.zeebe.model.bpmn.instance.SignalEventDefinition;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public final class UnsupportedSignalBoundaryEventValidator
    implements ModelElementValidator<BoundaryEvent> {

  @Override
  public Class<BoundaryEvent> getElementType() {
    return BoundaryEvent.class;
  }

  @Override
  public void validate(
      final BoundaryEvent boundaryEvent,
      final ValidationResultCollector validationResultCollector) {
    boundaryEvent.getEventDefinitions().stream()
        .filter(SignalEventDefinition.class::isInstance)
        .forEach(
            signalBoundaryEvent ->
                validationResultCollector.addError(
                    0, "Elements of type signal boundary event are currently not supported"));
  }
}
