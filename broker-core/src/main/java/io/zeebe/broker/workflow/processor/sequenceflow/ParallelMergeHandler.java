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
package io.zeebe.broker.workflow.processor.sequenceflow;

import io.zeebe.broker.workflow.model.element.ExecutableFlowNode;
import io.zeebe.broker.workflow.model.element.ExecutableSequenceFlow;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;

public class ParallelMergeHandler implements BpmnStepHandler<ExecutableSequenceFlow> {

  private final WorkflowState workflowState;

  public ParallelMergeHandler(WorkflowState workflowState) {
    this.workflowState = workflowState;
  }

  @Override
  public void handle(BpmnStepContext<ExecutableSequenceFlow> context) {

    final EventOutput eventOutput = context.getOutput();

    final ElementInstance scopeInstance = context.getFlowScopeInstance();
    eventOutput.deferEvent(context.getRecord());

    final ExecutableSequenceFlow sequenceFlow = context.getElement();
    final ExecutableFlowNode gateway = sequenceFlow.getTarget();

    final Map<ExecutableSequenceFlow, IndexedRecord> mergeableRecords =
        getMergeableRecords(gateway, scopeInstance);

    if (mergeableRecords.size() == gateway.getIncoming().size()) {

      final DirectBuffer propagatedPayload =
          mergePayloads(context.getMergeTool(), mergeableRecords);

      mergeableRecords
          .values()
          .forEach(r -> eventOutput.consumeDeferredEvent(scopeInstance.getKey(), r.getKey()));

      final WorkflowInstanceRecord value = context.getValue();
      value.setActivityId(gateway.getId());
      value.setPayload(propagatedPayload);
      context.getOutput().writeNewEvent(WorkflowInstanceIntent.GATEWAY_ACTIVATED, value);
    }
  }

  private DirectBuffer mergePayloads(
      MsgPackMergeTool mergeTool, Map<ExecutableSequenceFlow, IndexedRecord> records) {
    // default merge
    for (IndexedRecord record : records.values()) {
      mergeTool.mergeDocument(record.getValue().getPayload());
    }

    // apply mappings
    for (Map.Entry<ExecutableSequenceFlow, IndexedRecord> entry : records.entrySet()) {
      final Mapping[] mappings = entry.getKey().getPayloadMappings();
      final DirectBuffer payload = entry.getValue().getValue().getPayload();

      // don't merge the document a second time
      if (mappings.length > 0) {
        mergeTool.mergeDocument(payload, mappings);
      }
    }

    return mergeTool.writeResultToBuffer();
  }

  /** @return the records that can be merged */
  private Map<ExecutableSequenceFlow, IndexedRecord> getMergeableRecords(
      ExecutableFlowNode parallelGateway, ElementInstance scopeInstance) {

    final List<ExecutableSequenceFlow> incomingFlows = parallelGateway.getIncoming();
    final Map<ExecutableSequenceFlow, IndexedRecord> mergingRecords = new HashMap<>();

    final List<IndexedRecord> deferredTokens =
        workflowState.getElementInstanceState().getDeferredTokens(scopeInstance.getKey());

    for (int i = 0; i < incomingFlows.size(); i++) {
      final ExecutableSequenceFlow flow = incomingFlows.get(i);

      for (int j = 0; j < deferredTokens.size(); j++) {
        final IndexedRecord recordToMatch = deferredTokens.get(j);

        if (recordToMatch.getValue().getActivityId().equals(flow.getId())) {
          mergingRecords.put(flow, recordToMatch);
          break;
        }
      }
    }

    return mergingRecords;
  }
}
