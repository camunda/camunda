/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.model.bpmn.instance.Condition;
import io.camunda.zeebe.model.bpmn.instance.ConditionalEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.model.bpmn.instance.SubProcess;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ProcessConditionalStartEventValidator implements ModelElementValidator<Process> {

  @Override
  public Class<Process> getElementType() {
    return Process.class;
  }

  @Override
  public void validate(
      final Process process, final ValidationResultCollector validationResultCollector) {

    // root-level start events
    validateConditionalStartEventsDuplicate(
        process.getChildElementsByType(StartEvent.class),
        process,
        validationResultCollector,
        "Duplicate condition expression '%s' found in conditional start events of process '%s'. "
            + "Condition expressions for conditional start events must be unique within a process.");

    // event subprocess start events
    final Collection<StartEvent> eventSubprocessStartEvents =
        process.getChildElementsByType(SubProcess.class).stream()
            .filter(SubProcess::triggeredByEvent)
            .map(sub -> sub.getChildElementsByType(StartEvent.class))
            .flatMap(Collection::stream)
            .toList();
    validateConditionalStartEventsDuplicate(
        eventSubprocessStartEvents,
        process,
        validationResultCollector,
        "Duplicate condition expression '%s' found in conditional start events of event subprocesses in process '%s'. "
            + "Condition expressions for conditional start events in event subprocesses must be unique within a process.");
  }

  private static void validateConditionalStartEventsDuplicate(
      final Collection<StartEvent> startEvents,
      final Process process,
      final ValidationResultCollector validationResultCollector,
      final String errorMessageTemplate) {

    final List<String> conditionalStartEventConditions =
        extractConditionalStartEventConditions(startEvents);

    findDuplicates(conditionalStartEventConditions)
        .forEach(
            duplicateCondition ->
                validationResultCollector.addError(
                    0, String.format(errorMessageTemplate, duplicateCondition, process.getId())));
  }

  private static List<String> extractConditionalStartEventConditions(
      final Collection<StartEvent> startEvents) {

    return startEvents.stream()
        .map(StartEvent::getEventDefinitions)
        .flatMap(Collection::stream)
        .filter(ConditionalEventDefinition.class::isInstance)
        .map(ConditionalEventDefinition.class::cast)
        .map(ConditionalEventDefinition::getCondition)
        .filter(Objects::nonNull)
        .map(Condition::getTextContent)
        .toList();
  }

  private static Set<String> findDuplicates(final List<String> values) {
    return values.stream()
        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
        .entrySet()
        .stream()
        .filter(entry -> entry.getValue() > 1)
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }
}
