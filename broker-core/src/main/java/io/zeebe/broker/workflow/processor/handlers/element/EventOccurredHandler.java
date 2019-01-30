package io.zeebe.broker.workflow.processor.handlers.element;

import io.zeebe.broker.workflow.model.element.ExecutableFlowNode;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.handlers.AbstractHandler;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

/**
 * As what to do with an event is highly dependent on the element type, the base event handler does
 * nothing.
 *
 * @param <T>
 */
public class EventOccurredHandler<T extends ExecutableFlowNode> extends AbstractHandler<T> {
  public EventOccurredHandler() {
    this(null);
  }

  public EventOccurredHandler(WorkflowInstanceIntent nextState) {
    super(nextState);
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    return true;
  }
}
