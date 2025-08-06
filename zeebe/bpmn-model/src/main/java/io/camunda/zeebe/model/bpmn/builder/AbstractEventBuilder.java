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
import io.camunda.zeebe.model.bpmn.instance.Event;
import java.util.function.Consumer;

/**
 * @author Sebastian Menski
 */
public abstract class AbstractEventBuilder<B extends AbstractEventBuilder<B, E>, E extends Event>
    extends AbstractFlowNodeBuilder<B, E>
    implements ZeebeExecutionListenersBuilder<B>, ZeebePropertiesBuilder<B> {

  private final ZeebeExecutionListenersBuilder<B> zeebeExecutionListenersBuilder;
  private final ZeebePropertiesBuilder<B> zeebePropertiesBuilder;

  protected AbstractEventBuilder(
      final BpmnModelInstance modelInstance, final E element, final Class<?> selfType) {
    super(modelInstance, element, selfType);
    zeebeExecutionListenersBuilder = new ZeebeExecutionListenersBuilderImpl<>(myself);
    zeebePropertiesBuilder = new ZeebePropertiesBuilderImpl<>(myself);
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
