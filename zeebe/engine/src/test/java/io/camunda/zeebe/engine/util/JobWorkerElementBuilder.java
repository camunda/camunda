/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.util;

import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.builder.ZeebeJobWorkerElementBuilder;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A builder for the BPMN model API to add an element to the process that is based on a job and
 * should be processed by a job worker. For example, a service task.
 */
public final class JobWorkerElementBuilder {

  private final BpmnElementType elementType;
  private final Function<AbstractFlowNodeBuilder<?, ?>, AbstractFlowNodeBuilder<?, ?>> builder;

  private <T extends AbstractFlowNodeBuilder<?, ?> & ZeebeJobWorkerElementBuilder<T>>
      JobWorkerElementBuilder(
          final BpmnElementType elementType,
          final Function<AbstractFlowNodeBuilder<?, ?>, T> builder) {
    this.elementType = elementType;
    this.builder = builder::apply;
  }

  public AbstractFlowNodeBuilder<?, ?> build(
      final AbstractFlowNodeBuilder<?, ?> processBuilder,
      final Consumer<ZeebeJobWorkerElementBuilder<?>> builderConsumer) {
    final AbstractFlowNodeBuilder<?, ?> elementBuilder = builder.apply(processBuilder);
    builderConsumer.accept((ZeebeJobWorkerElementBuilder<?>) elementBuilder);
    return elementBuilder;
  }

  public static <T extends AbstractFlowNodeBuilder<?, ?> & ZeebeJobWorkerElementBuilder<T>>
      JobWorkerElementBuilder of(
          final BpmnElementType elementType,
          final Function<AbstractFlowNodeBuilder<?, ?>, T> builder) {
    return new JobWorkerElementBuilder(elementType, builder);
  }

  public BpmnElementType getElementType() {
    return elementType;
  }

  @Override
  public String toString() {
    return elementType.name();
  }
}
