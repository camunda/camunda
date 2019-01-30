package io.zeebe.broker.workflow.processor.handlers.element;

import io.zeebe.broker.workflow.model.element.ExecutableFlowNode;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.handlers.AbstractHandler;
import io.zeebe.broker.workflow.state.ElementInstance;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

/**
 * Once terminated, consumes its token, and if it is the last active element in its flow scope, will
 * terminate the flow scope. If it is the last active element but there is a deferred token, it will
 * instead trigger that deferred token. If it has no flow scope (e.g. the process), does nothing.
 *
 * @param <T>
 */
public class ElementTerminatedHandler<T extends ExecutableFlowNode> extends AbstractHandler<T> {
  public ElementTerminatedHandler() {
    this(null);
  }

  public ElementTerminatedHandler(WorkflowInstanceIntent nextState) {
    super(nextState);
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    if (!handleTerminated(context)) {
      return false;
    }

    final ElementInstance flowScopeInstance = context.getFlowScopeInstance();

    // if this is a root element (e.g. the workflow instance), then there is nothing to be done
    if (flowScopeInstance == null) {
      return true;
    }

    flowScopeInstance.consumeToken();
    final int activeExecutionPaths = flowScopeInstance.getNumberOfActiveExecutionPaths();

    if (activeExecutionPaths > 0) {
      context.getCatchEventBehavior().triggerDeferredEvent(context);
    } else if (activeExecutionPaths == 0) {
      terminateFlowScope(context, flowScopeInstance);
    } else {
      throw new IllegalStateException("number of active execution paths is negative");
    }

    return true;
  }

  protected boolean handleTerminated(BpmnStepContext<T> context) {
    return true;
  }

  private void terminateFlowScope(BpmnStepContext<T> context, ElementInstance flowScopeInstance) {
    context
        .getOutput()
        .appendFollowUpEvent(
            flowScopeInstance.getKey(),
            WorkflowInstanceIntent.ELEMENT_TERMINATED,
            flowScopeInstance.getValue());
  }
}
