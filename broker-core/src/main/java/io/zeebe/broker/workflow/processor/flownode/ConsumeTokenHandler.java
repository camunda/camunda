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

import io.zeebe.broker.workflow.model.element.ExecutableEndEvent;
import io.zeebe.broker.workflow.model.element.ExecutableFlowElement;
import io.zeebe.broker.workflow.model.element.ExecutableFlowNode;
import io.zeebe.broker.workflow.model.element.ExecutableWorkflow;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.BpmnStepHandler;
import io.zeebe.broker.workflow.processor.EventOutput;
import io.zeebe.broker.workflow.state.ElementInstance;
import io.zeebe.broker.workflow.state.IndexedRecord;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.msgpack.mapping.Mapping;
import io.zeebe.msgpack.mapping.MsgPackMergeTool;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.List;
import org.agrona.DirectBuffer;

public class ConsumeTokenHandler implements BpmnStepHandler<ExecutableFlowNode> {

  private final WorkflowState workflowState;

  public ConsumeTokenHandler(WorkflowState workflowState) {
    this.workflowState = workflowState;
  }

  @Override
  public void handle(BpmnStepContext<ExecutableFlowNode> context) {
    final WorkflowInstanceRecord value = context.getValue();

    final long scopeInstanceKey = value.getScopeInstanceKey();
    final ElementInstance scopeInstance = context.getFlowScopeInstance();
    final WorkflowInstanceRecord scopeInstanceValue = scopeInstance.getValue();

    final EventOutput output = context.getOutput();
    output.storeFinishedToken(context.getRecord());

    if (scopeInstance.getNumberOfActiveExecutionPaths() == 0) {
      final MsgPackMergeTool payloadMergeTool = context.getMergeTool();
      final List<IndexedRecord> finishedTokens =
          workflowState.getElementInstanceState().getFinishedTokens(scopeInstanceKey);

      final DirectBuffer mergedPayload =
          mergePayloads(payloadMergeTool, finishedTokens, context.getWorkflow());
      scopeInstanceValue.setPayload(mergedPayload);

      output.writeFollowUpEvent(
          scopeInstanceKey, WorkflowInstanceIntent.ELEMENT_COMPLETING, scopeInstanceValue);
    }
  }

  private DirectBuffer mergePayloads(
      final MsgPackMergeTool payloadMergeTool,
      final List<IndexedRecord> finishedTokens,
      ExecutableWorkflow workflow) {

    payloadMergeTool.reset();

    for (IndexedRecord record : finishedTokens) {
      payloadMergeTool.mergeDocument(record.getValue().getPayload());
    }

    for (IndexedRecord record : finishedTokens) {
      final WorkflowInstanceRecord mergingValue = record.getValue();
      final ExecutableFlowElement element = workflow.getElementById(mergingValue.getActivityId());
      if (element instanceof ExecutableEndEvent) {
        final Mapping[] mappings = ((ExecutableEndEvent) element).getPayloadMappings();

        if (mappings.length > 0) {
          payloadMergeTool.mergeDocument(mergingValue.getPayload(), mappings);
        }
      }
    }

    return payloadMergeTool.writeResultToBuffer();
  }
}
