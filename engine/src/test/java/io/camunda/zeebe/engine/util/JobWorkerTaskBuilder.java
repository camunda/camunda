/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.util;

import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.builder.AbstractJobWorkerTaskBuilder;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.function.Function;

/**
 * A builder for the BPMN model API to add a task to the process that is based on a job and should
 * be processed by a job worker. For example, a service task.
 */
public final class JobWorkerTaskBuilder {

  private final BpmnElementType taskType;
  private final Function<AbstractFlowNodeBuilder<?, ?>, AbstractJobWorkerTaskBuilder<?, ?>> builder;

  private JobWorkerTaskBuilder(
      final BpmnElementType taskType,
      final Function<AbstractFlowNodeBuilder<?, ?>, AbstractJobWorkerTaskBuilder<?, ?>> builder) {
    this.taskType = taskType;
    this.builder = builder;
  }

  public AbstractJobWorkerTaskBuilder<?, ?> build(
      final AbstractFlowNodeBuilder<?, ?> processBuilder) {
    return builder.apply(processBuilder);
  }

  public static JobWorkerTaskBuilder of(
      final BpmnElementType taskType,
      final Function<AbstractFlowNodeBuilder<?, ?>, AbstractJobWorkerTaskBuilder<?, ?>> builder) {
    return new JobWorkerTaskBuilder(taskType, builder);
  }

  public BpmnElementType getTaskType() {
    return taskType;
  }

  @Override
  public String toString() {
    return taskType.name();
  }
}
