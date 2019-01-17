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
import io.zeebe.broker.workflow.model.element.ExecutableServiceTask;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeOutputBehavior;
import io.zeebe.msgpack.mapping.Mapping;
import io.zeebe.msgpack.mapping.MsgPackMergeTool;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public class IOMappingHelper {

  public <T extends ExecutableFlowNode> void applyOutputMappings(BpmnStepContext<T> context) {

    final T element = context.getElement();
    final MsgPackMergeTool mergeTool = context.getMergeTool();
    final WorkflowInstanceRecord record = context.getValue();
    final ZeebeOutputBehavior outputBehavior = element.getOutputBehavior();

    mergeTool.reset();

    // (saig0): we need copy the buffer so that it is not overridden by the second variable request.
    // This can be remove when #1852 is implemented.
    final DirectBuffer scopePayload =
        BufferUtil.cloneBuffer(
            context
                .getElementInstanceState()
                .getVariablesState()
                .getVariablesAsDocument(record.getScopeInstanceKey()));

    final DirectBuffer instancePayload;

    if (element instanceof ExecutableServiceTask && element.getOutputMappings().length > 0) {
      // (saig0): if the activity has no output mappings then the payload is already merged. We
      // can't use the local variables in this case for the record payload because it may contain
      // variables from input mapping. This will go away together with the record payload.

      instancePayload =
          context
              .getElementInstanceState()
              .getVariablesState()
              .getVariablesLocalAsDocument(context.getRecord().getKey());
    } else {
      // TODO (saig0) #1614: should use also the variables from the state when the message payload
      // is set as local variables
      instancePayload = record.getPayload();
    }

    final DirectBuffer propagatedPayload;
    if (outputBehavior != ZeebeOutputBehavior.none) {
      if (element.getOutputBehavior() != ZeebeOutputBehavior.overwrite) {
        mergeTool.mergeDocument(scopePayload);
      }

      mergeTool.mergeDocumentStrictly(instancePayload, element.getOutputMappings());
      propagatedPayload = mergeTool.writeResultToBuffer();

    } else {
      propagatedPayload = scopePayload;
    }

    context
        .getElementInstanceState()
        .getVariablesState()
        .setVariablesFromDocument(record.getScopeInstanceKey(), propagatedPayload);

    record.setPayload(propagatedPayload);
  }

  public <T extends ExecutableFlowNode> void applyInputMappings(BpmnStepContext<T> context) {

    final WorkflowInstanceRecord value = context.getValue();
    final MsgPackMergeTool mergeTool = context.getMergeTool();
    final T element = context.getElement();
    final Mapping[] mappings = element.getInputMappings();

    if (mappings.length > 0) {
      mergeTool.reset();

      // TODO (saig0) #1612: we should use the variables from the state instead of the record
      // payload
      mergeTool.mergeDocumentStrictly(value.getPayload(), element.getInputMappings());

      final DirectBuffer mappedPayload = mergeTool.writeResultToBuffer();
      context.getValue().setPayload(mappedPayload);

      context
          .getElementInstanceState()
          .getVariablesState()
          .setVariablesLocalFromDocument(context.getRecord().getKey(), mappedPayload);
    }
  }
}
