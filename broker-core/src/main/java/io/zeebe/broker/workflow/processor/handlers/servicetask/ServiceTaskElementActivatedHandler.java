package io.zeebe.broker.workflow.processor.handlers.servicetask;

import io.zeebe.broker.workflow.model.element.ExecutableServiceTask;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.handlers.activity.ActivityElementActivatedHandler;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import org.agrona.DirectBuffer;

public class ServiceTaskElementActivatedHandler<T extends ExecutableServiceTask>
    extends ActivityElementActivatedHandler<T> {

  public ServiceTaskElementActivatedHandler() {
    this(null);
  }

  public ServiceTaskElementActivatedHandler(WorkflowInstanceIntent nextState) {
    super(nextState);
  }

  private final JobRecord jobCommand = new JobRecord();

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    if (!super.handleState(context)) {
      final WorkflowInstanceRecord value = context.getValue();
      final ExecutableServiceTask serviceTask = context.getElement();

      populateJobFromTask(context, value, serviceTask);
      context.getCommandWriter().appendNewCommand(JobIntent.CREATE, jobCommand);

      return true;
    }

    return false;
  }

  private void populateJobFromTask(
      BpmnStepContext<T> context, WorkflowInstanceRecord value, ExecutableServiceTask serviceTask) {
    final DirectBuffer headers = serviceTask.getEncodedHeaders();

    jobCommand.reset();
    jobCommand
        .setType(serviceTask.getType())
        .setRetries(serviceTask.getRetries())
        .setPayload(value.getPayload())
        .setCustomHeaders(headers)
        .getHeaders()
        .setBpmnProcessId(value.getBpmnProcessId())
        .setWorkflowDefinitionVersion(value.getVersion())
        .setWorkflowKey(value.getWorkflowKey())
        .setWorkflowInstanceKey(value.getWorkflowInstanceKey())
        .setElementId(serviceTask.getId())
        .setElementInstanceKey(context.getRecord().getKey());
  }
}
