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
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableExclusiveGateway;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.BpmnTransformer;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
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
