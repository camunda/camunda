/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.processor.workflow.deployment.model.BpmnStep;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableExclusiveGateway;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.BpmnTransformer;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.junit.Test;

public class ExclusiveGatewayTransformationTest {

  @Test
  public void shouldTransformExclusiveGatewayCorrectly() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.readModelFromStream(
            this.getClass().getResourceAsStream("/workflows/exclusive-gateway.bpmn"));

    // when
    final BpmnTransformer transformer = new BpmnTransformer();
    final List<ExecutableWorkflow> workflows = transformer.transformDefinitions(modelInstance);

    // then
    final ExecutableExclusiveGateway splitGateway =
        workflows.get(0).getElementById("split", ExecutableExclusiveGateway.class);

    assertThat(splitGateway.getOutgoing()).hasSize(2);
    assertThat(BufferUtil.bufferAsString(splitGateway.getDefaultFlow().getId()))
        .isEqualTo("split-to-a");
    assertThat(splitGateway.getStep(WorkflowInstanceIntent.ELEMENT_COMPLETED))
        .isEqualTo(BpmnStep.EXCLUSIVE_GATEWAY_ELEMENT_COMPLETED);
  }
}
