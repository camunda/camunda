/*
 * Zeebe Broker Core
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
package io.zeebe.broker.workflow.processor.flownode;

import io.zeebe.broker.workflow.model.element.ExecutableFlowNode;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeOutputBehavior;
import io.zeebe.msgpack.mapping.Mapping;
import io.zeebe.msgpack.mapping.MsgPackMergeTool;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import org.agrona.DirectBuffer;

public class IOMappingHelper {
  public <T extends ExecutableFlowNode> void applyOutputMappings(BpmnStepContext<T> context) {
    final T element = context.getElement();
    final MsgPackMergeTool mergeTool = context.getMergeTool();
    final WorkflowInstanceRecord record = context.getValue();
    final WorkflowInstanceRecord scope = context.getFlowScopeInstance().getValue();
    final ZeebeOutputBehavior outputBehavior = element.getOutputBehavior();

    DirectBuffer payload = scope.getPayload();
    mergeTool.reset();

    if (outputBehavior != ZeebeOutputBehavior.none) {
      if (element.getOutputBehavior() != ZeebeOutputBehavior.overwrite) {
        mergeTool.mergeDocument(scope.getPayload());
      }

      mergeTool.mergeDocumentStrictly(record.getPayload(), element.getOutputMappings());
      payload = mergeTool.writeResultToBuffer();
      scope.setPayload(payload);
    }

    record.setPayload(payload);
  }

  public <T extends ExecutableFlowNode> void applyInputMappings(BpmnStepContext<T> context) {
    final WorkflowInstanceRecord record = context.getValue();
    final MsgPackMergeTool mergeTool = context.getMergeTool();
    final T element = context.getElement();
    final Mapping[] mappings = element.getInputMappings();

    if (mappings.length > 0) {
      mergeTool.reset();
      mergeTool.mergeDocumentStrictly(record.getPayload(), element.getInputMappings());
      record.setPayload(mergeTool.writeResultToBuffer());
    }
  }
}
