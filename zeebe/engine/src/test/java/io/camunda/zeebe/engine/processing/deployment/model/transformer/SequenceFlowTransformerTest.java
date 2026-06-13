/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableSequenceFlow;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.engine.util.FakeExpressionLanguage;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.SequenceFlow;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SequenceFlowTransformerTest {

  private final BpmnTransformer transformer = new BpmnTransformer(new FakeExpressionLanguage());

  @ParameterizedTest
  @ValueSource(strings = {"sourceRef", "targetRef"})
  void shouldIgnoreUnresolvedSequenceFlowReferences(final String missingReferenceAttribute) {
    // given
    final BpmnModelInstance modelInstance = createModelWithDanglingSequenceFlow();
    final SequenceFlow sequenceFlow =
        modelInstance.getModelElementsByType(SequenceFlow.class).iterator().next();
    sequenceFlow.setAttributeValue(missingReferenceAttribute, "missing", false);

    // when
    final List<ExecutableProcess> processes = transformer.transformDefinitions(modelInstance);

    // then
    assertThat(processes)
        .singleElement()
        .satisfies(
            process -> {
              final ExecutableSequenceFlow executableSequenceFlow =
                  process.getElementById("flow", ExecutableSequenceFlow.class);
              final ExecutableFlowNode source =
                  process.getElementById("source", ExecutableFlowNode.class);
              final ExecutableFlowNode target =
                  process.getElementById("target", ExecutableFlowNode.class);

              assertThat(executableSequenceFlow.getSource()).isNull();
              assertThat(executableSequenceFlow.getTarget()).isNull();
              assertThat(source.getOutgoing()).isEmpty();
              assertThat(target.getIncoming()).isEmpty();
            });
  }

  private static BpmnModelInstance createModelWithDanglingSequenceFlow() {
    return Bpmn.createExecutableProcess("process")
        .startEvent("source")
        .sequenceFlowId("flow")
        .endEvent("target")
        .done();
  }
}
