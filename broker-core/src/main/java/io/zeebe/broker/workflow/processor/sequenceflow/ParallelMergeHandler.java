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
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.ArrayList;
import java.util.List;

public class ParallelMergeHandler implements BpmnStepHandler<ExecutableSequenceFlow> {

  private final WorkflowState workflowState;

  public ParallelMergeHandler(WorkflowState workflowState) {
    this.workflowState = workflowState;
  }

  @Override
  public void handle(BpmnStepContext<ExecutableSequenceFlow> context) {
    final ElementInstance scopeInstance = context.getFlowScopeInstance();
    final EventOutput eventOutput = context.getOutput();
    final ExecutableSequenceFlow sequenceFlow = context.getElement();
    final ExecutableFlowNode gateway = sequenceFlow.getTarget();

    eventOutput.deferEvent(context.getRecord());

    final List<IndexedRecord> mergeableRecords = getMergeableRecords(gateway, scopeInstance);
    if (mergeableRecords.size() == gateway.getIncoming().size()) {

      // consume all deferred tokens and spawn a new one to continue after the gateway
      mergeableRecords.forEach(
          r -> {
            eventOutput.removeDeferredEvent(scopeInstance.getKey(), r.getKey());
            context.getFlowScopeInstance().consumeToken();
          });

      final WorkflowInstanceRecord value = context.getValue();
      value.setElementId(gateway.getId());
      context.getOutput().appendNewEvent(WorkflowInstanceIntent.GATEWAY_ACTIVATED, value);

      context.getFlowScopeInstance().spawnToken();
    }
  }

  /** @return the records that can be merged */
  private List<IndexedRecord> getMergeableRecords(
      ExecutableFlowNode parallelGateway, ElementInstance scopeInstance) {
    final List<ExecutableSequenceFlow> incomingFlows = parallelGateway.getIncoming();
    final List<IndexedRecord> mergingRecords = new ArrayList<>(incomingFlows.size());

    final List<IndexedRecord> storedRecords =
        workflowState.getElementInstanceState().getDeferredRecords(scopeInstance.getKey());

    for (int i = 0; i < incomingFlows.size(); i++) {
      final ExecutableSequenceFlow flow = incomingFlows.get(i);

      for (int j = 0; j < storedRecords.size(); j++) {
        final IndexedRecord recordToMatch = storedRecords.get(j);

        if (recordToMatch.getValue().getElementId().equals(flow.getId())) {
          mergingRecords.add(recordToMatch);
          break;
        }
      }
    }

    return mergingRecords;
  }
}
