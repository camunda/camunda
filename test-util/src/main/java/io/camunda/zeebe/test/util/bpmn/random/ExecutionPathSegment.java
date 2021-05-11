/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.bpmn.random;

import io.camunda.zeebe.test.util.bpmn.random.steps.AbstractExecutionStep;
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

  private final List<ScheduledExecutionStep> scheduledSteps = new ArrayList<>();
  private final Map<String, Object> variableDefaults = new HashMap<>();

  /**
   * Appends the next step to the execution path. This next step is a direct successor to the
   * previous step. This means that the last step in the execution path is both its logical and its
   * scheduling predecessor.
   *
   * @param executionStep direct successor to previous step
   */
  public void appendDirectSuccessor(final AbstractExecutionStep executionStep) {
    final ScheduledExecutionStep predecessor;
    if (scheduledSteps.isEmpty()) {
      predecessor = null;
    } else {
      predecessor = scheduledSteps.get(scheduledSteps.size() - 1);
    }

    scheduledSteps.add(new ScheduledExecutionStep(predecessor, predecessor, executionStep));
  }

  /**
   * Appends the next step to the execution path. This next step is a scheduling successor to the
   * previous step. This means that the last step in the execution path is its execution
   * predecessor. However, the logical predecessor of the next step is the one passed in via {@code
   * logicalPredecessorStep}
   *
   * @param executionStep the next step
   * @param logicalPredecessorStep the logical predecessor of {@code executionStep}; this step must
   *     already be part of the execution path or else an exception will be thrown
   * @throws IllegalStateException thrown is {@code logicalPredecessorStep} is not part of the
   *     existing execution path
   */
  public void appendExecutionSuccessor(
      final AbstractExecutionStep executionStep,
      final AbstractExecutionStep logicalPredecessorStep) {
    final ScheduledExecutionStep executionPredecessor;
    if (scheduledSteps.isEmpty()) {
      executionPredecessor = null;
    } else {
      executionPredecessor = scheduledSteps.get(scheduledSteps.size() - 1);
    }

    final var logicalPredecessor =
        scheduledSteps.stream()
            .filter(scheduledStep -> scheduledStep.getStep() == logicalPredecessorStep)
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Unable to find step "
                            + logicalPredecessorStep
                            + ". This step was passed as a logical predecessor, thus it should already be present in the execution path segment. But it was not found."));

    scheduledSteps.add(
        new ScheduledExecutionStep(logicalPredecessor, executionPredecessor, executionStep));
  }

  public void append(final ExecutionPathSegment pathToAdd) {
    mergeVariableDefaults(pathToAdd);

    pathToAdd.getScheduledSteps().forEach(this::append);
  }

  public void append(final ScheduledExecutionStep scheduledExecutionStep) {
    final var logicalPredecessor = scheduledExecutionStep.getLogicalPredecessor();

    if (logicalPredecessor == null) {
      appendDirectSuccessor(scheduledExecutionStep.getStep());
    } else {
      appendExecutionSuccessor(scheduledExecutionStep.getStep(), logicalPredecessor.getStep());
    }
  }

  public boolean canBeInterrupted() {
    return scheduledSteps.stream()
        .map(ScheduledExecutionStep::getStep)
        .anyMatch(Predicate.not(AbstractExecutionStep::isAutomatic));
  }

  /**
   * Sets a default value for a variable. The default value must be independent of the execution
   * path taken. The default value can be overwritten by any step
   */
  public void setVariableDefault(final String key, final Object value) {
    variableDefaults.put(key, value);
  }

  public void mergeVariableDefaults(final ExecutionPathSegment other) {
    variableDefaults.putAll(other.variableDefaults);
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

    scheduledSteps.subList(findCutOffPoint(random), scheduledSteps.size()).clear();
  }

  /**
   * This method finds execution boundaries. A execution boundary is the point where automatic and
   * non-automatic step meet each other. We can cut only at positions where we have a non-automatic
   * steps.
   *
   * @return an object which holds the first and last execution boundary
   */
  private ExecutionBoundaries findExecutionBoundaries() {
    // find the first and last point where a cutoff is possible
    final var cutOffPoints = new ExecutionBoundaries();

    for (int index = 0; index < scheduledSteps.size(); index++) {
      final var step = scheduledSteps.get(index).getStep();

      if (!step.isAutomatic()) {
        cutOffPoints.addBoundary(index);
      }
    }

    return cutOffPoints;
  }

  /**
   * This method finds a cutoff point between execution boundaries. It will return an index which
   * marks such execution boundary. If there is only one boundary it will return the corresponding
   * position, or zero if there is none.
   *
   * <p>A execution boundary is a position where a previous step is automatic and the next one is
   * non-automatic. Here we can cut the execution off.
   *
   * @return index of the cutoff point
   */
  private int findCutOffPoint(final Random random) {
    final var executionBoundaries = findExecutionBoundaries();

    return executionBoundaries.chooseRandomBoundary(random);
  }

  /**
   * Inserts given execution step at a pseudo-random position. The method ensures that it is
   * inserted at latest before the last non automatic step, such that race conditions can be
   * avoided.
   *
   * <p>This is method is mostly used for execution steps which can happen in parallel to the normal
   * flow, like non interrupting boundary events or event sub processes.
   *
   * <p>The existing execution step at this index and all exceution's steps come after are moved to
   * the right. The related ScheduledSteps which are before and come immediately after are updated.
   *
   * @param random used to pseudo-randomly chose where the step should be inserted
   * @param executionStep the step which should be inserted
   */
  public void insertExecutionStep(final Random random, final AbstractExecutionStep executionStep) {
    final var index = findCutOffPoint(random);

    final var successor = scheduledSteps.remove(index);
    final ScheduledExecutionStep newStep;
    if (index == 0) {
      newStep = new ScheduledExecutionStep(null, null, executionStep);
    } else {
      final var predecessor = scheduledSteps.get(index - 1);
      newStep = new ScheduledExecutionStep(predecessor, predecessor, executionStep);
    }

    // add at index will move all elements to the right, including the object on the given index
    scheduledSteps.add(index, new ScheduledExecutionStep(newStep, newStep, successor.getStep()));
    scheduledSteps.add(index, newStep);
  }

  public List<ScheduledExecutionStep> getScheduledSteps() {
    return Collections.unmodifiableList(scheduledSteps);
  }

  public List<AbstractExecutionStep> getSteps() {
    return scheduledSteps.stream()
        .map(ScheduledExecutionStep::getStep)
        .collect(Collectors.toList());
  }

  public Map<String, Object> collectVariables() {
    final Map<String, Object> result = new HashMap<>(variableDefaults);

    scheduledSteps.forEach(scheduledStep -> result.putAll(scheduledStep.getVariables()));

    return result;
  }

  @Override
  public int hashCode() {
    return scheduledSteps.hashCode();
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

    return scheduledSteps.equals(that.scheduledSteps);
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }

  /**
   * An execution boundary is the point where automatic and non-automatic {@link
   * ScheduledExecutionStep}'s meet each other. This class contains information about the existing
   * execution boundaries in a {@link ExecutionPath}.
   */
  private static final class ExecutionBoundaries {

    private final List<Integer> boundaries = new ArrayList<>();

    /**
     * Adds an execution boundary index to the collection.
     *
     * @param boundaryIndex the index of the execution boundary in the corresponding execution path
     */
    void addBoundary(final int boundaryIndex) {
      boundaries.add(boundaryIndex);
    }

    /**
     * Returns a pseudo-randomly chosen execution boundary.
     *
     * @return the execution boundary
     */
    public int chooseRandomBoundary(final Random random) {
      return boundaries.isEmpty() ? 0 : boundaries.get(random.nextInt(boundaries.size()));
    }
  }
}
