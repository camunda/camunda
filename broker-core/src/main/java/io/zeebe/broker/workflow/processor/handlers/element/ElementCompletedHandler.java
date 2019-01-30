package io.zeebe.broker.workflow.processor.handlers.element;

import io.zeebe.broker.workflow.model.element.ExecutableFlowNode;
import io.zeebe.broker.workflow.model.element.ExecutableSequenceFlow;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.handlers.AbstractHandler;
import io.zeebe.broker.workflow.state.ElementInstance;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.List;

/**
 * Delegates completion logic to sub class, and if successful, will either take all outgoing
 * sequence flows, or if there are none, and it is the last active element in its flow scope, will
 * terminate its flow scope.
 *
 * @param <T>
 */
public class ElementCompletedHandler<T extends ExecutableFlowNode> extends AbstractHandler<T> {
  public ElementCompletedHandler() {
    this(null);
  }

  public ElementCompletedHandler(WorkflowInstanceIntent nextState) {
    super(nextState);
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    if (!handleCompleted(context)) {
      return false;
    }

    final List<ExecutableSequenceFlow> outgoing = context.getElement().getOutgoing();

    if (!outgoing.isEmpty()) {
      takeSequenceFlows(context, outgoing);
    } else {
      completeFlowScope(context);
    }
    return true;
  }

  /**
   * Must be implemented by subclass.
   *
   * @param context current step context
   * @return true if handled successfully, false otherwise
   */
  protected boolean handleCompleted(BpmnStepContext<T> context) {
    return true;
  }

  private void completeFlowScope(BpmnStepContext<T> context) {
    final ElementInstance flowScopeInstance = context.getFlowScopeInstance();
    flowScopeInstance.consumeToken();

    if (flowScopeInstance.getNumberOfActiveExecutionPaths() == 0) {
      final WorkflowInstanceRecord flowScopeInstanceValue = flowScopeInstance.getValue();
      flowScopeInstanceValue.setPayload(context.getValue().getPayload());

      context
          .getOutput()
          .appendFollowUpEvent(
              flowScopeInstance.getKey(),
              WorkflowInstanceIntent.ELEMENT_COMPLETING,
              flowScopeInstanceValue);
    } else if (flowScopeInstance.getNumberOfActiveExecutionPaths() < 0) {
      throw new IllegalStateException("number of active execution paths is negative");
    }
  }

  private void takeSequenceFlows(
      BpmnStepContext<T> context, List<ExecutableSequenceFlow> outgoing) {
    context.getFlowScopeInstance().consumeToken();
    for (final ExecutableSequenceFlow flow : outgoing) {
      takeSequenceFlow(context, flow);
      context.getFlowScopeInstance().spawnToken();
    }
  }

  private void takeSequenceFlow(BpmnStepContext<T> context, ExecutableSequenceFlow flow) {
    context
        .getOutput()
        .appendNewEvent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, context.getValue(), flow);
  }
}
