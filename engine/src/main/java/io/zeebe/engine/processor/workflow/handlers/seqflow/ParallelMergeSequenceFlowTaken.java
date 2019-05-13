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
package io.zeebe.engine.processor.workflow.handlers.seqflow;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.EventOutput;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableSequenceFlow;
import io.zeebe.engine.processor.workflow.handlers.AbstractHandler;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.instance.IndexedRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.ArrayList;
import java.util.List;

public class ParallelMergeSequenceFlowTaken<T extends ExecutableSequenceFlow>
    extends AbstractHandler<T> {
  public ParallelMergeSequenceFlowTaken() {
    super(null);
  }

  public ParallelMergeSequenceFlowTaken(WorkflowInstanceIntent nextState) {
    super(nextState);
  }

  @Override
  protected boolean shouldHandleState(BpmnStepContext<T> context) {
    return super.shouldHandleState(context) && isElementActive(context.getFlowScopeInstance());
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    final ElementInstance scopeInstance = context.getFlowScopeInstance();
    final EventOutput eventOutput = context.getOutput();
    final ExecutableSequenceFlow sequenceFlow = context.getElement();
    final ExecutableFlowNode gateway = sequenceFlow.getTarget();

    eventOutput.deferEvent(context.getRecord());

    final List<IndexedRecord> mergeableRecords =
        getMergeableRecords(context, gateway, scopeInstance);
    if (mergeableRecords.size() == gateway.getIncoming().size()) {

      // consume all deferred tokens and spawn a new one to continue after the gateway
      mergeableRecords.forEach(
          r -> {
            eventOutput.removeDeferredEvent(scopeInstance.getKey(), r.getKey());
            scopeInstance.consumeToken();
          });

      context
          .getOutput()
          .appendNewEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATING, context.getValue(), gateway);
      scopeInstance.spawnToken();
      context.getStateDb().getElementInstanceState().updateInstance(scopeInstance);
    }

    return true;
  }

  /** @return the records that can be merged */
  private List<IndexedRecord> getMergeableRecords(
      BpmnStepContext<T> context,
      ExecutableFlowNode parallelGateway,
      ElementInstance scopeInstance) {
    final List<ExecutableSequenceFlow> incomingFlows = parallelGateway.getIncoming();
    final List<IndexedRecord> mergingRecords = new ArrayList<>(incomingFlows.size());

    final List<IndexedRecord> storedRecords =
        context.getElementInstanceState().getDeferredRecords(scopeInstance.getKey());

    for (final ExecutableSequenceFlow flow : incomingFlows) {
      for (final IndexedRecord recordToMatch : storedRecords) {
        if (recordToMatch.getValue().getElementId().equals(flow.getId())) {
          mergingRecords.add(recordToMatch);
          break;
        }
      }
    }

    return mergingRecords;
  }
}
