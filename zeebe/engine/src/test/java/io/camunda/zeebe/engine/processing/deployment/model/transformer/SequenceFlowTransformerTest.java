/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.engine.util.FakeExpressionLanguage;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.SequenceFlow;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SequenceFlowTransformerTest {

  private final BpmnTransformer transformer = new BpmnTransformer(new FakeExpressionLanguage());

  @ParameterizedTest
  @ValueSource(strings = {"sourceRef", "targetRef"})
  void shouldFailFastOnUnresolvedSequenceFlowReferences(final String missingReferenceAttribute) {
    // given
    final BpmnModelInstance modelInstance = createModelWithDanglingSequenceFlow();
    final SequenceFlow sequenceFlow =
        modelInstance.getModelElementsByType(SequenceFlow.class).iterator().next();
    sequenceFlow.setAttributeValue(missingReferenceAttribute, "missing", false);

    // when
    assertThatThrownBy(() -> transformer.transformDefinitions(modelInstance))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Sequence flow 'flow'")
        .hasMessageContaining("unresolved " + missingReferenceAttribute);
  }

  private static BpmnModelInstance createModelWithDanglingSequenceFlow() {
    return Bpmn.createExecutableProcess("process")
        .startEvent("source")
        .sequenceFlowId("flow")
        .endEvent("target")
        .done();
  }
}
