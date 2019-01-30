package io.zeebe.broker.workflow.processor.handlers.element;

import io.zeebe.broker.workflow.model.element.ExecutableFlowNode;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.flownode.IOMappingHelper;
import io.zeebe.broker.workflow.processor.handlers.AbstractHandler;
import io.zeebe.msgpack.mapping.MappingException;
import io.zeebe.protocol.impl.record.value.incident.ErrorType;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

/**
 * Applies output mappings to the scope.
 *
 * @param <T>
 */
public class ElementCompletingHandler<T extends ExecutableFlowNode> extends AbstractHandler<T> {
  private final IOMappingHelper ioMappingHelper;

  public ElementCompletingHandler() {
    this(new IOMappingHelper());
  }

  public ElementCompletingHandler(IOMappingHelper ioMappingHelper) {
    this(WorkflowInstanceIntent.ELEMENT_COMPLETED, ioMappingHelper);
  }

  public ElementCompletingHandler(
      WorkflowInstanceIntent nextState, IOMappingHelper ioMappingHelper) {
    super(nextState);
    this.ioMappingHelper = ioMappingHelper;
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    try {
      ioMappingHelper.applyOutputMappings(context);
      return true;
    } catch (MappingException e) {
      context.raiseIncident(ErrorType.IO_MAPPING_ERROR, e.getMessage());
    }

    return false;
  }
}
