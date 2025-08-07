/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.camunda.zeebe.model.bpmn.builder;

import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.model.bpmn.instance.SubProcess;
import io.camunda.zeebe.model.bpmn.instance.bpmndi.BpmnShape;
import io.camunda.zeebe.model.bpmn.instance.dc.Bounds;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeIoMapping;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeOutput;
import java.util.function.Consumer;

public class AbstractEventSubProcessBuilder<B extends AbstractEventSubProcessBuilder<B>>
    extends AbstractFlowElementBuilder<B, SubProcess>
    implements ZeebeVariablesMappingBuilder<B>,
        ZeebeExecutionListenersBuilder<B>,
        ZeebePropertiesBuilder<B> {

  private final ZeebeExecutionListenersBuilder<B> zeebeExecutionListenersBuilder;
  private final ZeebePropertiesBuilder<B> zeebePropertiesBuilder;

  protected AbstractEventSubProcessBuilder(
      final BpmnModelInstance modelInstance, final SubProcess element, final Class<?> selfType) {
    super(modelInstance, element, selfType);
    zeebeExecutionListenersBuilder = new ZeebeExecutionListenersBuilderImpl<>(myself);
    zeebePropertiesBuilder = new ZeebePropertiesBuilderImpl<>(myself);
  }

  public StartEventBuilder startEvent() {
    return startEvent(null);
  }

  public StartEventBuilder startEvent(final String id) {
    final StartEvent start = createChild(StartEvent.class, id);

    final BpmnShape startShape = createBpmnShape(start);
    final BpmnShape subProcessShape = findBpmnShape(getElement());

    if (subProcessShape != null) {
      final Bounds subProcessBounds = subProcessShape.getBounds();
      final Bounds startBounds = startShape.getBounds();

      final double subProcessX = subProcessBounds.getX();
      final double subProcessY = subProcessBounds.getY();
      final double subProcessHeight = subProcessBounds.getHeight();
      final double startHeight = startBounds.getHeight();

      startBounds.setX(subProcessX + SPACE);
      startBounds.setY(subProcessY + subProcessHeight / 2 - startHeight / 2);
    }

    return start.builder();
  }

  public StartEventBuilder startEvent(final String id, final Consumer<StartEventBuilder> consumer) {
    final StartEventBuilder builder = startEvent(id);
    consumer.accept(builder);
    return builder;
  }

  @Override
  public B zeebeInputExpression(final String sourceExpression, final String target) {
    final String expression = asZeebeExpression(sourceExpression);
    return zeebeInput(expression, target);
  }

  @Override
  public B zeebeOutputExpression(final String sourceExpression, final String target) {
    final String expression = asZeebeExpression(sourceExpression);
    return zeebeOutput(expression, target);
  }

  @Override
  public B zeebeInput(final String source, final String target) {
    final ZeebeIoMapping ioMapping = getCreateSingleExtensionElement(ZeebeIoMapping.class);
    final ZeebeInput input = createChild(ioMapping, ZeebeInput.class);
    input.setSource(source);
    input.setTarget(target);

    return myself;
  }

  @Override
  public B zeebeOutput(final String source, final String target) {
    final ZeebeIoMapping ioMapping = getCreateSingleExtensionElement(ZeebeIoMapping.class);
    final ZeebeOutput input = createChild(ioMapping, ZeebeOutput.class);
    input.setSource(source);
    input.setTarget(target);

    return myself;
  }

  @Override
  public B zeebeStartExecutionListener(final String type, final String retries) {
    return zeebeExecutionListenersBuilder.zeebeStartExecutionListener(type, retries);
  }

  @Override
  public B zeebeStartExecutionListener(final String type) {
    return zeebeExecutionListenersBuilder.zeebeStartExecutionListener(type);
  }

  @Override
  public B zeebeEndExecutionListener(final String type, final String retries) {
    return zeebeExecutionListenersBuilder.zeebeEndExecutionListener(type, retries);
  }

  @Override
  public B zeebeEndExecutionListener(final String type) {
    return zeebeExecutionListenersBuilder.zeebeEndExecutionListener(type);
  }

  @Override
  public B zeebeExecutionListener(
      final Consumer<ExecutionListenerBuilder> executionListenerBuilderConsumer) {
    return zeebeExecutionListenersBuilder.zeebeExecutionListener(executionListenerBuilderConsumer);
  }

  @Override
  public B zeebeProperty(final String name, final String value) {
    return zeebePropertiesBuilder.zeebeProperty(name, value);
  }
}
