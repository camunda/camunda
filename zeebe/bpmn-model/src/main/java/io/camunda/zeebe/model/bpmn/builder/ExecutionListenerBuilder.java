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

import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListener;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListenerEventType;

public class ExecutionListenerBuilder {
  private final ZeebeExecutionListener element;
  private final AbstractBaseElementBuilder<?, ?> elementBuilder;

  protected ExecutionListenerBuilder(
      final ZeebeExecutionListener element, final AbstractBaseElementBuilder<?, ?> elementBuilder) {
    this.element = element;
    this.elementBuilder = elementBuilder;
  }

  public ExecutionListenerBuilder eventType(final ZeebeExecutionListenerEventType eventType) {
    element.setEventType(eventType);
    return this;
  }

  public ExecutionListenerBuilder start() {
    return eventType(ZeebeExecutionListenerEventType.start);
  }

  public ExecutionListenerBuilder end() {
    return eventType(ZeebeExecutionListenerEventType.end);
  }

  public ExecutionListenerBuilder type(final String type) {
    element.setType(type);
    return this;
  }

  public ExecutionListenerBuilder typeExpression(final String typeExpression) {
    return type(elementBuilder.asZeebeExpression(typeExpression));
  }

  public ExecutionListenerBuilder retries(final String retries) {
    element.setRetries(retries);
    return this;
  }

  public ExecutionListenerBuilder retriesExpression(final String retriesExpression) {
    return retries(elementBuilder.asZeebeExpression(retriesExpression));
  }
}
