package io.zeebe.broker.workflow.processor.handlers.activity;

import io.zeebe.broker.workflow.model.element.ExecutableActivity;
import io.zeebe.broker.workflow.processor.handlers.element.ElementActivatedHandler;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

/**
 * Base activity activated does nothing, should be subclass per activity type.
 *
 * @param <T>
 */
public class ActivityElementActivatedHandler<T extends ExecutableActivity>
    extends ElementActivatedHandler<T> {

  public ActivityElementActivatedHandler(WorkflowInstanceIntent nextState) {
    super(nextState);
  }
}
