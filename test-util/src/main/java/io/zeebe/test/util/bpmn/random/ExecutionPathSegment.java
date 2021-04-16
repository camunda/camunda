/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.bpmn.random;

import io.zeebe.test.util.bpmn.random.steps.AbstractExecutionStep;
import io.zeebe.test.util.bpmn.random.steps.StepPublishMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * Segment of an execution path. This will not execute a process start to finish but only covers a
 * part of the process.
 *
 * <p>Execution path segments are mutable
 */
public final class ExecutionPathSegment {

  private final List<ScheduledExecutionStep> steps = new ArrayList<>();

  public void append(final AbstractExecutionStep executionStep) {
    final ScheduledExecutionStep predecessor;
    if (steps.isEmpty()) {
      predecessor = null;
    } else {
      predecessor = steps.get(steps.size() - 1);
    }

    steps.add(new ScheduledExecutionStep(predecessor, predecessor, executionStep));
  }

  public void append(
      final AbstractExecutionStep executionStep,
      final AbstractExecutionStep logicalPredecessorStep) {
    final ScheduledExecutionStep executionPredecessor;
    if (steps.isEmpty()) {
      executionPredecessor = null;
    } else {
      executionPredecessor = steps.get(steps.size() - 1);
    }

    final var logicalPredecessor =
        steps.stream()
            .filter(scheduledStep -> scheduledStep.getStep() == logicalPredecessorStep)
            .findFirst()
            .orElseThrow();

    steps.add(new ScheduledExecutionStep(logicalPredecessor, executionPredecessor, executionStep));
  }

  public void append(final ExecutionPathSegment pathToAdd) {
    pathToAdd
        .getScheduledSteps()
        .forEach(
            scheduledExecutionStep -> {
              final var logicalPredecessor = scheduledExecutionStep.getLogicalPredecessor();

              if (logicalPredecessor == null) {
                append(scheduledExecutionStep.getStep());
              } else {

                append(scheduledExecutionStep.getStep(), logicalPredecessor.getStep());
              }
            });
  }

  // TODO pihme modify index position to consider automatic steps
  public void replace(final int index, final AbstractExecutionStep executionStep) {
    steps.subList(index, steps.size()).clear();
    append(executionStep);
  }

  // TODO pihme modify index position to consider automatic steps
  public void insert(final int index, final StepPublishMessage stepPublishMessage) {
    final var tail = steps.subList(index, steps.size());
    replace(index, stepPublishMessage);
    tail.forEach(scheduledStep -> append(scheduledStep.getStep()));
  }

  public List<ScheduledExecutionStep> getScheduledSteps() {
    return Collections.unmodifiableList(steps);
  }

  public List<AbstractExecutionStep> getSteps() {
    return steps.stream().map(ScheduledExecutionStep::getStep).collect(Collectors.toList());
  }

  public Map<String, Object> collectVariables() {
    final Map<String, Object> result = new HashMap<>();

    steps.forEach(step -> result.putAll(step.getVariables()));

    return result;
  }

  @Override
  public int hashCode() {
    return steps.hashCode();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final ExecutionPathSegment that = (ExecutionPathSegment) o;

    return steps.equals(that.steps);
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }
}
