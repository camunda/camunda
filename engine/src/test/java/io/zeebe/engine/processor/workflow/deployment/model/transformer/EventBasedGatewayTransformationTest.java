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
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableCatchEvent;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableEventBasedGateway;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.BpmnTransformer;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.List;
import org.junit.Test;

public class EventBasedGatewayTransformationTest {
  @Test
  public void shouldTransformEventBasedGatewayCorrectly() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.readModelFromStream(
            this.getClass().getResourceAsStream("/workflows/event-based-gateway.bpmn"));

    // when
    final BpmnTransformer transformer = new BpmnTransformer();
    final List<ExecutableWorkflow> workflows = transformer.transformDefinitions(modelInstance);

    // then
    final ExecutableEventBasedGateway eventGateway =
        workflows.get(0).getElementById("event_gateway", ExecutableEventBasedGateway.class);

    assertThat(eventGateway.getOutgoing()).hasSize(2);
    assertThat(eventGateway.getEvents()).allMatch(e -> e.isMessage() ^ e.isTimer());
    assertThat(eventGateway.getEvents()).allMatch(this::assertLifecycle);
  }

  private boolean assertLifecycle(ExecutableCatchEvent event) {
    return event.getStep(WorkflowInstanceIntent.ELEMENT_ACTIVATING) == BpmnStep.ELEMENT_ACTIVATING
        && event.getStep(WorkflowInstanceIntent.ELEMENT_ACTIVATED) == BpmnStep.ELEMENT_ACTIVATED
        && event.getStep(WorkflowInstanceIntent.EVENT_OCCURRED) == BpmnStep.EVENT_OCCURRED
        && event.getStep(WorkflowInstanceIntent.ELEMENT_COMPLETING) == BpmnStep.ELEMENT_COMPLETING
        && event.getStep(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            == BpmnStep.FLOWOUT_ELEMENT_COMPLETED
        && event.getStep(WorkflowInstanceIntent.ELEMENT_TERMINATING)
            == BpmnStep.ELEMENT_TERMINATING;
  }
}
