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
package io.zeebe.broker.workflow.processor.activity;

import io.zeebe.broker.incident.data.ErrorType;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.workflow.model.element.ExecutableFlowNode;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.BpmnStepHandler;
import io.zeebe.broker.workflow.state.ElementInstance;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeOutputBehavior;
import io.zeebe.msgpack.mapping.Mapping;
import io.zeebe.msgpack.mapping.MappingException;
import io.zeebe.msgpack.mapping.MsgPackMergeTool;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import org.agrona.DirectBuffer;

public class OutputMappingHandler implements BpmnStepHandler<ExecutableFlowNode> {

  @Override
  public void handle(BpmnStepContext<ExecutableFlowNode> context) {

    final MsgPackMergeTool payloadMergeTool = context.getMergeTool();
    final TypedRecord<WorkflowInstanceRecord> record = context.getRecord();
    final WorkflowInstanceRecord activityEvent = record.getValue();
    final ExecutableFlowNode element = context.getElement();

    final ElementInstance flowScopeInstance = context.getFlowScopeInstance();

    final DirectBuffer scopeInstancePayload = flowScopeInstance.getValue().getPayload();

    final ZeebeOutputBehavior outputBehavior = element.getOutputBehavior();

    MappingException mappingException = null;

    if (outputBehavior == ZeebeOutputBehavior.none) {
      activityEvent.setPayload(scopeInstancePayload);
    } else {
      payloadMergeTool.reset();
      if (outputBehavior != ZeebeOutputBehavior.overwrite) {
        payloadMergeTool.mergeDocument(scopeInstancePayload);
      }

      final Mapping[] outputMappings = element.getOutputMappings();
      final DirectBuffer jobPayload = activityEvent.getPayload();

      try {
        payloadMergeTool.mergeDocumentStrictly(jobPayload, outputMappings);
        final DirectBuffer result = payloadMergeTool.writeResultToBuffer();

        activityEvent.setPayload(result);
        flowScopeInstance.getValue().setPayload(result);
      } catch (MappingException e) {
        mappingException = e;
      }
    }

    if (mappingException == null) {
      context
          .getOutput()
          .writeFollowUpEvent(
              record.getKey(), WorkflowInstanceIntent.ELEMENT_COMPLETED, activityEvent);
    } else {
      context.raiseIncident(ErrorType.IO_MAPPING_ERROR, mappingException.getMessage());
    }
  }
}
