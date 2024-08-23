/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator.parallel;

import io.camunda.process.generator.BpmnFeature;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import java.util.List;
import java.util.function.IntPredicate;

public interface BpmnParallelFlowGenerator extends BpmnFeature {

  /**
   * Add parallel flows to the given process builder. The flows are open-ended, i.e., they are not
   * yet joined. You can use this method to add parallel flows and then join them explicitly.
   *
   * @param numberOfFlows the number of parallel flows to add
   * @param processBuilder the process builder to which the flows should be added
   * @param generateExecutionPath whether to generate an execution path for the flows
   * @param shouldGenerateExecutionPathForFlow a predicate that determines whether to generate an
   *     execution path for the flow with the given index
   * @return a list of flow builders, one for each of the parallel flows added
   */
  List<? extends AbstractFlowNodeBuilder<?, ?>> addFlows(
      final int numberOfFlows,
      final AbstractFlowNodeBuilder<?, ?> processBuilder,
      final boolean generateExecutionPath,
      final IntPredicate shouldGenerateExecutionPathForFlow);
}
