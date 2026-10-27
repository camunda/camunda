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
package io.camunda.zeebe.model.bpmn.validation;

import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import java.util.function.UnaryOperator;

final class BpmnElementBuilder {

  private final String elementType;
  private final UnaryOperator<AbstractFlowNodeBuilder<?, ?>> builder;

  private BpmnElementBuilder(
      final String elementType, final UnaryOperator<AbstractFlowNodeBuilder<?, ?>> builder) {
    this.elementType = elementType;
    this.builder = builder;
  }

  public AbstractFlowNodeBuilder<?, ?> build(final AbstractFlowNodeBuilder<?, ?> processBuilder) {
    return builder.apply(processBuilder);
  }

  public static BpmnElementBuilder of(
      final String elementType, final UnaryOperator<AbstractFlowNodeBuilder<?, ?>> builder) {
    return new BpmnElementBuilder(elementType, builder);
  }

  public String getElementType() {
    return elementType;
  }

  @Override
  public String toString() {
    return elementType;
  }
}
