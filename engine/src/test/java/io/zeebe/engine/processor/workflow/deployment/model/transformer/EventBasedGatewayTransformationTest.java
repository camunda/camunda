/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
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
