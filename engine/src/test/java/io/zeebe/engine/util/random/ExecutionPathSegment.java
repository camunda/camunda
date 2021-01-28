/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.util.random;

import io.zeebe.engine.util.random.steps.AbstractExecutionStep;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class ExecutionPathSegment {

  List<AbstractExecutionStep> steps = new ArrayList<>();

  public void append(AbstractExecutionStep executionStep) {
    steps.add(executionStep);
  }

  public void append(ExecutionPathSegment pathToAdd) {
    steps.addAll(pathToAdd.getSteps());
  }

  public List<AbstractExecutionStep> getSteps() {
    return Collections.unmodifiableList(steps);
  }

  public Map<String, Object> collectVariables() {
    Map<String, Object> result = new HashMap<>();

    steps.forEach(step -> result.putAll(step.getVariables()));

    return result;
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }
}
