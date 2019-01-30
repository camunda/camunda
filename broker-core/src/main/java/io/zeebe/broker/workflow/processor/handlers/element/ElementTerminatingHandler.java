package io.zeebe.broker.workflow.processor.handlers.element;

import io.zeebe.broker.incident.processor.IncidentState;
import io.zeebe.broker.workflow.model.element.ExecutableFlowNode;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.handlers.AbstractHandler;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

/**
 * Resolves any incident that were associated with this element.
 *
 * @param <T>
 */
public class ElementTerminatingHandler<T extends ExecutableFlowNode> extends AbstractHandler<T> {
  protected final IncidentState incidentState;

  public ElementTerminatingHandler(IncidentState incidentState) {
    this(WorkflowInstanceIntent.ELEMENT_TERMINATED, incidentState);
  }

  public ElementTerminatingHandler(WorkflowInstanceIntent nextState, IncidentState incidentState) {
    super(nextState);
    this.incidentState = incidentState;
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    // todo: this is used to be executed after the child class' terminate logic; see if it matters
    // could perhaps be moved to the terminated phase
    incidentState.forExistingWorkflowIncident(
        context.getRecord().getKey(),
        (record, key) -> context.getOutput().appendResolvedIncidentEvent(key, record));

    return true;
  }
}
