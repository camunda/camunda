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

import io.zeebe.engine.Loggers;
import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.BpmnStepHandler;
import io.zeebe.engine.processor.workflow.WorkflowInstanceLifecycle;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElement;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public abstract class AbstractHandler<T extends ExecutableFlowElement>
    implements BpmnStepHandler<T> {
  private final WorkflowInstanceIntent nextState;

  /**
   * @param nextState the next state in the lifecycle; if one is given, will immediately move on to
   *     this state if successfully handled, otherwise will not. Asynchronous handlers should thus
   *     pass null here.
   */
  public AbstractHandler(WorkflowInstanceIntent nextState) {
    this.nextState = nextState;
  }

  /**
   * Delegates handling the state logic to subclasses, and moves to the next state iff there is a
   * next state to move to and it is a synchronous handler.
   *
   * @param context current step context
   */
  @Override
  public void handle(BpmnStepContext<T> context) {
    if (shouldHandleState(context)) {
      final boolean handled = handleState(context);

      if (handled && shouldTransition()) {
        transitionToNext(context);
      }
    } else {
      Loggers.WORKFLOW_PROCESSOR_LOGGER.debug(
          "Skipping record {} due to step guard; in-memory element is {}",
          context.getRecord(),
          context.getElementInstance());
    }
  }

  /**
   * To be overridden by subclasses.
   *
   * @param context current step context
   * @return true if ready successful, false otherwise
   */
  protected abstract boolean handleState(BpmnStepContext<T> context);

  protected boolean shouldHandleState(BpmnStepContext<T> context) {
    return true;
  }

  protected boolean isRootScope(BpmnStepContext<T> context) {
    return context.getRecord().getValue().getFlowScopeKey() == -1;
  }

  /**
   * Returns true if the current record intent is the same as the element's current state. This is
   * primarily to ensure we're not processing concurrent state transitions (e.g. writing
   * ELEMENT_ACTIVATING and ELEMENT_ACTIVATED in the same step will transition the element to
   * ACTIVATED, and we shouldn't process the ELEMENT_ACTIVATING in that case).
   */
  protected boolean isStateSameAsElementState(BpmnStepContext<T> context) {
    return context.getElementInstance() != null
        && context.getState() == context.getElementInstance().getState();
  }

  protected boolean isElementActive(ElementInstance instance) {
    return instance != null && instance.isActive();
  }

  protected boolean isElementTerminating(ElementInstance instance) {
    return instance != null && instance.isTerminating();
  }

  protected boolean shouldTransition() {
    return nextState != null;
  }

  protected void transitionToNext(BpmnStepContext<T> context) {
    this.transitionTo(context, nextState);
  }

  protected void transitionTo(BpmnStepContext<T> context, WorkflowInstanceIntent nextState) {
    final ElementInstance elementInstance = context.getElementInstance();
    final WorkflowInstanceIntent state = elementInstance.getState();

    assert WorkflowInstanceLifecycle.canTransition(state, nextState)
        : String.format("cannot transition from '%s' to '%s'", state, nextState);

    elementInstance.setState(nextState);
    context
        .getOutput()
        .appendFollowUpEvent(context.getRecord().getKey(), nextState, context.getValue());

    // todo: this is an ugly workaround which should be removed once we have a better workflow
    // instance state abstraction: essentially, whenever transitioning to a terminating state, we
    // want to reject any potential event triggers
    // https://github.com/zeebe-io/zeebe/issues/1980
    if (nextState == WorkflowInstanceIntent.ELEMENT_COMPLETING
        || nextState == WorkflowInstanceIntent.ELEMENT_TERMINATING) {
      context
          .getStateDb()
          .getEventScopeInstanceState()
          .shutdownInstance(context.getRecord().getKey());
    }
    context.getElementInstanceState().updateInstance(elementInstance);
  }
}
