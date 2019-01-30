package io.zeebe.broker.workflow.processor.handlers.activity;

import io.zeebe.broker.workflow.model.element.ExecutableActivity;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.CatchEventBehavior.MessageCorrelationKeyException;
import io.zeebe.broker.workflow.processor.flownode.IOMappingHelper;
import io.zeebe.broker.workflow.processor.handlers.element.ElementReadyHandler;
import io.zeebe.protocol.impl.record.value.incident.ErrorType;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class ActivityElementReadyHandler<T extends ExecutableActivity>
    extends ElementReadyHandler<T> {
  public ActivityElementReadyHandler() {}

  public ActivityElementReadyHandler(WorkflowInstanceIntent nextState) {
    super(nextState);
  }

  public ActivityElementReadyHandler(
      WorkflowInstanceIntent nextState, IOMappingHelper ioMappingHelper) {
    super(nextState, ioMappingHelper);
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    if (super.handleState(context)) {
      try {
        context.getCatchEventBehavior().subscribeToEvents(context, context.getElement());
        return true;
      } catch (MessageCorrelationKeyException e) {
        context.raiseIncident(ErrorType.EXTRACT_VALUE_ERROR, e.getMessage());
      }
    }

    return false;
  }
}
