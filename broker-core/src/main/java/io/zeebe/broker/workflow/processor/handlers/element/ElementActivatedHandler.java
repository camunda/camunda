package io.zeebe.broker.workflow.processor.handlers.element;

import io.zeebe.broker.workflow.model.element.ExecutableFlowNode;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.handlers.AbstractHandler;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

/**
 * Represents the "business logic" phase the element, so the base handler does nothing.
 *
 * @param <T>
 */
public class ElementActivatedHandler<T extends ExecutableFlowNode> extends AbstractHandler<T> {
  public ElementActivatedHandler() {
    this(WorkflowInstanceIntent.ELEMENT_COMPLETING);
  }

  public ElementActivatedHandler(WorkflowInstanceIntent nextState) {
    super(nextState);
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    return true;
  }
}
