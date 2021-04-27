/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.bpmn.random.steps;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * Abstract implementation of execution steps. Each execution step has a map of variables. These
 * variables need to be set before the execution step can be executed (e.g. setting variables when
 * process is created)
 *
 * <p>New implementations should also extends the execution logic in {@code
 * io.zeebe.engine.util.ProcessExecutor}
 *
 * <p>Contract: each implementing class must implement {@code equals(...)/hashCode()} This is mostly
 * in order to be able to compare two randomly generated execution paths to see if they are the same
 */
public abstract class AbstractExecutionStep {

  public static final Duration VIRTUALLY_NO_TIME = Duration.ofMillis(0);
  public static final Duration DEFAULT_DELTA = Duration.ofHours(1);
  public static final Duration VIRTUALLY_INFINITE = Duration.ofDays(365);

  protected final Map<String, Object> variables = new HashMap<>();

  public Map<String, Object> getVariables() {
    return Collections.unmodifiableMap(variables);
  }

  public final Map<String, Object> getVariables(final Duration activationDuration) {
    return updateVariables(Collections.unmodifiableMap(variables), activationDuration);
  }

  /** Update the variables to reflect the {@code activationDuration} in this execution path */
  protected abstract Map<String, Object> updateVariables(
      final Map<String, Object> variables, final Duration activationDuration);

  /**
   * Returns {@code true} if the execution step runs automatically. A step runs automatically if it
   * is activated and completed without any client command. Such a step is controlled completely by
   * the engine and we have no way to influence its execution otherwise
   *
   * @return {@code true} if the execution step runs automatically
   */
  public abstract boolean isAutomatic();

  /**
   * Returns the time this step will take
   *
   * @return time this step will take
   */
  public abstract Duration getDeltaTime();

  @Override
  public int hashCode() {
    return variables.hashCode();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final AbstractExecutionStep that = (AbstractExecutionStep) o;

    return variables.equals(that.variables);
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }
}
