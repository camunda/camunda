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

import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeIoMapping;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeOutput;

public class ZeebeVariableMappingBuilderImpl<B extends AbstractBaseElementBuilder<?, ?>>
    implements ZeebeVariablesMappingBuilder<B> {

  private final B elementBuilder;

  public ZeebeVariableMappingBuilderImpl(final B elementBuilder) {
    this.elementBuilder = elementBuilder;
  }

  @Override
  public B zeebeInputExpression(final String sourceExpression, final String target) {
    final String expression = elementBuilder.asZeebeExpression(sourceExpression);
    return zeebeInput(expression, target);
  }

  @Override
  public B zeebeOutputExpression(final String sourceExpression, final String target) {
    final String expression = elementBuilder.asZeebeExpression(sourceExpression);
    return zeebeOutput(expression, target);
  }

  @Override
  public B zeebeInput(final String source, final String target) {
    final ZeebeIoMapping ioMapping =
        elementBuilder.getCreateSingleExtensionElement(ZeebeIoMapping.class);
    final ZeebeInput input = elementBuilder.createChild(ioMapping, ZeebeInput.class);
    input.setSource(source);
    input.setTarget(target);

    return elementBuilder;
  }

  @Override
  public B zeebeOutput(final String source, final String target) {
    final ZeebeIoMapping ioMapping =
        elementBuilder.getCreateSingleExtensionElement(ZeebeIoMapping.class);
    final ZeebeOutput input = elementBuilder.createChild(ioMapping, ZeebeOutput.class);
    input.setSource(source);
    input.setTarget(target);

    return elementBuilder;
  }
}
