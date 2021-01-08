package io.zeebe.engine.processing.bpmn;

import io.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;

public interface NewBpmnElementProcessor<T extends ExecutableFlowElement>
    extends BpmnElementProcessor<T> {

  void onActivate(final T element, final BpmnElementContext context);
}
