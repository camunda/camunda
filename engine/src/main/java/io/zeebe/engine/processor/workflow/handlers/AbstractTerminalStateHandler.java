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
package io.zeebe.engine.processor.workflow.handlers;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElement;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.instance.IndexedRecord;
import java.util.List;

/**
 * Primarily to clean up once we reach final state.
 *
 * @param <T>
 */
public class AbstractTerminalStateHandler<T extends ExecutableFlowElement>
    extends AbstractHandler<T> {
  public AbstractTerminalStateHandler() {
    super(null);
  }

  @Override
  public void handle(BpmnStepContext<T> context) {
    super.handle(context);

    // currently we always cleanup whether or not the state was successfully handled, which is fine
    // as by convention we shouldn't perform anything in terminal state which might fail
    context.getStateDb().getEventScopeInstanceState().deleteInstance(context.getRecord().getKey());
    context.getElementInstanceState().removeInstance(context.getRecord().getKey());
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    final ElementInstance flowScopeInstance = context.getFlowScopeInstance();
    if (flowScopeInstance != null) {
      flowScopeInstance.consumeToken();
      context.getStateDb().getElementInstanceState().updateInstance(flowScopeInstance);
    }

    return true;
  }

  protected void publishDeferredRecords(BpmnStepContext<T> context) {
    final List<IndexedRecord> deferredRecords =
        context.getElementInstanceState().getDeferredRecords(context.getRecord().getKey());
    final ElementInstance flowScopeInstance = context.getFlowScopeInstance();

    for (final IndexedRecord record : deferredRecords) {
      record.getValue().setFlowScopeKey(flowScopeInstance.getKey());
      context
          .getOutput()
          .appendFollowUpEvent(record.getKey(), record.getState(), record.getValue());
      flowScopeInstance.spawnToken();
    }
    context.getStateDb().getElementInstanceState().updateInstance(flowScopeInstance);
  }

  protected boolean isLastActiveExecutionPathInScope(BpmnStepContext<T> context) {
    final ElementInstance flowScopeInstance = context.getFlowScopeInstance();

    if (flowScopeInstance == null) {
      return false;
    }

    final int activePaths = flowScopeInstance.getNumberOfActiveTokens();
    assert activePaths >= 0 : "number of active paths should never be negative";

    return activePaths == 1;
  }
}
