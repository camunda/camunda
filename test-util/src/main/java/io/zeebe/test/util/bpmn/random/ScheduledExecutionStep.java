/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.bpmn.random;

import io.zeebe.test.util.bpmn.random.steps.AbstractExecutionStep;
import java.time.Duration;
import java.util.Map;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class ScheduledExecutionStep {

  private final AbstractExecutionStep step;
  private final ScheduledExecutionStep logicalPredecessor;
  private final ScheduledExecutionStep executionPredecessor;

  protected ScheduledExecutionStep(
      final ScheduledExecutionStep logicalPredecessor,
      final ScheduledExecutionStep executionPredecessor,
      final AbstractExecutionStep step) {
    this.logicalPredecessor = logicalPredecessor;
    this.executionPredecessor = executionPredecessor;
    this.step = step;
  }

  public Map<String, Object> getVariables() {
    return step.getVariables(getActivationDuration());
  }

  public AbstractExecutionStep getStep() {
    return step;
  }

  public final Duration getScheduledActivationTime() {
    if (logicalPredecessor != null) {
      return logicalPredecessor.getScheduledExecutionEndTime();
    } else {
      return getScheduledExecutionStartTime();
    }
  }

  public ScheduledExecutionStep getLogicalPredecessor() {
    return logicalPredecessor;
  }

  /*
   * The following methods deal with time. "Time" here means a logical scheduled execution time.
   * (The actual execution time may differ from the scheduled times.)
   *
   * To summarize, we distinguish the following points in time:
   * _scheduledActivationTime_ this is the time when a BPMN element is activated.
   *
   * An element might be activated, but its execution step(s) might be scheduled later.
   * E.g. a parallel gateway will activate 2 or more elements, but only once of them will be executed
   * next. The others are executed later. This is captured in
   *
   * _scheduledExecutionStartTime_ this is the time when an execution step will be executed.
   *
   * _deltaTime_ the time the execution step will take
   *
   * _scheduledExecutionEndTime_ this is the time when the execution of a step is finished.
   *
   * Distinguishing the activation vs. the execution time is important for calculating correct
   * values for timeouts.
   *
   * For a broader discussion, please consult https://github.com/camunda-cloud/zeebe/issues/6568
   * which discusses the problem and the concept.
   */

  /**
   * Returns the point in time during the execution at which the underlying BPMN element will be
   * activated.
   *
   * @return point in time during the execution at which the underlying BPMN element will be *
   *     activated
   */
  public final Duration getScheduledExecutionStartTime() {
    if (executionPredecessor != null) {
      return executionPredecessor.getScheduledExecutionEndTime();
    } else {
      return Duration.ofMillis(0);
    }
  }

  /**
   * Returns the point in time during the execution at which this step will be executed.
   *
   * @return point in time during the execution at which this step will be executed
   */
  public final Duration getScheduledExecutionEndTime() {
    return getScheduledExecutionStartTime().plus(step.getDeltaTime());
  }

  /**
   * Returns the point in time during the execution at which the execution of this step is complete
   *
   * @return point in time during the execution at which the execution of this step is complete
   */
  public final Duration getActivationDuration() {
    return getScheduledExecutionEndTime().minus(getScheduledActivationTime());
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
    if (logicalPredecessor != null
        ? !logicalPredecessor.equals(that.logicalPredecessor)
        : that.logicalPredecessor != null) {
      return false;
    }
    return executionPredecessor != null
        ? executionPredecessor.equals(that.executionPredecessor)
        : that.executionPredecessor == null;
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }
}
