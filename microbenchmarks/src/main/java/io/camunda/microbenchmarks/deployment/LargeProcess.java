/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.microbenchmarks.deployment;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;

public final class LargeProcess {
  public static final String VERSION_PLACEHOLDER = "@VERSION@";

  public static final String RESOURCE_NAME = "large-process.bpmn";

  private LargeProcess() {}

  /**
   * Creates a sequential process of service tasks, each with FEEL input/output mappings and a job
   * type expression, since expressions are parsed and validated on deployment too. The process name
   * contains a placeholder to make each deployed version unique.
   */
  public static BpmnModelInstance createLargeProcess(final int serviceTaskCount) {
    AbstractFlowNodeBuilder<?, ?> builder =
        Bpmn.createExecutableProcess("large-process")
            .name("large-process-" + VERSION_PLACEHOLDER)
            .startEvent();
    for (int i = 0; i < serviceTaskCount; i++) {
      final var index = i;
      builder =
          builder.serviceTask(
              "task-" + i,
              t ->
                  t.zeebeJobTypeExpression("\"task-\" + string(" + index + ")")
                      .zeebeInputExpression("order.items[" + (index + 1) + "]", "item")
                      .zeebeInputExpression("if item.price > 100 then true else false", "premium")
                      .zeebeOutputExpression("result.status", "status" + index));
    }
    return builder.endEvent().done();
  }
}
