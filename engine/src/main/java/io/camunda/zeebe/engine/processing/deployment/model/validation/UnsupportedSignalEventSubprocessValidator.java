/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.model.bpmn.instance.CatchEvent;
import io.camunda.zeebe.model.bpmn.instance.SignalEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.model.bpmn.instance.SubProcess;
import java.util.Collection;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public final class UnsupportedSignalEventSubprocessValidator
    implements ModelElementValidator<SubProcess> {

  @Override
  public Class<SubProcess> getElementType() {
    return SubProcess.class;
  }

  @Override
  public void validate(
      final SubProcess subProcess, final ValidationResultCollector validationResultCollector) {
    if (!subProcess.triggeredByEvent()) {
      return;
    }

    final Collection<StartEvent> startEvents = subProcess.getChildElementsByType(StartEvent.class);
    startEvents.stream()
        .map(CatchEvent::getEventDefinitions)
        .filter(e -> e.stream().anyMatch(SignalEventDefinition.class::isInstance))
        .forEach(
            signalSubProcess ->
                validationResultCollector.addError(
                    0, "Elements of type signal event subprocess are currently not supported"));
  }
}
