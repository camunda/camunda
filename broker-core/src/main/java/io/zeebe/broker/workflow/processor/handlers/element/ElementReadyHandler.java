package io.zeebe.broker.workflow.processor.handlers.element;

import io.zeebe.broker.workflow.model.element.ExecutableFlowNode;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.flownode.IOMappingHelper;
import io.zeebe.broker.workflow.processor.handlers.AbstractHandler;
import io.zeebe.msgpack.mapping.MappingException;
import io.zeebe.protocol.impl.record.value.incident.ErrorType;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

/**
 * Applies input mappings in the scope.
 *
 * @param <T>
 */
public class ElementReadyHandler<T extends ExecutableFlowNode> extends AbstractHandler<T> {
  private final IOMappingHelper ioMappingHelper;

  public ElementReadyHandler() {
    this(WorkflowInstanceIntent.ELEMENT_ACTIVATED);
  }

  public ElementReadyHandler(WorkflowInstanceIntent nextState) {
    this(nextState, new IOMappingHelper());
  }

  public ElementReadyHandler(WorkflowInstanceIntent nextState, IOMappingHelper ioMappingHelper) {
    super(nextState);
    this.ioMappingHelper = ioMappingHelper;
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    try {
      ioMappingHelper.applyInputMappings(context);
      return true;
    } catch (MappingException e) {
      context.raiseIncident(ErrorType.IO_MAPPING_ERROR, e.getMessage());
    }

    return false;
  }
}
