/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableSequenceFlow;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import java.time.InstantSource;
import java.util.List;
import org.junit.jupiter.api.Test;

final class StraightThroughProcessingLoopPerformanceTest {

  private final ExpressionLanguage expressionLanguage =
      ExpressionLanguageFactory.createExpressionLanguage(
          new ZeebeFeelEngineClock(InstantSource.system()));
  private final BpmnTransformer transformer = new BpmnTransformer(expressionLanguage);

  @Test
  void shouldRecurseIntoDeepGatewayAtMostOncePerProcess() {
    final var diamonds = 10;
    final var deepGatewayId = "join-" + (diamonds - 1);
    final var model = diamondChain(diamonds);
    final var process = transformer.transformDefinitions(model).getFirst();

    final var deepGateway = process.getElementById(deepGatewayId, ExecutableFlowNode.class);
    final var spyDeepGateway = spy(deepGateway);
    rewireIncomingFlowsToSpy(process, deepGateway, spyDeepGateway);

    final var resource =
        new DeploymentResource()
            .setResourceName(wrapString("perf.bpmn"))
            .setResource(wrapString(Bpmn.convertToString(model)));
    final var result = StraightThroughProcessingLoopValidator.validate(resource, List.of(process));

    assertThat(result.isRight())
        .as("Acyclic diamond chain must validate without a false-positive loop")
        .isTrue();

    // Each descending recursion of checkForStraightThroughProcessingLoop into deepGateway
    // calls deepGateway.getOutgoing() exactly once (via getNextElements). Memoized short-
    // circuits at the visited.contains check return without reaching getOutgoing.
    // Pre-fix (no memoization): 2^(N-1) = 512 visits via brt-0-a's DFS alone, plus more
    // from other roots — easily thousands of calls.
    verify(spyDeepGateway, times(1)).getOutgoing();
  }

  private BpmnModelInstance diamondChain(final int diamonds) {
    AbstractFlowNodeBuilder<?, ?> builder =
        Bpmn.createExecutableProcess("perf-process").startEvent("start");
    for (int i = 0; i < diamonds; i++) {
      final String fork = "fork-" + i;
      final String join = "join-" + i;
      builder =
          builder
              .parallelGateway(fork)
              .businessRuleTask(
                  "brt-" + i + "-a",
                  t -> t.zeebeCalledDecisionId("decision-id").zeebeResultVariable("result"))
              .parallelGateway(join)
              .moveToNode(fork)
              .businessRuleTask(
                  "brt-" + i + "-b",
                  t -> t.zeebeCalledDecisionId("decision-id").zeebeResultVariable("result"))
              .connectTo(join);
    }
    return builder.endEvent().done();
  }

  private static void rewireIncomingFlowsToSpy(
      final ExecutableProcess process,
      final ExecutableFlowNode original,
      final ExecutableFlowNode spy) {
    for (final ExecutableFlowElement element : process.getFlowElements()) {
      if (element instanceof final ExecutableSequenceFlow sf && sf.getTarget() == original) {
        sf.setTarget(spy);
      }
    }
  }
}
