/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.bpmn.random;

import io.camunda.zeebe.test.util.bpmn.random.steps.AbstractExecutionStep;
import java.time.Duration;
import java.util.Map;

/**
 * Class that schedules an execution step to run at a certain time.
 *
 * <p>"Time" here means a scheduled execution time.
 *
 * <p>To summarize, we distinguish the following points in time: _scheduledActivationTime_ this is
 * the time when a BPMN element is activated.
 *
 * <p>An element might be activated, but its execution step(s) might be scheduled later. E.g. a
 * parallel gateway will activate 2 or more elements, but only once of them will be executed next.
 * The others are executed later. This is captured in
 *
 * <p>_scheduledExecutionStartTime_ this is the time when an execution step will be executed.
 *
 * <p>_deltaTime_ the time the execution step will take
 *
 * <p>_scheduledExecutionEndTime_ this is the time when the execution of a step is finished.
 *
 * <p>Distinguishing the activation vs. the execution time is important for calculating correct
 * values for timeouts.
 *
 * <p>For a broader discussion, please consult https://github.com/camunda/zeebe/issues/6568 which
 * discusses the problem and the concept.
 */
public class ScheduledExecutionStep {

  private final AbstractExecutionStep step;
  private final ScheduledExecutionStep logicalPredecessor;

  private final Duration activationTime;
  private final Duration startTime;
  private final Duration endTime;
  private final Duration activationDuration;

  protected ScheduledExecutionStep(
      final ScheduledExecutionStep logicalPredecessor,
      final ScheduledExecutionStep executionPredecessor,
      final AbstractExecutionStep step) {
    this.logicalPredecessor = logicalPredecessor;
    this.step = step;

    if (executionPredecessor != null) {
      startTime = executionPredecessor.getEndTime();
    } else {
      startTime = Duration.ofMillis(0);
    }

    if (logicalPredecessor != null) {
      activationTime = logicalPredecessor.getEndTime();
    } else {
      activationTime = startTime;
    }

    endTime = startTime.plus(step.getDeltaTime());
    activationDuration = endTime.minus(activationTime);
  }

  public Map<String, Object> getVariables() {
    return step.getVariables(getActivationDuration());
  }

  public AbstractExecutionStep getStep() {
    return step;
  }

  public ScheduledExecutionStep getLogicalPredecessor() {
    return logicalPredecessor;
  }

  /**
   * Returns the point in time during the execution at which the underlying BPMN element will be
   * activated.
   *
   * @return point in time during the execution at which the underlying BPMN element will be
   *     activated
   */
  public final Duration getActivationTime() {
    return activationTime;
  }

  /**
   * Returns the point in time during the execution at which the underlying BPMN element will be
   * executed.
   *
   * @return point in time during the execution at which the underlying BPMN element will be
   *     executed
   */
  public final Duration getStartTime() {
    return startTime;
  }

  /**
   * Returns the point in time during the execution at which the execution of this tep is complete.
   *
   * @return point in time during the execution at which the execution of this tep is complete.
   */
  public final Duration getEndTime() {
    return endTime;
  }

  /**
   * Returns total duration that the underlying BPMN element is activated
   *
   * @return total duration that the underlying BPMN element is activated
   */
  public final Duration getActivationDuration() {
    return activationDuration;
  }

  /**
   * Returns the total duration that the underlying BPMN element is activated
   *
   * @return total duration that the underlying BPMN element is activated
   */
  @Override
  public int hashCode() {
    return step.hashCode();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final ScheduledExecutionStep that = (ScheduledExecutionStep) o;

    if (!step.equals(that.step)) {
      return false;
    }
    if (!activationTime.equals(that.activationTime)) {
      return false;
    }
    if (!startTime.equals(that.startTime)) {
      return false;
    }
    if (!endTime.equals(that.endTime)) {
      return false;
    }
    return activationDuration.equals(that.activationDuration);
  }

  @Override
  public String toString() {
    return "ScheduledExecutionStep{"
        + "step="
        + step
        + ", activationTime="
        + activationTime
        + ", startTime="
        + startTime
        + ", endTime="
        + endTime
        + ", activationDuration="
        + activationDuration
        + '}';
  }
}
