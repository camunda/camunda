package io.zeebe.broker.workflow.processor.handlers.activity;

import io.zeebe.broker.workflow.model.element.ExecutableActivity;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.handlers.element.EventOccurredHandler;
import io.zeebe.broker.workflow.state.IndexedRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.util.List;

public class ActivityEventOccurredHandler<T extends ExecutableActivity>
    extends EventOccurredHandler<T> {

  public ActivityEventOccurredHandler() {
    this(null);
  }

  public ActivityEventOccurredHandler(WorkflowInstanceIntent nextState) {
    super(nextState);
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    if (super.handleState(context)) {
      final List<IndexedRecord> events =
          context.getElementInstanceState().getDeferredRecords(context.getRecord().getKey());
      final WorkflowInstanceRecord record;

      for (final IndexedRecord event : events) {
        if (BufferUtil.contentsEqual(event.getValue().getElementId(), )
      }

      if (isInterrupting(record)) {
        moveToState(context, WorkflowInstanceIntent.ELEMENT_TERMINATING);
      }
    }

    return false;
  }
}
