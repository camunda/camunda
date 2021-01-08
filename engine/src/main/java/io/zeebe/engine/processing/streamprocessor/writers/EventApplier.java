package io.zeebe.engine.processing.streamprocessor.writers;

import io.zeebe.engine.processing.bpmn.BpmnElementContextImpl;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

// todo move this class out of this package
public final class EventApplier {

  private final BpmnStateBehavior stateBehavior;

  public EventApplier(final BpmnStateBehavior stateBehavior) {
    this.stateBehavior = stateBehavior;
  }

  public void applyState(final long key, final Intent intent, final UnifiedRecordValue value) {
    if (value instanceof WorkflowInstanceRecord) {
      final var record = (WorkflowInstanceRecord) value;
      final var newState = (WorkflowInstanceIntent) intent;
      final var context = new BpmnElementContextImpl();
      context.init(key, record, newState);

      // todo this filter to determine what to do could be wrapped in a nicer mapping/interface
      if (newState == WorkflowInstanceIntent.ELEMENT_ACTIVATED) {
        stateBehavior.updateElementInstance(
            context, elementInstance -> elementInstance.setState(newState));
      }
    }
  }
}
