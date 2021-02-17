/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.bpmn.random.blocks;

import io.zeebe.model.bpmn.builder.ProcessBuilder;
import io.zeebe.test.util.bpmn.random.AbstractExecutionStep;
import io.zeebe.test.util.bpmn.random.ConstructionContext;
import io.zeebe.test.util.bpmn.random.ExecutionPathSegment;
import io.zeebe.test.util.bpmn.random.StartEventBlockBuilder;
import java.util.Map;

public final class NoneStartEventBuilder implements StartEventBlockBuilder {

  private final String startEventId;

  public NoneStartEventBuilder(final ConstructionContext context) {
    final var idGenerator = context.getIdGenerator();
    startEventId = idGenerator.nextId();
  }

  @Override
  public io.zeebe.model.bpmn.builder.StartEventBuilder buildStartEvent(
      final ProcessBuilder processBuilder) {
    return processBuilder.startEvent(startEventId);
  }

  @Override
  public ExecutionPathSegment findRandomExecutionPath(
      final String processId, final Map<String, Object> variables) {
    final var pathSegment = new ExecutionPathSegment();
    pathSegment.append(new StepStartProcessInstance(processId, variables));
    return pathSegment;
  }

  public static final class StepStartProcessInstance extends AbstractExecutionStep {

    private final String processId;

    public StepStartProcessInstance(final String processId, final Map<String, Object> variables) {
      this.processId = processId;
      this.variables.putAll(variables);
    }

    public String getProcessId() {
      return processId;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final StepStartProcessInstance that = (StepStartProcessInstance) o;

      if (processId != null ? !processId.equals(that.processId) : that.processId != null) {
        return false;
      }
      return variables.equals(that.variables);
    }

    @Override
    public int hashCode() {
      int result = processId != null ? processId.hashCode() : 0;
      result = 31 * result + variables.hashCode();
      return result;
    }
  }
}
