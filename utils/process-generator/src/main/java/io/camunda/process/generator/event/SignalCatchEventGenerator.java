/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator.event;

import io.camunda.process.generator.BpmnFeature;
import io.camunda.process.generator.GeneratorContext;
import io.camunda.process.generator.execution.BroadcastSignalStep;
import io.camunda.zeebe.model.bpmn.builder.AbstractCatchEventBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignalCatchEventGenerator implements BpmnCatchEventGenerator {

  private static final Logger LOG = LoggerFactory.getLogger(SignalCatchEventGenerator.class);

  @Override
  public void addEventDefinition(
      final String elementId,
      final AbstractCatchEventBuilder<?, ?> catchEventBuilder,
      final GeneratorContext generatorContext,
      final boolean generateExecutionPath) {

    LOG.debug("Turning catch event with id {} into a signal catch event", elementId);

    final var signalName = "signal_" + elementId;
    catchEventBuilder.signal(signalName);

    if (generateExecutionPath) {
      generatorContext.addExecutionStep(new BroadcastSignalStep(signalName));
    }
  }

  @Override
  public BpmnFeature getFeature() {
    return BpmnFeature.SIGNAL_EVENT;
  }
}
