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
import java.util.Random;
import java.util.function.Predicate;
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
  private final Map<String, Object> variables = new HashMap<>();

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
    mergeVariableDefaults(pathToAdd);

    pathToAdd.getScheduledSteps().forEach(this::append);
  }

  public void append(final ScheduledExecutionStep scheduledExecutionStep) {
    final var logicalPredecessor = scheduledExecutionStep.getLogicalPredecessor();

    if (logicalPredecessor == null) {
      append(scheduledExecutionStep.getStep());
    } else {
      append(scheduledExecutionStep.getStep(), logicalPredecessor.getStep());
    }
  }

  public boolean canBeInterrupted() {
    if (steps.isEmpty()) {
      return false;
    }

    return steps.stream()
            .map(ScheduledExecutionStep::getStep)
            .filter(Predicate.not(AbstractExecutionStep::isAutomatic))
            .collect(Collectors.toList())
            .size()
        > 1;
  }

  /**
   * Sets a default value for a variable. The default value must be independent of the execution
   * path taken. The default value can be overwritten by any step
   */
  public void setVariableDefault(final String key, final Object value) {
    variables.put(key, value);
  }

  public void mergeVariableDefaults(final ExecutionPathSegment other) {
    variables.putAll(other.variables);
  }

  /**
   * Cuts the execution path at a random position at which the process can be interrupted
   *
   * @param random random generator
   */
  public void cutAtRandomPosition(final Random random) {

    if (!canBeInterrupted()) {
      throw new IllegalArgumentException("This execution flow cannot be interrupted");
    }

    steps.subList(findCutOffPoint(random), steps.size()).clear();
  }

  /**
   * This method finds a cutoff point. We can cut only at a position where we have a non-automatic
   * step
   *
   * @return index of cutoff point
   */
  private int findCutOffPoint(final Random random) {
    // find the first and last point where a cutoff is possible
    Integer firstCutOffPoint = null;
    Integer lastCutOffPoint = null;

    for (int index = 0; index < steps.size(); index++) {
      final var step = steps.get(index).getStep();

      if (!step.isAutomatic()) {
        if (firstCutOffPoint == null) {
          firstCutOffPoint = index;
        }

        lastCutOffPoint = index;
      }
    }

    // find a random position between these two cutoff points
    final int initialCutOffPoint =
        firstCutOffPoint + random.nextInt(lastCutOffPoint - firstCutOffPoint);

    // skip automatic steps
    int finalCutOffPoint = initialCutOffPoint;
    while (steps.get(finalCutOffPoint).getStep().isAutomatic()) {
      finalCutOffPoint++;
    }

    return finalCutOffPoint;
  }

  @Deprecated // use interruptAtRandomPosition instead
  public void replace(final int index, final AbstractExecutionStep executionStep) {
    steps.subList(index, steps.size()).clear();
    append(executionStep);
  }

  /**
   * Deprecated for several reasons:
   *
   * <p>a) not clear why a specific step is part of the signature; should be a more general class
   *
   * <p>b) overall, not clear what the method should achieve.
   *
   * <p>It might be better to have a look at
   * ParallelGatewayBlockBuilder#shuffleStepsFromDifferentLists(...) which demonstrates how to merge
   * different parallel execution paths into a single execution path
   */
  @Deprecated
  public void insert(final int index, final StepPublishMessage stepPublishMessage) {
    final var tail = steps.subList(index, steps.size());
    replace(index, stepPublishMessage);
    tail.forEach(scheduledStep -> append(scheduledStep));
  }

  public List<ScheduledExecutionStep> getScheduledSteps() {
    return Collections.unmodifiableList(steps);
  }

  public List<AbstractExecutionStep> getSteps() {
    return steps.stream().map(ScheduledExecutionStep::getStep).collect(Collectors.toList());
  }

  public Map<String, Object> collectVariables() {
    final Map<String, Object> result = new HashMap<>(variables);

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
