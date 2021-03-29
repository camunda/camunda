/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.bpmn.random.steps;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * Abstract implementation of execution steps. Each execution step has a map of variables. These
 * variables need to be set before the execution step can be executed (e.g. setting the variables
 * when process is created)
 *
 * <p>New implementations should also extends the execution logic in {@link
 * io.zeebe.engine.util.ProcessExecutor}
 *
 * <p>Contract: each implementing class must implement {@code equals(...)/hashCode()} This is mostly
 * in order to be able to compare two randomly generated execution paths to see if they are the same
 */
public abstract class AbstractExecutionStep {

  protected final Map<String, Object> variables = new HashMap<>();

  public Map<String, Object> getVariables() {
    return Collections.unmodifiableMap(variables);
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }

  @Override
  public abstract boolean equals(final Object o);

  @Override
  public abstract int hashCode();
}
