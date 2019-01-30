package io.zeebe.broker.workflow.processor.handlers;

import io.zeebe.broker.workflow.model.element.ExecutableFlowElement;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.BpmnStepHandler;
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
    if (handleState(context) && shouldMoveToNextState()) {
      moveToNextState(context);
    }
  }

  /**
   * To be overridden by subclasses.
   *
   * @param context current step context
   * @return true if ready successful, false otherwise
   */
  protected abstract boolean handleState(BpmnStepContext<T> context);

  protected boolean shouldMoveToNextState() {
    return nextState != null;
  }

  protected void moveToNextState(BpmnStepContext<T> context) {
    this.moveToState(context, nextState);
  }

  protected void moveToState(BpmnStepContext<T> context, WorkflowInstanceIntent nextState) {
    context
        .getOutput()
        .appendFollowUpEvent(context.getRecord().getKey(), nextState, context.getValue());
  }
}
